package com.cybersammy.bugreport.api.specification;

/** Importance of one item to the usefulness of a completed report. */
public enum ReportQualityRole {
    /** Omitting the item makes the report incomplete but never forces inclusion. */
    REQUIRED,
    /** The item materially improves diagnosis. */
    RECOMMENDED,
    /** The item is useful only in some report contexts. */
    OPTIONAL
}
