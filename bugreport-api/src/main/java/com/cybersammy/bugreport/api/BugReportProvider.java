package com.cybersammy.bugreport.api;

/**
 * Loader-neutral contract implemented by Bug Report providers.
 *
 * <p>A provider declared for runtime discovery must be a public concrete class
 * with a public no-argument constructor. Construction must be cheap and
 * side-effect free: it must not register events or perform file or network I/O.
 * The provider class, its static initialization, and its constructor must be
 * safe to load on both a physical client and a dedicated server. Expensive or
 * side-specific diagnostics belong in callbacks invoked after discovery.
 */
public interface BugReportProvider {
    /**
     * Returns the stable provider identifier.
     *
     * <p>A mod's default provider uses its declaring NeoForge mod ID. Additional
     * providers use {@code <mod_id>:<local_name>}. Both components use
     * lowercase ASCII letters, digits, and underscores, start with a letter,
     * and contain at most 64 characters. The namespace must equal the declaring
     * mod ID.
     */
    String providerId();

    /** Returns the provider version when it is available. */
    default String providerVersion() {
        return "unknown";
    }
}
