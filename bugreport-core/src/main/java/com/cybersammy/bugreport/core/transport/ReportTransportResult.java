package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.api.identifier.TransportId;
import com.cybersammy.bugreport.core.packaging.ReportZipArchive;
import com.cybersammy.bugreport.core.packaging.ReportZipCode;
import java.util.Objects;
import java.util.Optional;

/** Typed outcome of one explicitly authorized transport attempt. */
public final class ReportTransportResult {
    public enum Status {
        SUCCESS,
        FAILED,
        CANCELLED
    }

    private final TransportAttemptId attemptId;
    private final TransportId transportId;
    private final Status status;
    private final ReportZipArchive archive;
    private final TransportFailureCode failureCode;
    private final ReportZipCode zipCode;

    private ReportTransportResult(
            TransportAttemptId attemptId,
            TransportId transportId,
            Status status,
            ReportZipArchive archive,
            TransportFailureCode failureCode,
            ReportZipCode zipCode) {
        this.attemptId = Objects.requireNonNull(attemptId, "attemptId");
        this.transportId = Objects.requireNonNull(transportId, "transportId");
        this.status = Objects.requireNonNull(status, "status");
        this.archive = archive;
        this.failureCode = failureCode;
        this.zipCode = zipCode;
        if ((status == Status.SUCCESS) != (archive != null)
                || (status == Status.SUCCESS) != (failureCode == null)
                || (zipCode != null && failureCode != TransportFailureCode.ZIP_FAILED)) {
            throw new IllegalArgumentException("Transport result fields are inconsistent");
        }
    }

    static ReportTransportResult success(
            TransportAttemptId attemptId, TransportId transportId, ReportZipArchive archive) {
        return new ReportTransportResult(
                attemptId, transportId, Status.SUCCESS, archive, null, null);
    }

    static ReportTransportResult failure(
            TransportAttemptId attemptId,
            TransportId transportId,
            Status status,
            TransportFailureCode failureCode,
            ReportZipCode zipCode) {
        return new ReportTransportResult(
                attemptId, transportId, status, null, failureCode, zipCode);
    }

    public TransportAttemptId attemptId() {
        return attemptId;
    }

    public TransportId transportId() {
        return transportId;
    }

    public Status status() {
        return status;
    }

    public Optional<ReportZipArchive> archive() {
        return Optional.ofNullable(archive);
    }

    public Optional<TransportFailureCode> failureCode() {
        return Optional.ofNullable(failureCode);
    }

    public Optional<ReportZipCode> zipCode() {
        return Optional.ofNullable(zipCode);
    }
}
