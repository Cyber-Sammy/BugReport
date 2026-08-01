package com.cybersammy.bugreport.api;

import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import java.util.Optional;

/**
 * Loader-neutral contract implemented by Bug Report providers.
 *
 * <p>A provider declared for runtime discovery must be a public concrete class
 * with a public no-argument constructor. Construction must be cheap and
 * side-effect free: it must not register events or perform file or network I/O.
 * The provider class, its static initialization, and its constructor must be
 * safe to load on both a physical client and a dedicated server. Expensive or
 * side-specific diagnostics belong in callbacks invoked after discovery.
 */
public interface BugReportProvider {
    /**
     * Returns the stable provider identifier.
     *
     * <p>A mod's default provider uses its declaring NeoForge mod ID. Additional
     * providers use {@code <mod_id>:<local_name>}. Both components use
     * lowercase ASCII letters, digits, and underscores, start with a letter,
     * and contain at most 64 characters. The namespace must equal the declaring
     * mod ID. {@link ProviderId} provides the canonical typed representation;
     * this spike-era method retains its string return type for binary
     * compatibility.
     *
     * @return the stable provider identifier
     */
    String providerId();

    /**
     * Returns the provider version when it is available.
     *
     * @return the provider version, or {@code "unknown"} when it is unavailable
     */
    default String providerVersion() {
        return "unknown";
    }

    /**
     * Returns the immutable declarative reporting specification when this
     * provider implements the M1 contract.
     *
     * <p>The default preserves binary compatibility with M0 providers compiled
     * before specification contracts existed. A future production registry
     * diagnoses an empty value as an unsupported legacy provider; it does not
     * invent collection authority or user-facing metadata.
     *
     * @return provider specification, or empty for a legacy M0 provider
     */
    default Optional<ProviderSpecification> specification() {
        return Optional.empty();
    }
}
