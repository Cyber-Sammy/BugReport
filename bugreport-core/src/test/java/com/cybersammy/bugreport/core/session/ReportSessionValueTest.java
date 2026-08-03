package com.cybersammy.bugreport.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
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
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReportSessionSnapshot(
                                ReportSessionId.parse(CANONICAL_ID),
                                specification(),
                                ReportSessionState.CREATED,
                                -1));
    }

    private static ProviderSpecification specification() {
        return ProviderSpecification.builder(
                        ProviderId.parse("example_mod"),
                        ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of("example_mod.bugreport.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addCategory(
                        CategorySpecification.builder(
                                        CategoryId.of("general"),
                                        LocalizationKey.of(
                                                "example_mod.bugreport.category.general"))
                                .build())
                .build();
    }
}
