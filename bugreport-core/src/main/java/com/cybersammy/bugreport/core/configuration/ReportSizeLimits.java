package com.cybersammy.bugreport.core.configuration;

import com.cybersammy.bugreport.core.source.SourceSelectorPlanner;
import com.cybersammy.bugreport.core.workspace.FileCollectionCoordinator;

/** User-selected collection limits constrained by immutable product ceilings. */
public record ReportSizeLimits(
        int maximumMatchedFiles, long maximumBytesPerFile, long maximumReportBytes) {
    public ReportSizeLimits {
        if (maximumMatchedFiles <= 0
                || maximumMatchedFiles > SourceSelectorPlanner.MAX_MATCHED_FILES
                || maximumBytesPerFile <= 0
                || maximumBytesPerFile > SourceSelectorPlanner.MAX_BYTES_PER_FILE
                || maximumReportBytes <= 0
                || maximumReportBytes > FileCollectionCoordinator.PRODUCT_MAX_COLLECTION_BYTES
                || maximumBytesPerFile > maximumReportBytes) {
            throw new IllegalArgumentException("Report size limits exceed product bounds");
        }
    }

    /** Returns the most permissive limits supported by the current product release. */
    public static ReportSizeLimits productDefaults() {
        return new ReportSizeLimits(
                SourceSelectorPlanner.MAX_MATCHED_FILES,
                SourceSelectorPlanner.MAX_BYTES_PER_FILE,
                FileCollectionCoordinator.PRODUCT_MAX_COLLECTION_BYTES);
    }
}
