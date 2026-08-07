package com.cybersammy.bugreport.core.packaging;

import com.cybersammy.bugreport.core.manifest.ReportManifest;
import com.cybersammy.bugreport.core.workspace.ReviewedWorkspaceSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Factory-issued immutable plan for deterministic package creation. */
public final class ReportPackagePlan {
    private final ReviewedWorkspaceSnapshot snapshot;
    private final ReportManifest manifest;
    private final byte[] manifestDocument;
    private final byte[] markdownDocument;
    private final List<PackagePlanEntry> entries;
    private final long totalUncompressedBytes;

    ReportPackagePlan(
            ReviewedWorkspaceSnapshot snapshot,
            ReportManifest manifest,
            byte[] manifestDocument,
            byte[] markdownDocument,
            List<PackagePlanEntry> entries) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.manifestDocument = Objects.requireNonNull(manifestDocument, "manifestDocument").clone();
        this.markdownDocument = markdownDocument == null ? null : markdownDocument.clone();
        this.entries = canonicalEntries(entries);
        this.totalUncompressedBytes = this.entries.stream()
                .mapToLong(PackagePlanEntry::uncompressedBytes)
                .reduce(0L, Math::addExact);
    }

    public ReviewedWorkspaceSnapshot snapshot() {
        return snapshot;
    }

    public ReportManifest manifest() {
        return manifest;
    }

    public byte[] manifestDocument() {
        return manifestDocument.clone();
    }

    public Optional<byte[]> markdownDocument() {
        return markdownDocument == null
                ? Optional.empty()
                : Optional.of(markdownDocument.clone());
    }

    public List<PackagePlanEntry> entries() {
        return entries;
    }

    public long totalUncompressedBytes() {
        return totalUncompressedBytes;
    }

    private static List<PackagePlanEntry> canonicalEntries(List<PackagePlanEntry> values) {
        List<PackagePlanEntry> ordered = Objects.requireNonNull(values, "entries").stream()
                .map(entry -> Objects.requireNonNull(entry, "package entry"))
                .sorted()
                .toList();
        if (ordered.isEmpty() || ordered.getFirst().kind() != PackagePlanEntryKind.MANIFEST) {
            throw new IllegalArgumentException("A package plan requires manifest.json");
        }
        Set<String> paths = new HashSet<>();
        for (PackagePlanEntry entry : ordered) {
            String folded = entry.archivePath().toLowerCase(Locale.ROOT);
            if (!paths.add(folded)) {
                throw new IllegalArgumentException(
                        "Package archive paths must be case-insensitively unique");
            }
        }
        return ordered;
    }
}
