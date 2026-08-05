package com.cybersammy.bugreport.core.workspace;

/** Stable reason why one planned source could not be copied into its workspace. */
public enum SourceCopyCode {
    SOURCE_CHANGED,
    SOURCE_UNSAFE,
    BYTE_LIMIT_EXCEEDED,
    WORKSPACE_CHANGED,
    DESTINATION_ALREADY_EXISTS,
    ATOMIC_MOVE_UNSUPPORTED,
    IO_FAILURE,
    ROLLBACK_FAILED
}
