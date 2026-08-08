package com.cybersammy.bugreport.core.packaging;

/** Monotonic uncompressed-byte progress for one ZIP creation pass. */
public record ReportZipProgress(
        int completedEntries, int totalEntries, long processedBytes, long totalBytes) {
    public ReportZipProgress {
        if (totalEntries <= 0
                || completedEntries < 0
                || completedEntries > totalEntries
                || totalBytes < 0
                || processedBytes < 0
                || processedBytes > totalBytes) {
            throw new IllegalArgumentException("Report ZIP progress is inconsistent");
        }
    }
}
