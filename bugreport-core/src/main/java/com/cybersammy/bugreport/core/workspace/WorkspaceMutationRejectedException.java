package com.cybersammy.bugreport.core.workspace;

/** Internal signal that a sealed or quarantined workspace rejected new write authority. */
final class WorkspaceMutationRejectedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    WorkspaceMutationRejectedException(String message) {
        super(message);
    }
}
