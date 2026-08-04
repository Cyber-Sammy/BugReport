package com.cybersammy.bugreport.core.source;

/** Internal product ceilings applied before collection configuration is introduced. */
record SourcePlanningLimits(int maxMatchedFiles, long maxBytesPerFile, long maxTotalBytes) {
    static final int PRODUCT_MAX_MATCHED_FILES = 16;
    static final long PRODUCT_MAX_BYTES_PER_FILE = 64L * 1024L * 1024L;
    static final long PRODUCT_MAX_TOTAL_BYTES = 128L * 1024L * 1024L;

    SourcePlanningLimits {
        if (maxMatchedFiles <= 0 || maxBytesPerFile <= 0 || maxTotalBytes <= 0) {
            throw new IllegalArgumentException("Source planning limits must be positive");
        }
        if (maxBytesPerFile > maxTotalBytes) {
            throw new IllegalArgumentException("Per-file limit cannot exceed total limit");
        }
    }

    static SourcePlanningLimits productDefaults() {
        return new SourcePlanningLimits(
                PRODUCT_MAX_MATCHED_FILES,
                PRODUCT_MAX_BYTES_PER_FILE,
                PRODUCT_MAX_TOTAL_BYTES);
    }
}
