package com.cybersammy.bugreport.api.specification;

/** Produces bounded relative paths for a diagnostic source that cannot be declared statically. */
@FunctionalInterface
public interface DynamicSourcePathProducer {
    /**
     * Publishes zero or more exact relative file paths through the product-owned sink.
     *
     * <p>The callback runs on a Bug Report worker. It must return synchronously, honor
     * cancellation, and must not retain or use the request or sink after returning.
     *
     * @param request bounded invocation context
     * @param sink product-owned bounded path sink
     * @throws Exception when the provider cannot produce a valid result
     */
    void produce(DynamicSourcePathRequest request, DynamicSourcePathSink sink)
            throws Exception;
}
