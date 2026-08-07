package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.core.workspace.ReportWorkspace;
import java.util.Objects;

/** Exact package, workspace, destination, and consent for one transport attempt. */
public final class ReportTransportRequest {
    private final ReportPackagePlan plan;
    private final ReportWorkspace workspace;
    private final TransportDestination destination;
    private final TransportConsent consent;

    public ReportTransportRequest(
            ReportPackagePlan plan,
            ReportWorkspace workspace,
            TransportDestination destination,
            TransportConsent consent) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.consent = Objects.requireNonNull(consent, "consent");
    }

    public ReportPackagePlan plan() {
        return plan;
    }

    public ReportWorkspace workspace() {
        return workspace;
    }

    public TransportDestination destination() {
        return destination;
    }

    public TransportConsent consent() {
        return consent;
    }
}
