package com.cybersammy.bugreport.core.workspace;

/** Stable outcome of one explicitly authorized abandoned-workspace cleanup attempt. */
public enum AbandonedWorkspaceCleanupCode {
    REMOVED,
    NOT_FOUND,
    ACTIVE_SESSION,
    ROOT_UNSAFE,
    WORKSPACE_UNSAFE,
    MARKER_INVALID,
    UNEXPECTED_ENTRY,
    ENTRY_CHANGED,
    IO_FAILURE,
    PARTIAL_FAILURE
}
