package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import java.util.List;
import java.util.Objects;

/** One unique file selected for collection with every contributing declaration retained. */
public final class PlannedSourceFile {
    private final ResolvedSourceFile file;
    private final List<SourceProvenance> provenances;
    private final DiagnosticContentType contentType;
    private final PrivacyClassification privacy;
    private final ReportQualityRole qualityRole;
    private final InclusionDefault inclusionDefault;
    private final long maximumBytes;

    PlannedSourceFile(
            ResolvedSourceFile file,
            List<SourceProvenance> provenances,
            long maximumBytes) {
        this.file = Objects.requireNonNull(file, "file");
        this.provenances = SourcePlanConflict.canonicalProvenances(provenances);
        if (this.provenances.isEmpty()) {
            throw new IllegalArgumentException("A planned file requires source provenance");
        }
        contentType = this.provenances.getFirst().contentType();
        if (this.provenances.stream()
                .map(SourceProvenance::contentType)
                .anyMatch(type -> type != contentType)) {
            throw new IllegalArgumentException("A planned file cannot merge content types");
        }
        privacy = this.provenances.stream()
                .map(SourceProvenance::privacy)
                .reduce(PrivacyClassification.LOW, PrivacyClassification::mostRestrictive);
        qualityRole = this.provenances.stream()
                .map(SourceProvenance::qualityRole)
                .reduce(ReportQualityRole.OPTIONAL, PlannedSourceFile::moreImportant);
        inclusionDefault = this.provenances.stream()
                        .anyMatch(
                                provenance ->
                                        provenance.inclusionDefault() == InclusionDefault.INCLUDED)
                ? InclusionDefault.INCLUDED
                : InclusionDefault.EXCLUDED;
        if (maximumBytes <= 0 || file.observedSize() > maximumBytes) {
            throw new IllegalArgumentException(
                    "A planned file requires a positive effective byte ceiling");
        }
        this.maximumBytes = maximumBytes;
    }

    /** Returns the trusted planning-time file observation used only by Core collection. */
    public ResolvedSourceFile file() {
        return file;
    }

    /** Returns contributing declarations in canonical source-ID order. */
    public List<SourceProvenance> provenances() {
        return provenances;
    }

    /** Reports whether more than one declaration selected this canonical file. */
    public boolean duplicate() {
        return provenances.size() > 1;
    }

    /** Returns the common representation required for the file. */
    public DiagnosticContentType contentType() {
        return contentType;
    }

    /** Returns the most restrictive declared privacy floor. */
    public PrivacyClassification privacy() {
        return privacy;
    }

    /** Returns the most important declared report-quality role. */
    public ReportQualityRole qualityRole() {
        return qualityRole;
    }

    /** Returns included when at least one declaration requests initial inclusion. */
    public InclusionDefault inclusionDefault() {
        return inclusionDefault;
    }

    /** Returns the strict effective byte ceiling for streaming this unique file. */
    public long maximumBytes() {
        return maximumBytes;
    }

    private static ReportQualityRole moreImportant(
            ReportQualityRole first, ReportQualityRole second) {
        return importance(first) <= importance(second) ? first : second;
    }

    private static int importance(ReportQualityRole role) {
        return switch (role) {
            case REQUIRED -> 0;
            case RECOMMENDED -> 1;
            case OPTIONAL -> 2;
        };
    }
}
