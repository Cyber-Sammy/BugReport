package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import java.util.List;
import java.util.Objects;

/** Applies effective provider/product byte ceilings to selected file observations. */
final class SourceSizeEstimator {
    private SourceSizeEstimator() {}

    static SourceSizeEstimate estimate(
            List<ResolvedSourceFile> files,
            CollectionConstraints constraints,
            SourcePlanningLimits productLimits)
            throws SourceSizeLimitException {
        List<ResolvedSourceFile> selected = List.copyOf(Objects.requireNonNull(files, "files"));
        CollectionConstraints requested = Objects.requireNonNull(constraints, "constraints");
        SourcePlanningLimits product = Objects.requireNonNull(productLimits, "productLimits");
        long perFileLimit =
                requested.maxBytesPerFile().isPresent()
                        ? Math.min(product.maxBytesPerFile(), requested.maxBytesPerFile().getAsLong())
                        : product.maxBytesPerFile();
        long totalLimit =
                requested.maxTotalBytes().isPresent()
                        ? Math.min(product.maxTotalBytes(), requested.maxTotalBytes().getAsLong())
                        : product.maxTotalBytes();

        long knownBytes = 0;
        for (ResolvedSourceFile file : selected) {
            ResolvedSourceFile observed = Objects.requireNonNull(file, "file");
            long size = observed.observedSize();
            if (size > perFileLimit) {
                throw new SourceSizeLimitException(
                        SourceSelectionFailureCode.FILE_SIZE_LIMIT_EXCEEDED);
            }
            if (size > totalLimit - knownBytes) {
                throw new SourceSizeLimitException(
                        SourceSelectionFailureCode.TOTAL_SIZE_LIMIT_EXCEEDED);
            }
            knownBytes += size;
        }
        return SourceSizeEstimate.exact(selected.size(), knownBytes);
    }

    static final class SourceSizeLimitException extends Exception {
        private static final long serialVersionUID = 1L;

        private final SourceSelectionFailureCode code;

        SourceSizeLimitException(SourceSelectionFailureCode code) {
            this.code = Objects.requireNonNull(code, "code");
        }

        SourceSelectionFailureCode code() {
            return code;
        }
    }
}
