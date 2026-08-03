package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.CapabilityRequirement;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ReportSessionFactoryTest {
    private static final ReportSessionId SESSION_ID =
            new ReportSessionId(UUID.fromString("00000000-0000-4000-8000-000000000001"));

    @Test
    void capturesTheExactAcceptedRegistryResult() {
        ProviderSpecification specification = SessionProviderFixture.specification("example_mod");
        ProviderRegistrySnapshot registry = SessionProviderFixture.registry(specification);
        RegisteredProvider registered = registry.providers().getFirst();

        ReportSessionSnapshot snapshot =
                new ReportSessionFactory(registry)
                        .create(SESSION_ID, registered.id())
                        .snapshot();

        assertSame(registered.specification(), snapshot.providerSpecification());
        assertSame(registered.support(), snapshot.providerSupport());
        assertEquals(ProviderSupportState.ENABLED, snapshot.providerSupport().state());
        assertEquals(0, ReportSession.class.getConstructors().length);
    }

    @Test
    void allowsPartiallySupportedProvider() {
        ProviderSpecification partial =
                SessionProviderFixture.specificationBuilder("partial_mod")
                        .requireCapability(requirement(false))
                        .build();
        ProviderRegistrySnapshot registry = SessionProviderFixture.registry(partial);

        ReportSessionSnapshot snapshot =
                new ReportSessionFactory(registry)
                        .create(SESSION_ID, ProviderId.parse("partial_mod"))
                        .snapshot();

        assertEquals(
                ProviderSupportState.PARTIALLY_SUPPORTED,
                snapshot.providerSupport().state());
    }

    @Test
    void rejectsDisabledProvider() {
        ProviderSpecification disabled =
                SessionProviderFixture.specificationBuilder("disabled_mod")
                        .requireCapability(requirement(true))
                        .build();
        ProviderRegistrySnapshot registry = SessionProviderFixture.registry(disabled);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ReportSessionFactory(registry)
                                        .create(
                                                SESSION_ID,
                                                ProviderId.parse("disabled_mod")));

        assertEquals(
                "Cannot create a report session for a disabled provider: disabled_mod",
                exception.getMessage());
    }

    @Test
    void rejectsProviderAbsentFromRegistry() {
        ReportSessionFactory factory =
                new ReportSessionFactory(ProviderRegistrySnapshot.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> factory.create(SESSION_ID, ProviderId.parse("missing_mod")));

        assertEquals(
                "Cannot create a report session for an unregistered provider: missing_mod",
                exception.getMessage());
    }

    @Test
    void rejectsProviderRemovedByDuplicatePolicy() {
        ProviderSpecification first = SessionProviderFixture.specification("duplicate_mod");
        ProviderSpecification second = SessionProviderFixture.specification("duplicate_mod");
        ProviderRegistrySnapshot registry = SessionProviderFixture.registry(first, second);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportSessionFactory(registry)
                                .create(
                                        SESSION_ID,
                                        ProviderId.parse("duplicate_mod")));
    }

    private static CapabilityRequirement requirement(boolean required) {
        return new CapabilityRequirement(
                CapabilityId.of("bugreport:unavailable_session_fixture"),
                new CapabilityVersion(1, 0),
                required);
    }
}
