package com.cybersammy.bugreport.core.transport;

/** Stable transport-layer reason for a failed attempt. */
public enum TransportFailureCode {
    DESTINATION_UNSUPPORTED,
    CONSENT_MISMATCH,
    CONSENT_ALREADY_USED,
    CANCELLED,
    ZIP_FAILED,
    TRANSPORT_FAILED
}
