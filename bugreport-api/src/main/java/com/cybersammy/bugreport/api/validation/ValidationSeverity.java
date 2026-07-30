package com.cybersammy.bugreport.api.validation;

/** Severity of a typed provider specification validation issue. */
public enum ValidationSeverity {
    /** The contract is invalid and cannot be registered. */
    ERROR,
    /** The contract is usable but has an actionable limitation or fallback. */
    WARNING
}
