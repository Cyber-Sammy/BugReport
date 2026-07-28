package com.cybersammy.bugreport.api;

/** Minimal loader-neutral provider contract used by the M0 packaging spike. */
public interface BugReportProvider {
    /** Returns the stable provider identifier. */
    String providerId();
}
