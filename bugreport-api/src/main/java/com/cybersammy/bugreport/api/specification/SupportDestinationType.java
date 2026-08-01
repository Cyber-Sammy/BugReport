package com.cybersammy.bugreport.api.specification;

/** Descriptive support destination category; it never executes delivery. */
public enum SupportDestinationType {
    /** User-selected local archive location. */
    LOCAL_ARCHIVE,
    /** External HTTPS support page. */
    EXTERNAL_SUPPORT_URL,
    /** External HTTPS issue-tracker page or template. */
    ISSUE_TRACKER,
    /** Email composition description. */
    EMAIL
}
