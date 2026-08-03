package com.cybersammy.bugreport.core.form;

import com.cybersammy.bugreport.api.localization.LocalizationKey;

/** Closed product-defined severity choices in increasing impact order. */
public enum ReportSeverity {
    /** Minor issue with a practical workaround. */
    LOW("low"),
    /** Material issue that does not block normal use. */
    MODERATE("moderate"),
    /** Major issue that prevents an important workflow. */
    HIGH("high"),
    /** Issue that blocks startup, loading, or continued safe use. */
    BLOCKING("blocking");

    private final String value;
    private final LocalizationKey labelKey;

    ReportSeverity(String value) {
        this.value = value;
        labelKey = LocalizationKey.of("bugreport.field.severity.option." + value);
    }

    /**
     * Returns the stable persisted value.
     *
     * @return canonical value
     */
    public String value() {
        return value;
    }

    /**
     * Returns the Bug Report-owned localized label key.
     *
     * @return label localization key
     */
    public LocalizationKey labelKey() {
        return labelKey;
    }
}
