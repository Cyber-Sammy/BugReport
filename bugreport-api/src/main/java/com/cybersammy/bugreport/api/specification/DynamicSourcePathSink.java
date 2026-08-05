package com.cybersammy.bugreport.api.specification;

/** Product-owned bounded output authority for one dynamic source-path callback. */
@FunctionalInterface
public interface DynamicSourcePathSink {
    /**
     * Publishes one exact portable path relative to the source's predeclared logical root.
     *
     * <p>The implementation rejects nulls, duplicates, emissions after return or cancellation,
     * and results beyond the declared and product-owned count ceiling. Publishing a path does not
     * grant filesystem access; Core independently resolves and validates every result.
     *
     * @param path exact relative file path
     */
    void emit(RelativePath path);
}
