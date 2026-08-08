package com.cybersammy.bugreport.core.history;

import com.cybersammy.bugreport.core.packaging.ReportZipArchive;
import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
import java.util.Objects;

/** Path-free immutable identity of a completed local report archive. */
public record ReportArchiveSummary(long bytes, Sha256Checksum checksum, int entryCount) {
    public ReportArchiveSummary {
        if (bytes <= 0 || entryCount <= 0) {
            throw new IllegalArgumentException("Archive summary must contain positive values");
        }
        Objects.requireNonNull(checksum, "checksum");
    }

    static ReportArchiveSummary from(ReportZipArchive archive) {
        ReportZipArchive value = Objects.requireNonNull(archive, "archive");
        return new ReportArchiveSummary(value.archiveBytes(), value.checksum(), value.entryCount());
    }
}
