package com.cybersammy.bugreport.api.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.identifier.IdentifierCollisionException;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FieldSpecificationTest {
    @Test
    void buildsSelectionWithCanonicalImmutableOptions() {
        FieldSpecification field = FieldSpecification.builder(
                        FieldId.of("severity"),
                        FieldKind.SINGLE_SELECT,
                        key("severity"),
                        PrivacyClassification.LOW)
                .addOption(option("high"))
                .addOption(option("low"))
                .build();

        assertEquals(
                List.of(FieldOptionId.of("high"), FieldOptionId.of("low")),
                List.copyOf(field.options().keySet()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> field.options().clear());
    }

    @Test
    void rejectsDuplicateOptionsByTypedIdentity() {
        FieldSpecification.Builder builder = FieldSpecification.builder(
                        FieldId.of("severity"),
                        FieldKind.SINGLE_SELECT,
                        key("severity"),
                        PrivacyClassification.LOW)
                .addOption(option("low"));

        assertThrows(IdentifierCollisionException.class, () -> builder.addOption(option("low")));
    }

    @Test
    void rejectsFreeFormDataBelowPersonalPrivacy() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FieldSpecification.builder(
                                FieldId.of("summary"),
                                FieldKind.SINGLE_LINE_TEXT,
                                key("summary"),
                                PrivacyClassification.LOW)
                        .build());
    }

    @Test
    void rejectsConstraintsThatDoNotApplyToFieldKind() {
        FieldConstraints lengths = FieldConstraints.builder().maximumLength(10).build();

        assertThrows(
                IllegalArgumentException.class,
                () -> FieldSpecification.builder(
                                FieldId.of("confirmed"),
                                FieldKind.CHECKBOX,
                                key("confirmed"),
                                PrivacyClassification.LOW)
                        .constraints(lengths)
                        .build());
    }

    @Test
    void rejectsFractionalIntegerBounds() {
        FieldConstraints numbers = FieldConstraints.builder()
                .minimumNumber(new BigDecimal("1.5"))
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> FieldSpecification.builder(
                                FieldId.of("count"),
                                FieldKind.INTEGER,
                                key("count"),
                                PrivacyClassification.LOW)
                        .constraints(numbers)
                        .build());
    }

    private static FieldOption option(String id) {
        return new FieldOption(FieldOptionId.of(id), key("option." + id));
    }

    private static LocalizationKey key(String suffix) {
        return LocalizationKey.of("example." + suffix);
    }
}
