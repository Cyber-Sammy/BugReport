package com.cybersammy.bugreport.core.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.bugreport.api.identifier.TransportId;
import com.cybersammy.bugreport.core.error.DomainError;
import com.cybersammy.bugreport.core.error.DomainErrorCode;
import com.cybersammy.bugreport.core.error.DomainErrorContext;
import com.cybersammy.bugreport.core.error.DomainOperation;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ReportTransportResultTest {
    @Test
    void failedResultRetainsAValueLevelDomainError() {
        DomainError error = new DomainError(
                DomainErrorCode.from("transport", TransportFailureCode.ZIP_FAILED),
                "Transport ZIP creation failed",
                DomainErrorContext.builder().operation(DomainOperation.TRANSPORT_EXECUTE).build());

        ReportTransportResult result = ReportTransportResult.failure(
                new TransportAttemptId(UUID.fromString("00000000-0000-4000-8000-000000000201")),
                TransportId.of("bugreport:local_zip"),
                ReportTransportResult.Status.FAILED,
                TransportFailureCode.ZIP_FAILED,
                com.cybersammy.bugreport.core.packaging.ReportZipCode.WRITE_FAILED,
                error);

        assertEquals(error, result.error().orElseThrow());
        assertEquals(TransportFailureCode.ZIP_FAILED, result.failureCode().orElseThrow());
        assertEquals(
                com.cybersammy.bugreport.core.packaging.ReportZipCode.WRITE_FAILED,
                result.zipCode().orElseThrow());
    }
}
