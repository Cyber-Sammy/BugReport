package com.cybersammy.bugreport.core.session;

/** Stable reasons why a structurally resolved draft cannot become a live session. */
public enum ReportSessionRecoveryCode {
    /** Completed and cancelled drafts are not resumable. */
    TERMINAL_DRAFT,
    /** The persisted revision cannot be advanced for the recovery event. */
    REVISION_EXHAUSTED
}
