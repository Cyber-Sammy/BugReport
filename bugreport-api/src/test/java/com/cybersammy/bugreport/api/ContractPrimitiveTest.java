package com.cybersammy.bugreport.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ContractPrimitiveTest {
    @Test
    void privacyCanOnlyMoveTowardMoreRestrictivePolicy() {
        assertEquals(
                PrivacyClassification.SENSITIVE,
                PrivacyClassification.mostRestrictive(
                        PrivacyClassification.PERSONAL,
                        PrivacyClassification.SENSITIVE));
        assertTrue(
                PrivacyClassification.PROHIBITED.isAtLeast(
                        PrivacyClassification.SENSITIVE));
        assertFalse(
                PrivacyClassification.LOW.isAtLeast(
                        PrivacyClassification.PERSONAL));
    }

    @Test
    void collectionConstraintsAreImmutableAndOptional() {
        CollectionConstraints constraints =
                CollectionConstraints.builder()
                        .maxTraversalDepth(0)
                        .maxMatchedFiles(10)
                        .maxBytesPerFile(1_024)
                        .maxTotalBytes(4_096)
                        .maxGeneratedArtifacts(2)
                        .callbackTimeout(Duration.ofSeconds(5))
                        .build();

        assertEquals(0, constraints.maxTraversalDepth().orElseThrow());
        assertEquals(10, constraints.maxMatchedFiles().orElseThrow());
        assertEquals(1_024, constraints.maxBytesPerFile().orElseThrow());
        assertEquals(4_096, constraints.maxTotalBytes().orElseThrow());
        assertEquals(2, constraints.maxGeneratedArtifacts().orElseThrow());
        assertEquals(Duration.ofSeconds(5), constraints.callbackTimeout().orElseThrow());
        assertTrue(CollectionConstraints.defaults().maxMatchedFiles().isEmpty());
    }

    @Test
    void rejectsInvalidOrContradictoryCollectionBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CollectionConstraints.builder().maxMatchedFiles(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> CollectionConstraints.builder().callbackTimeout(Duration.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CollectionConstraints.builder()
                                .maxBytesPerFile(2_048)
                                .maxTotalBytes(1_024)
                                .build());
    }

    @Test
    void localizationKeysAreCanonicalAndBounded() {
        assertEquals(
                "bugreport.provider.example_mod.title",
                LocalizationKey.of("bugreport.provider.example_mod.title").value());
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizationKey.of("BugReport.provider.title"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LocalizationKey.of("bugreport..title"));
    }
}
