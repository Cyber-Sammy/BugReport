package com.cybersammy.bugreport.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.NamespaceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ProviderRegistryTest {
    @Test
    void producesTheSameSnapshotForEveryCandidateOrder() {
        List<DiscoveredProvider> candidates =
                List.of(
                        candidate("mod_z", "ZProvider", provider("mod_z", "1.0.0")),
                        candidate(
                                "mod_dup",
                                "DuplicateB",
                                provider("mod_dup:secondary", "1.0.0")),
                        candidate("mod_a", "AProvider", provider("mod_a", "1.0.0")),
                        candidate(
                                "mod_dup",
                                "DuplicateA",
                                provider("mod_dup:secondary", "1.0.0")));

        ProviderRegistrySnapshot forward = ProviderRegistry.createSnapshot(candidates);
        assertEquals(
                List.of(ProviderId.parse("mod_a"), ProviderId.parse("mod_z")),
                forward.providerIds());
        assertEquals(
                List.of("DuplicateA", "DuplicateB"),
                forward.diagnostics().stream()
                        .map(ProviderRegistryDiagnostic::implementationClass)
                        .toList());

        for (int seed = 0; seed < 100; seed++) {
            List<DiscoveredProvider> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, new Random(seed));
            ProviderRegistrySnapshot actual = ProviderRegistry.createSnapshot(shuffled);

            assertEquals(forward.providerIds(), actual.providerIds());
            assertEquals(
                    forward.diagnostics().stream()
                            .map(ProviderRegistryDiagnostic::logToken)
                            .toList(),
                    actual.diagnostics().stream()
                            .map(ProviderRegistryDiagnostic::logToken)
                            .toList());
        }
    }

    @Test
    void rejectsBridgeAndSpecificationIdentityMismatchesBeforeRegistration() {
        DiscoveredProvider idMismatch =
                candidate(
                        "mod_a",
                        "IdMismatch",
                        provider(
                                "mod_a",
                                "1.0.0",
                                specification("mod_a:other", "1.0.0")));
        DiscoveredProvider versionMismatch =
                candidate(
                        "mod_b",
                        "VersionMismatch",
                        provider(
                                "mod_b",
                                "2.0.0",
                                specification("mod_b", "1.0.0")));

        ProviderRegistrySnapshot snapshot =
                ProviderRegistry.createSnapshot(List.of(versionMismatch, idMismatch));

        assertEquals(List.of(), snapshot.providers());
        assertEquals(
                List.of(
                        ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_ID_MISMATCH,
                        ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_VERSION_MISMATCH),
                snapshot.diagnostics().stream()
                        .map(ProviderRegistryDiagnostic::code)
                        .toList());
        ProviderRegistryDiagnostic idDiagnostic = snapshot.diagnostics().getFirst();
        assertEquals("mod_a", idDiagnostic.bridgeValue());
        assertEquals("mod_a:other", idDiagnostic.specificationValue());
        ProviderRegistryDiagnostic versionDiagnostic = snapshot.diagnostics().get(1);
        assertEquals("2.0.0", versionDiagnostic.bridgeValue());
        assertEquals("1.0.0", versionDiagnostic.specificationValue());
    }

    @Test
    void resolvesDuplicatesBeforeInspectingSpecifications() {
        ProviderRegistrySnapshot snapshot =
                ProviderRegistry.createSnapshot(
                        List.of(
                                candidate(
                                        "mod_a",
                                        "BrokenDuplicate",
                                        new ThrowingSpecificationProvider("mod_a")),
                                candidate(
                                        "mod_a",
                                        "ValidDuplicate",
                                        provider("mod_a", "1.0.0"))));

        assertEquals(List.of(), snapshot.providers());
        assertEquals(
                List.of(
                        ProviderRegistryDiagnosticCode.DUPLICATE_PROVIDER_ID,
                        ProviderRegistryDiagnosticCode.DUPLICATE_PROVIDER_ID),
                snapshot.diagnostics().stream()
                        .map(ProviderRegistryDiagnostic::code)
                        .toList());
    }

    @Test
    void isolatesInvalidLegacyAndThrowingProviders() {
        BugReportProvider throwingId = new ThrowingProvider(ThrowPoint.ID);
        BugReportProvider throwingSpecification =
                new ThrowingProvider(ThrowPoint.SPECIFICATION);
        BugReportProvider throwingVersion = new ThrowingProvider(ThrowPoint.VERSION);
        BugReportProvider nullSpecification = new NullSpecificationProvider();
        BugReportProvider valid = provider("mod_ok", "1.0.0");

        ProviderRegistrySnapshot snapshot =
                ProviderRegistry.createSnapshot(
                        List.of(
                                candidate("mod_throw_id", "ThrowId", throwingId),
                                candidate("mod_invalid", "NullId", new NullIdProvider()),
                                candidate("mod_owner", "ForeignId", provider("other_mod", "1.0.0")),
                                candidate(
                                        "mod_throw_spec",
                                        "ThrowSpecification",
                                        throwingSpecification),
                                candidate("mod_legacy", "Legacy", new LegacyProvider()),
                                candidate("mod_null_spec", "NullSpecification", nullSpecification),
                                candidate(
                                        "mod_throw_version",
                                        "ThrowVersion",
                                        throwingVersion),
                                candidate("mod_ok", "Valid", valid)));

        assertEquals(List.of(ProviderId.parse("mod_ok")), snapshot.providerIds());
        assertSame(valid, snapshot.providers().getFirst().provider());
        assertEquals(
                List.of(
                        ProviderRegistryDiagnosticCode.INVALID_PROVIDER_ID,
                        ProviderRegistryDiagnosticCode.MISSING_PROVIDER_SPECIFICATION,
                        ProviderRegistryDiagnosticCode.PROVIDER_ID_FAILED,
                        ProviderRegistryDiagnosticCode.PROVIDER_ID_OWNERSHIP_MISMATCH,
                        ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_FAILED,
                        ProviderRegistryDiagnosticCode.PROVIDER_SPECIFICATION_FAILED,
                        ProviderRegistryDiagnosticCode.PROVIDER_VERSION_FAILED),
                snapshot.diagnostics().stream()
                        .map(ProviderRegistryDiagnostic::code)
                        .toList());
    }

    @Test
    void snapshotDefensivelyCopiesCollectionsAndSupportsCanonicalLookup() {
        DiscoveredProvider valid = candidate("mod_a", "Provider", provider("mod_a", "1.0.0"));
        List<DiscoveredProvider> input = new ArrayList<>(List.of(valid));

        ProviderRegistrySnapshot snapshot = ProviderRegistry.createSnapshot(input);
        input.clear();

        assertEquals(1, snapshot.providers().size());
        assertSame(
                valid.provider(),
                snapshot.find(ProviderId.parse("mod_a")).orElseThrow().provider());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.providers().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.diagnostics().clear());
    }

    private static DiscoveredProvider candidate(
            String ownerNamespace,
            String implementationClass,
            BugReportProvider provider) {
        return new DiscoveredProvider(
                NamespaceId.of(ownerNamespace),
                implementationClass,
                provider);
    }

    private static BugReportProvider provider(String id, String version) {
        return provider(id, version, specification(id, version));
    }

    private static BugReportProvider provider(
            String id,
            String version,
            ProviderSpecification specification) {
        return new BugReportProvider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public String providerVersion() {
                return version;
            }

            @Override
            public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
    }

    private static ProviderSpecification specification(String id, String version) {
        return ProviderSpecification.builder(
                        ProviderId.parse(id),
                        ProviderVersion.parse(version),
                        LocalizationKey.of(id.replace(':', '.') + ".provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addCategory(
                        CategorySpecification.builder(
                                        CategoryId.of("general"),
                                        LocalizationKey.of(
                                                id.replace(':', '.') + ".category.general"))
                                .build())
                .build();
    }

    private enum ThrowPoint {
        ID,
        SPECIFICATION,
        VERSION
    }

    private static final class ThrowingProvider implements BugReportProvider {
        private final ThrowPoint throwPoint;

        private ThrowingProvider(ThrowPoint throwPoint) {
            this.throwPoint = throwPoint;
        }

        @Override
        public String providerId() {
            if (throwPoint == ThrowPoint.ID) {
                throw new IllegalStateException("id failure");
            }
            return switch (throwPoint) {
                case SPECIFICATION -> "mod_throw_spec";
                case VERSION -> "mod_throw_version";
                case ID -> throw new AssertionError("Handled before the switch");
            };
        }

        @Override
        public String providerVersion() {
            if (throwPoint == ThrowPoint.VERSION) {
                throw new IllegalStateException("version failure");
            }
            return "1.0.0";
        }

        @Override
        public Optional<ProviderSpecification> specification() {
            if (throwPoint == ThrowPoint.SPECIFICATION) {
                throw new IllegalStateException("specification failure");
            }
            return Optional.of(
                    ProviderRegistryTest.specification(providerId(), "1.0.0"));
        }
    }

    private static final class LegacyProvider implements BugReportProvider {
        @Override
        public String providerId() {
            return "mod_legacy";
        }
    }

    private static final class NullIdProvider implements BugReportProvider {
        @Override
        public String providerId() {
            return null;
        }
    }

    private static final class NullSpecificationProvider implements BugReportProvider {
        @Override
        public String providerId() {
            return "mod_null_spec";
        }

        @Override
        public Optional<ProviderSpecification> specification() {
            return null;
        }
    }

    private static final class ThrowingSpecificationProvider implements BugReportProvider {
        private final String id;

        private ThrowingSpecificationProvider(String id) {
            this.id = id;
        }

        @Override
        public String providerId() {
            return id;
        }

        @Override
        public Optional<ProviderSpecification> specification() {
            throw new AssertionError("Duplicate specifications must not be inspected");
        }
    }
}
