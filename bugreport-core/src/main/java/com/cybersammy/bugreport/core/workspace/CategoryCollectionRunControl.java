package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import java.util.concurrent.atomic.AtomicReference;

/** Non-reusable cancellation and polling handle for a combined category collection run. */
public final class CategoryCollectionRunControl implements CancellationSignal {
    private final CollectionRunControl files = new CollectionRunControl();
    private final AtomicReference<State> state = new AtomicReference<>(new State(
            CategoryCollectionProgressSnapshot.Phase.IDLE, 0, 0, false));

    /** Requests cancellation while file or generated diagnostic work is active. */
    public boolean requestCancellation() {
        while (true) {
            State current = state.get();
            if (current.cancelled() || terminal(current.phase())) {
                return false;
            }
            State cancelled = new State(
                    current.phase(), current.totalGenerators(), current.completedGenerators(), true);
            if (state.compareAndSet(current, cancelled)) {
                files.requestCancellation();
                return true;
            }
        }
    }

    @Override
    public boolean isCancellationRequested() {
        return state.get().cancelled();
    }

    /** Returns the latest internally consistent combined progress. */
    public CategoryCollectionProgressSnapshot progress() {
        State current = state.get();
        return new CategoryCollectionProgressSnapshot(
                current.phase(),
                files.progress(),
                current.totalGenerators(),
                current.completedGenerators());
    }

    CollectionRunControl fileControl() {
        return files;
    }

    void begin(int totalGenerators) {
        while (true) {
            State current = state.get();
            if (current.phase() != CategoryCollectionProgressSnapshot.Phase.IDLE) {
                throw new IllegalStateException("Collection run control is already in use");
            }
            if (state.compareAndSet(
                    current,
                    new State(
                            CategoryCollectionProgressSnapshot.Phase.FILES,
                            totalGenerators,
                            0,
                            current.cancelled()))) {
                return;
            }
        }
    }

    void beginGenerated() {
        advance(CategoryCollectionProgressSnapshot.Phase.GENERATED_DIAGNOSTICS, 0);
    }

    void generatedComplete(int completedGenerators) {
        advance(CategoryCollectionProgressSnapshot.Phase.GENERATED_DIAGNOSTICS, completedGenerators);
    }

    CategoryCollectionResult.Status finish(
            CategoryCollectionResult.Status proposedStatus, int completedGenerators) {
        while (true) {
            State current = state.get();
            if (terminal(current.phase())) {
                throw new IllegalStateException("Collection run is already terminal");
            }
            CategoryCollectionResult.Status actual = current.cancelled()
                    ? CategoryCollectionResult.Status.CANCELLED
                    : proposedStatus;
            State terminal = new State(
                    phase(actual),
                    current.totalGenerators(),
                    completedGenerators,
                    current.cancelled());
            if (state.compareAndSet(current, terminal)) {
                return actual;
            }
        }
    }

    private void advance(CategoryCollectionProgressSnapshot.Phase phase, int completedGenerators) {
        while (true) {
            State current = state.get();
            State next = new State(
                    phase, current.totalGenerators(), completedGenerators, current.cancelled());
            if (state.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private static boolean terminal(CategoryCollectionProgressSnapshot.Phase phase) {
        return phase == CategoryCollectionProgressSnapshot.Phase.COMPLETE
                || phase == CategoryCollectionProgressSnapshot.Phase.PARTIAL
                || phase == CategoryCollectionProgressSnapshot.Phase.FAILED
                || phase == CategoryCollectionProgressSnapshot.Phase.CANCELLED;
    }

    private static CategoryCollectionProgressSnapshot.Phase phase(
            CategoryCollectionResult.Status status) {
        return switch (status) {
            case COMPLETE -> CategoryCollectionProgressSnapshot.Phase.COMPLETE;
            case PARTIAL -> CategoryCollectionProgressSnapshot.Phase.PARTIAL;
            case FAILED -> CategoryCollectionProgressSnapshot.Phase.FAILED;
            case CANCELLED -> CategoryCollectionProgressSnapshot.Phase.CANCELLED;
        };
    }

    private record State(
            CategoryCollectionProgressSnapshot.Phase phase,
            int totalGenerators,
            int completedGenerators,
            boolean cancelled) {}
}
