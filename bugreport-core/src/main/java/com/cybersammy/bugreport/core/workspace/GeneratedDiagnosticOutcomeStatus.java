package com.cybersammy.bugreport.core.workspace;

/** Terminal status of one category generator execution. */
public enum GeneratedDiagnosticOutcomeStatus {
    COLLECTED,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    BUDGET_REJECTED,
    EXECUTION_CONTEXT_UNAVAILABLE
}
