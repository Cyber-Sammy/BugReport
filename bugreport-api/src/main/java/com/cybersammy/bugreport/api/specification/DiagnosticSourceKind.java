package com.cybersammy.bugreport.api.specification;

/** Closed initial set of declarative diagnostic source selectors. */
public enum DiagnosticSourceKind {
    /** One exact file below an approved logical root. */
    EXACT_FILE,
    /** Newest file matching one non-recursive filename pattern. */
    LATEST_FILE,
    /** Bounded non-recursive matches below one relative directory. */
    FILTERED_DIRECTORY,
    /** Product-owned latest client log selector. */
    LATEST_LOG,
    /** Product-owned latest crash-report selector. */
    LATEST_CRASH_REPORT,
    /** One exact file below the mod-configuration root. */
    MOD_CONFIGURATION,
    /** Request that the user be offered explicit screenshot selection. */
    USER_SELECTED_SCREENSHOT,
    /** Product-owned bounded mod-list summary. */
    MOD_LIST,
    /** Product-owned bounded environment summary. */
    ENVIRONMENT_SUMMARY
}
