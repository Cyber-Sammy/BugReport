package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.OptionalInt;
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
        assertThrows(
                IllegalArgumentException.class,
                () -> progress(
                        CollectionProgressSnapshot.State.CANCELLED,
                        1,
                        1,
                        1,
                        0,
                        0,
                        1,
                        OptionalInt.empty()));
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
