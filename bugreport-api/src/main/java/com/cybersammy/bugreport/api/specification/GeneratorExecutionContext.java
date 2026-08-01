package com.cybersammy.bugreport.api.specification;

/** Thread context requested for a narrowly bounded generated diagnostic callback. */
public enum GeneratorExecutionContext {
    /** Run through the product-owned diagnostic worker policy. */
    WORKER,
    /** Capture a short non-blocking snapshot on the owning game thread. */
    GAME_THREAD_SNAPSHOT
}
