package com.cybersammy.bugreport.core.transport;

/** One-attempt delivery authority issued only after explicit user confirmation. */
public sealed interface TransportConsent permits LocalExportConsent {
    TransportAttemptId attemptId();
}
