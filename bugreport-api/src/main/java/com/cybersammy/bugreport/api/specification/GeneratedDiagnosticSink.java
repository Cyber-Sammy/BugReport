package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;

/**
 * Product-owned bounded output authority for one generated diagnostic.
 *
 * <p>The implementation enforces declared and product-owned count and byte
 * ceilings. It exposes no filesystem path, stream, workspace, or package
 * handle. Emitting after cancellation or beyond a limit fails the invocation.
 */
public interface GeneratedDiagnosticSink {
    /**
     * Emits one bounded UTF-8 text artifact.
     *
     * @param id artifact ID within the generator
     * @param content artifact text
     */
    void emitText(GeneratedArtifactId id, CharSequence content);

    /**
     * Emits one bounded JSON-compatible object artifact.
     *
     * @param id artifact ID within the generator
     * @param content bounded JSON-compatible metadata object
     */
    void emitJson(GeneratedArtifactId id, ExtensionMetadata content);
}
