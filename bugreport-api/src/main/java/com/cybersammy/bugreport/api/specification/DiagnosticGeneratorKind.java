package com.cybersammy.bugreport.api.specification;

/** Product-recognized purpose of a bounded generated diagnostic callback. */
public enum DiagnosticGeneratorKind {
    /** Ordinary provider-owned text or JSON diagnostic. */
    GENERAL,
    /** Small purpose-built export derived from local world or player state. */
    WORLD_STATE_EXPORT
}
