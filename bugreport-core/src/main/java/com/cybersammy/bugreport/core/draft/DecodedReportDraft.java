package com.cybersammy.bugreport.core.draft;

import com.cybersammy.bugreport.api.version.SchemaVersion;
import java.util.Objects;

/** Result of decoding and, when necessary, migrating one persisted draft. */
public record DecodedReportDraft(
        ReportDraft draft, SchemaVersion sourceVersion, boolean migrated) {
    /** Validates decoded draft provenance. */
    public DecodedReportDraft {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(sourceVersion, "sourceVersion");
        if (migrated != !DraftJsonCodec.CURRENT_SCHEMA_VERSION.equals(sourceVersion)) {
            throw new IllegalArgumentException(
                    "Migration flag must reflect the source schema version");
        }
    }
}
