package com.cybersammy.bugreport.api.specification;

/** Cooperative cancellation signal supplied to generated diagnostic code. */
@FunctionalInterface
public interface CancellationSignal {
    /**
     * Reports whether the operation should stop producing output.
     *
     * @return {@code true} when cancellation was requested
     */
    boolean isCancellationRequested();

    /**
     * Returns a signal that is never cancelled, primarily for deterministic tests.
     *
     * @return shared non-cancelling signal
     */
    static CancellationSignal neverCancelled() {
        return () -> false;
    }
}
