package com.cybersammy.bugreport.api.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.identifier.ValidationCode;
import org.junit.jupiter.api.Test;

final class ValidationResultTest {
    private static final ValidationCode DUPLICATE =
            ValidationCode.of("bugreport:duplicate_identifier");
    private static final ValidationCode UNSUPPORTED =
            ValidationCode.of("bugreport:unsupported_capability");

    @Test
    void rendersExactNestedPaths() {
        ValidationPath path =
                ValidationPath.root()
                        .property("categories")
                        .index(2)
                        .property("fields")
                        .index(1)
                        .property("id");

        assertEquals("$.categories[2].fields[1].id", path.toString());
    }

    @Test
    void ordersIssuesDeterministicallyAndProtectsResultState() {
        ValidationResult result =
                ValidationResult.builder()
                        .warning(
                                UNSUPPORTED,
                                ValidationPath.root().property("zeta"),
                                "Optional capability is unavailable")
                        .error(
                                DUPLICATE,
                                ValidationPath.root().property("alpha"),
                                "Duplicate field identifier")
                        .build();

        assertFalse(result.isValid());
        assertTrue(result.hasWarnings());
        assertEquals("$.alpha", result.issues().get(0).path().toString());
        assertEquals("$.zeta", result.issues().get(1).path().toString());
        assertThrows(UnsupportedOperationException.class, () -> result.issues().clear());
    }

    @Test
    void warningsDoNotInvalidateAnOtherwiseUsableContract() {
        ValidationResult result =
                ValidationResult.builder()
                        .warning(
                                UNSUPPORTED,
                                ValidationPath.root(),
                                "Optional capability is unavailable")
                        .build();

        assertTrue(result.isValid());
        assertTrue(result.hasWarnings());
        assertTrue(ValidationResult.valid().isValid());
    }

    @Test
    void rejectsUnsafeMessagesAndUnboundedPaths() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ValidationIssue(
                                ValidationSeverity.ERROR,
                                DUPLICATE,
                                ValidationPath.root(),
                                "line one\nline two"));

        ValidationPath path = ValidationPath.root();
        for (int index = 0; index < 64; index++) {
            path = path.property("child");
        }
        ValidationPath maximumPath = path;
        assertThrows(
                IllegalArgumentException.class,
                () -> maximumPath.property("overflow"));
    }
}
