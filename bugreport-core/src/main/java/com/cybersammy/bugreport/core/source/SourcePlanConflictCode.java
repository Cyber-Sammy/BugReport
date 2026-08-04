package com.cybersammy.bugreport.core.source;

/** Stable reason why duplicate file declarations could not be merged safely. */
public enum SourcePlanConflictCode {
    CONTENT_TYPE_MISMATCH,
    PATH_CHANGED_BETWEEN_SELECTORS
}
