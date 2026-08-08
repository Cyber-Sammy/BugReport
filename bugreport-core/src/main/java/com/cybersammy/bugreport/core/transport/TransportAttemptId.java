package com.cybersammy.bugreport.core.transport;

import java.util.Objects;
import java.util.UUID;

/** Opaque identity of one explicitly authorized delivery attempt. */
public record TransportAttemptId(UUID value) {
    public TransportAttemptId {
        Objects.requireNonNull(value, "value");
    }

    static TransportAttemptId create() {
        return new TransportAttemptId(UUID.randomUUID());
    }
}
