package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.core.packaging.ReportZipProgress;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe cancellation and polling handle for one transport attempt. */
public final class TransportRunControl implements CancellationSignal {
    private final AtomicReference<ControlState> state = new AtomicReference<>(
            new ControlState(new TransportProgressSnapshot(
                    TransportProgressSnapshot.State.IDLE, 0, 0, 0, 0), false));

    public boolean requestCancellation() {
        while (true) {
            ControlState current = state.get();
            TransportProgressSnapshot.State progressState = current.progress().state();
            if (current.cancellationRequested()
                    || (progressState != TransportProgressSnapshot.State.IDLE
                            && progressState != TransportProgressSnapshot.State.RUNNING)) {
                return false;
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

    public TransportProgressSnapshot progress() {
        return state.get().progress();
    }

    void begin(ReportPackagePlan plan) {
        TransportProgressSnapshot initial = new TransportProgressSnapshot(
                TransportProgressSnapshot.State.RUNNING,
                0,
                plan.entries().size(),
                0,
                plan.totalUncompressedBytes());
        while (true) {
            ControlState current = state.get();
            if (current.progress().state() != TransportProgressSnapshot.State.IDLE) {
                throw new IllegalStateException("Transport run control is already in use");
            }
            if (state.compareAndSet(
                    current, new ControlState(initial, current.cancellationRequested()))) {
                return;
            }
        }
    }

    void publish(ReportZipProgress progress) {
        while (true) {
            ControlState current = state.get();
            if (current.progress().state() != TransportProgressSnapshot.State.RUNNING) {
                throw new IllegalStateException("Transport progress is already terminal");
            }
            TransportProgressSnapshot next = new TransportProgressSnapshot(
                    TransportProgressSnapshot.State.RUNNING,
                    progress.completedEntries(),
                    progress.totalEntries(),
                    progress.processedBytes(),
                    progress.totalBytes());
            requireMonotonic(current.progress(), next);
            if (state.compareAndSet(
                    current, new ControlState(next, current.cancellationRequested()))) {
                return;
            }
        }
    }

    void finish(TransportProgressSnapshot.State terminal) {
        if (terminal != TransportProgressSnapshot.State.COMPLETE
                && terminal != TransportProgressSnapshot.State.FAILED
                && terminal != TransportProgressSnapshot.State.CANCELLED) {
            throw new IllegalArgumentException("Transport terminal state is invalid");
        }
        while (true) {
            ControlState current = state.get();
            if (current.progress().state() != TransportProgressSnapshot.State.RUNNING) {
                throw new IllegalStateException("Transport progress is not running");
            }
            TransportProgressSnapshot previous = current.progress();
            TransportProgressSnapshot next = new TransportProgressSnapshot(
                    terminal,
                    previous.completedEntries(),
                    previous.totalEntries(),
                    previous.processedBytes(),
                    previous.totalBytes());
            if (state.compareAndSet(
                    current, new ControlState(next, current.cancellationRequested()))) {
                return;
            }
        }
    }

    private static void requireMonotonic(
            TransportProgressSnapshot previous, TransportProgressSnapshot next) {
        if (next.totalEntries() != previous.totalEntries()
                || next.totalBytes() != previous.totalBytes()
                || next.completedEntries() < previous.completedEntries()
                || next.processedBytes() < previous.processedBytes()) {
            throw new IllegalStateException("Transport progress must advance monotonically");
        }
    }

    private record ControlState(
            TransportProgressSnapshot progress, boolean cancellationRequested) {}
}
