package com.cybersammy.bugreport.core.workspace;

/** Stable reasons why a reviewed workspace snapshot could not be created. */
public enum ReviewedWorkspaceSnapshotCode {
    SESSION_MISMATCH,
    CATEGORY_MISMATCH,
    INVALID_SESSION_STATE,
    ARTIFACT_NOT_COLLECTED,
    DUPLICATE_ARTIFACT,
    ARTIFACT_CHANGED,
    WORKSPACE_CHANGED,
    WORKSPACE_BUSY,
    IO_FAILURE
}
