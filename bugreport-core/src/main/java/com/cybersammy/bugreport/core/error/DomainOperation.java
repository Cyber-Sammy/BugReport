package com.cybersammy.bugreport.core.error;

/** Bounded Core operation names permitted in structured failure context. */
public enum DomainOperation {
    CONFIGURATION_LOAD("configuration.load"),
    CONFIGURATION_SAVE("configuration.save"),
    DRAFT_LOAD("draft.load"),
    DRAFT_SAVE("draft.save"),
    HISTORY_LOAD("history.load"),
    HISTORY_SAVE("history.save"),
    COLLECTION_COPY("collection.copy"),
    SANITIZATION("sanitization.run"),
    ZIP_WRITE("zip.write"),
    TRANSPORT_EXECUTE("transport.execute");

    private final String token;

    DomainOperation(String token) {
        this.token = token;
    }

    /** Returns the stable operation token used in a structured context. */
    public String token() {
        return token;
    }
}
