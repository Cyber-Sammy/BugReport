package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import java.util.List;

/**
 * Reusable Bug Report-owned field declarations for common issue details.
 *
 * <p>The returned specifications are immutable and use localization keys supplied by the Bug
 * Report mod. Providers may include any subset in their category declarations and remain free to
 * declare provider-specific fields alongside them.
 */
public final class StandardFields {
    private static final String LOCALIZATION_PREFIX = "bugreport.field.";

    private static final FieldSpecification SUMMARY = text(
            "summary",
            FieldKind.SINGLE_LINE_TEXT,
            true,
            FieldConstraints.builder()
                    .minimumLength(1)
                    .maximumLength(256)
                    .build());
    private static final FieldSpecification DESCRIPTION = text(
            "description",
            FieldKind.MULTILINE_TEXT,
            true,
            FieldConstraints.builder()
                    .minimumLength(1)
                    .maximumLength(8_000)
                    .build());
    private static final FieldSpecification REPRODUCTION_STEPS = text(
            "reproduction_steps",
            FieldKind.REPRODUCTION_STEPS,
            false,
            FieldConstraints.builder()
                    .maximumLength(2_000)
                    .maximumItems(32)
                    .build());
    private static final FieldSpecification EXPECTED_BEHAVIOR = text(
            "expected_behavior",
            FieldKind.EXPECTED_BEHAVIOR,
            false,
            FieldConstraints.builder().maximumLength(4_000).build());
    private static final FieldSpecification ACTUAL_BEHAVIOR = text(
            "actual_behavior",
            FieldKind.ACTUAL_BEHAVIOR,
            false,
            FieldConstraints.builder().maximumLength(4_000).build());
    private static final FieldSpecification SEVERITY = productChoice("severity", FieldKind.SEVERITY);
    private static final FieldSpecification SIDE_CONTEXT =
            productChoice("side_context", FieldKind.SIDE_CONTEXT);

    private static final List<FieldSpecification> ALL = List.of(
            ACTUAL_BEHAVIOR,
            DESCRIPTION,
            EXPECTED_BEHAVIOR,
            REPRODUCTION_STEPS,
            SEVERITY,
            SIDE_CONTEXT,
            SUMMARY);

    private StandardFields() {}

    /**
     * Returns the required, bounded one-line issue summary.
     *
     * @return standard summary specification
     */
    public static FieldSpecification summary() {
        return SUMMARY;
    }

    /**
     * Returns the required, bounded detailed issue description.
     *
     * @return standard description specification
     */
    public static FieldSpecification description() {
        return DESCRIPTION;
    }

    /**
     * Returns optional bounded ordered reproduction steps.
     *
     * @return standard reproduction-steps specification
     */
    public static FieldSpecification reproductionSteps() {
        return REPRODUCTION_STEPS;
    }

    /**
     * Returns optional bounded expected-behavior text.
     *
     * @return standard expected-behavior specification
     */
    public static FieldSpecification expectedBehavior() {
        return EXPECTED_BEHAVIOR;
    }

    /**
     * Returns optional bounded actual-behavior text.
     *
     * @return standard actual-behavior specification
     */
    public static FieldSpecification actualBehavior() {
        return ACTUAL_BEHAVIOR;
    }

    /**
     * Returns the required product-defined severity selector.
     *
     * @return standard severity specification
     */
    public static FieldSpecification severity() {
        return SEVERITY;
    }

    /**
     * Returns the required product-defined physical-side and gameplay-context selector.
     *
     * @return standard side/context specification
     */
    public static FieldSpecification sideContext() {
        return SIDE_CONTEXT;
    }

    /**
     * Returns every standard field in canonical field-ID order.
     *
     * @return immutable standard field list
     */
    public static List<FieldSpecification> all() {
        return ALL;
    }

    private static FieldSpecification text(
            String id,
            FieldKind kind,
            boolean required,
            FieldConstraints constraints) {
        return base(id, kind, PrivacyClassification.PERSONAL)
                .required(required)
                .constraints(constraints)
                .build();
    }

    private static FieldSpecification productChoice(String id, FieldKind kind) {
        return base(id, kind, PrivacyClassification.LOW).required(true).build();
    }

    private static FieldSpecification.Builder base(
            String id, FieldKind kind, PrivacyClassification privacy) {
        return FieldSpecification.builder(
                        FieldId.of(id),
                        kind,
                        LocalizationKey.of(LOCALIZATION_PREFIX + id + ".label"),
                        privacy)
                .descriptionKey(LocalizationKey.of(LOCALIZATION_PREFIX + id + ".description"));
    }
}
