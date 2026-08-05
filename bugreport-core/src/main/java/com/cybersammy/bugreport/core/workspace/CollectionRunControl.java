package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe cancellation and progress handle for one non-reusable collection run. */
public final class CollectionRunControl implements CancellationSignal {
    private static final Runnable NO_OP = () -> {};

    private final AtomicReference<ControlState> state = new AtomicReference<>(
            new ControlState(CollectionProgressSnapshot.idle(), false));
    private final Runnable beforeCancellationCommit;

    public CollectionRunControl() {
        this(NO_OP);
    }

    CollectionRunControl(Runnable beforeCancellationCommit) {
        this.beforeCancellationCommit =
                Objects.requireNonNull(beforeCancellationCommit, "beforeCancellationCommit");
    }

    /** Requests cooperative cancellation and reports whether this call changed the signal. */
    public boolean requestCancellation() {
        boolean hookInvoked = false;
        while (true) {
            ControlState current = state.get();
            CollectionProgressSnapshot.State progressState = current.progress().state();
            if (current.cancellationRequested()
                    || (progressState != CollectionProgressSnapshot.State.IDLE
                            && progressState != CollectionProgressSnapshot.State.RUNNING)) {
                return false;
            }
            if (!hookInvoked) {
                beforeCancellationCommit.run();
                hookInvoked = true;
            }
            if (state.compareAndSet(current, new ControlState(current.progress(), true))) {
                return true;
            }
        }
    }

    @Override
    public boolean isCancellationRequested() {
        return state.get().cancellationRequested();
    }

    /** Returns the latest internally consistent progress snapshot. */
    public CollectionProgressSnapshot progress() {
        return state.get().progress();
    }

    void begin(CollectionProgressSnapshot initial) {
        CollectionProgressSnapshot requested = Objects.requireNonNull(initial, "initial");
        if (requested.state() != CollectionProgressSnapshot.State.RUNNING) {
            throw new IllegalStateException("Collection run control is already in use");
        }
        while (true) {
            ControlState current = state.get();
            if (current.progress().state() != CollectionProgressSnapshot.State.IDLE) {
                throw new IllegalStateException("Collection run control is already in use");
            }
            if (state.compareAndSet(
                    current, new ControlState(requested, current.cancellationRequested()))) {
                return;
            }
        }
    }

    void publish(CollectionProgressSnapshot snapshot) {
        CollectionProgressSnapshot next = Objects.requireNonNull(snapshot, "snapshot");
        if (next.state() != CollectionProgressSnapshot.State.RUNNING) {
            throw new IllegalStateException("Only running progress can be published directly");
        }
        while (true) {
            ControlState current = state.get();
            validateAdvance(current.progress(), next);
            if (state.compareAndSet(
                    current, new ControlState(next, current.cancellationRequested()))) {
                return;
            }
        }
    }

    CollectionProgressSnapshot finish(
            CollectionProgressSnapshot ordinaryTerminal,
            CollectionProgressSnapshot cancelledTerminal) {
        CollectionProgressSnapshot ordinary =
                Objects.requireNonNull(ordinaryTerminal, "ordinaryTerminal");
        CollectionProgressSnapshot cancelled =
                Objects.requireNonNull(cancelledTerminal, "cancelledTerminal");
        if (!isTerminal(ordinary.state())
                || cancelled.state() != CollectionProgressSnapshot.State.CANCELLED) {
            throw new IllegalArgumentException("Collection finish requires terminal snapshots");
        }
        while (true) {
            ControlState current = state.get();
            CollectionProgressSnapshot terminal =
                    current.cancellationRequested() ? cancelled : ordinary;
            validateAdvance(current.progress(), terminal);
            if (state.compareAndSet(
                    current, new ControlState(terminal, current.cancellationRequested()))) {
                return terminal;
            }
        }
    }

    private static boolean isTerminal(CollectionProgressSnapshot.State state) {
        return state == CollectionProgressSnapshot.State.COMPLETE
                || state == CollectionProgressSnapshot.State.PARTIAL
                || state == CollectionProgressSnapshot.State.FAILED
                || state == CollectionProgressSnapshot.State.CANCELLED;
    }

    private static void validateAdvance(
            CollectionProgressSnapshot previous, CollectionProgressSnapshot next) {
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
    }

    private record ControlState(
            CollectionProgressSnapshot progress, boolean cancellationRequested) {
        private ControlState {
            Objects.requireNonNull(progress, "progress");
        }
    }
}
