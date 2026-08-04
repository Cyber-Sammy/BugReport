package com.cybersammy.bugreport.core.source;

import java.nio.file.FileStore;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/** Internal trusted planning-time observation of a directory path chain. */
record ResolvedSourceDirectory(
        Path declaredPath,
        Path realPath,
        FileStore fileStore,
        Object fileKey,
        FileTime creationTime) {
    ResolvedSourceDirectory {
        declaredPath = requireAbsolute(declaredPath, "declaredPath");
        realPath = requireAbsolute(realPath, "realPath");
        Objects.requireNonNull(fileStore, "fileStore");
        Objects.requireNonNull(creationTime, "creationTime");
    }

    private static Path requireAbsolute(Path path, String name) {
        Path normalized = Objects.requireNonNull(path, name).normalize();
        if (!normalized.isAbsolute()) {
            throw new IllegalArgumentException(name + " must be absolute");
        }
        return normalized;
    }
}
