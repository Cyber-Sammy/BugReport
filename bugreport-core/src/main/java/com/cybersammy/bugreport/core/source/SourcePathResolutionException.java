package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.io.Serial;
import java.util.Objects;

/** Typed rejection at the approved source-path boundary. */
public final class SourcePathResolutionException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final SourcePathResolutionCode code;
    private final LogicalRoot root;
    private final String relativePath;

    SourcePathResolutionException(
            SourcePathResolutionCode code,
            LogicalRoot root,
            RelativePath relativePath,
            String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.root = Objects.requireNonNull(root, "root");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath").value();
    }

    SourcePathResolutionException(
            SourcePathResolutionCode code,
            LogicalRoot root,
            RelativePath relativePath,
            String message,
            Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
        this.root = Objects.requireNonNull(root, "root");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath").value();
    }

    /** Returns the stable rejection reason. */
    public SourcePathResolutionCode code() {
        return code;
    }

    /** Returns the logical root without exposing its local absolute mapping. */
    public LogicalRoot root() {
        return root;
    }

    /** Returns the portable provider-declared path. */
    public RelativePath relativePath() {
        return RelativePath.of(relativePath);
    }
}
