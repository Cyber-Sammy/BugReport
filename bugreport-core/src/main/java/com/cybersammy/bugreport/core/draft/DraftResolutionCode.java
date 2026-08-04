package com.cybersammy.bugreport.core.draft;

/** Stable reasons why a parsed draft cannot bind to the current registry. */
public enum DraftResolutionCode {
    /** The draft provider is no longer registered. */
    PROVIDER_MISSING,
    /** The current runtime disabled the draft provider. */
    PROVIDER_DISABLED,
    /** The provider specification version changed. */
    PROVIDER_VERSION_MISMATCH,
    /** The recorded category is no longer declared. */
    CATEGORY_MISSING,
    /** Persisted field representations do not match the trusted category. */
    INVALID_FORM_STRUCTURE
}
