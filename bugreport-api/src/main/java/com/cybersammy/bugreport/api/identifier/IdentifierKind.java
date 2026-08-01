package com.cybersammy.bugreport.api.identifier;

/** Semantic scope of a canonical Bug Report identifier. */
public enum IdentifierKind {
    /** Declaring mod or contract namespace. */
    NAMESPACE,
    /** Provider identity in the global provider registry. */
    PROVIDER,
    /** Category identity within a provider. */
    CATEGORY,
    /** Field identity within a category. */
    FIELD,
    /** Choice identity within a field. */
    FIELD_OPTION,
    /** Diagnostic source identity within a provider. */
    DIAGNOSTIC_SOURCE,
    /** Generated diagnostic identity within a provider. */
    DIAGNOSTIC_GENERATOR,
    /** Generated artifact identity within one diagnostic generator. */
    GENERATED_ARTIFACT,
    /** Globally namespaced support destination identity. */
    DESTINATION,
    /** Globally namespaced capability identity. */
    CAPABILITY,
    /** Globally namespaced transport identity. */
    TRANSPORT,
    /** Globally namespaced extension metadata key. */
    EXTENSION_METADATA_KEY,
    /** Globally namespaced validation issue code. */
    VALIDATION_CODE
}
