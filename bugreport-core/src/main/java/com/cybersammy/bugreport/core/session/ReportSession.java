package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.draft.ReportDraft;
import com.cybersammy.bugreport.core.draft.ResolvedReportDraft;
import com.cybersammy.bugreport.core.registry.ProviderSupport;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** Mutable state-machine boundary created from one accepted registry provider. */
public final class ReportSession {
    private final ReportSessionId id;
    private final ProviderSpecification providerSpecification;
    private final ProviderSupport providerSupport;
    private final Clock clock;
    private final ArrayDeque<SessionAuditEvent> auditEvents = new ArrayDeque<>();

    private CategorySpecification selectedCategory;
    private ReportSessionState state = ReportSessionState.CREATED;
    private long revision;
    private long lastAuditSequence;
    private long discardedAuditEvents;

    ReportSession(
            ReportSessionId id,
            RegisteredProvider provider,
            Clock clock) {
        this.id = Objects.requireNonNull(id, "id");
        RegisteredProvider acceptedProvider = Objects.requireNonNull(provider, "provider");
        if (acceptedProvider.support().state() == ProviderSupportState.DISABLED) {
            throw new IllegalArgumentException(
                    "Cannot create a report session for a disabled provider: "
                            + acceptedProvider.id());
        }
        providerSpecification = acceptedProvider.specification();
        providerSupport = acceptedProvider.support();
        this.clock = Objects.requireNonNull(clock, "clock");
        auditEvents.addLast(
                new SessionAuditEvent.Created(
                        id,
                        0,
                        0,
                        auditInstant(),
                        providerSpecification.id()));
    }

    ReportSession(ResolvedReportDraft resolvedDraft, Clock clock) {
        ResolvedReportDraft resolved = Objects.requireNonNull(resolvedDraft, "resolvedDraft");
        ReportDraft draft = resolved.draft();
        id = draft.sessionId();
        providerSpecification = resolved.providerSpecification();
        providerSupport = resolved.providerSupport();
        this.clock = Objects.requireNonNull(clock, "clock");
        selectedCategory = resolved.category().orElse(null);
        state = selectedCategory == null
                ? ReportSessionState.CREATED
                : ReportSessionState.FORM_IN_PROGRESS;
        long recoveryRevision = Math.addExact(draft.revision(), 1);
        revision = recoveryRevision;
        lastAuditSequence = recoveryRevision;
        discardedAuditEvents = recoveryRevision;
        auditEvents.addLast(
                new SessionAuditEvent.Recovered(
                        id,
                        recoveryRevision,
                        recoveryRevision,
                        auditInstant(),
                        draft.recordedState(),
                        state));
    }

    /** Returns an immutable, internally consistent point-in-time snapshot. */
    public synchronized ReportSessionSnapshot snapshot() {
        return currentSnapshot();
    }

    /**
     * Selects one category declared by the session provider.
     *
     * <p>The first successful selection atomically starts form entry. Re-selecting the same
     * category while editing is idempotent; selecting another declared category replaces the
     * selection and advances the revision.
     *
     * @param categoryId category declared by the session provider
     * @return resulting immutable session snapshot
     */
    public synchronized ReportSessionSnapshot selectCategory(CategoryId categoryId) {
        CategoryId requestedCategoryId = Objects.requireNonNull(categoryId, "categoryId");
        if (state != ReportSessionState.CREATED
                && state != ReportSessionState.FORM_IN_PROGRESS) {
            throw new InvalidReportCategorySelectionStateException(id, state, requestedCategoryId);
        }

        CategorySpecification requestedCategory =
                Optional.ofNullable(providerSpecification.categories().get(requestedCategoryId))
                        .orElseThrow(
                                () ->
                                        new UnknownReportCategoryException(
                                                id,
                                                providerSpecification.id(),
                                                requestedCategoryId));
        if (selectedCategory == requestedCategory) {
            return currentSnapshot();
        }

        long nextRevision = nextRevision();
        long nextSequence = nextAuditSequence();
        Instant occurredAt = auditInstant();
        SessionAuditEvent event =
                selectedCategory == null
                        ? new SessionAuditEvent.CategorySelected(
                                id,
                                nextSequence,
                                nextRevision,
                                occurredAt,
                                requestedCategory.id())
                        : new SessionAuditEvent.CategoryChanged(
                                id,
                                nextSequence,
                                nextRevision,
                                occurredAt,
                                selectedCategory.id(),
                                requestedCategory.id());
        appendAuditEvent(event);
        selectedCategory = requestedCategory;
        state = ReportSessionState.FORM_IN_PROGRESS;
        revision = nextRevision;
        lastAuditSequence = nextSequence;
        return currentSnapshot();
    }

    /**
     * Publishes a new revision for form values that have been durably persisted elsewhere.
     *
     * <p>The audit event deliberately records no field identifiers or values. Callers must
     * complete persistence before invoking this method, or persist the returned revision as part
     * of one application-level transaction.
     *
     * @return resulting immutable session snapshot
     */
    public synchronized ReportSessionSnapshot recordFormDraftUpdate() {
        if (state != ReportSessionState.FORM_IN_PROGRESS) {
            throw new IllegalStateException(
                    "Form drafts can only be updated while form entry is in progress");
        }
        long nextRevision = nextRevision();
        long nextSequence = nextAuditSequence();
        Instant occurredAt = auditInstant();
        appendAuditEvent(
                new SessionAuditEvent.FormDraftUpdated(
                        id, nextSequence, nextRevision, occurredAt));
        revision = nextRevision;
        lastAuditSequence = nextSequence;
        return currentSnapshot();
    }

    /** Applies one valid direct transition and returns the resulting snapshot. */
    public synchronized ReportSessionSnapshot transitionTo(ReportSessionState requestedState) {
        Objects.requireNonNull(requestedState, "requestedState");
        if (!state.canTransitionTo(requestedState)
                || requestedState == ReportSessionState.CANCELLED
                || (state == ReportSessionState.CREATED
                        && requestedState == ReportSessionState.FORM_IN_PROGRESS)) {
            throw new InvalidReportSessionTransitionException(id, state, requestedState);
        }
        long nextRevision = nextRevision();
        long nextSequence = nextAuditSequence();
        SessionAuditEvent event =
                new SessionAuditEvent.StateTransitioned(
                        id,
                        nextSequence,
                        nextRevision,
                        auditInstant(),
                        state,
                        requestedState);
        appendAuditEvent(event);
        state = requestedState;
        revision = nextRevision;
        lastAuditSequence = nextSequence;
        return currentSnapshot();
    }

    /**
     * Explicitly cancels one active session and records the technical reason.
     *
     * @param reason cancellation reason without user-supplied text
     * @return terminal cancelled snapshot
     */
    public synchronized ReportSessionSnapshot cancel(CancellationReason reason) {
        CancellationReason cancellationReason = Objects.requireNonNull(reason, "reason");
        if (!state.canTransitionTo(ReportSessionState.CANCELLED)) {
            throw new InvalidReportSessionTransitionException(
                    id, state, ReportSessionState.CANCELLED);
        }
        long nextRevision = nextRevision();
        long nextSequence = nextAuditSequence();
        SessionAuditEvent event =
                new SessionAuditEvent.Cancelled(
                        id,
                        nextSequence,
                        nextRevision,
                        auditInstant(),
                        state,
                        cancellationReason);
        appendAuditEvent(event);
        state = ReportSessionState.CANCELLED;
        revision = nextRevision;
        lastAuditSequence = nextSequence;
        return currentSnapshot();
    }

    private ReportSessionSnapshot currentSnapshot() {
        return new ReportSessionSnapshot(
                id,
                providerSpecification,
                providerSupport,
                Optional.ofNullable(selectedCategory),
                state,
                revision,
                new SessionAuditTrail(
                        new ArrayList<>(auditEvents), discardedAuditEvents));
    }

    private long nextRevision() {
        return Math.addExact(revision, 1);
    }

    private long nextAuditSequence() {
        return Math.addExact(lastAuditSequence, 1);
    }

    private Instant auditInstant() {
        return Objects.requireNonNull(clock.instant(), "clock.instant()");
    }

    private void appendAuditEvent(SessionAuditEvent event) {
        if (auditEvents.size() == SessionAuditTrail.MAX_RETAINED_EVENTS) {
            long nextDiscarded = Math.addExact(discardedAuditEvents, 1);
            auditEvents.removeFirst();
            discardedAuditEvents = nextDiscarded;
        }
        auditEvents.addLast(event);
    }
}
