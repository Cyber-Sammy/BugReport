package com.cybersammy.bugreport.core.manifest;

/** Stable reason for rejecting an encoded report manifest. */
public enum ManifestFormatCode {
    MALFORMED_JSON,
    DUPLICATE_MEMBER,
    LIMIT_EXCEEDED,
    UNSUPPORTED_SCHEMA_ID,
    UNSUPPORTED_SCHEMA_MAJOR,
    INVALID_VALUE
}
