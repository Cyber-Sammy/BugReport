package com.cybersammy.bugreport.api.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.identifier.FieldId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StandardFieldsTest {
    @Test
    void exposesImmutableFieldsInCanonicalIdOrder() {
        List<FieldSpecification> fields = StandardFields.all();

        assertEquals(
                List.of(
                        "actual_behavior",
                        "description",
                        "expected_behavior",
                        "reproduction_steps",
                        "severity",
                        "side_context",
                        "summary"),
                fields.stream().map(field -> field.id().value()).toList());
        assertThrows(UnsupportedOperationException.class, () -> fields.clear());
        assertSame(StandardFields.summary(), StandardFields.summary());
    }

    @Test
    void declaresStableIdentityKindsAndLocalizationKeys() {
        assertField(StandardFields.summary(), "summary", FieldKind.SINGLE_LINE_TEXT);
        assertField(StandardFields.description(), "description", FieldKind.MULTILINE_TEXT);
        assertField(
                StandardFields.reproductionSteps(),
                "reproduction_steps",
                FieldKind.REPRODUCTION_STEPS);
        assertField(
                StandardFields.expectedBehavior(),
                "expected_behavior",
                FieldKind.EXPECTED_BEHAVIOR);
        assertField(
                StandardFields.actualBehavior(),
                "actual_behavior",
                FieldKind.ACTUAL_BEHAVIOR);
        assertField(StandardFields.severity(), "severity", FieldKind.SEVERITY);
        assertField(StandardFields.sideContext(), "side_context", FieldKind.SIDE_CONTEXT);
    }

    @Test
    void appliesBoundedInputAndPrivacyPolicy() {
        assertEquals(1, StandardFields.summary().constraints().minimumLength().orElseThrow());
        assertEquals(256, StandardFields.summary().constraints().maximumLength().orElseThrow());
        assertEquals(
                8_000,
                StandardFields.description().constraints().maximumLength().orElseThrow());
        assertEquals(
                32,
                StandardFields.reproductionSteps()
                        .constraints()
                        .maximumItems()
                        .orElseThrow());
        assertEquals(
                2_000,
                StandardFields.reproductionSteps()
                        .constraints()
                        .maximumLength()
                        .orElseThrow());
        assertEquals(
                4_000,
                StandardFields.expectedBehavior()
                        .constraints()
                        .maximumLength()
                        .orElseThrow());
        assertEquals(
                4_000,
                StandardFields.actualBehavior()
                        .constraints()
                        .maximumLength()
                        .orElseThrow());

        for (FieldSpecification field : StandardFields.all()) {
            PrivacyClassification expected = switch (field.kind()) {
                case SEVERITY, SIDE_CONTEXT -> PrivacyClassification.LOW;
                default -> PrivacyClassification.PERSONAL;
            };
            assertEquals(expected, field.privacy());
        }
    }

    @Test
    void requiresOnlyTheMinimumReportIdentityAndProductChoices() {
        assertEquals(
                List.of("description", "severity", "side_context", "summary"),
                StandardFields.all().stream()
                        .filter(FieldSpecification::required)
                        .map(field -> field.id().value())
                        .toList());
    }

    private static void assertField(
            FieldSpecification field, String expectedId, FieldKind expectedKind) {
        assertEquals(FieldId.of(expectedId), field.id());
        assertEquals(expectedKind, field.kind());
        assertEquals("bugreport.field." + expectedId + ".label", field.labelKey().value());
        assertEquals(
                "bugreport.field." + expectedId + ".description",
                field.descriptionKey().orElseThrow().value());
    }
}
