package com.cybersammy.bugreport.core.transport;

import java.nio.file.Path;
import java.util.Objects;

/** User-selected local archive destination with no provider-controlled path. */
public final class LocalArchiveDestination implements TransportDestination {
    private final Path path;

    public LocalArchiveDestination(Path path) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    public Path path() {
        return path;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LocalArchiveDestination destination
                && path.equals(destination.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return "LocalArchiveDestination[redacted]";
    }
}
