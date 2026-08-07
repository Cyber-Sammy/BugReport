package com.cybersammy.bugreport.core.transport;

import com.cybersammy.bugreport.api.identifier.TransportId;
import com.cybersammy.bugreport.api.specification.SupportDestinationType;
import java.util.Objects;
import java.util.Set;

/** Immutable capability declaration for a restricted first-party transport. */
public record ReportTransportDescriptor(
        TransportId id,
        Set<SupportDestinationType> supportedDestinations,
        boolean networkAccess,
        boolean authenticationRequired,
        boolean retrySupported) {
    public ReportTransportDescriptor {
        Objects.requireNonNull(id, "id");
        Set<SupportDestinationType> destinations =
                Objects.requireNonNull(supportedDestinations, "supportedDestinations");
        if (destinations.isEmpty() || destinations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Transport requires supported destinations");
        }
        supportedDestinations = Set.copyOf(destinations);
    }
}
