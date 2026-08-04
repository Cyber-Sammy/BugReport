package com.cybersammy.bugreport.api.specification;

import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import java.time.Duration;

/** Bug Report-owned capability contracts available to provider specifications. */
public final class StandardCapabilities {
    static final int WORLD_STATE_MAX_ARTIFACTS = 4;
    static final long WORLD_STATE_MAX_BYTES_PER_ARTIFACT = 1_048_576;
    static final long WORLD_STATE_MAX_TOTAL_BYTES = 2_097_152;
    static final Duration WORLD_STATE_MAX_CALLBACK_TIMEOUT = Duration.ofSeconds(2);

    private static final CapabilityId BOUNDED_WORLD_STATE_EXPORT_ID =
            CapabilityId.of("bugreport:bounded_world_state_export");
    private static final CapabilityVersion BOUNDED_WORLD_STATE_EXPORT_VERSION =
            new CapabilityVersion(1, 0);
    private static final CapabilityRequirement BOUNDED_WORLD_STATE_EXPORT =
            new CapabilityRequirement(
                    BOUNDED_WORLD_STATE_EXPORT_ID,
                    BOUNDED_WORLD_STATE_EXPORT_VERSION,
                    true);
    private static final CollectionConstraints BOUNDED_WORLD_STATE_EXPORT_MAXIMUMS =
            CollectionConstraints.builder()
                    .maxGeneratedArtifacts(WORLD_STATE_MAX_ARTIFACTS)
                    .maxBytesPerFile(WORLD_STATE_MAX_BYTES_PER_ARTIFACT)
                    .maxTotalBytes(WORLD_STATE_MAX_TOTAL_BYTES)
                    .callbackTimeout(WORLD_STATE_MAX_CALLBACK_TIMEOUT)
                    .build();

    private StandardCapabilities() {}

    /**
     * Returns the required version 1.0 capability for a bounded world-state export.
     *
     * <p>This capability authorizes only a generated TEXT or JSON diagnostic through the
     * product-owned sink. It never grants filesystem access or permission to traverse
     * {@code saves/}, world files, player data, or another local path.
     *
     * @return immutable required capability declaration
     */
    public static CapabilityRequirement boundedWorldStateExport() {
        return BOUNDED_WORLD_STATE_EXPORT;
    }

    /**
     * Returns the strict version 1.0 product ceilings for a world-state export.
     *
     * <p>A provider may copy these constraints or request tighter positive values. Version 1.0
     * permits at most four artifacts, one MiB per artifact, two MiB in aggregate, and two seconds
     * of callback time. Collection enforces the same ceilings independently.
     *
     * @return immutable maximum constraint set
     */
    public static CollectionConstraints boundedWorldStateExportMaximums() {
        return BOUNDED_WORLD_STATE_EXPORT_MAXIMUMS;
    }
}
