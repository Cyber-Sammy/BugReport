package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import java.util.Objects;

/**
 * Narrow immutable context supplied to one dynamic source-path invocation.
 *
 * @param side current physical side
 * @param cancellation cooperative cancellation signal
 */
public record DynamicSourcePathRequest(
        SupportedSide side, CancellationSignal cancellation) {
    /** Validates and creates an invocation request. */
    public DynamicSourcePathRequest {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(cancellation, "cancellation");
    }
}
