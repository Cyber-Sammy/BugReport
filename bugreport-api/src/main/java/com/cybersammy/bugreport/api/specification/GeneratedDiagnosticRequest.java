package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import java.util.Objects;

/**
 * Narrow immutable context supplied to one generated diagnostic invocation.
 *
 * @param side current physical side
 * @param cancellation cooperative cancellation signal
 */
public record GeneratedDiagnosticRequest(
        SupportedSide side, CancellationSignal cancellation) {
    /** Validates and creates a request. */
    public GeneratedDiagnosticRequest {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
