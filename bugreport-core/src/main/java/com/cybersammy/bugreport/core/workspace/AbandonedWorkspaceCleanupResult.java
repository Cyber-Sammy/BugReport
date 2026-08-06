package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable deterministic result of a bounded abandoned-workspace cleanup pass. */
public record AbandonedWorkspaceCleanupResult(
        List<AbandonedWorkspaceCleanupOutcome> outcomes) {
    public AbandonedWorkspaceCleanupResult {
        outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
        if (outcomes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Cleanup outcomes must not contain null");
        }
        List<AbandonedWorkspaceCleanupOutcome> canonical = outcomes.stream()
                .sorted(Comparator.comparing(outcome -> outcome.sessionId().toString()))
                .toList();
        if (!outcomes.equals(canonical)) {
            throw new IllegalArgumentException("Cleanup outcomes must use canonical session order");
        }
        Set<ReportSessionId> sessions = new HashSet<>();
        if (outcomes.stream().anyMatch(outcome -> !sessions.add(outcome.sessionId()))) {
            throw new IllegalArgumentException("Cleanup outcomes must have unique sessions");
        }
    }

    public long removedCount() {
        return outcomes.stream().filter(AbandonedWorkspaceCleanupOutcome::removed).count();
    }
}
