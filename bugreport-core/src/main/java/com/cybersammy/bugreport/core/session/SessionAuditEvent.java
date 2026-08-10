package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import java.time.Instant;
import java.util.Objects;

/**
 * Privacy-minimized immutable audit event for one report-session mutation.
 *
 * <p>Events deliberately cannot carry form values, filesystem paths, arbitrary messages, or
 * provider-supplied labels.
 */
public sealed interface SessionAuditEvent {
    /** Returns the owning report session. */
    ReportSessionId sessionId();

    /** Returns the monotonic event sequence within the session. */
    long sequence();

    /** Returns the session revision published by this event. */
    long revision();

    /** Returns the UTC instant supplied by the session clock. */
    Instant occurredAt();

    /** Records trusted session creation. */
    record Created(
            ReportSessionId sessionId,
            long sequence,
            long revision,
            Instant occurredAt,
            ProviderId providerId)
            implements SessionAuditEvent {
        /** Validates creation event metadata. */
        public Created {
            SessionAuditEventChecks.requireMetadata(
                    sessionId, sequence, revision, occurredAt);
            Objects.requireNonNull(providerId, "providerId");
            if (sequence != 0 || revision != 0) {
                throw new IllegalArgumentException(
                        "Session creation must have sequence and revision zero");
            }
        }
    }

    /** Records the first category selection and entry into form editing. */
    record CategorySelected(
            ReportSessionId sessionId,
            long sequence,
            long revision,
            Instant occurredAt,
            CategoryId categoryId)
            implements SessionAuditEvent {
        /** Validates category-selection event metadata. */
        public CategorySelected {
            SessionAuditEventChecks.requireMutationMetadata(
                    sessionId, sequence, revision, occurredAt);
            Objects.requireNonNull(categoryId, "categoryId");
        }
    }

    /** Records an explicit category replacement while editing the form. */
    record CategoryChanged(
            ReportSessionId sessionId,
            long sequence,
            long revision,
            Instant occurredAt,
            CategoryId previousCategoryId,
            CategoryId categoryId)
            implements SessionAuditEvent {
        /** Validates category-change event metadata. */
        public CategoryChanged {
            SessionAuditEventChecks.requireMutationMetadata(
                    sessionId, sequence, revision, occurredAt);
            Objects.requireNonNull(previousCategoryId, "previousCategoryId");
            Objects.requireNonNull(categoryId, "categoryId");
            if (previousCategoryId.equals(categoryId)) {
                throw new IllegalArgumentException(
                        "Category change requires two different category IDs");
            }
        }
    }

    /** Records persistence of changed form values without retaining their contents. */
    record FormDraftUpdated(
            ReportSessionId sessionId,
            long sequence,
            long revision,
            Instant occurredAt)
            implements SessionAuditEvent {
        /** Validates privacy-minimized form-draft update metadata. */
        public FormDraftUpdated {
            SessionAuditEventChecks.requireMutationMetadata(
                    sessionId, sequence, revision, occurredAt);
        }
    }

    /** Records one successful ordinary lifecycle transition. */
    record StateTransitioned(
            ReportSessionId sessionId,
            long sequence,
            long revision,
            Instant occurredAt,
            ReportSessionState previousState,
            ReportSessionState state)
            implements SessionAuditEvent {
        /** Validates lifecycle-transition event metadata. */
        public StateTransitioned {
            SessionAuditEventChecks.requireMutationMetadata(
                    sessionId, sequence, revision, occurredAt);
            Objects.requireNonNull(previousState, "previousState");
            Objects.requireNonNull(state, "state");
            if (state == ReportSessionState.CANCELLED
                    || !previousState.canTransitionTo(state)
                    || (previousState == ReportSessionState.CREATED
                            && state == ReportSessionState.FORM_IN_PROGRESS)) {
                throw new IllegalArgumentException(
                        "Audit event does not describe an ordinary session transition");
            }
        }
    }

    /** Records explicit cancellation from one non-terminal state. */
    record Cancelled(
            ReportSessionId sessionId,
            long sequence,
            long revision,
            Instant occurredAt,
            ReportSessionState previousState,
            CancellationReason reason)
            implements SessionAuditEvent {
        /** Validates cancellation event metadata. */
        public Cancelled {
            SessionAuditEventChecks.requireMutationMetadata(
                    sessionId, sequence, revision, occurredAt);
            Objects.requireNonNull(previousState, "previousState");
            Objects.requireNonNull(reason, "reason");
            if (!previousState.canTransitionTo(ReportSessionState.CANCELLED)) {
                throw new IllegalArgumentException(
                        "Cancellation audit event requires a non-terminal previous state");
            }
        }
    }

    /** Records restart recovery into a deliberately safe lifecycle state. */
    record Recovered(
            ReportSessionId sessionId,
            long sequence,
            long revision,
            Instant occurredAt,
            ReportSessionState recordedState,
            ReportSessionState state)
            implements SessionAuditEvent {
        /** Validates restart-recovery event metadata and safe-state policy. */
        public Recovered {
            SessionAuditEventChecks.requireMutationMetadata(
                    sessionId, sequence, revision, occurredAt);
            Objects.requireNonNull(recordedState, "recordedState");
            Objects.requireNonNull(state, "state");
            if (recordedState.terminal()
                    || (recordedState == ReportSessionState.CREATED
                            && state != ReportSessionState.CREATED)
                    || (recordedState != ReportSessionState.CREATED
                            && state != ReportSessionState.FORM_IN_PROGRESS)) {
                throw new IllegalArgumentException(
                        "Recovery audit event does not follow safe restart policy");
            }
        }
    }
}
