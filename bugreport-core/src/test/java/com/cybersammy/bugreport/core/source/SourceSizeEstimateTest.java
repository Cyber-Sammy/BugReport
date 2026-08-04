package com.cybersammy.bugreport.core.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SourceSizeEstimateTest {
    @Test
    void distinguishesCompleteEstimateFromLowerBound() {
        SourceSizeEstimate exact = SourceSizeEstimate.exact(2, 42);
        SourceSizeEstimate lowerBound = SourceSizeEstimate.lowerBound(0, 0);

        assertTrue(exact.complete());
        assertEquals(2, exact.selectedFileCount());
        assertEquals(42, exact.knownBytes());
        assertFalse(lowerBound.complete());
    }

    @Test
    void rejectsNegativeCountsAndSizes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceSizeEstimate(-1, 0, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceSizeEstimate(0, -1, false));
    }
}
