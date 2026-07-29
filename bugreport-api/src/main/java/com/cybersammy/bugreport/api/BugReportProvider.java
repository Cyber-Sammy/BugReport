package com.cybersammy.bugreport.api;

/** Loader-neutral contract implemented by Bug Report providers. */
public interface BugReportProvider {
    /** Returns the stable provider identifier. */
    String providerId();

    /** Returns the provider version when it is available. */
    default String providerVersion() {
        return "unknown";
    }
}
