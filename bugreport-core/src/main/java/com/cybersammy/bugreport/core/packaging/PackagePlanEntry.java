package com.cybersammy.bugreport.core.packaging;

import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Canonical path and exact byte identity for one future archive entry. */
public record PackagePlanEntry(
        String archivePath,
        PackagePlanEntryKind kind,
        long uncompressedBytes,
        Sha256Checksum checksum,
        Optional<String> workspaceArtifactName)
        implements Comparable<PackagePlanEntry> {
    private static final Pattern CONTENT_PATH =
            Pattern.compile("content/[a-z0-9][a-z0-9._-]{0,159}");

    public PackagePlanEntry {
        Objects.requireNonNull(archivePath, "archivePath");
        Objects.requireNonNull(kind, "kind");
        if (uncompressedBytes < 0) {
            throw new IllegalArgumentException("Package entry size must be non-negative");
        }
        Objects.requireNonNull(checksum, "checksum");
        Objects.requireNonNull(workspaceArtifactName, "workspaceArtifactName");
        validateIdentity(archivePath, kind, workspaceArtifactName);
    }

    @Override
    public int compareTo(PackagePlanEntry other) {
        int kindOrder = Integer.compare(kind.ordinal(), other.kind.ordinal());
        return kindOrder != 0 ? kindOrder : archivePath.compareTo(other.archivePath);
    }

    private static void validateIdentity(
            String path, PackagePlanEntryKind kind, Optional<String> artifactName) {
        switch (kind) {
            case MANIFEST -> requireInline(path, "manifest.json", artifactName);
            case MARKDOWN -> requireInline(path, "report.md", artifactName);
            case WORKSPACE_ARTIFACT -> {
                if (!CONTENT_PATH.matcher(path).matches()
                        || artifactName.isEmpty()
                        || !path.equals("content/" + artifactName.orElseThrow())) {
                    throw new IllegalArgumentException(
                            "Workspace package entry identity is not canonical");
                }
            }
        }
    }

    private static void requireInline(
            String path, String expectedPath, Optional<String> artifactName) {
        if (!expectedPath.equals(path) || artifactName.isPresent()) {
            throw new IllegalArgumentException("Inline package entry identity is not canonical");
        }
    }
}
