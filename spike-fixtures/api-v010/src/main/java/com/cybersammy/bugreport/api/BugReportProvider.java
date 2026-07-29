package com.cybersammy.bugreport.api;

/**
 * Loader-neutral contract implemented by Bug Report providers.
 *
 * <p>A provider declared for runtime discovery must be a concrete class with a
 * public no-argument constructor. Construction must be cheap and side-effect
 * free: it must not register events or perform file or network I/O. Expensive
 * diagnostics belong in callbacks invoked after discovery.
 */
public interface BugReportProvider {
    /** Returns the stable provider identifier. */
    String providerId();
}
