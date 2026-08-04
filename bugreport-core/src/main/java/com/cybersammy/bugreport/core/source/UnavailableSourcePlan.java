package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import java.util.Objects;
import java.util.Optional;

/** Typed isolated failure for one source declaration. */
public final class UnavailableSourcePlan implements SourceSelectionPlan {
    private final DiagnosticSourceSpecification source;
    private final SourceSelectionFailureCode code;
    private final SourcePathResolutionCode pathCode;

    UnavailableSourcePlan(
            DiagnosticSourceSpecification source,
            SourceSelectionFailureCode code,
            SourcePathResolutionCode pathCode) {
        this.source = Objects.requireNonNull(source, "source");
        this.code = Objects.requireNonNull(code, "code");
        this.pathCode = pathCode;
        if ((code == SourceSelectionFailureCode.PATH_REJECTED
                        || code == SourceSelectionFailureCode.SOURCE_MISSING)
                != (pathCode != null)) {
            throw new IllegalArgumentException(
                    "Path resolution detail must accompany only path-based failures");
        }
    }

    @Override
    public DiagnosticSourceSpecification source() {
        return source;
    }

    /** Returns the stable selector-level failure reason. */
    public SourceSelectionFailureCode code() {
        return code;
    }

    /** Returns the exact path failure when resolution reached that boundary. */
    public Optional<SourcePathResolutionCode> pathCode() {
        return Optional.ofNullable(pathCode);
    }

    @Override
    public SourceSizeEstimate estimate() {
        return SourceSizeEstimate.exact(0, 0);
    }
}
