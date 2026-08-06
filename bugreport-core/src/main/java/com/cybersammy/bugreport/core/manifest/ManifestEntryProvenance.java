package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** Path-free provider declaration provenance for one included entry. */
public final class ManifestEntryProvenance implements Comparable<ManifestEntryProvenance> {
    private static final Comparator<ManifestEntryProvenance> ORDER =
            Comparator.comparing(ManifestEntryProvenance::providerId)
                    .thenComparing(ManifestEntryProvenance::categoryId)
                    .thenComparing(ManifestEntryProvenance::declarationKind)
                    .thenComparing(ManifestEntryProvenance::declarationId);

    private final ProviderId providerId;
    private final ProviderVersion providerVersion;
    private final CategoryId categoryId;
    private final ManifestDeclarationKind declarationKind;
    private final String declarationId;
    private final Optional<DiagnosticSourceKind> sourceKind;
    private final PrivacyClassification declaredPrivacy;

    private ManifestEntryProvenance(
            ProviderId providerId,
            ProviderVersion providerVersion,
            CategoryId categoryId,
            ManifestDeclarationKind declarationKind,
            String declarationId,
            Optional<DiagnosticSourceKind> sourceKind,
            PrivacyClassification declaredPrivacy) {
        this.providerId = Objects.requireNonNull(providerId, "providerId");
        this.providerVersion = Objects.requireNonNull(providerVersion, "providerVersion");
        this.categoryId = Objects.requireNonNull(categoryId, "categoryId");
        this.declarationKind = Objects.requireNonNull(declarationKind, "declarationKind");
        this.declarationId = Objects.requireNonNull(declarationId, "declarationId");
        this.sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
        this.declaredPrivacy = Objects.requireNonNull(declaredPrivacy, "declaredPrivacy");
        validateDeclaration();
    }

    public static ManifestEntryProvenance source(
            ProviderId providerId,
            ProviderVersion providerVersion,
            CategoryId categoryId,
            DiagnosticSourceId sourceId,
            DiagnosticSourceKind sourceKind,
            PrivacyClassification declaredPrivacy) {
        return new ManifestEntryProvenance(
                providerId,
                providerVersion,
                categoryId,
                ManifestDeclarationKind.SOURCE,
                Objects.requireNonNull(sourceId, "sourceId").value(),
                Optional.of(Objects.requireNonNull(sourceKind, "sourceKind")),
                declaredPrivacy);
    }

    public static ManifestEntryProvenance generator(
            ProviderId providerId,
            ProviderVersion providerVersion,
            CategoryId categoryId,
            DiagnosticGeneratorId generatorId,
            PrivacyClassification declaredPrivacy) {
        return new ManifestEntryProvenance(
                providerId,
                providerVersion,
                categoryId,
                ManifestDeclarationKind.GENERATOR,
                Objects.requireNonNull(generatorId, "generatorId").value(),
                Optional.empty(),
                declaredPrivacy);
    }

    static ManifestEntryProvenance decoded(
            ProviderId providerId,
            ProviderVersion providerVersion,
            CategoryId categoryId,
            ManifestDeclarationKind declarationKind,
            String declarationId,
            Optional<DiagnosticSourceKind> sourceKind,
            PrivacyClassification declaredPrivacy) {
        return new ManifestEntryProvenance(
                providerId,
                providerVersion,
                categoryId,
                declarationKind,
                declarationId,
                sourceKind,
                declaredPrivacy);
    }

    public ProviderId providerId() {
        return providerId;
    }

    public ProviderVersion providerVersion() {
        return providerVersion;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public ManifestDeclarationKind declarationKind() {
        return declarationKind;
    }

    public String declarationId() {
        return declarationId;
    }

    public Optional<DiagnosticSourceKind> sourceKind() {
        return sourceKind;
    }

    public PrivacyClassification declaredPrivacy() {
        return declaredPrivacy;
    }

    @Override
    public int compareTo(ManifestEntryProvenance other) {
        return ORDER.compare(this, other);
    }

    private void validateDeclaration() {
        if (declarationKind == ManifestDeclarationKind.SOURCE) {
            DiagnosticSourceId.of(declarationId);
            if (sourceKind.isEmpty()) {
                throw new IllegalArgumentException("Source provenance requires a source kind");
            }
        } else {
            DiagnosticGeneratorId.of(declarationId);
            if (sourceKind.isPresent()) {
                throw new IllegalArgumentException(
                        "Generator provenance must not declare a source kind");
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ManifestEntryProvenance provenance)) {
            return false;
        }
        return providerId.equals(provenance.providerId)
                && providerVersion.equals(provenance.providerVersion)
                && categoryId.equals(provenance.categoryId)
                && declarationKind == provenance.declarationKind
                && declarationId.equals(provenance.declarationId)
                && sourceKind.equals(provenance.sourceKind)
                && declaredPrivacy == provenance.declaredPrivacy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                providerId,
                providerVersion,
                categoryId,
                declarationKind,
                declarationId,
                sourceKind,
                declaredPrivacy);
    }
}
