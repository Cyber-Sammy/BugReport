package com.cybersammy.bugreport.neoforge.command;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.validation.ValidationResult;
import com.cybersammy.bugreport.core.form.FieldValidator;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.draft.DraftLoadBatch;
import com.cybersammy.bugreport.core.draft.DraftLoadOutcome;
import com.cybersammy.bugreport.core.draft.DraftResolutionException;
import com.cybersammy.bugreport.core.draft.DraftResolutionCode;
import com.cybersammy.bugreport.core.draft.DraftResolver;
import com.cybersammy.bugreport.core.draft.ReportDraft;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import com.cybersammy.bugreport.core.session.CancellationReason;
import com.cybersammy.bugreport.core.session.ReportSession;
import com.cybersammy.bugreport.core.session.ReportSessionFactory;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.RecoveredReportSession;
import com.cybersammy.bugreport.core.session.ReportSessionRecoveryException;
import com.cybersammy.bugreport.core.session.ReportSessionRecoveryCode;
import com.cybersammy.bugreport.core.session.ReportSessionSnapshot;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import com.cybersammy.bugreport.core.session.UnknownReportCategoryException;
import com.cybersammy.bugreport.core.source.CategorySourcePlan;
import com.cybersammy.bugreport.core.source.CollectionPlanFingerprint;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
import com.cybersammy.bugreport.core.workspace.FileCollectionResult;
import com.cybersammy.bugreport.core.workspace.ReportWorkspace;
import com.cybersammy.bugreport.core.workspace.PreparedWorkspaceSnapshot;
import com.cybersammy.bugreport.core.workspace.WorkspaceReviewCoordinator;
import com.cybersammy.bugreport.core.sanitization.SanitizationCaseSensitivity;
import com.cybersammy.bugreport.core.manifest.ManifestCollectionStatus;
import com.cybersammy.bugreport.core.manifest.ManifestEntry;
import com.cybersammy.bugreport.core.manifest.ManifestEntryProvenance;
import com.cybersammy.bugreport.core.manifest.ManifestEnvironment;
import com.cybersammy.bugreport.core.manifest.ManifestProducer;
import com.cybersammy.bugreport.core.manifest.ManifestTarget;
import com.cybersammy.bugreport.core.manifest.ReportManifest;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlanFactory;
import com.cybersammy.bugreport.core.transport.LocalArchiveDestination;
import com.cybersammy.bugreport.core.transport.NeoForgeLocalExportTransportAdapter;
import com.cybersammy.bugreport.core.transport.ReportTransportResult;
import com.cybersammy.bugreport.core.transport.TransportRunControl;
import com.cybersammy.bugreport.core.workspace.PreparedWorkspaceArtifact;
import com.cybersammy.bugreport.core.workspace.ReviewedWorkspaceArtifact;
import com.cybersammy.bugreport.core.workspace.CollectedGeneratedArtifact;
import com.cybersammy.bugreport.core.workspace.CollectedSourceFile;
import com.cybersammy.bugreport.core.history.ReportHistoryEntry;
import com.cybersammy.bugreport.core.history.ReportHistoryStatus;
import com.cybersammy.bugreport.core.source.SourceProvenance;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.version.ApiVersion;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Client-command application service bound to the current immutable provider registry. */
public final class BugReportCommandService {
    private final Supplier<ProviderRegistrySnapshot> registrySupplier;
    private final ReportHistoryRecorder history;
    private final ReportDraftPersistence drafts;
    private final Map<ReportSessionId, ReportSession> sessions = new LinkedHashMap<>();
    private final Map<ReportSessionId, FormSubmission> confirmedForms = new LinkedHashMap<>();
    private final Map<ReportSessionId, PersistedFormDraft> persistedForms = new LinkedHashMap<>();
    private final Set<ReportSessionId> persistedDraftFiles = new LinkedHashSet<>();
    private final Map<ReportSessionId, ReviewedCollectionPlan> collectionPlans = new LinkedHashMap<>();
    private final Map<ReportSessionId, FileCollectionResult> collectionResults = new LinkedHashMap<>();
    private final Map<ReportSessionId, ReportWorkspace> collectionWorkspaces = new LinkedHashMap<>();
    private final Map<ReportSessionId, WorkspaceReviewCoordinator.SanitizationBatch>
            sanitizationBatches = new LinkedHashMap<>();
    private final Map<ReportSessionId, PreparedWorkspaceSnapshot> preparedSnapshots =
            new LinkedHashMap<>();
    private final Map<ReportSessionId, SanitizationExecutionRequest> activeSanitizations =
            new LinkedHashMap<>();
    private final Map<ReportSessionId, WorkspaceReviewRequest> activeReviews =
            new LinkedHashMap<>();
    private final Map<ReportSessionId, LocalExportPreparationRequest> activeExportPreparations =
            new LinkedHashMap<>();
    private final Map<ReportSessionId, LocalExportRequest> activeExports = new LinkedHashMap<>();
    private final Map<ReportSessionId, RecoveredReportSession> recoverableDrafts =
            new LinkedHashMap<>();
    private final Map<ReportSessionId, DraftRecoveryChoice> rejectedDrafts =
            new LinkedHashMap<>();
    private boolean draftsScanned;
    private boolean draftScanFailed;

    public BugReportCommandService(Supplier<ProviderRegistrySnapshot> registrySupplier) {
        this(
                registrySupplier,
                ReportHistoryRecorder.empty(),
                ReportDraftPersistence.empty());
    }

    public BugReportCommandService(
            Supplier<ProviderRegistrySnapshot> registrySupplier, ReportHistoryRecorder history) {
        this(registrySupplier, history, ReportDraftPersistence.empty());
    }

    public BugReportCommandService(
            Supplier<ProviderRegistrySnapshot> registrySupplier,
            ReportHistoryRecorder history,
            ReportDraftPersistence drafts) {
        this.registrySupplier = Objects.requireNonNull(registrySupplier, "registrySupplier");
        this.history = Objects.requireNonNull(history, "history");
        this.drafts = Objects.requireNonNull(drafts, "drafts");
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

    /** Persists current typed form values under a newly published session revision. */
    public synchronized DraftSaveStatus saveFormDraft(
            String sessionValue, FormSubmission submission) {
        ReportSession session = session(sessionValue);
        if (session == null) {
            return DraftSaveStatus.UNKNOWN_SESSION;
        }
        if (!drafts.available()) {
            return DraftSaveStatus.UNAVAILABLE;
        }
        if (session.snapshot().state() != ReportSessionState.FORM_IN_PROGRESS) {
            return DraftSaveStatus.INVALID_STATE;
        }
        return persistFormDraft(session, Objects.requireNonNull(submission, "submission"))
                ? DraftSaveStatus.SAVED
                : DraftSaveStatus.FAILED;
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

        if (drafts.available()
                && !Optional.ofNullable(persistedForms.get(snapshot.id()))
                        .filter(persisted -> persisted.revision() == snapshot.revision())
                        .map(PersistedFormDraft::submission)
                        .filter(submission::equals)
                        .isPresent()) {
            return FormConfirmationResult.persistenceFailed();
        }
        if (drafts.available()) {
            try {
                drafts.delete(snapshot.id());
            } catch (RuntimeException failure) {
                return FormConfirmationResult.persistenceFailed();
            }
            persistedForms.remove(snapshot.id());
            persistedDraftFiles.remove(snapshot.id());
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

    /** Begins collection only from a currently accepted user-reviewed source selection. */
    public synchronized Optional<CollectionExecutionRequest> beginCollection(String sessionValue) {
        ReportSession session = session(sessionValue);
        if (session == null || session.snapshot().state() != ReportSessionState.COLLECTION_PLANNED) {
            return Optional.empty();
        }
        ReportSessionSnapshot planned = session.snapshot();
        ReviewedCollectionPlan reviewedPlan = collectionPlans.get(planned.id());
        if (reviewedPlan == null) {
            return Optional.empty();
        }
        session.transitionTo(ReportSessionState.COLLECTING);
        ReportSessionSnapshot collecting = session.snapshot();
        return Optional.of(new CollectionExecutionRequest(
                collecting.id(),
                collecting.revision(),
                reviewedPlan,
                CollectionPlanFingerprint.from(reviewedPlan.selectedFilePlan())));
    }

    /** Records a terminal collection result only for the exact active collection generation. */
    public synchronized boolean acceptCollectionResult(
            CollectionExecutionRequest request, FileCollectionResult result, ReportWorkspace workspace) {
        Objects.requireNonNull(request, "request");
        FileCollectionResult terminal = Objects.requireNonNull(result, "result");
        ReportWorkspace trustedWorkspace = Objects.requireNonNull(workspace, "workspace");
        ReportSession session = sessions.get(request.sessionId());
        if (session == null) {
            return false;
        }
        ReportSessionSnapshot collecting = session.snapshot();
        if (collecting.state() != ReportSessionState.COLLECTING
                || collecting.revision() != request.collectionRevision()
                || !collecting.id().equals(trustedWorkspace.sessionId())
                || !matchesPlan(request, terminal)) {
            return false;
        }
        switch (terminal.status()) {
            case COMPLETE -> session.transitionTo(ReportSessionState.SANITIZING);
            case PARTIAL -> session.transitionTo(ReportSessionState.PARTIALLY_COLLECTED);
            case FAILED -> session.transitionTo(ReportSessionState.FAILED_COLLECTION);
            case CANCELLED -> session.cancel(CancellationReason.USER_REQUESTED);
        }
        collectionResults.put(collecting.id(), terminal);
        collectionWorkspaces.put(collecting.id(), trustedWorkspace);
        return true;
    }

    /** Marks an active collection generation failed when product-owned setup cannot start it. */
    public synchronized boolean failCollectionSetup(CollectionExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        ReportSession session = sessions.get(request.sessionId());
        if (session == null) {
            return false;
        }
        ReportSessionSnapshot collecting = session.snapshot();
        if (collecting.state() != ReportSessionState.COLLECTING
                || collecting.revision() != request.collectionRevision()) {
            return false;
        }
        session.transitionTo(ReportSessionState.FAILED_COLLECTION);
        return true;
    }

    /** Returns the terminal result accepted for this report session, if any. */
    public synchronized Optional<FileCollectionResult> collectionResult(String sessionValue) {
        ReportSession session = session(sessionValue);
        return session == null ? Optional.empty()
                : Optional.ofNullable(collectionResults.get(session.snapshot().id()));
    }

    /** Returns the Core-owned workspace only for a terminal accepted collection result. */
    public synchronized Optional<ReportWorkspace> collectionWorkspace(String sessionValue) {
        ReportSession session = session(sessionValue);
        return session == null ? Optional.empty()
                : Optional.ofNullable(collectionWorkspaces.get(session.snapshot().id()));
    }

    /** Starts sanitization from an exact complete or partial accepted collection result. */
    public synchronized Optional<SanitizationExecutionRequest> beginSanitization(
            String sessionValue) {
        ReportSession session = session(sessionValue);
        if (session == null) {
            return Optional.empty();
        }
        ReportSessionSnapshot snapshot = session.snapshot();
        if (snapshot.state() == ReportSessionState.PARTIALLY_COLLECTED) {
            session.transitionTo(ReportSessionState.SANITIZING);
            snapshot = session.snapshot();
        }
        if (snapshot.state() != ReportSessionState.SANITIZING) {
            return Optional.empty();
        }
        FileCollectionResult files = collectionResults.get(snapshot.id());
        ReportWorkspace workspace = collectionWorkspaces.get(snapshot.id());
        if (files == null || workspace == null
                || (files.status() != FileCollectionResult.Status.COMPLETE
                        && files.status() != FileCollectionResult.Status.PARTIAL)
                || activeSanitizations.containsKey(snapshot.id())) {
            return Optional.empty();
        }
        SanitizationExecutionRequest request = new SanitizationExecutionRequest(
                snapshot.id(), snapshot.revision(), snapshot, files, workspace);
        activeSanitizations.put(snapshot.id(), request);
        return Optional.of(request);
    }

    /** Executes only the product-owned policy for one exact service-issued sanitization token. */
    public Optional<WorkspaceReviewRequest> executeSanitization(
            SanitizationExecutionRequest request, CancellationSignal cancellation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        synchronized (this) {
            ReportSession session = sessions.get(request.sessionId());
            if (session == null
                    || activeSanitizations.get(request.sessionId()) != request
                    || session.snapshot().state() != ReportSessionState.SANITIZING
                    || session.snapshot().revision() != request.sanitizationRevision()) {
                return Optional.empty();
            }
        }

        WorkspaceReviewCoordinator.SanitizationBatch evidence =
                WorkspaceReviewCoordinator.sanitizeProduct(
                        request.session(),
                        request.files(),
                        request.workspace(),
                        System.getProperty("user.home"),
                        System.getProperty("user.name"),
                        File.separatorChar == '\\'
                                ? SanitizationCaseSensitivity.INSENSITIVE
                                : SanitizationCaseSensitivity.SENSITIVE,
                        cancellation);

        synchronized (this) {
            ReportSession session = sessions.get(request.sessionId());
            if (session == null
                    || activeSanitizations.get(request.sessionId()) != request
                    || session.snapshot().state() != ReportSessionState.SANITIZING
                    || session.snapshot().revision() != request.sanitizationRevision()
                    || !WorkspaceReviewCoordinator.matches(
                            evidence, request.files(), request.workspace())) {
                return Optional.empty();
            }
            session.transitionTo(ReportSessionState.REVIEW_REQUIRED);
            ReportSessionSnapshot review = session.snapshot();
            activeSanitizations.remove(review.id());
            sanitizationBatches.put(review.id(), evidence);
            WorkspaceReviewRequest reviewRequest = new WorkspaceReviewRequest(
                    review.id(), review.revision(), review, evidence);
            activeReviews.put(review.id(), reviewRequest);
            return Optional.of(reviewRequest);
        }
    }

    /** Marks the exact active sanitization generation failed without exposing internal causes. */
    public synchronized boolean failSanitization(SanitizationExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        ReportSession session = sessions.get(request.sessionId());
        if (session == null || activeSanitizations.get(request.sessionId()) != request) {
            return false;
        }
        ReportSessionSnapshot snapshot = session.snapshot();
        if (snapshot.state() != ReportSessionState.SANITIZING
                || snapshot.revision() != request.sanitizationRevision()) {
            return false;
        }
        session.transitionTo(ReportSessionState.FAILED_SANITIZATION);
        activeSanitizations.remove(snapshot.id());
        return true;
    }

    /** Converts UI decision data into package authority for the exact service-issued review. */
    public Optional<PreparedWorkspaceSnapshot> confirmReview(
            WorkspaceReviewRequest request, ReviewDecision decision) {
        Objects.requireNonNull(request, "request");
        ReviewDecision choice = Objects.requireNonNull(decision, "decision");
        synchronized (this) {
            ReportSession session = sessions.get(request.sessionId());
            if (session == null
                    || activeReviews.get(request.sessionId()) != request
                    || session.snapshot().state() != ReportSessionState.REVIEW_REQUIRED
                    || session.snapshot().revision() != request.reviewRevision()) {
                return Optional.empty();
            }
        }

        WorkspaceReviewCoordinator.PreparedReview authority = WorkspaceReviewCoordinator.prepare(
                request.session(),
                request.batch(),
                choice.includedArtifacts(),
                choice.explicitlyReviewedArtifacts());

        synchronized (this) {
            ReportSession session = sessions.get(request.sessionId());
            if (session == null
                    || activeReviews.get(request.sessionId()) != request
                    || session.snapshot().state() != ReportSessionState.REVIEW_REQUIRED
                    || session.snapshot().revision() != request.reviewRevision()
                    || !authority.snapshot().reviewedSnapshot().sessionId()
                            .equals(session.snapshot().id())
                    || !authority.belongsTo(request.batch())
                    || sanitizationBatches.get(session.snapshot().id()) != request.batch()) {
                return Optional.empty();
            }
            session.transitionTo(ReportSessionState.READY);
            preparedSnapshots.put(session.snapshot().id(), authority.snapshot());
            activeReviews.remove(session.snapshot().id());
            return Optional.of(authority.snapshot());
        }
    }

    public synchronized Optional<PreparedWorkspaceSnapshot> preparedSnapshot(String sessionValue) {
        ReportSession session = session(sessionValue);
        return session == null ? Optional.empty()
                : Optional.ofNullable(preparedSnapshots.get(session.snapshot().id()));
    }

    /** Issues exact authority to build a package summary for a READY session. */
    public synchronized Optional<LocalExportPreparationRequest> beginLocalExport(String sessionValue) {
        ReportSession session = session(sessionValue);
        if (session == null || session.snapshot().state() != ReportSessionState.READY) {
            return Optional.empty();
        }
        ReportSessionSnapshot snapshot = session.snapshot();
        PreparedWorkspaceSnapshot prepared = preparedSnapshots.get(snapshot.id());
        ReportWorkspace workspace = collectionWorkspaces.get(snapshot.id());
        FormSubmission fields = confirmedForms.get(snapshot.id());
        if (prepared == null || workspace == null || fields == null
                || activeExportPreparations.containsKey(snapshot.id())
                || activeExports.containsKey(snapshot.id())) {
            return Optional.empty();
        }
        LocalExportPreparationRequest request = new LocalExportPreparationRequest(
                snapshot.id(), snapshot.revision(), snapshot, prepared, workspace, fields);
        activeExportPreparations.put(snapshot.id(), request);
        return Optional.of(request);
    }

    /** Builds the exact package plan off-thread for one service-issued export preparation token. */
    public Optional<LocalExportRequest> prepareLocalExport(LocalExportPreparationRequest request) {
        Objects.requireNonNull(request, "request");
        synchronized (this) {
            if (!isActiveExportPreparation(request)) {
                return Optional.empty();
            }
        }
        ReportPackagePlan plan = ReportPackagePlanFactory.create(
                request.prepared(), request.workspace(), manifest(request), true);
        synchronized (this) {
            if (!isActiveExportPreparation(request)) {
                return Optional.empty();
            }
            LocalExportRequest export = new LocalExportRequest(
                    request.sessionId(), request.readyRevision(), request.session(), request.workspace(), plan);
            activeExportPreparations.remove(request.sessionId());
            activeExports.put(request.sessionId(), export);
            return Optional.of(export);
        }
    }

    /** Performs a user-confirmed local export only for the exact prepared package token. */
    public Optional<ReportTransportResult> executeLocalExport(
            LocalExportRequest request,
            Path exportDirectory,
            String archiveFileName,
            TransportRunControl control) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(exportDirectory, "exportDirectory");
        Objects.requireNonNull(control, "control");
        Path destination;
        try {
            destination = safeArchiveDestination(exportDirectory, archiveFileName);
        } catch (IOException | IllegalArgumentException failure) {
            return Optional.empty();
        }
        synchronized (this) {
            if (!isActiveExport(request)) {
                return Optional.empty();
            }
            sessions.get(request.sessionId()).transitionTo(ReportSessionState.DELIVERING);
        }
        ReportTransportResult result;
        try {
            result = NeoForgeLocalExportTransportAdapter.executeConfirmed(
                    new ConfirmedLocalExport(
                            request.plan(), request.workspace(), new LocalArchiveDestination(destination)),
                    control);
        } catch (RuntimeException failure) {
            synchronized (this) {
                failActiveExport(request);
            }
            return Optional.empty();
        }
        synchronized (this) {
            ReportSession session = sessions.get(request.sessionId());
            if (session == null || activeExports.get(request.sessionId()) != request
                    || session.snapshot().state() != ReportSessionState.DELIVERING) {
                return Optional.empty();
            }
            session.transitionTo(result.status() == ReportTransportResult.Status.SUCCESS
                    ? ReportSessionState.COMPLETED : ReportSessionState.FAILED_DELIVERY);
            activeExports.remove(request.sessionId());
            recordDeliveryHistory(session.snapshot(), result);
            return Optional.of(result);
        }
    }

    /** Returns a failed local delivery to READY so the user can prepare a fresh export attempt. */
    public synchronized boolean retryLocalExport(String sessionValue) {
        ReportSession session = session(sessionValue);
        if (session == null || session.snapshot().state() != ReportSessionState.FAILED_DELIVERY) {
            return false;
        }
        session.transitionTo(ReportSessionState.READY);
        return true;
    }

    /** Returns the path-free terminal summaries persisted by the product history recorder. */
    public List<ReportHistoryEntry> reportHistory() {
        return history.entries();
    }

    /** Returns one bounded, lazily loaded view of restart-recoverable drafts. */
    public synchronized DraftRecoveryOverview draftRecovery() {
        scanDraftsIfNeeded();
        List<DraftRecoveryChoice> choices = new java.util.ArrayList<>();
        recoverableDrafts.forEach(
                (id, recovered) -> choices.add(recoveryChoice(recovered)));
        choices.addAll(rejectedDrafts.values());
        choices.sort(java.util.Comparator.comparing(choice -> choice.sessionId().toString()));
        return new DraftRecoveryOverview(choices, draftScanFailed);
    }

    /** Consumes one recoverable persisted draft and installs only its safe session/form state. */
    public synchronized Optional<DraftResume> resumeDraft(ReportSessionId sessionId) {
        scanDraftsIfNeeded();
        ReportSessionId id = Objects.requireNonNull(sessionId, "sessionId");
        if (sessions.containsKey(id)) {
            return Optional.empty();
        }
        RecoveredReportSession recovered = recoverableDrafts.remove(id);
        if (recovered == null) {
            return Optional.empty();
        }
        ReportSession session = recovered.session();
        ReportSessionSnapshot snapshot = session.snapshot();
        if (snapshot.state() != ReportSessionState.FORM_IN_PROGRESS
                || snapshot.selectedCategory().isEmpty()) {
            recoverableDrafts.put(id, recovered);
            return Optional.empty();
        }
        sessions.put(id, session);
        persistedDraftFiles.add(id);
        return Optional.of(
                new DraftResume(
                        id,
                        snapshot.providerSpecification().id(),
                        snapshot.selectedCategory().orElseThrow().id(),
                        recovered.formSubmission(),
                        recovered.recordedState()));
    }

    /** Deletes one still-pending canonical restart draft without creating a live session. */
    public synchronized boolean discardRecoveredDraft(ReportSessionId sessionId) {
        scanDraftsIfNeeded();
        ReportSessionId id = Objects.requireNonNull(sessionId, "sessionId");
        if (!recoverableDrafts.containsKey(id) && !rejectedDrafts.containsKey(id)) {
            return false;
        }
        try {
            if (!drafts.delete(id)) {
                return false;
            }
        } catch (RuntimeException failure) {
            return false;
        }
        recoverableDrafts.remove(id);
        rejectedDrafts.remove(id);
        return true;
    }

    public synchronized List<Message> discard(String sessionValue) {
        ReportSessionId id = parseSessionId(sessionValue);
        if (id == null || !sessions.containsKey(id)) {
            return List.of(new Message("bugreport.command.error.unknown_session"));
        }
        ReportSession session = sessions.get(id);
        if (persistedDraftFiles.contains(id)) {
            try {
                drafts.delete(id);
            } catch (RuntimeException failure) {
                return List.of(new Message("bugreport.command.error.draft_discard_failed"));
            }
            persistedDraftFiles.remove(id);
        }
        session.cancel(CancellationReason.USER_REQUESTED);
        sessions.remove(id);
        confirmedForms.remove(id);
        persistedForms.remove(id);
        collectionPlans.remove(id);
        collectionResults.remove(id);
        collectionWorkspaces.remove(id);
        sanitizationBatches.remove(id);
        preparedSnapshots.remove(id);
        activeSanitizations.remove(id);
        activeReviews.remove(id);
        activeExportPreparations.remove(id);
        activeExports.remove(id);
        return List.of(new Message("bugreport.command.discard.success", id.toString()));
    }

    private boolean persistFormDraft(ReportSession session, FormSubmission submission) {
        try {
            ReportSessionSnapshot before = session.snapshot();
            long persistedRevision = Math.addExact(before.revision(), 1);
            ReportDraft draft = new ReportDraft(
                    before.id(),
                    persistedRevision,
                    before.providerSpecification().id(),
                    before.providerSpecification().version(),
                    before.selectedCategory().map(CategorySpecification::id),
                    before.state(),
                    submission);
            DraftResolver.resolve(draft, registry());
            drafts.save(draft);
            persistedDraftFiles.add(before.id());
            ReportSessionSnapshot snapshot = session.recordFormDraftUpdate();
            if (snapshot.revision() != persistedRevision) {
                throw new IllegalStateException(
                        "Persisted form draft and session revision diverged");
            }
            persistedForms.put(
                    snapshot.id(), new PersistedFormDraft(snapshot.revision(), submission));
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private void scanDraftsIfNeeded() {
        if (draftsScanned || !drafts.available()) {
            return;
        }
        draftsScanned = true;
        final DraftLoadBatch batch;
        try {
            batch = drafts.loadAll();
        } catch (RuntimeException failure) {
            draftScanFailed = true;
            return;
        }
        draftScanFailed = batch.scanLimitReached();
        ReportSessionFactory factory = new ReportSessionFactory(registry());
        for (DraftLoadOutcome outcome : batch.outcomes()) {
            if (outcome instanceof DraftLoadOutcome.Rejected rejected) {
                rejectedDrafts.put(
                        rejected.sessionId(),
                        DraftRecoveryChoice.rejected(
                                rejected.sessionId(), DraftRecoveryStatus.FILE_REJECTED));
                continue;
            }
            DraftLoadOutcome.Loaded loaded = (DraftLoadOutcome.Loaded) outcome;
            ReportDraft draft = loaded.decoded().draft();
            try {
                RecoveredReportSession recovered = factory.recover(draft);
                if (recovered.session().snapshot().state()
                        != ReportSessionState.FORM_IN_PROGRESS) {
                    rejectedDrafts.put(
                            loaded.sessionId(),
                            DraftRecoveryChoice.rejected(
                                    loaded.sessionId(),
                                    draft.providerId(),
                                    draft.recordedState(),
                                    DraftRecoveryStatus.NO_SELECTED_CATEGORY));
                } else {
                    recoverableDrafts.put(loaded.sessionId(), recovered);
                }
            } catch (DraftResolutionException failure) {
                rejectedDrafts.put(
                        loaded.sessionId(),
                        DraftRecoveryChoice.rejected(
                                loaded.sessionId(),
                                draft.providerId(),
                                draft.recordedState(),
                                recoveryStatus(failure.code())));
            } catch (ReportSessionRecoveryException failure) {
                rejectedDrafts.put(
                        loaded.sessionId(),
                        DraftRecoveryChoice.rejected(
                                loaded.sessionId(),
                                draft.providerId(),
                                draft.recordedState(),
                                recoveryStatus(failure.code())));
            }
        }
    }

    private static DraftRecoveryChoice recoveryChoice(RecoveredReportSession recovered) {
        ReportSessionSnapshot snapshot = recovered.session().snapshot();
        CategorySpecification category = snapshot.selectedCategory().orElseThrow();
        return new DraftRecoveryChoice(
                snapshot.id(),
                Optional.of(snapshot.providerSpecification().id()),
                Optional.of(snapshot.providerSpecification().labelKey()),
                Optional.of(category.labelKey()),
                Optional.of(recovered.recordedState()),
                DraftRecoveryStatus.READY);
    }

    private static DraftRecoveryStatus recoveryStatus(DraftResolutionCode code) {
        return switch (code) {
            case PROVIDER_MISSING -> DraftRecoveryStatus.PROVIDER_MISSING;
            case PROVIDER_DISABLED -> DraftRecoveryStatus.PROVIDER_DISABLED;
            case PROVIDER_VERSION_MISMATCH ->
                    DraftRecoveryStatus.PROVIDER_VERSION_MISMATCH;
            case CATEGORY_MISSING -> DraftRecoveryStatus.CATEGORY_MISSING;
            case INVALID_FORM_STRUCTURE -> DraftRecoveryStatus.INVALID_FORM_STRUCTURE;
        };
    }

    private static DraftRecoveryStatus recoveryStatus(ReportSessionRecoveryCode code) {
        return switch (code) {
            case TERMINAL_DRAFT -> DraftRecoveryStatus.TERMINAL_DRAFT;
            case REVISION_EXHAUSTED -> DraftRecoveryStatus.REVISION_EXHAUSTED;
        };
    }

    private record PersistedFormDraft(long revision, FormSubmission submission) {
        private PersistedFormDraft {
            if (revision < 0) {
                throw new IllegalArgumentException("Persisted draft revision must be non-negative");
            }
            Objects.requireNonNull(submission, "submission");
        }
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

    private static boolean matchesPlan(
            CollectionExecutionRequest request, FileCollectionResult result) {
        CategorySourcePlan plan = request.reviewedPlan().plan();
        return plan.providerId().equals(result.providerId())
                && plan.providerVersion().equals(result.providerVersion())
                && plan.categoryId().equals(result.categoryId())
                && result.planFingerprint().filter(request.planFingerprint()::equals).isPresent();
    }

    private boolean isActiveExportPreparation(LocalExportPreparationRequest request) {
        ReportSession session = sessions.get(request.sessionId());
        return session != null
                && activeExportPreparations.get(request.sessionId()) == request
                && session.snapshot().state() == ReportSessionState.READY
                && session.snapshot().revision() == request.readyRevision();
    }

    private boolean isActiveExport(LocalExportRequest request) {
        ReportSession session = sessions.get(request.sessionId());
        return session != null
                && activeExports.get(request.sessionId()) == request
                && session.snapshot().state() == ReportSessionState.READY
                && session.snapshot().revision() == request.readyRevision();
    }

    private void failActiveExport(LocalExportRequest request) {
        ReportSession session = sessions.get(request.sessionId());
        if (session != null && activeExports.get(request.sessionId()) == request
                && session.snapshot().state() == ReportSessionState.DELIVERING) {
            session.transitionTo(ReportSessionState.FAILED_DELIVERY);
            activeExports.remove(request.sessionId());
            recordDeliveryHistory(session.snapshot(), null);
        }
    }

    private void recordDeliveryHistory(ReportSessionSnapshot snapshot, ReportTransportResult result) {
        ReportHistoryEntry entry = new ReportHistoryEntry(
                snapshot.id(),
                snapshot.providerSpecification().id(),
                snapshot.providerSpecification().version(),
                snapshot.selectedCategory().map(CategorySpecification::id),
                result != null && result.status() == ReportTransportResult.Status.SUCCESS
                        ? ReportHistoryStatus.COMPLETED : ReportHistoryStatus.FAILED,
                snapshot.revision(),
                Instant.now(),
                result != null && result.archive().isPresent()
                        ? java.util.Optional.of(new com.cybersammy.bugreport.core.history.ReportArchiveSummary(
                                result.archive().orElseThrow().archiveBytes(),
                                result.archive().orElseThrow().checksum(),
                                result.archive().orElseThrow().entryCount()))
                        : java.util.Optional.empty());
        try {
            history.record(entry);
        } catch (RuntimeException ignored) {
            // Delivery succeeded or failed independently; history persistence never changes its result.
        }
    }

    private static Path safeArchiveDestination(Path gameDirectory, String fileName) throws IOException {
        String name = Objects.requireNonNull(fileName, "archiveFileName");
        if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,119}\\.bugreport\\.zip")) {
            throw new IllegalArgumentException("Archive file name is invalid");
        }
        Path gameRoot = gameDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(gameRoot, LinkOption.NOFOLLOW_LINKS)
                || !gameRoot.equals(gameRoot.toRealPath(LinkOption.NOFOLLOW_LINKS))
                || !gameRoot.equals(gameRoot.toRealPath())) {
            throw new IOException("Game directory is not a safe export root");
        }
        Path root = gameRoot.resolve("bugreport-exports");
        try {
            Files.createDirectory(root);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            // The following identity checks distinguish an existing safe directory from a redirect.
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !root.equals(root.toRealPath(LinkOption.NOFOLLOW_LINKS))
                || !root.equals(root.toRealPath())) {
            throw new IOException("Bug Report export directory is not safe");
        }
        Path target = root.resolve(name).normalize();
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException("Archive destination escapes the export directory");
        }
        return target;
    }

    private static ReportManifest manifest(LocalExportPreparationRequest request) {
        ReportSessionSnapshot snapshot = request.session();
        return ReportManifest.builder(
                        snapshot.id(),
                        Instant.now(),
                        new ManifestProducer("0.3.0", ApiVersion.parse("0.3.0")),
                        new ManifestEnvironment("1.21.1", "neoforge", "21.1", SupportedSide.PHYSICAL_CLIENT))
                .target(new ManifestTarget(
                        snapshot.providerSpecification().id(),
                        snapshot.providerSpecification().version(),
                        snapshot.selectedCategory().orElseThrow().id()))
                .reviewedFields(request.fields())
                .entries(request.prepared().artifacts().stream()
                        .map(BugReportCommandService::manifestEntry)
                        .toList())
                .build();
    }

    private static ManifestEntry manifestEntry(PreparedWorkspaceArtifact prepared) {
        ReviewedWorkspaceArtifact artifact = prepared.artifact();
        if (artifact instanceof ReviewedWorkspaceArtifact.Source source) {
            CollectedSourceFile collected = source.collected();
            List<ManifestEntryProvenance> provenance = collected.provenances().stream()
                    .map(BugReportCommandService::sourceProvenance)
                    .toList();
            return new ManifestEntry("content/" + artifact.artifactName(), artifact.byteCount(),
                    artifact.checksum(), artifact.contentType(), Optional.empty(),
                    prepared.effectivePrivacy(), artifact.qualityRole(),
                    ManifestCollectionStatus.SOURCE_COLLECTED, prepared.sanitizationStatus(), provenance,
                    prepared.sanitizationFindings(), ExtensionMetadata.empty());
        }
        CollectedGeneratedArtifact generated = ((ReviewedWorkspaceArtifact.Generated) artifact).collected();
        return new ManifestEntry("content/" + artifact.artifactName(), artifact.byteCount(), artifact.checksum(),
                artifact.contentType(), Optional.empty(), prepared.effectivePrivacy(), artifact.qualityRole(),
                ManifestCollectionStatus.GENERATOR_COMPLETED, prepared.sanitizationStatus(), List.of(
                        ManifestEntryProvenance.generator(generated.providerId(), generated.providerVersion(),
                                generated.categoryId(), generated.generatorId(), generated.privacy())),
                prepared.sanitizationFindings(), ExtensionMetadata.empty());
    }

    private static ManifestEntryProvenance sourceProvenance(SourceProvenance value) {
        return ManifestEntryProvenance.source(value.providerId(), value.providerVersion(), value.categoryId(),
                value.sourceId(), value.kind(), value.privacy());
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

        private static FormConfirmationResult persistenceFailed() {
            return new FormConfirmationResult(
                    FormConfirmationStatus.PERSISTENCE_FAILED,
                    Optional.empty(),
                    Optional.empty());
        }
    }

    /** Stable non-sensitive outcome for the form-to-plan lifecycle boundary. */
    public enum FormConfirmationStatus {
        ACCEPTED,
        INVALID,
        UNKNOWN_SESSION,
        INVALID_STATE,
        PERSISTENCE_FAILED
    }

    /** Stable non-sensitive outcome for explicit form-draft persistence. */
    public enum DraftSaveStatus {
        SAVED,
        UNKNOWN_SESSION,
        INVALID_STATE,
        UNAVAILABLE,
        FAILED
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

    /** Exact immutable authority for one asynchronous collection execution. */
    public record CollectionExecutionRequest(
            ReportSessionId sessionId,
            long collectionRevision,
            ReviewedCollectionPlan reviewedPlan,
            CollectionPlanFingerprint planFingerprint) {
        public CollectionExecutionRequest {
            Objects.requireNonNull(sessionId, "sessionId");
            if (collectionRevision < 0) {
                throw new IllegalArgumentException("collectionRevision must be non-negative");
            }
            Objects.requireNonNull(reviewedPlan, "reviewedPlan");
            Objects.requireNonNull(planFingerprint, "planFingerprint");
        }
    }

    /** Exact service-issued authority to construct the package summary for a READY report. */
    public static final class LocalExportPreparationRequest {
        private final ReportSessionId sessionId;
        private final long readyRevision;
        private final ReportSessionSnapshot session;
        private final PreparedWorkspaceSnapshot prepared;
        private final ReportWorkspace workspace;
        private final FormSubmission fields;

        private LocalExportPreparationRequest(
                ReportSessionId sessionId,
                long readyRevision,
                ReportSessionSnapshot session,
                PreparedWorkspaceSnapshot prepared,
                ReportWorkspace workspace,
                FormSubmission fields) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
            this.readyRevision = readyRevision;
            this.session = Objects.requireNonNull(session, "session");
            this.prepared = Objects.requireNonNull(prepared, "prepared");
            this.workspace = Objects.requireNonNull(workspace, "workspace");
            this.fields = Objects.requireNonNull(fields, "fields");
            if (readyRevision < 0 || !sessionId.equals(session.id())
                    || !sessionId.equals(workspace.sessionId())) {
                throw new IllegalArgumentException("Export preparation identity is inconsistent");
            }
        }

        public ReportSessionId sessionId() { return sessionId; }
        public long readyRevision() { return readyRevision; }
        ReportSessionSnapshot session() { return session; }
        PreparedWorkspaceSnapshot prepared() { return prepared; }
        ReportWorkspace workspace() { return workspace; }
        FormSubmission fields() { return fields; }
    }

    /** Exact service-issued authority for one user-confirmed local export. */
    public static final class LocalExportRequest {
        private final ReportSessionId sessionId;
        private final long readyRevision;
        private final ReportSessionSnapshot session;
        private final ReportWorkspace workspace;
        private final ReportPackagePlan plan;

        private LocalExportRequest(
                ReportSessionId sessionId,
                long readyRevision,
                ReportSessionSnapshot session,
                ReportWorkspace workspace,
                ReportPackagePlan plan) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
            this.readyRevision = readyRevision;
            this.session = Objects.requireNonNull(session, "session");
            this.workspace = Objects.requireNonNull(workspace, "workspace");
            this.plan = Objects.requireNonNull(plan, "plan");
            if (readyRevision < 0 || !sessionId.equals(session.id())
                    || !sessionId.equals(workspace.sessionId())) {
                throw new IllegalArgumentException("Local export identity is inconsistent");
            }
        }

        public ReportSessionId sessionId() { return sessionId; }
        public long readyRevision() { return readyRevision; }
        /** Safe package summary for the confirmation screen; it exposes no workspace path. */
        public ExportSummary summary() {
            return new ExportSummary(
                    sessionId,
                    session.providerSpecification().id(),
                    session.selectedCategory().orElseThrow().id(),
                    plan.entries().size(),
                    plan.totalUncompressedBytes(),
                    plan.markdownDocument().isPresent());
        }
        ReportWorkspace workspace() { return workspace; }
        ReportPackagePlan plan() { return plan; }
    }

    /**
     * Opaque execution authority minted only after the application service has accepted the
     * explicit local-export action for its exact active request. It is never returned to UI code.
     */
    public static final class ConfirmedLocalExport {
        private final ReportPackagePlan plan;
        private final ReportWorkspace workspace;
        private final LocalArchiveDestination destination;

        private ConfirmedLocalExport(
                ReportPackagePlan plan, ReportWorkspace workspace, LocalArchiveDestination destination) {
            this.plan = Objects.requireNonNull(plan, "plan");
            this.workspace = Objects.requireNonNull(workspace, "workspace");
            this.destination = Objects.requireNonNull(destination, "destination");
        }

        /** Internal bridge access only; callers cannot construct a confirmation. */
        public ReportPackagePlan plan() { return plan; }
        /** Internal bridge access only; callers cannot construct a confirmation. */
        public ReportWorkspace workspace() { return workspace; }
        /** Internal bridge access only; callers cannot construct a confirmation. */
        public LocalArchiveDestination destination() { return destination; }
    }

    /** Path-free data rendered before a user confirms a local archive write. */
    public record ExportSummary(
            ReportSessionId sessionId,
            ProviderId providerId,
            CategoryId categoryId,
            int entryCount,
            long totalBytes,
            boolean includesMarkdown) {
        public ExportSummary {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(categoryId, "categoryId");
            if (entryCount <= 0 || totalBytes < 0) {
                throw new IllegalArgumentException("Export summary is invalid");
            }
        }
    }

    /** Safe product-facing status for one canonical persisted draft. */
    public enum DraftRecoveryStatus {
        READY,
        FILE_REJECTED,
        PROVIDER_MISSING,
        PROVIDER_DISABLED,
        PROVIDER_VERSION_MISMATCH,
        CATEGORY_MISSING,
        INVALID_FORM_STRUCTURE,
        TERMINAL_DRAFT,
        REVISION_EXHAUSTED,
        NO_SELECTED_CATEGORY
    }

    /** Path-free recovery-list entry; only READY entries may be resumed. */
    public record DraftRecoveryChoice(
            ReportSessionId sessionId,
            Optional<ProviderId> providerId,
            Optional<LocalizationKey> providerLabel,
            Optional<LocalizationKey> categoryLabel,
            Optional<ReportSessionState> recordedState,
            DraftRecoveryStatus status) {
        public DraftRecoveryChoice {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(providerLabel, "providerLabel");
            Objects.requireNonNull(categoryLabel, "categoryLabel");
            Objects.requireNonNull(recordedState, "recordedState");
            Objects.requireNonNull(status, "status");
            if (status == DraftRecoveryStatus.READY
                    != (providerId.isPresent()
                            && providerLabel.isPresent()
                            && categoryLabel.isPresent()
                            && recordedState.isPresent())) {
                throw new IllegalArgumentException(
                        "Only a ready recovery choice carries complete trusted labels");
            }
        }

        private static DraftRecoveryChoice rejected(
                ReportSessionId sessionId, DraftRecoveryStatus status) {
            return new DraftRecoveryChoice(
                    sessionId,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    status);
        }

        private static DraftRecoveryChoice rejected(
                ReportSessionId sessionId,
                ProviderId providerId,
                ReportSessionState recordedState,
                DraftRecoveryStatus status) {
            return new DraftRecoveryChoice(
                    sessionId,
                    Optional.of(providerId),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(recordedState),
                    status);
        }

        public boolean resumable() {
            return status == DraftRecoveryStatus.READY;
        }
    }

    /** One bounded restart scan, including a safe aggregate scan warning. */
    public record DraftRecoveryOverview(
            List<DraftRecoveryChoice> choices, boolean scanIncomplete) {
        public DraftRecoveryOverview {
            choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
        }
    }

    /** Safe state returned after consuming one exact restart-recovery candidate. */
    public record DraftResume(
            ReportSessionId sessionId,
            ProviderId providerId,
            CategoryId categoryId,
            FormSubmission formSubmission,
            ReportSessionState recordedState) {
        public DraftResume {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(categoryId, "categoryId");
            Objects.requireNonNull(formSubmission, "formSubmission");
            Objects.requireNonNull(recordedState, "recordedState");
        }
    }

    /** Product-side persistence boundary for bounded report drafts. */
    public interface ReportDraftPersistence {
        boolean available();

        void save(ReportDraft draft);

        DraftLoadBatch loadAll();

        boolean delete(ReportSessionId sessionId);

        static ReportDraftPersistence empty() {
            return new ReportDraftPersistence() {
                @Override public boolean available() { return false; }
                @Override public void save(ReportDraft draft) {}
                @Override public DraftLoadBatch loadAll() {
                    return new DraftLoadBatch(List.of(), 0, false);
                }
                @Override public boolean delete(ReportSessionId sessionId) { return false; }
            };
        }
    }

    /** Product-side persistence boundary for safe terminal history summaries. */
    public interface ReportHistoryRecorder {
        void record(ReportHistoryEntry entry);

        List<ReportHistoryEntry> entries();

        static ReportHistoryRecorder empty() {
            return new ReportHistoryRecorder() {
                @Override public void record(ReportHistoryEntry entry) {}
                @Override public List<ReportHistoryEntry> entries() { return List.of(); }
            };
        }
    }

    /** Exact immutable authority for one asynchronous sanitization execution. */
    public static final class SanitizationExecutionRequest {
        private final ReportSessionId sessionId;
        private final long sanitizationRevision;
        private final ReportSessionSnapshot session;
        private final FileCollectionResult files;
        private final ReportWorkspace workspace;

        private SanitizationExecutionRequest(
                ReportSessionId sessionId,
                long sanitizationRevision,
                ReportSessionSnapshot session,
                FileCollectionResult files,
                ReportWorkspace workspace) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
            this.sanitizationRevision = sanitizationRevision;
            this.session = Objects.requireNonNull(session, "session");
            this.files = Objects.requireNonNull(files, "files");
            this.workspace = Objects.requireNonNull(workspace, "workspace");
            if (sanitizationRevision < 0 || !sessionId.equals(workspace.sessionId())) {
                throw new IllegalArgumentException("Sanitization request identity is inconsistent");
            }
        }

        public ReportSessionId sessionId() { return sessionId; }
        public long sanitizationRevision() { return sanitizationRevision; }
        ReportSessionSnapshot session() { return session; }
        FileCollectionResult files() { return files; }
        ReportWorkspace workspace() { return workspace; }
    }

    /** Exact immutable authority for one user review of coordinator-issued evidence. */
    public static final class WorkspaceReviewRequest {
        private final ReportSessionId sessionId;
        private final long reviewRevision;
        private final ReportSessionSnapshot session;
        private final WorkspaceReviewCoordinator.SanitizationBatch batch;

        private WorkspaceReviewRequest(
                ReportSessionId sessionId,
                long reviewRevision,
                ReportSessionSnapshot session,
                WorkspaceReviewCoordinator.SanitizationBatch batch) {
            this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
            this.reviewRevision = reviewRevision;
            this.session = Objects.requireNonNull(session, "session");
            this.batch = Objects.requireNonNull(batch, "batch");
            if (reviewRevision < 0
                    || !sessionId.equals(session.id())
                    || reviewRevision != session.revision()) {
                throw new IllegalArgumentException("Workspace review request identity is inconsistent");
            }
        }

        public ReportSessionId sessionId() { return sessionId; }
        public long reviewRevision() { return reviewRevision; }
        ReportSessionSnapshot session() { return session; }
        public WorkspaceReviewCoordinator.SanitizationBatch batch() { return batch; }
    }

    /** Untrusted UI decision data; only the service can convert it into package authority. */
    public record ReviewDecision(
            java.util.Set<String> includedArtifacts,
            java.util.Set<String> explicitlyReviewedArtifacts) {
        public ReviewDecision {
            includedArtifacts = java.util.Set.copyOf(
                    Objects.requireNonNull(includedArtifacts, "includedArtifacts"));
            explicitlyReviewedArtifacts = java.util.Set.copyOf(
                    Objects.requireNonNull(
                            explicitlyReviewedArtifacts, "explicitlyReviewedArtifacts"));
            if (includedArtifacts.stream().anyMatch(Objects::isNull)
                    || explicitlyReviewedArtifacts.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Review decision must not contain null");
            }
        }
    }
}
