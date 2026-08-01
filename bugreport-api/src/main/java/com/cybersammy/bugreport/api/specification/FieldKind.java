package com.cybersammy.bugreport.api.specification;

/** Closed initial set of declarative form-field behaviors. */
public enum FieldKind {
    /** One line of free-form text. */
    SINGLE_LINE_TEXT,
    /** Multiple lines of free-form text. */
    MULTILINE_TEXT,
    /** Ordered list of reproduction steps. */
    REPRODUCTION_STEPS,
    /** Boolean checkbox. */
    CHECKBOX,
    /** Exactly one provider-declared option. */
    SINGLE_SELECT,
    /** Zero or more provider-declared options. */
    MULTI_SELECT,
    /** Signed whole number. */
    INTEGER,
    /** Arbitrary-precision decimal number. */
    DECIMAL,
    /** Expected behavior text. */
    EXPECTED_BEHAVIOR,
    /** Actual behavior text. */
    ACTUAL_BEHAVIOR,
    /** Product-defined severity choice. */
    SEVERITY,
    /** Product-defined physical-side or gameplay-context choice. */
    SIDE_CONTEXT,
    /** Localized, non-editable information. */
    READ_ONLY_INFORMATION
}
