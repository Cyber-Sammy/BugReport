package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import java.util.Objects;

/** Explicit request for later user-controlled attachment selection. */
public final class UserSelectionSourcePlan implements SourceSelectionPlan {
    private final DiagnosticSourceSpecification source;

    UserSelectionSourcePlan(DiagnosticSourceSpecification source) {
        this.source = Objects.requireNonNull(source, "source");
        if (source.kind() != DiagnosticSourceKind.USER_SELECTED_SCREENSHOT) {
            throw new IllegalArgumentException("Only screenshot sources require user selection");
        }
    }

    @Override
    public DiagnosticSourceSpecification source() {
        return source;
    }

    @Override
    public SourceSizeEstimate estimate() {
        return SourceSizeEstimate.lowerBound(0, 0);
    }
}
