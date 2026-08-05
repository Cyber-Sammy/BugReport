package com.cybersammy.bugreport.core.workspace;

/** Stable reason why an isolated report workspace could not be created. */
public enum WorkspaceCreationCode {
    ROOT_INVALID,
    WORKSPACE_ALREADY_EXISTS,
    WORKSPACE_UNSAFE,
    IO_FAILURE,
    ROLLBACK_FAILED
}
