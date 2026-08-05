package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class CollectionRunControlTest {
    @Test
    void publishesOnlyMonotonicInternallyConsistentProgress() {
        CollectionRunControl control = new CollectionRunControl();
        CollectionProgressSnapshot started = progress(
                CollectionProgressSnapshot.State.RUNNING,
                2,
                0,
                0,
                0,
                0,
                0,
                OptionalInt.of(1));
        control.begin(started);
        CollectionProgressSnapshot advanced = progress(
                CollectionProgressSnapshot.State.RUNNING,
                2,
                1,
                1,
                0,
                0,
                3,
                OptionalInt.empty());
        control.publish(advanced);

        assertEquals(advanced, control.progress());
        assertThrows(
                IllegalStateException.class,
                () -> control.publish(progress(
                        CollectionProgressSnapshot.State.RUNNING,
                        2,
                        0,
                        0,
                        0,
                        0,
                        2,
                        OptionalInt.empty())));
        assertThrows(IllegalStateException.class, () -> control.begin(started));
    }

    @Test
    void terminalPublicationRejectsCancellationThatObservedRunningState() throws Exception {
        CountDownLatch runningStateObserved = new CountDownLatch(1);
        CountDownLatch allowCancellationCommit = new CountDownLatch(1);
        CollectionRunControl control = new CollectionRunControl(() -> {
            runningStateObserved.countDown();
            await(allowCancellationCommit);
        });
        control.begin(progress(
                CollectionProgressSnapshot.State.RUNNING,
                1,
                1,
                1,
                0,
                0,
                10,
                OptionalInt.empty()));

        FutureTask<Boolean> cancellation = new FutureTask<>(control::requestCancellation);
        Thread.ofVirtual().start(cancellation);
        assertTrue(runningStateObserved.await(5, TimeUnit.SECONDS));

        CollectionProgressSnapshot complete = progress(
                CollectionProgressSnapshot.State.COMPLETE,
                1,
                1,
                1,
                0,
                0,
                10,
                OptionalInt.empty());
        CollectionProgressSnapshot cancelled = progress(
                CollectionProgressSnapshot.State.CANCELLED,
                1,
                1,
                1,
                0,
                0,
                10,
                OptionalInt.empty());
        CollectionProgressSnapshot terminal;
        try {
            terminal = control.finish(complete, cancelled);
        } finally {
            allowCancellationCommit.countDown();
        }

        assertEquals(complete, terminal);
        assertFalse(cancellation.get(5, TimeUnit.SECONDS));
        assertFalse(control.isCancellationRequested());
        assertEquals(complete, control.progress());
    }

    @Test
    void cancellationWinningTerminalRaceDeterminesTerminalState() {
        CollectionRunControl control = new CollectionRunControl();
        control.begin(progress(
                CollectionProgressSnapshot.State.RUNNING,
                1,
                1,
                1,
                0,
                0,
                10,
                OptionalInt.empty()));
        assertTrue(control.requestCancellation());

        CollectionProgressSnapshot complete = progress(
                CollectionProgressSnapshot.State.COMPLETE,
                1,
                1,
                1,
                0,
                0,
                10,
                OptionalInt.empty());
        CollectionProgressSnapshot cancelled = progress(
                CollectionProgressSnapshot.State.CANCELLED,
                1,
                1,
                1,
                0,
                0,
                10,
                OptionalInt.empty());

        assertEquals(cancelled, control.finish(complete, cancelled));
        assertTrue(control.isCancellationRequested());
        assertFalse(control.requestCancellation());
    }

    @Test
    void rejectsTerminalStatesWhoseCountersContradictTheirMeaning() {
        assertThrows(
                IllegalArgumentException.class,
                () -> progress(
                        CollectionProgressSnapshot.State.PARTIAL,
                        2,
                        2,
                        2,
                        0,
                        0,
                        2,
                        OptionalInt.empty()));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for collection control test barrier");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    "Interrupted while waiting for collection control test barrier", exception);
        }
    }

    private static CollectionProgressSnapshot progress(
            CollectionProgressSnapshot.State state,
            int total,
            int completed,
            int successful,
            int failed,
            int cancelled,
            long processed,
            OptionalInt active) {
        return new CollectionProgressSnapshot(
                state,
                total,
                completed,
                successful,
                failed,
                cancelled,
                processed,
                10,
                active);
    }
}
