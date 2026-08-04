package com.cybersammy.bugreport.core.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SourceSizeEstimatorTest {
    private static final FileTime OBSERVED_TIME = FileTime.from(Instant.EPOCH);

    @Test
    void detectsAggregateOverflowWithoutAddingPastLongMaximum() {
        List<ResolvedSourceFile> files =
                List.of(
                        observed("first.log", Long.MAX_VALUE - 1),
                        observed("second.log", 10));

        SourceSizeEstimator.SourceSizeLimitException exception =
                assertThrows(
                        SourceSizeEstimator.SourceSizeLimitException.class,
                        () ->
                                SourceSizeEstimator.estimate(
                                        files,
                                        CollectionConstraints.defaults(),
                                        new SourcePlanningLimits(
                                                2, Long.MAX_VALUE, Long.MAX_VALUE)));

        assertEquals(
                SourceSelectionFailureCode.TOTAL_SIZE_LIMIT_EXCEEDED,
                exception.code());
    }

    @Test
    void returnsExactAggregateAtInclusiveLimits() throws Exception {
        SourceSizeEstimate estimate =
                SourceSizeEstimator.estimate(
                        List.of(observed("first.log", 4), observed("second.log", 6)),
                        CollectionConstraints.defaults(),
                        new SourcePlanningLimits(2, 6, 10));

        assertEquals(SourceSizeEstimate.exact(2, 10), estimate);
    }

    private static ResolvedSourceFile observed(String name, long size) {
        return new ResolvedSourceFile(
                LogicalRoot.GAME_LOGS,
                RelativePath.of(name),
                Path.of("C:/trusted/logs").resolve(name),
                size,
                OBSERVED_TIME,
                name);
    }
}
