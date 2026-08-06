package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.constraint.CollectionConstraints;

/** Effective product/provider/report limits shared by capture and publication. */
record GeneratedDiagnosticLimits(
        int maxArtifacts,
        long maxBytesPerArtifact,
        long maxTotalBytes,
        long remainingCollectionBytes) {
    GeneratedDiagnosticLimits {
        if (maxArtifacts <= 0
                || maxBytesPerArtifact <= 0
                || maxTotalBytes <= 0
                || remainingCollectionBytes < 0) {
            throw new IllegalArgumentException("Generated diagnostic limits are invalid");
        }
    }

    static GeneratedDiagnosticLimits from(
            CollectionConstraints constraints, long remainingCollectionBytes) {
        int requestedArtifacts = constraints.maxGeneratedArtifacts().isPresent()
                ? constraints.maxGeneratedArtifacts().getAsInt()
                : GeneratedDiagnosticCollector.PRODUCT_MAX_ARTIFACTS;
        long requestedPerArtifact = constraints.maxBytesPerFile().isPresent()
                ? constraints.maxBytesPerFile().getAsLong()
                : GeneratedDiagnosticCollector.PRODUCT_MAX_BYTES_PER_ARTIFACT;
        long requestedTotal = constraints.maxTotalBytes().isPresent()
                ? constraints.maxTotalBytes().getAsLong()
                : GeneratedDiagnosticCollector.PRODUCT_MAX_GENERATOR_BYTES;
        long effectiveTotal = Math.min(
                GeneratedDiagnosticCollector.PRODUCT_MAX_GENERATOR_BYTES,
                requestedTotal);
        return new GeneratedDiagnosticLimits(
                Math.min(GeneratedDiagnosticCollector.PRODUCT_MAX_ARTIFACTS, requestedArtifacts),
                Math.min(
                        Math.min(
                                GeneratedDiagnosticCollector.PRODUCT_MAX_BYTES_PER_ARTIFACT,
                                requestedPerArtifact),
                        effectiveTotal),
                effectiveTotal,
                remainingCollectionBytes);
    }
}
