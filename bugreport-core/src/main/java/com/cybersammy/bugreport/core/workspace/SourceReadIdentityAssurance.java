package com.cybersammy.bugreport.core.workspace;

/** Strength of the relationship between an open source channel and its observation. */
enum SourceReadIdentityAssurance {
    /** The platform prevents path-entry replacement while the channel remains open. */
    HANDLE_STABILIZED,

    /** Portable fallback based on an immediate path observation after opening. */
    PATH_REVALIDATED
}
