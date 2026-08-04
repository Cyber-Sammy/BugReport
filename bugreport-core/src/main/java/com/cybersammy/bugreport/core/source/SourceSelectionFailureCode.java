package com.cybersammy.bugreport.core.source;

/** Stable reason why one diagnostic-source selector produced no usable plan. */
public enum SourceSelectionFailureCode {
    SOURCE_MISSING,
    NO_MATCH,
    PATH_REJECTED,
    SCAN_LIMIT_EXCEEDED,
    MATCH_LIMIT_EXCEEDED,
    UNSUPPORTED_MATCH,
    PATH_CHANGED_DURING_SCAN,
    IO_FAILURE
}
