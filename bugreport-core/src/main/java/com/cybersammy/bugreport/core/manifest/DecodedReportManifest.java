package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.version.SchemaVersion;
import java.util.Objects;

/** Current manifest model plus the exact compatible source schema version. */
public record DecodedReportManifest(
        ReportManifest manifest, SchemaVersion sourceVersion, boolean newerMinorVersion) {
    public DecodedReportManifest {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(sourceVersion, "sourceVersion");
        if (sourceVersion.major() != ReportManifestJsonCodec.CURRENT_SCHEMA_VERSION.major()) {
            throw new IllegalArgumentException("Decoded manifest major must be supported");
        }
        if (newerMinorVersion
                != (sourceVersion.minor()
                        > ReportManifestJsonCodec.CURRENT_SCHEMA_VERSION.minor())) {
            throw new IllegalArgumentException("Newer-minor marker is inconsistent");
        }
    }
}
