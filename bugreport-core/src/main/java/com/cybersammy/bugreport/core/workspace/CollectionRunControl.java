package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe cancellation and progress handle for one non-reusable collection run. */
public final class CollectionRunControl implements CancellationSignal {
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private final CollectionProgressSnapshot idle = CollectionProgressSnapshot.idle();
    private final AtomicReference<CollectionProgressSnapshot> progress =
            new AtomicReference<>(idle);

    /** Requests cooperative cancellation and reports whether this call changed the signal. */
    public boolean requestCancellation() {
        CollectionProgressSnapshot.State state = progress.get().state();
        if (state != CollectionProgressSnapshot.State.IDLE
                && state != CollectionProgressSnapshot.State.RUNNING) {
            return false;
        }
        return cancellationRequested.compareAndSet(false, true);
    }

    @Override
    public boolean isCancellationRequested() {
        return cancellationRequested.get();
    }

    /** Returns the latest internally consistent progress snapshot. */
    public CollectionProgressSnapshot progress() {
        return progress.get();
    }

    void begin(CollectionProgressSnapshot initial) {
        CollectionProgressSnapshot requested = Objects.requireNonNull(initial, "initial");
        if (requested.state() != CollectionProgressSnapshot.State.RUNNING
                || !progress.compareAndSet(idle, requested)) {
            throw new IllegalStateException("Collection run control is already in use");
        }
    }

    void publish(CollectionProgressSnapshot snapshot) {
        CollectionProgressSnapshot next = Objects.requireNonNull(snapshot, "snapshot");
        CollectionProgressSnapshot previous = progress.get();
        if (previous.state() != CollectionProgressSnapshot.State.RUNNING
                || next.totalFiles() != previous.totalFiles()
                || next.plannedBytes() != previous.plannedBytes()
                || next.completedFiles() < previous.completedFiles()
                || next.successfulFiles() < previous.successfulFiles()
                || next.failedFiles() < previous.failedFiles()
                || next.cancelledFiles() < previous.cancelledFiles()
                || next.processedBytes() < previous.processedBytes()) {
            throw new IllegalStateException("Collection progress must advance monotonically");
        }
        progress.set(next);
    }
}
