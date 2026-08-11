package com.cybersammy.bugreport.core.workspace;

/** Stable reason why one planned source could not be copied into its workspace. */
public enum SourceCopyCode {
    SOURCE_CHANGED,
    SOURCE_UNSAFE,
    CANCELLED,
    BYTE_LIMIT_EXCEEDED,
    IMAGE_DIMENSIONS_EXCEEDED,
    UNSUPPORTED_IMAGE,
    COLLECTION_BYTE_LIMIT_EXCEEDED,
    WORKSPACE_CHANGED,
    DESTINATION_ALREADY_EXISTS,
    ATOMIC_MOVE_UNSUPPORTED,
    IO_FAILURE,
    ROLLBACK_FAILED
}
