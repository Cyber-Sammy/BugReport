package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.api.identifier.TransportId;
import com.cybersammy.bugreport.api.specification.SupportDestinationType;
import com.cybersammy.bugreport.core.error.DomainError;
import com.cybersammy.bugreport.core.error.DomainErrorCode;
import com.cybersammy.bugreport.core.error.DomainErrorContext;
import com.cybersammy.bugreport.core.error.DomainErrorContextKey;
import com.cybersammy.bugreport.core.error.DomainOperation;
import com.cybersammy.bugreport.core.packaging.ReportZipArchive;
import com.cybersammy.bugreport.core.packaging.ReportZipCode;
import com.cybersammy.bugreport.core.packaging.ReportZipException;
import com.cybersammy.bugreport.core.packaging.ReportZipWriter;
import java.util.Objects;
import java.util.Set;

/** First-party offline transport that writes one explicitly authorized local ZIP. */
public final class LocalZipTransport implements ReportTransport {
    public static final TransportId ID = TransportId.of("bugreport:local_zip");
    private static final ReportTransportDescriptor DESCRIPTOR = new ReportTransportDescriptor(
            ID, Set.of(SupportDestinationType.LOCAL_ARCHIVE), false, false, true);

    @Override
    public ReportTransportDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ReportTransportResult execute(
            ReportTransportRequest request, TransportRunControl control) {
        ReportTransportRequest attempt = Objects.requireNonNull(request, "request");
        TransportRunControl run = Objects.requireNonNull(control, "control");
        run.begin(attempt.plan());
        TransportAttemptId attemptId = attempt.consent().attemptId();
        if (!(attempt.destination() instanceof LocalArchiveDestination destination)) {
            return failed(
                    run,
                    attemptId,
                    attempt.plan(),
                    TransportFailureCode.DESTINATION_UNSUPPORTED,
                    null);
        }
        if (!(attempt.consent() instanceof LocalExportConsent consent)) {
            return failed(run, attemptId, attempt.plan(), TransportFailureCode.CONSENT_MISMATCH, null);
        }
        LocalExportConsent.ConsentDecision decision =
                consent.consume(ID, attempt.plan(), destination);
        if (decision == LocalExportConsent.ConsentDecision.MISMATCH) {
            return failed(run, attemptId, attempt.plan(), TransportFailureCode.CONSENT_MISMATCH, null);
        }
        if (decision == LocalExportConsent.ConsentDecision.ALREADY_USED) {
            return failed(run, attemptId, attempt.plan(), TransportFailureCode.CONSENT_ALREADY_USED, null);
        }
        try {
            ReportZipArchive archive = ReportZipWriter.write(
                    attempt.plan(),
                    attempt.workspace(),
                    destination.path(),
                    run,
                    run::publish);
            run.finish(TransportProgressSnapshot.State.COMPLETE);
            return ReportTransportResult.success(attemptId, ID, archive);
        } catch (ReportZipException exception) {
            if (exception.code() == ReportZipCode.CANCELLED) {
                run.finish(TransportProgressSnapshot.State.CANCELLED);
                return ReportTransportResult.failure(
                        attemptId,
                        ID,
                        ReportTransportResult.Status.CANCELLED,
                        TransportFailureCode.CANCELLED,
                        null,
                        error(attempt.plan(), TransportFailureCode.CANCELLED, null));
            }
            return failed(run, attemptId, attempt.plan(), TransportFailureCode.ZIP_FAILED, exception.code());
        } catch (RuntimeException exception) {
            return failed(run, attemptId, attempt.plan(), TransportFailureCode.TRANSPORT_FAILED, null);
        }
    }

    private static ReportTransportResult failed(
            TransportRunControl control,
            TransportAttemptId attemptId,
            com.cybersammy.bugreport.core.packaging.ReportPackagePlan plan,
            TransportFailureCode failureCode,
            ReportZipCode zipCode) {
        control.finish(TransportProgressSnapshot.State.FAILED);
        return ReportTransportResult.failure(
                attemptId,
                ID,
                ReportTransportResult.Status.FAILED,
                failureCode,
                zipCode,
                error(plan, failureCode, zipCode));
    }

    private static DomainError error(
            com.cybersammy.bugreport.core.packaging.ReportPackagePlan plan,
            TransportFailureCode failureCode,
            ReportZipCode zipCode) {
        var snapshot = plan.preparedSnapshot().reviewedSnapshot();
        DomainErrorContext.Builder context = DomainErrorContext.builder()
                .operation(DomainOperation.TRANSPORT_EXECUTE)
                .put(DomainErrorContextKey.SESSION_ID, snapshot.sessionId().toString())
                .put(DomainErrorContextKey.PROVIDER_ID, snapshot.providerId().value())
                .put(DomainErrorContextKey.CATEGORY_ID, snapshot.categoryId().value())
                .put(DomainErrorContextKey.TRANSPORT_ID, ID.value());
        if (zipCode != null) {
            context.put(DomainErrorContextKey.ZIP_CODE, zipCode.name());
        }
        return new DomainError(
                DomainErrorCode.from("transport", failureCode),
                safeMessage(failureCode),
                context.build());
    }

    private static String safeMessage(TransportFailureCode failureCode) {
        return switch (failureCode) {
            case DESTINATION_UNSUPPORTED -> "Transport destination is unsupported";
            case CONSENT_MISMATCH -> "Transport consent does not match the request";
            case CONSENT_ALREADY_USED -> "Transport consent was already used";
            case CANCELLED -> "Transport was cancelled";
            case ZIP_FAILED -> "Transport ZIP creation failed";
            case TRANSPORT_FAILED -> "Transport execution failed";
        };
    }
}
