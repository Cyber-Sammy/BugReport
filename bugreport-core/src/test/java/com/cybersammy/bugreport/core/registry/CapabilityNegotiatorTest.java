package com.cybersammy.bugreport.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CapabilityOffer;
import com.cybersammy.bugreport.api.specification.CapabilityRequirement;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CapabilityNegotiatorTest {
    private static final CapabilityId COMPATIBLE = capability("bugreport:compatible");
    private static final CapabilityId OLD_MINOR = capability("bugreport:old_minor");
    private static final CapabilityId OTHER_MAJOR = capability("bugreport:other_major");
    private static final CapabilityId MISSING = capability("bugreport:missing");

    @Test
    void appliesExactMajorAndMinimumMinorCompatibilityRules() {
        ProviderRegistrySnapshot snapshot =
                ProviderRegistry.createSnapshot(
                        List.of(
                                provider(
                                        "mod_enabled",
                                        List.of(requirement(COMPATIBLE, 1, 2, true)),
                                        List.of()),
                                provider(
                                        "mod_partial",
                                        List.of(requirement(MISSING, 1, 0, false)),
                                        List.of()),
                                provider(
                                        "mod_old",
                                        List.of(requirement(OLD_MINOR, 2, 2, true)),
                                        List.of()),
                                provider(
                                        "mod_major",
                                        List.of(requirement(OTHER_MAJOR, 2, 0, true)),
                                        List.of()),
                                provider(
                                        "mod_missing",
                                        List.of(requirement(MISSING, 1, 0, true)),
                                        List.of())),
                        Map.of(
                                COMPATIBLE, version(1, 5),
                                OLD_MINOR, version(2, 1),
                                OTHER_MAJOR, version(3, 0)));

        assertEquals(
                Map.of(
                        ProviderId.parse("mod_enabled"), ProviderSupportState.ENABLED,
                        ProviderId.parse("mod_partial"), ProviderSupportState.PARTIALLY_SUPPORTED,
                        ProviderId.parse("mod_old"), ProviderSupportState.DISABLED,
                        ProviderId.parse("mod_major"), ProviderSupportState.DISABLED,
                        ProviderId.parse("mod_missing"), ProviderSupportState.DISABLED),
                states(snapshot));
        assertEquals(
                CapabilityRequirementStatus.SATISFIED,
                result(snapshot, "mod_enabled").status());
        assertEquals(
                CapabilityRequirementStatus.MISSING,
                result(snapshot, "mod_partial").status());
        assertEquals(
                CapabilityRequirementStatus.INSUFFICIENT_MINOR,
                result(snapshot, "mod_old").status());
        assertEquals(
                CapabilityRequirementStatus.INCOMPATIBLE_MAJOR,
                result(snapshot, "mod_major").status());
        assertEquals(version(2, 1), result(snapshot, "mod_old").availableVersion().orElseThrow());
    }

    @Test
    void removesOffersFromDisabledProvidersUntilNegotiationStabilizes() {
        CapabilityId capabilityA = capability("mod_a:service");
        CapabilityId capabilityB = capability("mod_b:service");
        ProviderRegistrySnapshot snapshot =
                ProviderRegistry.createSnapshot(
                        List.of(
                                provider(
                                        "mod_a",
                                        List.of(requirement(capabilityB, 1, 0, true)),
                                        List.of(offer(capabilityA, 1, 0))),
                                provider(
                                        "mod_b",
                                        List.of(requirement(MISSING, 1, 0, true)),
                                        List.of(offer(capabilityB, 1, 0))),
                                provider(
                                        "mod_c",
                                        List.of(requirement(capabilityA, 1, 0, true)),
                                        List.of())));

        assertEquals(
                Map.of(
                        ProviderId.parse("mod_a"), ProviderSupportState.DISABLED,
                        ProviderId.parse("mod_b"), ProviderSupportState.DISABLED,
                        ProviderId.parse("mod_c"), ProviderSupportState.DISABLED),
                states(snapshot));
        assertEquals(CapabilityRequirementStatus.MISSING, result(snapshot, "mod_a").status());
        assertEquals(CapabilityRequirementStatus.MISSING, result(snapshot, "mod_c").status());
    }

    @Test
    void retainsMutuallySatisfiedCapabilityCycle() {
        CapabilityId capabilityA = capability("mod_a:service");
        CapabilityId capabilityB = capability("mod_b:service");
        ProviderRegistrySnapshot snapshot =
                ProviderRegistry.createSnapshot(
                        List.of(
                                provider(
                                        "mod_a",
                                        List.of(requirement(capabilityB, 1, 0, true)),
                                        List.of(offer(capabilityA, 1, 0))),
                                provider(
                                        "mod_b",
                                        List.of(requirement(capabilityA, 1, 0, true)),
                                        List.of(offer(capabilityB, 1, 0)))));

        assertEquals(
                Map.of(
                        ProviderId.parse("mod_a"), ProviderSupportState.ENABLED,
                        ProviderId.parse("mod_b"), ProviderSupportState.ENABLED),
                states(snapshot));
    }

    @Test
    void rejectsEveryGlobalCapabilityOfferCollision() {
        CapabilityId shared = capability("shared:service");
        ProviderRegistrySnapshot snapshot =
                ProviderRegistry.createSnapshot(
                        List.of(
                                provider(
                                        "shared:first",
                                        List.of(),
                                        List.of(offer(shared, 1, 0))),
                                provider(
                                        "shared:second",
                                        List.of(),
                                        List.of(offer(shared, 1, 1))),
                                provider(
                                        "consumer",
                                        List.of(requirement(shared, 1, 0, true)),
                                        List.of())));

        RegisteredProvider first = registered(snapshot, "shared:first");
        RegisteredProvider second = registered(snapshot, "shared:second");
        assertEquals(ProviderSupportState.DISABLED, first.support().state());
        assertEquals(ProviderSupportState.DISABLED, second.support().state());
        assertEquals(
                List.of(ProviderId.parse("shared:first"), ProviderId.parse("shared:second")),
                first.support().offerCollisions().getFirst().providerIds());
        assertEquals(
                List.of(version(1, 0), version(1, 1)),
                first.support().offerCollisions().getFirst().providerOffers().stream()
                        .map(CapabilityOfferProvenance::version)
                        .toList());
        assertEquals(
                Optional.empty(),
                first.support().offerCollisions().getFirst().runtimeVersion());
        assertEquals(
                ProviderSupportState.DISABLED,
                registered(snapshot, "consumer").support().state());
        assertEquals(
                CapabilityRequirementStatus.MISSING,
                result(snapshot, "consumer").status());
    }

    @Test
    void reportsRuntimeAndProviderVersionsForOfferCollision() {
        CapabilityId shared = capability("shared:runtime_service");
        ProviderRegistrySnapshot snapshot =
                ProviderRegistry.createSnapshot(
                        List.of(
                                provider(
                                        "shared:provider",
                                        List.of(),
                                        List.of(offer(shared, 1, 1)))),
                        Map.of(shared, version(1, 0)));

        CapabilityOfferCollision collision =
                registered(snapshot, "shared:provider")
                        .support()
                        .offerCollisions()
                        .getFirst();
        assertEquals(ProviderSupportState.DISABLED, registered(snapshot, "shared:provider").support().state());
        assertEquals(version(1, 1), collision.providerOffers().getFirst().version());
        assertEquals(Optional.of(version(1, 0)), collision.runtimeVersion());
    }

    private static Map<ProviderId, ProviderSupportState> states(
            ProviderRegistrySnapshot snapshot) {
        return snapshot.providers().stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                RegisteredProvider::id,
                                provider -> provider.support().state()));
    }

    private static CapabilityRequirementResult result(
            ProviderRegistrySnapshot snapshot,
            String providerId) {
        return registered(snapshot, providerId).support().requirements().getFirst();
    }

    private static RegisteredProvider registered(
            ProviderRegistrySnapshot snapshot,
            String providerId) {
        return snapshot.find(ProviderId.parse(providerId)).orElseThrow();
    }

    private static DiscoveredProvider provider(
            String id,
            List<CapabilityRequirement> requirements,
            List<CapabilityOffer> offers) {
        ProviderSpecification.Builder specification =
                ProviderSpecification.builder(
                                ProviderId.parse(id),
                                ProviderVersion.parse("1.0.0"),
                                key(id, "provider"))
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .addCategory(
                                CategorySpecification.builder(
                                                CategoryId.of("general"),
                                                key(id, "category.general"))
                                        .build());
        requirements.forEach(specification::requireCapability);
        offers.forEach(specification::offerCapability);
        ProviderSpecification built = specification.build();
        BugReportProvider provider = new BugReportProvider() {
            @Override
            public String providerId() {
                return id;
            }

            @Override
            public String providerVersion() {
                return "1.0.0";
            }

            @Override
            public Optional<ProviderSpecification> specification() {
                return Optional.of(built);
            }
        };
        return new DiscoveredProvider(
                ProviderId.parse(id).namespace(),
                "Provider_" + id,
                provider);
    }

    private static CapabilityRequirement requirement(
            CapabilityId id,
            int major,
            int minor,
            boolean required) {
        return new CapabilityRequirement(id, version(major, minor), required);
    }

    private static CapabilityOffer offer(CapabilityId id, int major, int minor) {
        return new CapabilityOffer(id, version(major, minor));
    }

    private static CapabilityId capability(String value) {
        return CapabilityId.of(value);
    }

    private static CapabilityVersion version(int major, int minor) {
        return new CapabilityVersion(major, minor);
    }

    private static LocalizationKey key(String providerId, String suffix) {
        return LocalizationKey.of(providerId.replace(':', '.') + "." + suffix);
    }
}
