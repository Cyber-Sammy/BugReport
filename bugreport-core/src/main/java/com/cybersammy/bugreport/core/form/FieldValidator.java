package com.cybersammy.bugreport.core.form;

import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.FieldConstraints;
import com.cybersammy.bugreport.api.specification.FieldKind;
import com.cybersammy.bugreport.api.specification.FieldSpecification;
import com.cybersammy.bugreport.api.validation.ValidationPath;
import com.cybersammy.bugreport.api.validation.ValidationResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/** Deterministically validates submitted values against one selected category. */
public final class FieldValidator {
    private static final ValidationPath FIELDS_PATH =
            ValidationPath.root().property("fields");

    private FieldValidator() {}

    /**
     * Validates only fields declared by the selected category.
     *
     * @param category selected immutable provider category
     * @param submission immutable submitted values
     * @return deterministic validation result
     */
    public static ValidationResult validate(
            CategorySpecification category, FormSubmission submission) {
        CategorySpecification selectedCategory = Objects.requireNonNull(category, "category");
        FormSubmission submittedValues = Objects.requireNonNull(submission, "submission");
        ValidationResult.Builder result = ValidationResult.builder();

        for (Map.Entry<FieldId, FieldSpecification> entry
                : selectedCategory.fields().entrySet()) {
            FieldId id = entry.getKey();
            FieldSpecification field = entry.getValue();
            FieldValue value = submittedValues.values().get(id);
            validateDeclaredField(field, value, fieldPath(id), result);
        }
        for (FieldId submittedId : submittedValues.values().keySet()) {
            if (!selectedCategory.fields().containsKey(submittedId)) {
                add(result, FieldValidationCode.UNDECLARED_FIELD, fieldPath(submittedId));
            }
        }
        return result.build();
    }

    private static void validateDeclaredField(
            FieldSpecification field,
            FieldValue value,
            ValidationPath path,
            ValidationResult.Builder result) {
        if (value != null && field.kind() == FieldKind.READ_ONLY_INFORMATION) {
            add(result, FieldValidationCode.READ_ONLY, path);
            return;
        }
        if (value == null || isEmpty(value)) {
            if (field.required()) {
                add(result, FieldValidationCode.REQUIRED, path);
            }
            return;
        }
        if (!matches(field.kind(), value)) {
            add(result, FieldValidationCode.TYPE_MISMATCH, path);
            return;
        }

        switch (field.kind()) {
            case SINGLE_LINE_TEXT -> validateSingleLine(
                    (FieldValue.Text) value, field.constraints(), path, result);
            case MULTILINE_TEXT, EXPECTED_BEHAVIOR, ACTUAL_BEHAVIOR -> validateText(
                    (FieldValue.Text) value, field.constraints(), path, result);
            case REPRODUCTION_STEPS -> validateTextList(
                    (FieldValue.TextList) value, field.constraints(), path, result);
            case SINGLE_SELECT -> validateSelection(
                    (FieldValue.Selection) value, field, path, result);
            case MULTI_SELECT -> validateMultiSelection(
                    (FieldValue.MultiSelection) value, field, path, result);
            case INTEGER -> validateNumber(
                    new BigDecimal(((FieldValue.IntegerNumber) value).value()),
                    field.constraints(),
                    path,
                    result);
            case DECIMAL -> validateNumber(
                    ((FieldValue.DecimalNumber) value).value(),
                    field.constraints(),
                    path,
                    result);
            case CHECKBOX, SEVERITY, SIDE_CONTEXT, READ_ONLY_INFORMATION -> {
                // Type and read-only policy fully validate these closed values.
            }
        }
    }

    private static boolean isEmpty(FieldValue value) {
        return switch (value) {
            case FieldValue.Text text -> text.value().isBlank();
            case FieldValue.TextList list ->
                    list.values().isEmpty() || list.values().stream().allMatch(String::isBlank);
            case FieldValue.MultiSelection selection -> selection.optionIds().isEmpty();
            default -> false;
        };
    }

    private static boolean matches(FieldKind kind, FieldValue value) {
        return switch (kind) {
            case SINGLE_LINE_TEXT,
                    MULTILINE_TEXT,
                    EXPECTED_BEHAVIOR,
                    ACTUAL_BEHAVIOR -> value instanceof FieldValue.Text;
            case REPRODUCTION_STEPS -> value instanceof FieldValue.TextList;
            case CHECKBOX -> value instanceof FieldValue.Checkbox;
            case SINGLE_SELECT -> value instanceof FieldValue.Selection;
            case MULTI_SELECT -> value instanceof FieldValue.MultiSelection;
            case INTEGER -> value instanceof FieldValue.IntegerNumber;
            case DECIMAL -> value instanceof FieldValue.DecimalNumber;
            case SEVERITY -> value instanceof FieldValue.Severity;
            case SIDE_CONTEXT -> value instanceof FieldValue.SideContext;
            case READ_ONLY_INFORMATION -> false;
        };
    }

    private static void validateSingleLine(
            FieldValue.Text value,
            FieldConstraints constraints,
            ValidationPath path,
            ValidationResult.Builder result) {
        if (value.value().indexOf('\n') >= 0 || value.value().indexOf('\r') >= 0) {
            add(result, FieldValidationCode.LINE_BREAK, path);
        }
        validateText(value, constraints, path, result);
    }

    private static void validateText(
            FieldValue.Text value,
            FieldConstraints constraints,
            ValidationPath path,
            ValidationResult.Builder result) {
        validateLength(codePointLength(value.value()), constraints, path, result);
    }

    private static void validateTextList(
            FieldValue.TextList value,
            FieldConstraints constraints,
            ValidationPath path,
            ValidationResult.Builder result) {
        validateItemCount(value.values().size(), constraints, path, result);

        boolean reportedEmpty = false;
        boolean reportedShort = false;
        boolean reportedLong = false;
        for (int index = 0; index < value.values().size(); index++) {
            String item = value.values().get(index);
            ValidationPath itemPath = path.index(index);
            if (!reportedEmpty && item.isBlank()) {
                add(result, FieldValidationCode.EMPTY_ITEM, itemPath);
                reportedEmpty = true;
                continue;
            }
            int length = codePointLength(item);
            if (!reportedShort && below(length, constraints.minimumLength())) {
                add(result, FieldValidationCode.TOO_SHORT, itemPath);
                reportedShort = true;
            }
            if (!reportedLong && above(length, constraints.maximumLength())) {
                add(result, FieldValidationCode.TOO_LONG, itemPath);
                reportedLong = true;
            }
        }
    }

    private static void validateSelection(
            FieldValue.Selection value,
            FieldSpecification field,
            ValidationPath path,
            ValidationResult.Builder result) {
        if (!field.options().containsKey(value.optionId())) {
            add(result, FieldValidationCode.UNKNOWN_OPTION, path);
        }
    }

    private static void validateMultiSelection(
            FieldValue.MultiSelection value,
            FieldSpecification field,
            ValidationPath path,
            ValidationResult.Builder result) {
        validateItemCount(value.optionIds().size(), field.constraints(), path, result);
        Set<FieldOptionId> declared = field.options().keySet();
        if (!declared.containsAll(value.optionIds())) {
            add(result, FieldValidationCode.UNKNOWN_OPTION, path);
        }
    }

    private static void validateLength(
            int length,
            FieldConstraints constraints,
            ValidationPath path,
            ValidationResult.Builder result) {
        if (below(length, constraints.minimumLength())) {
            add(result, FieldValidationCode.TOO_SHORT, path);
        }
        if (above(length, constraints.maximumLength())) {
            add(result, FieldValidationCode.TOO_LONG, path);
        }
    }

    private static void validateItemCount(
            int size,
            FieldConstraints constraints,
            ValidationPath path,
            ValidationResult.Builder result) {
        if (below(size, constraints.minimumItems())) {
            add(result, FieldValidationCode.TOO_FEW_ITEMS, path);
        }
        if (above(size, constraints.maximumItems())) {
            add(result, FieldValidationCode.TOO_MANY_ITEMS, path);
        }
    }

    private static void validateNumber(
            BigDecimal number,
            FieldConstraints constraints,
            ValidationPath path,
            ValidationResult.Builder result) {
        if (constraints.minimumNumber().map(minimum -> number.compareTo(minimum) < 0).orElse(false)) {
            add(result, FieldValidationCode.NUMBER_TOO_LOW, path);
        }
        if (constraints.maximumNumber().map(maximum -> number.compareTo(maximum) > 0).orElse(false)) {
            add(result, FieldValidationCode.NUMBER_TOO_HIGH, path);
        }
    }

    private static boolean below(int value, OptionalInt minimum) {
        return minimum.isPresent() && value < minimum.getAsInt();
    }

    private static boolean above(int value, OptionalInt maximum) {
        return maximum.isPresent() && value > maximum.getAsInt();
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static ValidationPath fieldPath(FieldId id) {
        return FIELDS_PATH.property(id.value());
    }

    private static void add(
            ValidationResult.Builder result,
            FieldValidationCode issue,
            ValidationPath path) {
        result.error(issue.code(), path, issue.message());
    }
}
