package com.cybersammy.bugreport.core.packaging;

import com.cybersammy.bugreport.core.manifest.ReportManifest;
import com.cybersammy.bugreport.core.manifest.ReportManifestJsonCodec;

/** Product ceilings applied independently while writing and reading report archives. */
final class ReportZipLimits {
    static final int MAX_ENTRIES = ReportManifest.MAX_ENTRIES + 2;
    static final long MAX_TOTAL_UNCOMPRESSED_BYTES = Math.addExact(
            ReportManifest.MAX_TOTAL_UNCOMPRESSED_BYTES,
            (long) ReportManifestJsonCodec.MAX_ENCODED_BYTES
                    + ReportMarkdownRenderer.MAX_ENCODED_BYTES);
    static final long MAX_ARCHIVE_BYTES = 272L * 1024L * 1024L;
    static final int BUFFER_BYTES = 64 * 1024;
    static final long CANONICAL_ENTRY_TIME_MILLIS = 0L;
    static final byte[] CANONICAL_TIMESTAMP_EXTRA =
            new byte[] {0x55, 0x54, 0x05, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00};

    private ReportZipLimits() {}
}
