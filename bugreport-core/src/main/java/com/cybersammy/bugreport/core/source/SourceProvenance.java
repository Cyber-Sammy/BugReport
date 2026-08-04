package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.Objects;

/** Stable declaration provenance retained without exposing local absolute paths. */
public record SourceProvenance(
        ProviderId providerId,
        ProviderVersion providerVersion,
        CategoryId categoryId,
        DiagnosticSourceId sourceId,
        DiagnosticSourceKind kind,
        DiagnosticContentType contentType,
        PrivacyClassification privacy,
        ReportQualityRole qualityRole,
        InclusionDefault inclusionDefault) {
    /** Validates complete source provenance. */
    public SourceProvenance {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(privacy, "privacy");
        Objects.requireNonNull(qualityRole, "qualityRole");
        Objects.requireNonNull(inclusionDefault, "inclusionDefault");
    }
}
