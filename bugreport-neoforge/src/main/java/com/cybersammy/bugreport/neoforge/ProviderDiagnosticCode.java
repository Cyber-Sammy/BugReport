package com.cybersammy.bugreport.neoforge;

enum ProviderDiagnosticCode {
    INVALID_PROPERTY("property"),
    MISSING_CLASS("missing"),
    CLASS_LOAD_FAILED("load"),
    OWNERSHIP_MISMATCH("ownership"),
    INVALID_TYPE("invalid"),
    INACCESSIBLE_CLASS("access"),
    ABSTRACT_TYPE("abstract"),
    MISSING_CONSTRUCTOR("constructor"),
    INSTANTIATION_FAILED("instantiation");

    private final String logToken;

    ProviderDiagnosticCode(String logToken) {
        this.logToken = logToken;
    }

    String logToken() {
        return logToken;
    }
}
