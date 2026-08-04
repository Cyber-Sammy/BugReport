package com.cybersammy.bugreport.core.session;

/** Closed technical reasons why an active report session was cancelled. */
public enum CancellationReason {
    /** The local user explicitly cancelled the report. */
    USER_REQUESTED,
    /** The owning orchestration operation was explicitly aborted. */
    OPERATION_ABORTED
}
