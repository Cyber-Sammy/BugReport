package com.cybersammy.bugreport.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.GeneratorExecutionContext;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.specification.StandardCapabilities;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class WorldStateExportCapabilityTest {
    @Test
    void enablesWorldStateExportOnlyForCompatibleRuntimeCapability() {
        DiscoveredProvider provider = provider();

        RegisteredProvider missing = ProviderRegistry.createSnapshot(List.of(provider))
                .providers()
                .getFirst();
        assertEquals(ProviderSupportState.DISABLED, missing.support().state());
        assertEquals(
                CapabilityRequirementStatus.MISSING,
                missing.support().requirements().getFirst().status());

        RegisteredProvider compatible = ProviderRegistry.createSnapshot(
                        List.of(provider),
                        Map.of(
                                StandardCapabilities.boundedWorldStateExport().id(),
                                StandardCapabilities.boundedWorldStateExport().minimumVersion()))
                .providers()
                .getFirst();
        assertEquals(ProviderSupportState.ENABLED, compatible.support().state());
        assertEquals(
                CapabilityRequirementStatus.SATISFIED,
                compatible.support().requirements().getFirst().status());

        RegisteredProvider incompatible = ProviderRegistry.createSnapshot(
                        List.of(provider),
                        Map.of(
                                StandardCapabilities.boundedWorldStateExport().id(),
                                new CapabilityVersion(2, 0)))
                .providers()
                .getFirst();
        assertEquals(ProviderSupportState.DISABLED, incompatible.support().state());
        assertEquals(
                CapabilityRequirementStatus.INCOMPATIBLE_MAJOR,
                incompatible.support().requirements().getFirst().status());
    }

    private static DiscoveredProvider provider() {
        ProviderSpecification specification = specification();
        BugReportProvider provider = new BugReportProvider() {
            @Override
            public String providerId() {
                return "example";
            }

            @Override
            public String providerVersion() {
                return "1.0.0";
            }

            @Override
            public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        return new DiscoveredProvider(NamespaceId.of("example"), "WorldProvider", provider);
    }

    private static ProviderSpecification specification() {
        DiagnosticGeneratorId generatorId = DiagnosticGeneratorId.of("world_summary");
        DiagnosticGeneratorSpecification generator =
                DiagnosticGeneratorSpecification.worldStateExport(
                                generatorId, (request, sink) -> {})
                        .labelKey(LocalizationKey.of("example.generator.world_summary"))
                        .privacy(PrivacyClassification.SENSITIVE)
                        .contentType(DiagnosticContentType.JSON)
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .executionContext(GeneratorExecutionContext.WORKER)
                        .constraints(StandardCapabilities.boundedWorldStateExportMaximums())
                        .build();
        return ProviderSpecification.builder(
                        ProviderId.parse("example"),
                        ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addGenerator(generator)
                .addCategory(CategorySpecification.builder(
                                CategoryId.of("general"),
                                LocalizationKey.of("example.category.general"))
                        .useGenerator(generatorId)
                        .build())
                .requireCapability(StandardCapabilities.boundedWorldStateExport())
                .build();
    }
}
