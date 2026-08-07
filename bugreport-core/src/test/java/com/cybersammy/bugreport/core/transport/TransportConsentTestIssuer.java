package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;

/** Test-only access to the product-owned consent issuing boundary. */
public final class TransportConsentTestIssuer {
    private TransportConsentTestIssuer() {}

    public static TransportConsent issueConfirmedLocalExport(
            ReportPackagePlan plan, LocalArchiveDestination destination) {
        return TransportConsentIssuer.issueConfirmedLocalExport(plan, destination);
    }
}
