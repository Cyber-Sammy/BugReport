package com.cybersammy.bugreport.core.workspace;

import java.util.Objects;
import java.util.OptionalInt;

/** Immutable polling snapshot for one bounded file-collection run. */
public record CollectionProgressSnapshot(
        State state,
        int totalFiles,
        int completedFiles,
        int successfulFiles,
        int failedFiles,
        int cancelledFiles,
        long processedBytes,
        long plannedBytes,
        OptionalInt activeFileOrdinal) {
    /** Lifecycle of one non-reusable collection control. */
    public enum State {
        IDLE,
        RUNNING,
        COMPLETE,
        PARTIAL,
        FAILED,
        CANCELLED
    }

    public CollectionProgressSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(activeFileOrdinal, "activeFileOrdinal");
        if (totalFiles < 0
                || completedFiles < 0
                || successfulFiles < 0
                || failedFiles < 0
                || cancelledFiles < 0
                || processedBytes < 0
                || plannedBytes < 0
                || completedFiles > totalFiles
                || (long) successfulFiles + failedFiles + cancelledFiles != completedFiles) {
            throw new IllegalArgumentException("Collection progress counters are inconsistent");
        }
        if (activeFileOrdinal.isPresent()
                && (state != State.RUNNING
                        || (long) activeFileOrdinal.getAsInt() != (long) completedFiles + 1
                        || activeFileOrdinal.getAsInt() > totalFiles)) {
            throw new IllegalArgumentException("Active file ordinal is inconsistent");
        }
        if (state == State.IDLE
                && (totalFiles != 0
                        || completedFiles != 0
                        || processedBytes != 0
                        || plannedBytes != 0
                        || activeFileOrdinal.isPresent())) {
            throw new IllegalArgumentException("Idle collection progress must be empty");
        }
        if (state != State.IDLE
                && state != State.RUNNING
                && (completedFiles != totalFiles || activeFileOrdinal.isPresent())) {
            throw new IllegalArgumentException("Terminal collection progress must be complete");
        }
        switch (state) {
            case IDLE -> {
                // Empty-state invariants are checked above.
            }
            case RUNNING -> {
                if (cancelledFiles != 0) {
                    throw new IllegalArgumentException(
                            "Running collection progress cannot contain cancellations");
                }
            }
            case COMPLETE -> {
                if (failedFiles != 0 || cancelledFiles != 0) {
                    throw new IllegalArgumentException(
                            "Complete collection progress cannot contain failures");
                }
            }
            case PARTIAL -> {
                if (successfulFiles == 0 || failedFiles == 0 || cancelledFiles != 0) {
                    throw new IllegalArgumentException(
                            "Partial collection requires successes and failures");
                }
            }
            case FAILED -> {
                if (successfulFiles != 0 || failedFiles == 0 || cancelledFiles != 0) {
                    throw new IllegalArgumentException(
                            "Failed collection requires only failed files");
                }
            }
            case CANCELLED -> {
                if (cancelledFiles == 0) {
                    throw new IllegalArgumentException(
                            "Cancelled collection requires cancelled files");
                }
            }
        }
    }

    static CollectionProgressSnapshot idle() {
        return new CollectionProgressSnapshot(
                State.IDLE, 0, 0, 0, 0, 0, 0, 0, OptionalInt.empty());
    }
}
