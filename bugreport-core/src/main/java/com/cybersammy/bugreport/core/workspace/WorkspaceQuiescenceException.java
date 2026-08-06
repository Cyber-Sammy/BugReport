package com.cybersammy.bugreport.core.workspace;

/** Internal failure to prove that a workspace has no outstanding mutations. */
final class WorkspaceQuiescenceException extends Exception {
    private static final long serialVersionUID = 1L;

    WorkspaceQuiescenceException(String message) {
        super(message);
    }

    WorkspaceQuiescenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
