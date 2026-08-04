package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;

/** Trusted immutable result of planning one diagnostic-source selector. */
public sealed interface SourceSelectionPlan
        permits FileSourcePlan,
                BuiltInSourcePlan,
                UserSelectionSourcePlan,
                UnavailableSourcePlan {
    /** Returns the exact source declaration represented by this result. */
    DiagnosticSourceSpecification source();
}
