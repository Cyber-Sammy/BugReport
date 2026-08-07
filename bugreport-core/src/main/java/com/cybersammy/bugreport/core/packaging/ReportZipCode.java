package com.cybersammy.bugreport.core.packaging;

/** Stable reason for rejecting ZIP creation, validation, or publication. */
public enum ReportZipCode {
    INVALID_DESTINATION,
    OUTPUT_ALREADY_EXISTS,
    PLAN_LIMIT_EXCEEDED,
    SNAPSHOT_CHANGED,
    ENTRY_CHANGED,
    CANCELLED,
    WRITE_FAILED,
    ARCHIVE_INVALID,
    ARCHIVE_LIMIT_EXCEEDED,
    UNSAFE_ENTRY,
    DUPLICATE_ENTRY,
    VALIDATION_MISMATCH,
    PUBLICATION_FAILED,
    ROLLBACK_FAILED
}
