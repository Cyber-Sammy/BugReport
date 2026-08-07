package com.cybersammy.bugreport.core.packaging;

import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
import java.util.Objects;

/** Exact identity of one validated portable report archive. */
public record ReportZipArchive(
        long archiveBytes, Sha256Checksum checksum, int entryCount) {
    public ReportZipArchive {
        if (archiveBytes <= 0) {
            throw new IllegalArgumentException("A report archive must contain bytes");
        }
        Objects.requireNonNull(checksum, "checksum");
        if (entryCount <= 0 || entryCount > ReportZipLimits.MAX_ENTRIES) {
            throw new IllegalArgumentException("Report archive entry count is invalid");
        }
    }
}
