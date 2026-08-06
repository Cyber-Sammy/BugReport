package com.cybersammy.bugreport.core.sanitization;

/** Stable, log-safe reason why one sanitization stage was skipped for a line. */
public enum SanitizationStageFailureCode {
    CALLBACK_FAILED,
    NULL_RESULT,
    INVALID_RESULT
}
