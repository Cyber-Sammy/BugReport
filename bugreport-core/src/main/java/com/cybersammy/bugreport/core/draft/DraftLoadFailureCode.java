package com.cybersammy.bugreport.core.draft;

/** Stable reason why one draft file was isolated during a directory scan. */
public enum DraftLoadFailureCode {
    FORMAT_INVALID,
    IO_FAILURE,
    SESSION_ID_MISMATCH,
    UNSAFE_FILE
}
