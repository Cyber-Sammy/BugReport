package com.cybersammy.bugreport.core.history;

/** Stable failure reason for history persistence. */
public enum HistoryStoreCode {
    PATH_INVALID,
    UNSAFE_FILE,
    ATOMIC_MOVE_UNSUPPORTED,
    IO_FAILURE
}
