package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import java.util.Objects;

/** Product-owned non-filesystem source planned without granting provider authority. */
public final class BuiltInSourcePlan implements SourceSelectionPlan {
    private final DiagnosticSourceSpecification source;

    BuiltInSourcePlan(DiagnosticSourceSpecification source) {
        this.source = Objects.requireNonNull(source, "source");
        if (source.kind() != DiagnosticSourceKind.MOD_LIST
                && source.kind() != DiagnosticSourceKind.ENVIRONMENT_SUMMARY) {
            throw new IllegalArgumentException("Unsupported built-in source kind");
        }
    }

    @Override
    public DiagnosticSourceSpecification source() {
        return source;
    }
}
