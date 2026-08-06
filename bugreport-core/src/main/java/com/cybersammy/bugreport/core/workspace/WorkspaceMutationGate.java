package com.cybersammy.bugreport.core.workspace;

import java.time.Duration;
import java.util.Objects;

/** Coordinates terminal workspace sealing with all product-owned mutation operations. */
final class WorkspaceMutationGate {
    private State state = State.OPEN;
    private int activeMutations;

    synchronized Lease begin() {
        if (state != State.OPEN) {
            throw new WorkspaceMutationRejectedException(
                    "Report workspace no longer accepts mutations");
        }
        activeMutations = Math.addExact(activeMutations, 1);
        return new Lease(this);
    }

    synchronized void seal(Duration timeout) throws WorkspaceQuiescenceException {
        Duration waitLimit = Objects.requireNonNull(timeout, "timeout");
        if (waitLimit.isNegative() || waitLimit.isZero()) {
            throw new IllegalArgumentException("Workspace quiescence timeout must be positive");
        }
        if (state == State.SEALED) {
            return;
        }
        if (state == State.QUARANTINED) {
            throw new WorkspaceQuiescenceException(
                    "Report workspace was quarantined after incomplete mutation cleanup");
        }
        state = State.SEALING;
        long deadline = saturatedDeadline(waitLimit);
        while (activeMutations != 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                state = State.QUARANTINED;
                notifyAll();
                throw new WorkspaceQuiescenceException(
                        "Report workspace mutations did not become quiescent before the deadline");
            }
            try {
                long millis = remaining / 1_000_000L;
                int nanos = (int) (remaining % 1_000_000L);
                wait(millis, nanos);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                state = State.QUARANTINED;
                notifyAll();
                throw new WorkspaceQuiescenceException(
                        "Interrupted while awaiting report workspace quiescence", exception);
            }
        }
        if (state == State.QUARANTINED) {
            throw new WorkspaceQuiescenceException(
                    "Report workspace was quarantined while sealing");
        }
        state = State.SEALED;
        notifyAll();
    }

    synchronized boolean sealed() {
        return state == State.SEALED;
    }

    private synchronized void release() {
        if (activeMutations <= 0) {
            throw new IllegalStateException("Workspace mutation lease was released twice");
        }
        activeMutations--;
        notifyAll();
    }

    private static long saturatedDeadline(Duration timeout) {
        long now = System.nanoTime();
        long duration = timeout.toNanos();
        return Long.MAX_VALUE - now < duration ? Long.MAX_VALUE : now + duration;
    }

    static final class Lease implements AutoCloseable {
        private WorkspaceMutationGate owner;

        private Lease(WorkspaceMutationGate owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            WorkspaceMutationGate current = owner;
            if (current == null) {
                throw new IllegalStateException("Workspace mutation lease was released twice");
            }
            owner = null;
            current.release();
        }
    }

    private enum State {
        OPEN,
        SEALING,
        SEALED,
        QUARANTINED
    }
}
