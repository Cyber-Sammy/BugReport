package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ReportSessionStateTest {
    @Test
    void nonCancellationTransitionPolicyIsExact() {
        Map<ReportSessionState, Set<ReportSessionState>> expected = expectedTransitions();

        for (ReportSessionState current : ReportSessionState.values()) {
            for (ReportSessionState target : ReportSessionState.values()) {
                if (target == ReportSessionState.CANCELLED) {
                    continue;
                }
                assertEquals(
                        expected.get(current).contains(target),
                        current.canTransitionTo(target),
                        () -> current + " -> " + target);
            }
        }
    }

    @Test
    void cancellationIsAllowedFromEveryNonTerminalState() {
        for (ReportSessionState state : ReportSessionState.values()) {
            assertEquals(
                    !state.terminal(),
                    state.canTransitionTo(ReportSessionState.CANCELLED),
                    state::name);
        }
    }

    @Test
    void completedAndCancelledAreTerminal() {
        assertTrue(ReportSessionState.COMPLETED.terminal());
        assertTrue(ReportSessionState.CANCELLED.terminal());
        assertFalse(ReportSessionState.FAILED_DELIVERY.terminal());
    }

    private static Map<ReportSessionState, Set<ReportSessionState>> expectedTransitions() {
        Map<ReportSessionState, Set<ReportSessionState>> expected =
                new EnumMap<>(ReportSessionState.class);
        expected.put(ReportSessionState.CREATED, states(ReportSessionState.FORM_IN_PROGRESS));
        expected.put(
                ReportSessionState.FORM_IN_PROGRESS,
                states(
                        ReportSessionState.COLLECTION_PLANNED,
                        ReportSessionState.FAILED_VALIDATION));
        expected.put(
                ReportSessionState.COLLECTION_PLANNED,
                states(ReportSessionState.COLLECTING, ReportSessionState.FORM_IN_PROGRESS));
        expected.put(
                ReportSessionState.COLLECTING,
                states(
                        ReportSessionState.PARTIALLY_COLLECTED,
                        ReportSessionState.SANITIZING,
                        ReportSessionState.FAILED_COLLECTION));
        expected.put(
                ReportSessionState.PARTIALLY_COLLECTED,
                states(
                        ReportSessionState.COLLECTION_PLANNED,
                        ReportSessionState.SANITIZING));
        expected.put(
                ReportSessionState.SANITIZING,
                states(
                        ReportSessionState.REVIEW_REQUIRED,
                        ReportSessionState.FAILED_SANITIZATION));
        expected.put(
                ReportSessionState.REVIEW_REQUIRED,
                states(ReportSessionState.READY, ReportSessionState.SANITIZING));
        expected.put(
                ReportSessionState.READY,
                states(ReportSessionState.DELIVERING, ReportSessionState.REVIEW_REQUIRED));
        expected.put(
                ReportSessionState.DELIVERING,
                states(ReportSessionState.COMPLETED, ReportSessionState.FAILED_DELIVERY));
        expected.put(
                ReportSessionState.FAILED_VALIDATION,
                states(ReportSessionState.FORM_IN_PROGRESS));
        expected.put(
                ReportSessionState.FAILED_COLLECTION,
                states(ReportSessionState.COLLECTION_PLANNED));
        expected.put(
                ReportSessionState.FAILED_SANITIZATION,
                states(ReportSessionState.SANITIZING));
        expected.put(
                ReportSessionState.FAILED_DELIVERY,
                states(ReportSessionState.READY, ReportSessionState.DELIVERING));
        expected.put(ReportSessionState.COMPLETED, states());
        expected.put(ReportSessionState.CANCELLED, states());
        return Map.copyOf(expected);
    }

    private static Set<ReportSessionState> states(ReportSessionState... states) {
        if (states.length == 0) {
            return Set.of();
        }
        return Set.copyOf(EnumSet.copyOf(List.of(states)));
    }
}
