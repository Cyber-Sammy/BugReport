package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.neoforge.command.BugReportCommandService;
import java.util.Objects;

/**
 * Narrow NeoForge-owned friend adapter for the package-private local-export consent issuer.
 *
 * <p>It deliberately accepts only a {@link BugReportCommandService.ConfirmedLocalExport}, whose
 * constructor is private and which the command service never returns. Consequently this adapter
 * cannot be used as a public {@code plan + workspace + destination} consent-minting API.
 */
public final class NeoForgeLocalExportTransportAdapter {
    private NeoForgeLocalExportTransportAdapter() {}

    public static ReportTransportResult executeConfirmed(
            BugReportCommandService.ConfirmedLocalExport confirmation,
            TransportRunControl control) {
        BugReportCommandService.ConfirmedLocalExport value =
                Objects.requireNonNull(confirmation, "confirmation");
        ReportPackagePlan plan = value.plan();
        LocalArchiveDestination destination = value.destination();
        return new LocalZipTransport().execute(
                new ReportTransportRequest(
                        plan,
                        value.workspace(),
                        destination,
                        LocalExportConsent.issueConfirmed(plan, destination)),
                Objects.requireNonNull(control, "control"));
    }
}
