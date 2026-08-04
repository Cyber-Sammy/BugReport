package com.cybersammy.bugreport.core.session;

import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.core.draft.DraftResolver;
import com.cybersammy.bugreport.core.draft.ReportDraft;
import com.cybersammy.bugreport.core.draft.ResolvedReportDraft;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.time.Clock;
import java.util.Objects;

/** Creates report sessions only from providers accepted by one immutable registry snapshot. */
public final class ReportSessionFactory {
    private final ProviderRegistrySnapshot registry;
    private final Clock clock;

    /** Binds session creation to a trusted immutable registry result. */
    public ReportSessionFactory(ProviderRegistrySnapshot registry) {
        this(registry, Clock.systemUTC());
    }

    /**
     * Binds session creation to a trusted registry and explicit audit clock.
     *
     * @param registry accepted immutable provider registry
     * @param clock source of audit timestamps
     */
    public ReportSessionFactory(ProviderRegistrySnapshot registry, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Creates a session for an accepted enabled or partially supported provider. */
    public ReportSession create(ReportSessionId sessionId, ProviderId providerId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(providerId, "providerId");
        RegisteredProvider provider = registry.find(providerId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Cannot create a report session for an unregistered provider: "
                                                + providerId));
        if (provider.support().state() == ProviderSupportState.DISABLED) {
            throw new IllegalArgumentException(
                    "Cannot create a report session for a disabled provider: " + providerId);
        }
        return new ReportSession(sessionId, provider, clock);
    }

    /**
     * Rebinds a persisted non-terminal draft and resumes it in a conservative safe state.
     *
     * <p>Collection, review, and delivery authority is never restored after a process restart.
     * A draft with a selected category resumes in form editing; an untouched draft resumes in
     * the created state.
     */
    public RecoveredReportSession recover(ReportDraft draft) {
        ReportDraft persistedDraft = Objects.requireNonNull(draft, "draft");
        if (persistedDraft.recordedState().terminal()) {
            throw new ReportSessionRecoveryException(
                    ReportSessionRecoveryCode.TERMINAL_DRAFT,
                    "A terminal report draft cannot be resumed: " + persistedDraft.sessionId());
        }
        if (persistedDraft.revision() == Long.MAX_VALUE) {
            throw new ReportSessionRecoveryException(
                    ReportSessionRecoveryCode.REVISION_EXHAUSTED,
                    "Report draft revision is exhausted: " + persistedDraft.sessionId());
        }
        ResolvedReportDraft resolved = DraftResolver.resolve(persistedDraft, registry);
        ReportSession session = new ReportSession(resolved, clock);
        return new RecoveredReportSession(
                session,
                persistedDraft.formSubmission(),
                persistedDraft.recordedState());
    }
}
