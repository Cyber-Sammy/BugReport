package com.cybersammy.bugreport.api.specification;

/** Declared representation of diagnostic content. */
public enum DiagnosticContentType {
    /** Human-readable text subject to text sanitization. */
    TEXT,
    /** Structured JSON data subject to schema-aware sanitization. */
    JSON,
    /** Opaque bytes requiring conservative review policy. */
    BINARY
}
