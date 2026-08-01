package com.cybersammy.bugreport.api.version;

import java.util.Objects;
import java.util.Optional;

/**
 * Version of one mod's Bug Report provider integration.
 *
 * <p>This is deliberately distinct from {@link ApiVersion}: it identifies the
 * provider-owned specification release and must not participate in Bug Report
 * API artifact selection or compatibility negotiation. Equality represents
 * exact canonical text, including build metadata; it is not SemVer precedence.
 */
public final class ProviderVersion {
    private final String value;
    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;

    private ProviderVersion(SemanticVersionSyntax.Components components) {
        value = components.value();
        major = components.major();
        minor = components.minor();
        patch = components.patch();
        preRelease = components.preRelease();
        buildMetadata = components.buildMetadata();
    }

    /**
     * Parses a bounded provider integration version.
     *
     * @param value exact canonical Semantic Versioning text
     * @return parsed provider version
     * @throws IllegalArgumentException when the value is not canonical SemVer
     */
    public static ProviderVersion parse(String value) {
        return new ProviderVersion(SemanticVersionSyntax.parse(value, "Provider version"));
    }

    /**
     * Returns the major component.
     *
     * @return major version
     */
    public int major() {
        return major;
    }

    /**
     * Returns the minor component.
     *
     * @return minor version
     */
    public int minor() {
        return minor;
    }

    /**
     * Returns the patch component.
     *
     * @return patch version
     */
    public int patch() {
        return patch;
    }

    /**
     * Returns the optional prerelease identifier.
     *
     * @return prerelease text
     */
    public Optional<String> preRelease() {
        return Optional.ofNullable(preRelease);
    }

    /**
     * Returns the optional build metadata.
     *
     * @return build metadata
     */
    public Optional<String> buildMetadata() {
        return Optional.ofNullable(buildMetadata);
    }

    /**
     * Returns the exact canonical version text.
     *
     * @return version text
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProviderVersion version && value.equals(version.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
