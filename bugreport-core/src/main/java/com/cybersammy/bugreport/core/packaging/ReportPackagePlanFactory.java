package com.cybersammy.bugreport.core.packaging;

import com.cybersammy.bugreport.core.manifest.ManifestCollectionStatus;
import com.cybersammy.bugreport.core.manifest.ManifestEntry;
import com.cybersammy.bugreport.core.manifest.ManifestEntryProvenance;
import com.cybersammy.bugreport.core.manifest.ManifestTarget;
import com.cybersammy.bugreport.core.manifest.ReportManifest;
import com.cybersammy.bugreport.core.manifest.ReportManifestJsonCodec;
import com.cybersammy.bugreport.core.source.SourceProvenance;
import com.cybersammy.bugreport.core.workspace.CollectedGeneratedArtifact;
import com.cybersammy.bugreport.core.workspace.PreparedWorkspaceArtifact;
import com.cybersammy.bugreport.core.workspace.PreparedWorkspaceSnapshot;
import com.cybersammy.bugreport.core.workspace.ReportWorkspace;
import com.cybersammy.bugreport.core.workspace.ReviewedWorkspaceArtifact;
import com.cybersammy.bugreport.core.workspace.ReviewedWorkspaceSnapshot;
import com.cybersammy.bugreport.core.workspace.ReviewedWorkspaceSnapshotFactory;
import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Binds portable manifest metadata to one exact current reviewed workspace inventory. */
public final class ReportPackagePlanFactory {
    private ReportPackagePlanFactory() {}

    /**
     * Creates a deterministic plan for a future package writer.
     *
     * <p>This operation performs filesystem revalidation and must run off UI and game threads.
     * The returned plan does not itself authorize later reads: the writer must call
     * {@link ReviewedWorkspaceSnapshotFactory#requireCurrent} again immediately before streaming.
     */
    public static ReportPackagePlan create(
            PreparedWorkspaceSnapshot preparedSnapshot,
            ReportWorkspace workspace,
            ReportManifest manifest,
            boolean includeMarkdown) {
        PreparedWorkspaceSnapshot prepared =
                Objects.requireNonNull(preparedSnapshot, "preparedSnapshot");
        ReviewedWorkspaceSnapshot reviewed = prepared.reviewedSnapshot();
        ReportWorkspace trustedWorkspace = Objects.requireNonNull(workspace, "workspace");
        ReportManifest portable = Objects.requireNonNull(manifest, "manifest");
        ReviewedWorkspaceSnapshotFactory.requireCurrent(reviewed, trustedWorkspace);
        validateIdentity(reviewed, portable);
        validateEntries(prepared, portable);

        byte[] manifestBytes = ReportManifestJsonCodec.encode(portable);
        byte[] markdownBytes = includeMarkdown ? ReportMarkdownRenderer.render(portable) : null;
        List<PackagePlanEntry> entries = new ArrayList<>();
        entries.add(inline("manifest.json", PackagePlanEntryKind.MANIFEST, manifestBytes));
        if (markdownBytes != null) {
            entries.add(inline("report.md", PackagePlanEntryKind.MARKDOWN, markdownBytes));
        }
        for (PreparedWorkspaceArtifact preparedArtifact : prepared.artifacts()) {
            ReviewedWorkspaceArtifact artifact = preparedArtifact.artifact();
            entries.add(new PackagePlanEntry(
                    "content/" + artifact.artifactName(),
                    PackagePlanEntryKind.WORKSPACE_ARTIFACT,
                    artifact.byteCount(),
                    artifact.checksum(),
                    java.util.Optional.of(artifact.artifactName())));
        }
        return new ReportPackagePlan(
                prepared, portable, manifestBytes, markdownBytes, entries);
    }

    private static void validateIdentity(
            ReviewedWorkspaceSnapshot snapshot, ReportManifest manifest) {
        if (!snapshot.sessionId().equals(manifest.reportId()) || manifest.target().isEmpty()) {
            throw mismatch(
                    ReportPackagePlanCode.SNAPSHOT_MISMATCH,
                    null,
                    "Manifest identity does not match the reviewed snapshot");
        }
        ManifestTarget target = manifest.target().orElseThrow();
        if (!snapshot.providerId().equals(target.providerId())
                || !snapshot.providerVersion().equals(target.providerVersion())
                || !snapshot.categoryId().equals(target.categoryId())) {
            throw mismatch(
                    ReportPackagePlanCode.SNAPSHOT_MISMATCH,
                    null,
                    "Manifest target does not match the reviewed snapshot");
        }
    }

    private static void validateEntries(
            PreparedWorkspaceSnapshot snapshot, ReportManifest manifest) {
        Map<String, ManifestEntry> manifestEntries = new TreeMap<>();
        manifest.entries().forEach(entry -> manifestEntries.put(entry.archivePath(), entry));
        if (manifestEntries.size() != snapshot.artifacts().size()) {
            throw mismatch(
                    ReportPackagePlanCode.ENTRY_MISMATCH,
                    null,
                    "Manifest and reviewed snapshot entry sets differ");
        }
        for (PreparedWorkspaceArtifact prepared : snapshot.artifacts()) {
            ReviewedWorkspaceArtifact artifact = prepared.artifact();
            ManifestEntry entry = manifestEntries.get("content/" + artifact.artifactName());
            if (entry == null || !matchesArtifact(entry, prepared)) {
                throw mismatch(
                        ReportPackagePlanCode.ENTRY_MISMATCH,
                        artifact.artifactName(),
                        "Manifest entry does not match the reviewed artifact");
            }
        }
    }

    private static boolean matchesArtifact(
            ManifestEntry entry, PreparedWorkspaceArtifact prepared) {
        ReviewedWorkspaceArtifact artifact = prepared.artifact();
        if (entry.uncompressedBytes() != artifact.byteCount()
                || !entry.checksum().equals(artifact.checksum())
                || entry.contentType() != artifact.contentType()
                || entry.effectivePrivacy() != prepared.effectivePrivacy()
                || entry.sanitizationStatus() != prepared.sanitizationStatus()
                || !entry.sanitizationFindings().equals(prepared.sanitizationFindings())
                || entry.qualityRole() != artifact.qualityRole()) {
            return false;
        }
        return switch (artifact) {
            case ReviewedWorkspaceArtifact.Source source ->
                    entry.collectionStatus() == ManifestCollectionStatus.SOURCE_COLLECTED
                            && entry.provenances().equals(source.collected().provenances().stream()
                                    .map(ReportPackagePlanFactory::sourceProvenance)
                                    .sorted()
                                    .toList());
            case ReviewedWorkspaceArtifact.Generated generated ->
                    entry.collectionStatus() == ManifestCollectionStatus.GENERATOR_COMPLETED
                            && entry.provenances().equals(List.of(generatorProvenance(
                                    generated.collected())));
        };
    }

    private static ManifestEntryProvenance sourceProvenance(SourceProvenance source) {
        return ManifestEntryProvenance.source(
                source.providerId(),
                source.providerVersion(),
                source.categoryId(),
                source.sourceId(),
                source.kind(),
                source.privacy());
    }

    private static ManifestEntryProvenance generatorProvenance(
            CollectedGeneratedArtifact generated) {
        return ManifestEntryProvenance.generator(
                generated.providerId(),
                generated.providerVersion(),
                generated.categoryId(),
                generated.generatorId(),
                generated.privacy());
    }

    private static PackagePlanEntry inline(
            String path, PackagePlanEntryKind kind, byte[] contents) {
        return new PackagePlanEntry(
                path,
                kind,
                contents.length,
                checksum(contents),
                java.util.Optional.empty());
    }

    private static Sha256Checksum checksum(byte[] contents) {
        try {
            return new Sha256Checksum(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(contents)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ReportPackagePlanException mismatch(
            ReportPackagePlanCode code, String artifactName, String message) {
        return new ReportPackagePlanException(code, artifactName, message);
    }
}
