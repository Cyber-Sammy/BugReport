package com.cybersammy.bugreport.api;

/** Loader-neutral contract implemented by Bug Report providers. */
public interface BugReportProvider {
    /** Returns the stable provider identifier. */
    String providerId();
}
