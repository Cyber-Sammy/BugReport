package com.cybersammy.bugreport.core.workspace;

/** Non-blocking platform boundary for scheduling a short capture on the owning game thread. */
@FunctionalInterface
public interface GameThreadDispatcher {
    /**
     * Attempts to enqueue a command without waiting for it to execute.
     *
     * @return {@code true} when accepted; {@code false} when the game thread is unavailable
     */
    boolean dispatch(Runnable command);
}
