package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.core.workspace.ReportWorkspace;
import java.util.Objects;

/**
 * Protected first-party bridge from application lifecycle authority to the internal local ZIP
 * consent issuer.
 *
 * <p>This class deliberately has no public execution operation. The NeoForge application service
 * invokes the protected method only after validating its exact service-issued export token and the
 * user's explicit confirmation.
 */
public abstract class LocalExportTransportBridge {
    protected final ReportTransportResult executeConfirmedLocalExport(
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
