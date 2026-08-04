package com.cybersammy.bugreport.core.form;

import com.cybersammy.bugreport.api.identifier.ValidationCode;

/** Stable machine-readable codes emitted by deterministic form validation. */
public enum FieldValidationCode {
    /** A required active field has no meaningful value. */
    REQUIRED("required", "A value is required."),
    /** A submission contains a field absent from the selected category. */
    UNDECLARED_FIELD("undeclared", "The field is not declared by the selected category."),
    /** A submitted value representation does not match the declared field kind. */
    TYPE_MISMATCH("type", "The value representation does not match the field kind."),
    /** A single-line value contains a line break. */
    LINE_BREAK("line_break", "A single-line value cannot contain line breaks."),
    /** A text value is shorter than its inclusive minimum. */
    TOO_SHORT("length_min", "The value is shorter than the declared minimum."),
    /** A text value is longer than its inclusive maximum. */
    TOO_LONG("length_max", "The value is longer than the declared maximum."),
    /** A list or selection contains too few items. */
    TOO_FEW_ITEMS("items_min", "The value contains fewer than the declared minimum items."),
    /** A list or selection contains too many items. */
    TOO_MANY_ITEMS("items_max", "The value contains more than the declared maximum items."),
    /** An ordered text list contains a blank item. */
    EMPTY_ITEM("item_empty", "An ordered text item cannot be blank."),
    /** A number is lower than its inclusive minimum. */
    NUMBER_TOO_LOW("number_min", "The value is lower than the declared minimum."),
    /** A number is higher than its inclusive maximum. */
    NUMBER_TOO_HIGH("number_max", "The value is higher than the declared maximum."),
    /** A selected option is not declared by the field. */
    UNKNOWN_OPTION("option", "The selected option is not declared by the field."),
    /** A caller attempted to submit a value for display-only information. */
    READ_ONLY("read_only", "Read-only information cannot accept a submitted value.");

    private final ValidationCode code;
    private final String message;

    FieldValidationCode(String localCode, String message) {
        code = ValidationCode.of("bugreport:field_" + localCode);
        this.message = message;
    }

    /**
     * Returns the stable namespaced validation code.
     *
     * @return validation code
     */
    public ValidationCode code() {
        return code;
    }

    /**
     * Returns the stable developer-facing explanation.
     *
     * @return issue message
     */
    public String message() {
        return message;
    }
}
