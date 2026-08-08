package com.cybersammy.bugreport.core.configuration;

import com.cybersammy.bugreport.api.version.SchemaVersion;
import java.util.Objects;

/** Decoded configuration with source-schema provenance for rewrite and diagnostics. */
public record DecodedReportConfiguration(
        ReportConfiguration configuration, SchemaVersion sourceVersion, boolean migrated) {
    public DecodedReportConfiguration {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(sourceVersion, "sourceVersion");
        if (migrated != !ReportConfigurationJsonCodec.CURRENT_SCHEMA_VERSION.equals(sourceVersion)) {
            throw new IllegalArgumentException("Configuration migration provenance is inconsistent");
        }
    }
}
