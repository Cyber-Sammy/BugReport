package com.cybersammy.bugreport.core.transport;

/** Immutable progress for one non-reusable transport attempt. */
public record TransportProgressSnapshot(
        State state,
        int completedEntries,
        int totalEntries,
        long processedBytes,
        long totalBytes) {
    public TransportProgressSnapshot {
        java.util.Objects.requireNonNull(state, "state");
        if (totalEntries < 0
                || completedEntries < 0
                || completedEntries > totalEntries
                || totalBytes < 0
                || processedBytes < 0
                || processedBytes > totalBytes) {
            throw new IllegalArgumentException("Transport progress is inconsistent");
        }
        if (state == State.IDLE
                && (completedEntries != 0
                        || totalEntries != 0
                        || processedBytes != 0
                        || totalBytes != 0)) {
            throw new IllegalArgumentException("Idle transport progress must be empty");
        }
        if (state == State.COMPLETE
                && (completedEntries != totalEntries || processedBytes != totalBytes)) {
            throw new IllegalArgumentException("Complete transport progress must be final");
        }
    }

    public enum State {
        IDLE,
        RUNNING,
        COMPLETE,
        FAILED,
        CANCELLED
    }
}
