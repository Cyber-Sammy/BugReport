package com.cybersammy.bugreport.core.sanitization;

/** Stable reason why a streaming sanitization pass could not produce trusted output. */
public enum SanitizationCode {
    STAGE_FAILED,
    CANCELLED,
    LINE_LIMIT_EXCEEDED,
    INPUT_LIMIT_EXCEEDED,
    OUTPUT_LIMIT_EXCEEDED,
    METADATA_LIMIT_EXCEEDED,
    IO_FAILURE
}
