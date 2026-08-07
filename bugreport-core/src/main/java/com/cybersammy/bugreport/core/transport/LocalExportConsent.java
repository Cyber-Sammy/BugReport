package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.api.identifier.TransportId;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-use authority for one exact local ZIP plan and destination. */
public final class LocalExportConsent implements TransportConsent {
    private final TransportAttemptId attemptId;
    private final TransportId transportId;
    private final PackagePlanFingerprint packageFingerprint;
    private final LocalArchiveDestination destination;
    private final AtomicBoolean consumed = new AtomicBoolean();

    private LocalExportConsent(
            TransportId transportId,
            ReportPackagePlan plan,
            LocalArchiveDestination destination) {
        attemptId = TransportAttemptId.create();
        this.transportId = Objects.requireNonNull(transportId, "transportId");
        packageFingerprint = PackagePlanFingerprint.of(
                Objects.requireNonNull(plan, "plan"));
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    /**
     * Issues authority after the caller has shown the exact package, transport, and destination
     * to the user and received explicit confirmation.
     */
    public static LocalExportConsent approve(
            ReportPackagePlan plan, LocalArchiveDestination destination) {
        return new LocalExportConsent(LocalZipTransport.ID, plan, destination);
    }

    @Override
    public TransportAttemptId attemptId() {
        return attemptId;
    }

    ConsentDecision consume(
            TransportId requestedTransport,
            ReportPackagePlan requestedPlan,
            LocalArchiveDestination requestedDestination) {
        if (!transportId.equals(requestedTransport)
                || !packageFingerprint.equals(PackagePlanFingerprint.of(requestedPlan))
                || !destination.equals(requestedDestination)) {
            return ConsentDecision.MISMATCH;
        }
        return consumed.compareAndSet(false, true)
                ? ConsentDecision.ACCEPTED
                : ConsentDecision.ALREADY_USED;
    }

    enum ConsentDecision {
        ACCEPTED,
        MISMATCH,
        ALREADY_USED
    }
}
