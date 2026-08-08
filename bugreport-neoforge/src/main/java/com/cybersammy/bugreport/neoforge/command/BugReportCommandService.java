package com.cybersammy.bugreport.neoforge.command;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.validation.ValidationResult;
import com.cybersammy.bugreport.core.form.FieldValidator;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import com.cybersammy.bugreport.core.session.CancellationReason;
import com.cybersammy.bugreport.core.session.ReportSession;
import com.cybersammy.bugreport.core.session.ReportSessionFactory;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionSnapshot;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import com.cybersammy.bugreport.core.session.UnknownReportCategoryException;
import com.cybersammy.bugreport.core.source.CategorySourcePlan;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Client-command application service bound to the current immutable provider registry. */
public final class BugReportCommandService {
    private final Supplier<ProviderRegistrySnapshot> registrySupplier;
    private final Map<ReportSessionId, ReportSession> sessions = new LinkedHashMap<>();
    private final Map<ReportSessionId, FormSubmission> confirmedForms = new LinkedHashMap<>();
    private final Map<ReportSessionId, ReviewedCollectionPlan> collectionPlans = new LinkedHashMap<>();

    public BugReportCommandService(Supplier<ProviderRegistrySnapshot> registrySupplier) {
        this.registrySupplier = Objects.requireNonNull(registrySupplier, "registrySupplier");
    }

    public List<Message> help() {
        return List.of(new Message("bugreport.command.help"));
    }

    public List<Message> listProviders() {
        List<RegisteredProvider> providers = registry().providers();
        if (providers.isEmpty()) {
            return List.of(new Message("bugreport.command.list.empty"));
        }
        return providers.stream()
                .map(provider -> new Message(
                        "bugreport.command.list.provider",
                        provider.id().toString(), provider.support().state().name()))
                .toList();
    }

    /** Returns immutable provider data suitable for a first-party client selector. */
    public List<ProviderChoice> providerChoices() {
        return registry().providers().stream()
                .map(provider -> new ProviderChoice(provider.id(), provider.specification().labelKey(),
                        provider.support().state()))
                .toList();
    }

    /** Finds one registered provider choice from the current trusted registry. */
    public Optional<ProviderChoice> providerChoice(ProviderId providerId) {
        return registry().find(Objects.requireNonNull(providerId, "providerId"))
                .map(provider -> new ProviderChoice(provider.id(), provider.specification().labelKey(),
                        provider.support().state()));
    }

    /** Returns categories declared by the requested registered provider. */
    public List<CategoryChoice> categoryChoices(ProviderId providerId) {
        return registry().find(Objects.requireNonNull(providerId, "providerId"))
                .map(provider -> provider.specification().categories().values().stream()
                        .map(category -> new CategoryChoice(category.id(), category.labelKey()))
                        .toList())
                .orElse(List.of());
    }

    public synchronized List<Message> create(String providerValue, String categoryValue) {
        final ProviderId providerId;
        try {
            providerId = ProviderId.parse(providerValue);
        } catch (IllegalArgumentException exception) {
            return List.of(new Message("bugreport.command.error.invalid_provider"));
        }
        RegisteredProvider provider = registry().find(providerId).orElse(null);
        if (provider == null) {
            return List.of(new Message("bugreport.command.error.unknown_provider", providerId.toString()));
        }
        if (provider.support().state() == ProviderSupportState.DISABLED) {
            return List.of(new Message("bugreport.command.error.provider_unavailable", providerId.toString()));
        }

        final CategoryId categoryId;
        try {
            categoryId = categoryValue == null ? null : CategoryId.of(categoryValue);
        } catch (IllegalArgumentException exception) {
            return List.of(new Message("bugreport.command.error.invalid_category"));
        }
        ReportSession session = new ReportSessionFactory(registry()).create(ReportSessionId.random(), providerId);
        if (categoryId != null) {
            try {
                session.selectCategory(categoryId);
            } catch (UnknownReportCategoryException exception) {
                return List.of(new Message("bugreport.command.error.unknown_category", categoryId.toString()));
            }
        }
        sessions.put(session.snapshot().id(), session);
        return List.of(new Message(
                "bugreport.command.create.success", session.snapshot().id().toString()));
    }

    public synchronized List<Message> open(String sessionValue) {
        ReportSession session = session(sessionValue);
        if (session == null) {
            return List.of(new Message("bugreport.command.error.unknown_session"));
        }
        ReportSessionSnapshot snapshot = session.snapshot();
        return List.of(new Message(
                "bugreport.command.open.summary",
                snapshot.id().toString(), snapshot.providerSpecification().id().toString(),
                snapshot.state().name()));
    }

    /** Returns the trusted immutable identity and declaration for one selected session form. */
    public synchronized Optional<FormView> form(String sessionValue) {
        ReportSession session = session(sessionValue);
        if (session == null) {
            return Optional.empty();
        }
        ReportSessionSnapshot snapshot = session.snapshot();
        return snapshot.selectedCategory().map(category -> new FormView(
                snapshot.id(), snapshot.providerSpecification().id(), category,
                snapshot.state(), snapshot.revision()));
    }

    /** Validates one already typed UI submission against the session's trusted selected category. */
    public synchronized FormResult submitForm(String sessionValue, FormSubmission submission) {
        ReportSession session = session(sessionValue);
        if (session == null) {
            return FormResult.missingSession();
        }
        CategorySpecification category = session.snapshot().selectedCategory().orElseThrow();
        ValidationResult validation = FieldValidator.validate(
                category, Objects.requireNonNull(submission, "submission"));
        return new FormResult(validation, false);
    }

    /**
     * Revalidates and accepts form values before atomically advancing to collection planning.
     *
     * <p>Only this method creates the application-owned association between a session and a
     * complete form submission. A client-side "valid" indicator is never authority to advance.
     */
    public synchronized FormConfirmationResult confirmForm(
            String sessionValue, FormSubmission submission) {
        ReportSession session = session(sessionValue);
        if (session == null) {
            return FormConfirmationResult.unknownSession();
        }
        ReportSessionSnapshot snapshot = session.snapshot();
        if (snapshot.state() != ReportSessionState.FORM_IN_PROGRESS) {
            return FormConfirmationResult.invalidState();
        }
        CategorySpecification category = snapshot.selectedCategory().orElseThrow();
        ValidationResult validation = FieldValidator.validate(
                category, Objects.requireNonNull(submission, "submission"));
        if (!validation.isValid()) {
            return FormConfirmationResult.invalid(validation);
        }

        session.transitionTo(ReportSessionState.COLLECTION_PLANNED);
        ReportSessionSnapshot planned = session.snapshot();
        confirmedForms.put(planned.id(), submission);
        return FormConfirmationResult.accepted(new CollectionPlanRequest(
                planned.id(),
                planned.revision(),
                planned.providerSpecification().id(),
                planned.providerSpecification().version(),
                category.id()));
    }

    /** Accepts a user-reviewed source selection bound to one exact form-confirmation revision. */
    public synchronized boolean acceptCollectionPlan(
            CollectionPlanRequest request, ReviewedCollectionPlan reviewedPlan) {
        Objects.requireNonNull(request, "request");
        ReportSession session = sessions.get(request.sessionId());
        if (session == null) {
            return false;
        }
        ReportSessionSnapshot snapshot = session.snapshot();
        if (snapshot.state() != ReportSessionState.COLLECTION_PLANNED
                || snapshot.revision() != request.collectionPlanRevision()) {
            return false;
        }
        CategorySpecification category = snapshot.selectedCategory().orElseThrow();
        CategorySourcePlan trustedPlan = Objects.requireNonNull(reviewedPlan, "reviewedPlan").plan();
        if (!snapshot.id().equals(request.sessionId())
                || !snapshot.providerSpecification().id().equals(request.providerId())
                || !snapshot.providerSpecification().version().equals(request.providerVersion())
                || !category.id().equals(request.categoryId())
                || !snapshot.providerSpecification().id().equals(trustedPlan.providerId())
                || !snapshot.providerSpecification().version().equals(trustedPlan.providerVersion())
                || !category.id().equals(trustedPlan.categoryId())) {
            return false;
        }
        collectionPlans.put(snapshot.id(), reviewedPlan);
        return true;
    }

    /** Returns to form editing and revokes every authority issued for the previous confirmation. */
    public synchronized boolean returnToForm(String sessionValue) {
        ReportSession session = session(sessionValue);
        if (session == null || session.snapshot().state() != ReportSessionState.COLLECTION_PLANNED) {
            return false;
        }
        session.transitionTo(ReportSessionState.FORM_IN_PROGRESS);
        collectionPlans.remove(session.snapshot().id());
        confirmedForms.remove(session.snapshot().id());
        return true;
    }

    /** Returns the exact validated submission accepted for the current collection-plan generation. */
    public synchronized Optional<FormSubmission> confirmedForm(String sessionValue) {
        ReportSession session = session(sessionValue);
        return session == null ? Optional.empty()
                : Optional.ofNullable(confirmedForms.get(session.snapshot().id()));
    }

    /** Returns the exact user-reviewed source selection accepted for the current plan generation. */
    public synchronized Optional<ReviewedCollectionPlan> collectionPlan(String sessionValue) {
        ReportSession session = session(sessionValue);
        return session == null ? Optional.empty()
                : Optional.ofNullable(collectionPlans.get(session.snapshot().id()));
    }

    public synchronized List<Message> discard(String sessionValue) {
        ReportSessionId id = parseSessionId(sessionValue);
        if (id == null || !sessions.containsKey(id)) {
            return List.of(new Message("bugreport.command.error.unknown_session"));
        }
        ReportSession session = sessions.get(id);
        session.cancel(CancellationReason.USER_REQUESTED);
        sessions.remove(id);
        confirmedForms.remove(id);
        collectionPlans.remove(id);
        return List.of(new Message("bugreport.command.discard.success", id.toString()));
    }

    private ProviderRegistrySnapshot registry() {
        return Objects.requireNonNull(registrySupplier.get(), "provider registry");
    }

    private ReportSession session(String value) {
        ReportSessionId id = parseSessionId(value);
        return id == null ? null : sessions.get(id);
    }

    private static ReportSessionId parseSessionId(String value) {
        try {
            return ReportSessionId.parse(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Safe, localized command response without exception text or filesystem data. */
    public record Message(String translationKey, Object... arguments) {
        public Message {
            Objects.requireNonNull(translationKey, "translationKey");
            arguments = arguments == null ? new Object[0] : arguments.clone();
        }
    }

    public record ProviderChoice(ProviderId id, LocalizationKey labelKey,
            ProviderSupportState supportState) {}

    public record CategoryChoice(CategoryId id, LocalizationKey labelKey) {}

    /** Trusted immutable view used by the first-party form adapter. */
    public record FormView(
            ReportSessionId sessionId,
            ProviderId providerId,
            CategorySpecification category,
            ReportSessionState state,
            long revision) {
        public FormView {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(state, "state");
            if (revision < 0) {
                throw new IllegalArgumentException("revision must be non-negative");
            }
        }
    }

    /** Result of pure form validation; validation does not mutate the report session. */
    public record FormResult(ValidationResult validation, boolean unknownSession) {
        public FormResult {
            if (unknownSession == (validation != null)) {
                throw new IllegalArgumentException(
                        "Exactly one of validation or unknownSession must be present");
            }
        }

        private static FormResult missingSession() {
            return new FormResult(null, true);
        }
    }

    /** Result of confirming a form for collection planning. */
    public record FormConfirmationResult(
            FormConfirmationStatus status,
            Optional<ValidationResult> validation,
            Optional<CollectionPlanRequest> planRequest) {
        public FormConfirmationResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(validation, "validation");
            Objects.requireNonNull(planRequest, "planRequest");
            if (status == FormConfirmationStatus.ACCEPTED
                    != (validation.isPresent() && planRequest.isPresent())) {
                throw new IllegalArgumentException(
                        "Accepted form confirmation requires validation and plan request");
            }
            if (status == FormConfirmationStatus.INVALID && validation.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid form confirmation requires validation details");
            }
            if (status != FormConfirmationStatus.INVALID
                    && status != FormConfirmationStatus.ACCEPTED
                    && validation.isPresent()) {
                throw new IllegalArgumentException(
                        "Only accepted or invalid form confirmation carries validation details");
            }
            if (status != FormConfirmationStatus.ACCEPTED && planRequest.isPresent()) {
                throw new IllegalArgumentException(
                        "Only accepted form confirmation carries a plan request");
            }
        }

        private static FormConfirmationResult accepted(CollectionPlanRequest request) {
            return new FormConfirmationResult(
                    FormConfirmationStatus.ACCEPTED,
                    Optional.of(ValidationResult.valid()),
                    Optional.of(request));
        }

        private static FormConfirmationResult invalid(ValidationResult validation) {
            return new FormConfirmationResult(
                    FormConfirmationStatus.INVALID,
                    Optional.of(Objects.requireNonNull(validation, "validation")),
                    Optional.empty());
        }

        private static FormConfirmationResult unknownSession() {
            return new FormConfirmationResult(
                    FormConfirmationStatus.UNKNOWN_SESSION, Optional.empty(), Optional.empty());
        }

        private static FormConfirmationResult invalidState() {
            return new FormConfirmationResult(
                    FormConfirmationStatus.INVALID_STATE, Optional.empty(), Optional.empty());
        }
    }

    /** Stable non-sensitive outcome for the form-to-plan lifecycle boundary. */
    public enum FormConfirmationStatus {
        ACCEPTED,
        INVALID,
        UNKNOWN_SESSION,
        INVALID_STATE
    }

    /** Identity-only request for client-side source planning. */
    public record CollectionPlanRequest(
            ReportSessionId sessionId,
            long collectionPlanRevision,
            ProviderId providerId,
            com.cybersammy.bugreport.api.version.ProviderVersion providerVersion,
            CategoryId categoryId) {
        public CollectionPlanRequest {
            Objects.requireNonNull(sessionId, "sessionId");
            if (collectionPlanRevision < 0) {
                throw new IllegalArgumentException("collectionPlanRevision must be non-negative");
            }
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(providerVersion, "providerVersion");
            Objects.requireNonNull(categoryId, "categoryId");
        }
    }
}
