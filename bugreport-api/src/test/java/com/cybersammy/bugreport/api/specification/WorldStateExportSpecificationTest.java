package com.cybersammy.bugreport.api.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class WorldStateExportSpecificationTest {
    private static final DiagnosticGeneratorId GENERATOR_ID =
            DiagnosticGeneratorId.of("world_summary");

    @Test
    void createsBoundedHighSensitivityWorldStateExport() {
        DiagnosticGeneratorSpecification generator = validGenerator();

        assertEquals(DiagnosticGeneratorKind.WORLD_STATE_EXPORT, generator.kind());
        assertEquals(PrivacyClassification.SENSITIVE, generator.privacy());
        assertEquals(InclusionDefault.EXCLUDED, generator.inclusionDefault());
        assertEquals(
                StandardCapabilities.boundedWorldStateExportMaximums(),
                generator.constraints());
    }

    @Test
    void generalGeneratorRemainsSeparateFromWorldStateAuthority() {
        DiagnosticGeneratorSpecification generator = DiagnosticGeneratorSpecification.builder(
                        DiagnosticGeneratorId.of("environment"), (request, sink) -> {})
                .labelKey(LocalizationKey.of("example.generator.environment"))
                .privacy(PrivacyClassification.LOW)
                .contentType(DiagnosticContentType.JSON)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .executionContext(GeneratorExecutionContext.WORKER)
                .build();

        assertEquals(DiagnosticGeneratorKind.GENERAL, generator.kind());
    }

    @Test
    void rejectsWeakPrivacyAndImplicitOrLooseLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> worldStateBuilder()
                        .privacy(PrivacyClassification.PERSONAL)
                        .constraints(StandardCapabilities.boundedWorldStateExportMaximums())
                        .build());
        assertThrows(IllegalArgumentException.class, () -> worldStateBuilder().build());
        for (CollectionConstraints constraints : new CollectionConstraints[] {
            constraints(5, 1_048_576, 2_097_152, Duration.ofSeconds(2)),
            constraints(4, 1_048_577, 2_097_152, Duration.ofSeconds(2)),
            constraints(4, 1_048_576, 2_097_153, Duration.ofSeconds(2)),
            constraints(4, 1_048_576, 2_097_152, Duration.ofMillis(2_001))
        }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> worldStateBuilder().constraints(constraints).build());
        }
    }

    @Test
    void rejectsFilesystemTraversalConstraintsForGeneratedWorldState() {
        CollectionConstraints constraints = CollectionConstraints.builder()
                .maxTraversalDepth(0)
                .maxGeneratedArtifacts(1)
                .maxBytesPerFile(1024)
                .maxTotalBytes(1024)
                .callbackTimeout(Duration.ofMillis(100))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> worldStateBuilder().constraints(constraints).build());
    }

    @Test
    void providerMustRequireTheStandardCapability() {
        DiagnosticGeneratorSpecification generator = validGenerator();

        assertThrows(
                IllegalArgumentException.class,
                () -> providerBuilder(generator).build());
        CapabilityRequirement optional = new CapabilityRequirement(
                StandardCapabilities.boundedWorldStateExport().id(),
                StandardCapabilities.boundedWorldStateExport().minimumVersion(),
                false);
        assertThrows(
                IllegalArgumentException.class,
                () -> providerBuilder(generator).requireCapability(optional).build());
        CapabilityRequirement obsolete = new CapabilityRequirement(
                StandardCapabilities.boundedWorldStateExport().id(),
                new CapabilityVersion(0, 9),
                true);
        assertThrows(
                IllegalArgumentException.class,
                () -> providerBuilder(generator).requireCapability(obsolete).build());

        ProviderSpecification specification = providerBuilder(generator)
                .requireCapability(StandardCapabilities.boundedWorldStateExport())
                .build();
        assertEquals(
                StandardCapabilities.boundedWorldStateExport(),
                specification.capabilityRequirements().get(
                        StandardCapabilities.boundedWorldStateExport().id()));

        CapabilityRequirement newer = new CapabilityRequirement(
                StandardCapabilities.boundedWorldStateExport().id(),
                new CapabilityVersion(1, 1),
                true);
        ProviderSpecification newerSpecification =
                providerBuilder(generator).requireCapability(newer).build();
        assertEquals(
                newer,
                newerSpecification.capabilityRequirements().get(newer.id()));
    }

    private static DiagnosticGeneratorSpecification validGenerator() {
        return worldStateBuilder()
                .constraints(StandardCapabilities.boundedWorldStateExportMaximums())
                .build();
    }

    private static DiagnosticGeneratorSpecification.Builder worldStateBuilder() {
        return DiagnosticGeneratorSpecification.worldStateExport(
                        GENERATOR_ID, (request, sink) -> {})
                .labelKey(LocalizationKey.of("example.generator.world_summary"))
                .privacy(PrivacyClassification.SENSITIVE)
                .contentType(DiagnosticContentType.JSON)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .executionContext(GeneratorExecutionContext.WORKER);
    }

    private static ProviderSpecification.Builder providerBuilder(
            DiagnosticGeneratorSpecification generator) {
        return ProviderSpecification.builder(
                        ProviderId.parse("example"),
                        ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addGenerator(generator)
                .addCategory(CategorySpecification.builder(
                                CategoryId.of("general"),
                                LocalizationKey.of("example.category.general"))
                        .useGenerator(generator.id())
                        .build());
    }

    private static CollectionConstraints constraints(
            int artifacts, long bytesPerArtifact, long totalBytes, Duration timeout) {
        return CollectionConstraints.builder()
                .maxGeneratedArtifacts(artifacts)
                .maxBytesPerFile(bytesPerArtifact)
                .maxTotalBytes(totalBytes)
                .callbackTimeout(timeout)
                .build();
    }
}
