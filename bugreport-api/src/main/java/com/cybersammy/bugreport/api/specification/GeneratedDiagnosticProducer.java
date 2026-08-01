package com.cybersammy.bugreport.api.specification;

/** Executable provider boundary for one explicitly requested generated diagnostic. */
@FunctionalInterface
public interface GeneratedDiagnosticProducer {
    /**
     * Produces bounded artifacts through the supplied sink.
     *
     * <p>The provider must cooperate with cancellation and must not perform
     * network I/O, register events, mutate the report session, or retain the
     * request or sink. The runtime isolates failures and applies its own timeout.
     *
     * @param request immutable side and cancellation context
     * @param sink product-owned bounded output sink
     * @throws Exception when generation cannot complete
     */
    void generate(GeneratedDiagnosticRequest request, GeneratedDiagnosticSink sink)
            throws Exception;
}
