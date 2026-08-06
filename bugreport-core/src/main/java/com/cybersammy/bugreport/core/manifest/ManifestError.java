package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.identifier.ProviderId;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** Safe structured failure metadata without exception messages or local paths. */
public record ManifestError(
        String code,
        String component,
        Optional<ProviderId> providerId,
        Optional<String> declarationId)
        implements Comparable<ManifestError> {
    private static final Comparator<ManifestError> ORDER =
            Comparator.comparing(ManifestError::component)
                    .thenComparing(ManifestError::code)
                    .thenComparing(error -> error.providerId().map(ProviderId::value).orElse(""))
                    .thenComparing(error -> error.declarationId().orElse(""));

    public ManifestError {
        code = ManifestContract.requireToken(code, "error code");
        component = ManifestContract.requireToken(component, "error component");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(declarationId, "declarationId");
        declarationId = declarationId.map(
                value -> ManifestContract.requireToken(value, "error declarationId"));
        if (declarationId.isPresent() && providerId.isEmpty()) {
            throw new IllegalArgumentException(
                    "A declaration error requires provider provenance");
        }
    }

    @Override
    public int compareTo(ManifestError other) {
        return ORDER.compare(this, other);
    }
}
