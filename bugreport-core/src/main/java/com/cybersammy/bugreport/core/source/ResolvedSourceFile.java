package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/** Trusted planning-time view of one regular file below an approved logical root. */
public final class ResolvedSourceFile {
    private final LogicalRoot root;
    private final RelativePath relativePath;
    private final Path localPath;
    private final long observedSize;
    private final FileTime observedLastModified;

    ResolvedSourceFile(
            LogicalRoot root,
            RelativePath relativePath,
            Path localPath,
            long observedSize,
            FileTime observedLastModified) {
        this.root = Objects.requireNonNull(root, "root");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        Path path = Objects.requireNonNull(localPath, "localPath");
        this.observedLastModified =
                Objects.requireNonNull(observedLastModified, "observedLastModified");
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("Resolved source path must be absolute");
        }
        if (observedSize < 0) {
            throw new IllegalArgumentException("Observed source size must be non-negative");
        }
        this.localPath = path.normalize();
        this.observedSize = observedSize;
    }

    /** Returns the approved logical root identity. */
    public LogicalRoot root() {
        return root;
    }

    /** Returns the portable path below the logical root. */
    public RelativePath relativePath() {
        return relativePath;
    }

    /**
     * Returns the local planning-time path for Core collection infrastructure.
     *
     * <p>This value must not be persisted, logged, or exposed to providers. Collection must
     * revalidate it before and while copying bytes.
     */
    public Path localPath() {
        return localPath;
    }

    /** Returns the size observed during planning, not an authorization to allocate or copy it. */
    public long observedSize() {
        return observedSize;
    }

    /** Returns the last-modified value observed during planning. */
    public FileTime observedLastModified() {
        return observedLastModified;
    }
}
