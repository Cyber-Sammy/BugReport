package com.cybersammy.bugreport.core.draft;

/** Stable reason for a draft-store operation failure. */
public enum DraftStoreCode {
    ROOT_INVALID,
    IO_FAILURE,
    ATOMIC_MOVE_UNSUPPORTED,
    STALE_REVISION,
    EXISTING_DRAFT_INVALID
}
