package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ReportSessionValueTest {
    private static final String CANONICAL_ID = "abcdefab-cdef-4abc-8def-abcdefabcdef";

    @Test
    void parsesOnlyCanonicalLowercaseUuidText() {
        ReportSessionId id = ReportSessionId.parse(CANONICAL_ID);

        assertEquals(UUID.fromString(CANONICAL_ID), id.value());
        assertEquals(CANONICAL_ID, id.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> ReportSessionId.parse(CANONICAL_ID.toUpperCase()));
        assertThrows(IllegalArgumentException.class, () -> ReportSessionId.parse("1-1-1-1-1"));
        assertThrows(IllegalArgumentException.class, () -> ReportSessionId.parse(null));
    }

    @Test
    void snapshotRejectsNegativeRevision() {
        ProviderRegistrySnapshot registry =
                SessionProviderFixture.registry(
                        SessionProviderFixture.specification("example_mod"));
        RegisteredProvider provider = registry.providers().getFirst();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportSessionSnapshot(
                                ReportSessionId.parse(CANONICAL_ID),
                                provider.specification(),
                                provider.support(),
                                Optional.empty(),
                                ReportSessionState.CREATED,
                                -1));
    }

    @Test
    void snapshotRequiresTrustedCategoryForFormState() {
        ProviderRegistrySnapshot registry =
                SessionProviderFixture.registry(
                        SessionProviderFixture.specification("example_mod"));
        RegisteredProvider provider = registry.providers().getFirst();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportSessionSnapshot(
                                ReportSessionId.parse(CANONICAL_ID),
                                provider.specification(),
                                provider.support(),
                                Optional.empty(),
                                ReportSessionState.FORM_IN_PROGRESS,
                                1));

        CategorySpecification independentlyBuilt =
                CategorySpecification.builder(
                                CategoryId.of("general"),
                                LocalizationKey.of("example_mod.bugreport.category.general"))
                        .build();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportSessionSnapshot(
                                ReportSessionId.parse(CANONICAL_ID),
                                provider.specification(),
                                provider.support(),
                                Optional.of(independentlyBuilt),
                                ReportSessionState.FORM_IN_PROGRESS,
                                1));
    }
}
