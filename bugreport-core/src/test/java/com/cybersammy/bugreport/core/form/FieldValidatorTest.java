package com.cybersammy.bugreport.core.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.FieldConstraints;
import com.cybersammy.bugreport.api.specification.FieldKind;
import com.cybersammy.bugreport.api.specification.FieldOption;
import com.cybersammy.bugreport.api.specification.FieldSpecification;
import com.cybersammy.bugreport.api.specification.StandardFields;
import com.cybersammy.bugreport.api.validation.ValidationIssue;
import com.cybersammy.bugreport.api.validation.ValidationResult;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FieldValidatorTest {
    @Test
    void acceptsEverySupportedFieldRepresentation() {
        CategorySpecification category = category(
                StandardFields.summary(),
                field("multiline", FieldKind.MULTILINE_TEXT, false, FieldConstraints.none()),
                field("steps", FieldKind.REPRODUCTION_STEPS, false, FieldConstraints.none()),
                field("confirmed", FieldKind.CHECKBOX, true, FieldConstraints.none()),
                selection("choice", FieldKind.SINGLE_SELECT, FieldConstraints.none()),
                selection("choices", FieldKind.MULTI_SELECT, FieldConstraints.none()),
                field("count", FieldKind.INTEGER, false, FieldConstraints.none()),
                field("ratio", FieldKind.DECIMAL, false, FieldConstraints.none()),
                field("expected", FieldKind.EXPECTED_BEHAVIOR, false, FieldConstraints.none()),
                field("actual", FieldKind.ACTUAL_BEHAVIOR, false, FieldConstraints.none()),
                field("severity", FieldKind.SEVERITY, true, FieldConstraints.none()),
                field("context", FieldKind.SIDE_CONTEXT, true, FieldConstraints.none()),
                field("information", FieldKind.READ_ONLY_INFORMATION, false, FieldConstraints.none()));
        FormSubmission submission = FormSubmission.builder()
                .put(FieldId.of("summary"), new FieldValue.Text("A concise summary"))
                .put(FieldId.of("multiline"), new FieldValue.Text("line one\nline two"))
                .put(FieldId.of("steps"), new FieldValue.TextList(List.of("first", "second")))
                .put(FieldId.of("confirmed"), new FieldValue.Checkbox(false))
                .put(FieldId.of("choice"), new FieldValue.Selection(FieldOptionId.of("first")))
                .put(
                        FieldId.of("choices"),
                        new FieldValue.MultiSelection(
                                Set.of(FieldOptionId.of("first"), FieldOptionId.of("second"))))
                .put(FieldId.of("count"), new FieldValue.IntegerNumber(BigInteger.TEN))
                .put(FieldId.of("ratio"), new FieldValue.DecimalNumber(new BigDecimal("1.25")))
                .put(FieldId.of("expected"), new FieldValue.Text("expected"))
                .put(FieldId.of("actual"), new FieldValue.Text("actual"))
                .put(
                        FieldId.of("severity"),
                        new FieldValue.Severity(ReportSeverity.MODERATE))
                .put(
                        FieldId.of("context"),
                        new FieldValue.SideContext(ReportSideContext.SINGLEPLAYER))
                .build();

        assertTrue(FieldValidator.validate(category, submission).isValid());
    }

    @Test
    void requiredStateIsScopedToFieldsIncludedByTheCategory() {
        CategorySpecification category = category(StandardFields.summary());
        FormSubmission submission = FormSubmission.builder()
                .put(StandardFields.summary().id(), new FieldValue.Text("Only summary is declared"))
                .build();

        assertTrue(FieldValidator.validate(category, submission).isValid());
    }

    @Test
    void reportsRequiredUndeclaredAndReadOnlyValuesAtExactPaths() {
        CategorySpecification category = category(
                StandardFields.summary(),
                field("information", FieldKind.READ_ONLY_INFORMATION, false, FieldConstraints.none()));
        FormSubmission submission = FormSubmission.builder()
                .put(FieldId.of("information"), new FieldValue.Text(""))
                .put(FieldId.of("unexpected"), new FieldValue.Text("not declared"))
                .build();

        ValidationResult result = FieldValidator.validate(category, submission);

        assertIssues(
                result,
                issue("$.fields.information", FieldValidationCode.READ_ONLY),
                issue("$.fields.summary", FieldValidationCode.REQUIRED),
                issue("$.fields.unexpected", FieldValidationCode.UNDECLARED_FIELD));
    }

    @Test
    void reportsTypeMismatchWithoutApplyingInapplicableConstraints() {
        FieldConstraints lengths = FieldConstraints.builder()
                .minimumLength(10)
                .maximumLength(20)
                .build();
        CategorySpecification category =
                category(field("summary", FieldKind.SINGLE_LINE_TEXT, true, lengths));
        FormSubmission submission = FormSubmission.builder()
                .put(FieldId.of("summary"), new FieldValue.Checkbox(true))
                .build();

        assertIssues(
                FieldValidator.validate(category, submission),
                issue("$.fields.summary", FieldValidationCode.TYPE_MISMATCH));
    }

    @Test
    void requiredOrderedTextNeedsAtLeastOneMeaningfulItem() {
        CategorySpecification category = category(
                field("steps", FieldKind.REPRODUCTION_STEPS, true, FieldConstraints.none()));
        FormSubmission submission = FormSubmission.builder()
                .put(FieldId.of("steps"), new FieldValue.TextList(List.of("", "  ")))
                .build();

        assertIssues(
                FieldValidator.validate(category, submission),
                issue("$.fields.steps", FieldValidationCode.REQUIRED));
    }

    @Test
    void validatesUnicodeLengthsLineBreaksAndOrderedItems() {
        CategorySpecification category = category(
                field(
                        "title",
                        FieldKind.SINGLE_LINE_TEXT,
                        false,
                        FieldConstraints.builder().minimumLength(2).maximumLength(3).build()),
                field(
                        "steps",
                        FieldKind.REPRODUCTION_STEPS,
                        false,
                        FieldConstraints.builder()
                                .minimumLength(2)
                                .maximumLength(4)
                                .minimumItems(2)
                                .maximumItems(4)
                                .build()));
        FormSubmission submission = FormSubmission.builder()
                .put(FieldId.of("title"), new FieldValue.Text("😀\n😀😀"))
                .put(
                        FieldId.of("steps"),
                        new FieldValue.TextList(List.of("", "x", "12345")))
                .build();

        assertIssues(
                FieldValidator.validate(category, submission),
                issue("$.fields.steps[0]", FieldValidationCode.EMPTY_ITEM),
                issue("$.fields.steps[1]", FieldValidationCode.TOO_SHORT),
                issue("$.fields.steps[2]", FieldValidationCode.TOO_LONG),
                issue("$.fields.title", FieldValidationCode.TOO_LONG),
                issue("$.fields.title", FieldValidationCode.LINE_BREAK));
    }

    @Test
    void validatesSelectionMembershipAndItemBounds() {
        CategorySpecification category = category(
                selection("single", FieldKind.SINGLE_SELECT, FieldConstraints.none()),
                selection(
                        "multiple",
                        FieldKind.MULTI_SELECT,
                        FieldConstraints.builder().minimumItems(2).maximumItems(2).build()));
        FormSubmission submission = FormSubmission.builder()
                .put(
                        FieldId.of("single"),
                        new FieldValue.Selection(FieldOptionId.of("unknown")))
                .put(
                        FieldId.of("multiple"),
                        new FieldValue.MultiSelection(Set.of(FieldOptionId.of("unknown"))))
                .build();

        assertIssues(
                FieldValidator.validate(category, submission),
                issue("$.fields.multiple", FieldValidationCode.TOO_FEW_ITEMS),
                issue("$.fields.multiple", FieldValidationCode.UNKNOWN_OPTION),
                issue("$.fields.single", FieldValidationCode.UNKNOWN_OPTION));
    }

    @Test
    void validatesInclusiveNumericBounds() {
        FieldConstraints numbers = FieldConstraints.builder()
                .minimumNumber(new BigDecimal("1.5"))
                .maximumNumber(new BigDecimal("10.5"))
                .build();
        FieldConstraints integers = FieldConstraints.builder()
                .minimumNumber(BigDecimal.ONE)
                .maximumNumber(BigDecimal.TEN)
                .build();
        CategorySpecification category = category(
                field("low", FieldKind.DECIMAL, false, numbers),
                field("high", FieldKind.DECIMAL, false, numbers),
                field("count", FieldKind.INTEGER, false, integers));
        FormSubmission invalid = FormSubmission.builder()
                .put(FieldId.of("low"), new FieldValue.DecimalNumber(new BigDecimal("1.49")))
                .put(FieldId.of("high"), new FieldValue.DecimalNumber(new BigDecimal("10.51")))
                .put(FieldId.of("count"), new FieldValue.IntegerNumber(BigInteger.ZERO))
                .build();

        assertIssues(
                FieldValidator.validate(category, invalid),
                issue("$.fields.count", FieldValidationCode.NUMBER_TOO_LOW),
                issue("$.fields.high", FieldValidationCode.NUMBER_TOO_HIGH),
                issue("$.fields.low", FieldValidationCode.NUMBER_TOO_LOW));

        FormSubmission boundaries = FormSubmission.builder()
                .put(FieldId.of("low"), new FieldValue.DecimalNumber(new BigDecimal("1.5")))
                .put(FieldId.of("high"), new FieldValue.DecimalNumber(new BigDecimal("10.5")))
                .put(FieldId.of("count"), new FieldValue.IntegerNumber(BigInteger.TEN))
                .build();
        assertTrue(FieldValidator.validate(category, boundaries).isValid());
    }

    @Test
    void issueOrderDoesNotDependOnDeclarationOrSubmissionOrder() {
        List<FieldSpecification> declarations = new ArrayList<>(List.of(
                StandardFields.summary(),
                field("title", FieldKind.SINGLE_LINE_TEXT, true, FieldConstraints.none())));
        CategorySpecification firstCategory = category(declarations.toArray(FieldSpecification[]::new));
        Collections.reverse(declarations);
        CategorySpecification secondCategory = category(declarations.toArray(FieldSpecification[]::new));

        FormSubmission firstSubmission = FormSubmission.builder()
                .put(FieldId.of("z_extra"), new FieldValue.Text("z"))
                .put(FieldId.of("a_extra"), new FieldValue.Text("a"))
                .build();
        FormSubmission secondSubmission = FormSubmission.builder()
                .put(FieldId.of("a_extra"), new FieldValue.Text("a"))
                .put(FieldId.of("z_extra"), new FieldValue.Text("z"))
                .build();

        assertEquals(
                FieldValidator.validate(firstCategory, firstSubmission),
                FieldValidator.validate(secondCategory, secondSubmission));
    }

    private static FieldSpecification field(
            String id,
            FieldKind kind,
            boolean required,
            FieldConstraints constraints) {
        PrivacyClassification privacy = switch (kind) {
            case SINGLE_LINE_TEXT,
                    MULTILINE_TEXT,
                    REPRODUCTION_STEPS,
                    EXPECTED_BEHAVIOR,
                    ACTUAL_BEHAVIOR -> PrivacyClassification.PERSONAL;
            default -> PrivacyClassification.LOW;
        };
        return FieldSpecification.builder(FieldId.of(id), kind, key("field." + id), privacy)
                .required(required)
                .constraints(constraints)
                .build();
    }

    private static FieldSpecification selection(
            String id, FieldKind kind, FieldConstraints constraints) {
        return FieldSpecification.builder(
                        FieldId.of(id), kind, key("field." + id), PrivacyClassification.LOW)
                .constraints(constraints)
                .addOption(option("first"))
                .addOption(option("second"))
                .build();
    }

    private static FieldOption option(String id) {
        return new FieldOption(FieldOptionId.of(id), key("option." + id));
    }

    private static CategorySpecification category(FieldSpecification... fields) {
        CategorySpecification.Builder builder = CategorySpecification.builder(
                CategoryId.of("general"), key("category.general"));
        for (FieldSpecification field : fields) {
            builder.addField(field);
        }
        return builder.build();
    }

    private static LocalizationKey key(String suffix) {
        return LocalizationKey.of("test." + suffix);
    }

    private static ExpectedIssue issue(String path, FieldValidationCode code) {
        return new ExpectedIssue(path, code);
    }

    private static void assertIssues(ValidationResult result, ExpectedIssue... expected) {
        assertEquals(
                List.of(expected),
                result.issues().stream().map(ExpectedIssue::from).toList());
    }

    private record ExpectedIssue(String path, FieldValidationCode code) {
        private static ExpectedIssue from(ValidationIssue issue) {
            for (FieldValidationCode code : FieldValidationCode.values()) {
                if (code.code().equals(issue.code())) {
                    return new ExpectedIssue(issue.path().toString(), code);
                }
            }
            throw new AssertionError("Unexpected validation code: " + issue.code());
        }
    }
}
