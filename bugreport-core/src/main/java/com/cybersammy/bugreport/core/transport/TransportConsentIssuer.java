package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;

/** Internal authority boundary entered only after product-owned user confirmation. */
final class TransportConsentIssuer {
    private TransportConsentIssuer() {}

    static TransportConsent issueConfirmedLocalExport(
            ReportPackagePlan plan, LocalArchiveDestination destination) {
        return LocalExportConsent.issueConfirmed(plan, destination);
    }
}
