package com.cybersammy.bugreport.core.error;

/** Safe identifiers permitted in structured failure logs. */
public enum DomainErrorContextKey {
    SESSION_ID("session"),
    PROVIDER_ID("provider"),
    CATEGORY_ID("category"),
    SOURCE_ID("source"),
    GENERATOR_ID("generator"),
    TRANSPORT_ID("transport"),
    LOGICAL_ROOT("root"),
    ARTIFACT_NAME("artifact");

    private final String logName;

    DomainErrorContextKey(String logName) {
        this.logName = logName;
    }

    /** Returns the stable key used in the rendered context token. */
    public String logName() {
        return logName;
    }
}
