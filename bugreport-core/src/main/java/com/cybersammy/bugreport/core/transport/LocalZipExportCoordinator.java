package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.core.workspace.ReportWorkspace;
import java.util.Objects;

/**
 * First-party boundary that performs one explicitly confirmed local ZIP export.
 *
 * <p>The caller is responsible for presenting the exact package plan and destination to the
 * user before calling this method. The raw consent issuer intentionally remains package-private.
 */
public final class LocalZipExportCoordinator {
    private LocalZipExportCoordinator() {}

    public static ReportTransportResult exportConfirmed(
            ReportPackagePlan plan,
            ReportWorkspace workspace,
            LocalArchiveDestination destination,
            TransportRunControl control) {
        ReportPackagePlan packagePlan = Objects.requireNonNull(plan, "plan");
        LocalArchiveDestination target = Objects.requireNonNull(destination, "destination");
        return new LocalZipTransport().execute(
                new ReportTransportRequest(
                        packagePlan,
                        Objects.requireNonNull(workspace, "workspace"),
                        target,
                        LocalExportConsent.issueConfirmed(packagePlan, target)),
                Objects.requireNonNull(control, "control"));
    }
}
