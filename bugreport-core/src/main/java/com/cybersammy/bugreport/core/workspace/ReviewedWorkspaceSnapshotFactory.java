package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.session.ReportSessionSnapshot;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Seals one trusted workspace and binds an explicit reviewed selection to its exact bytes. */
public final class ReviewedWorkspaceSnapshotFactory {
    public static final Duration PRODUCT_MAX_QUIESCENCE_WAIT = Duration.ofSeconds(2);
    private static final int HASH_BUFFER_BYTES = 64 * 1024;

    private ReviewedWorkspaceSnapshotFactory() {}

    /**
     * Creates a terminal reviewed snapshot from successful collection results.
     *
     * <p>The exact included names are an explicit review decision. Excluded collected artifacts
     * remain outside the snapshot and therefore cannot be packaged through this result.
     * Sealing may wait for bounded cleanup and must run off UI and game threads.
     */
    public static ReviewedWorkspaceSnapshot create(
            ReportSessionSnapshot session,
            ReportWorkspace workspace,
            FileCollectionResult files,
            CategoryGeneratedDiagnosticResult generated,
            Set<String> includedArtifactNames) {
        return create(
                session,
                workspace,
                files,
                generated,
                includedArtifactNames,
                PRODUCT_MAX_QUIESCENCE_WAIT);
    }

    /**
     * Revalidates every reviewed byte before a later consumer such as packaging reads it.
     *
     * <p>A successful snapshot closes product-owned mutation authority, but an in-process mod or
     * same-user process is not a filesystem sandbox. Consumers therefore fail closed when the
     * sealed workspace no longer matches the reviewed checksums.
     */
    public static void requireCurrent(
            ReviewedWorkspaceSnapshot snapshot, ReportWorkspace workspace) {
        ReviewedWorkspaceSnapshot reviewed = Objects.requireNonNull(snapshot, "snapshot");
        ReportWorkspace trustedWorkspace = Objects.requireNonNull(workspace, "workspace");
        if (!reviewed.sessionId().equals(trustedWorkspace.sessionId())) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.SESSION_MISMATCH,
                    trustedWorkspace,
                    null,
                    "Reviewed snapshot and workspace identities do not match",
                    null);
        }
        if (!trustedWorkspace.sealed()) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.WORKSPACE_CHANGED,
                    trustedWorkspace,
                    null,
                    "Reviewed snapshot workspace is not sealed",
                    null);
        }
        reviewed.artifacts().forEach(artifact -> verifyArtifact(trustedWorkspace, artifact));
    }

    static ReviewedWorkspaceSnapshot create(
            ReportSessionSnapshot session,
            ReportWorkspace workspace,
            FileCollectionResult files,
            CategoryGeneratedDiagnosticResult generated,
            Set<String> includedArtifactNames,
            Duration quiescenceTimeout) {
        ReportSessionSnapshot trustedSession = Objects.requireNonNull(session, "session");
        ReportWorkspace trustedWorkspace = Objects.requireNonNull(workspace, "workspace");
        FileCollectionResult fileResult = Objects.requireNonNull(files, "files");
        CategoryGeneratedDiagnosticResult generatedResult =
                Objects.requireNonNull(generated, "generated");
        Set<String> included = Set.copyOf(
                Objects.requireNonNull(includedArtifactNames, "includedArtifactNames"));
        if (included.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Included artifact names must not contain null");
        }
        Duration waitLimit = Objects.requireNonNull(quiescenceTimeout, "quiescenceTimeout");
        if (waitLimit.isNegative()
                || waitLimit.isZero()
                || waitLimit.compareTo(PRODUCT_MAX_QUIESCENCE_WAIT) > 0) {
            throw new IllegalArgumentException(
                    "Workspace quiescence timeout must be within the product maximum");
        }

        Identity identity = validateIdentity(
                trustedSession, trustedWorkspace, fileResult, generatedResult);
        TreeMap<String, ReviewedWorkspaceArtifact> collected = collectedArtifacts(
                trustedSession, fileResult, generatedResult);
        for (String selected : included) {
            if (!collected.containsKey(selected)) {
                throw failure(
                        ReviewedWorkspaceSnapshotCode.ARTIFACT_NOT_COLLECTED,
                        trustedWorkspace,
                        selected,
                        "Reviewed selection contains an artifact absent from collection results",
                        null);
            }
        }

        try {
            trustedWorkspace.seal(waitLimit);
        } catch (WorkspaceQuiescenceException exception) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.WORKSPACE_BUSY,
                    trustedWorkspace,
                    null,
                    "Report workspace could not reach a quiescent sealed state",
                    exception);
        }

        verifyWorkspaceInventory(trustedWorkspace, collected.keySet());
        List<ReviewedWorkspaceArtifact> reviewed = included.stream()
                .sorted()
                .map(collected::get)
                .toList();
        reviewed.forEach(artifact -> verifyArtifact(trustedWorkspace, artifact));
        long totalBytes = reviewed.stream()
                .mapToLong(ReviewedWorkspaceArtifact::byteCount)
                .reduce(0L, Math::addExact);
        return new ReviewedWorkspaceSnapshot(
                trustedSession.id(),
                trustedSession.revision(),
                identity.providerId(),
                identity.providerVersion(),
                identity.categoryId(),
                reviewed,
                totalBytes,
                snapshotChecksum(trustedSession, identity, reviewed));
    }

    private static Identity validateIdentity(
            ReportSessionSnapshot session,
            ReportWorkspace workspace,
            FileCollectionResult files,
            CategoryGeneratedDiagnosticResult generated) {
        if (!session.id().equals(workspace.sessionId())) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.SESSION_MISMATCH,
                    workspace,
                    null,
                    "Report session and workspace identities do not match",
                    null);
        }
        if (session.state() != ReportSessionState.REVIEW_REQUIRED) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.INVALID_SESSION_STATE,
                    workspace,
                    null,
                    "A reviewed snapshot requires the REVIEW_REQUIRED session state",
                    null);
        }
        CategoryId categoryId = session.selectedCategory()
                .orElseThrow(() -> failure(
                        ReviewedWorkspaceSnapshotCode.CATEGORY_MISMATCH,
                        workspace,
                        null,
                        "A reviewed snapshot requires a selected category",
                        null))
                .id();
        ProviderId providerId = session.providerSpecification().id();
        ProviderVersion providerVersion = session.providerSpecification().version();
        if (!providerId.equals(files.providerId())
                || !providerVersion.equals(files.providerVersion())
                || !categoryId.equals(files.categoryId())
                || !providerId.equals(generated.providerId())
                || !categoryId.equals(generated.categoryId())) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.CATEGORY_MISMATCH,
                    workspace,
                    null,
                    "Collection results do not belong to the reviewed session category",
                    null);
        }
        return new Identity(providerId, providerVersion, categoryId);
    }

    private static TreeMap<String, ReviewedWorkspaceArtifact> collectedArtifacts(
            ReportSessionSnapshot session,
            FileCollectionResult files,
            CategoryGeneratedDiagnosticResult generated) {
        TreeMap<String, ReviewedWorkspaceArtifact> collected = new TreeMap<>();
        files.outcomes().stream()
                .flatMap(outcome -> outcome.collectedFile().stream())
                .map(ReviewedWorkspaceArtifact.Source::new)
                .forEach(artifact -> {
                    boolean mismatched = artifact.collected().provenances().stream()
                            .anyMatch(provenance -> {
                                DiagnosticSourceSpecification declaration = session
                                        .providerSpecification()
                                        .sources()
                                        .get(provenance.sourceId());
                                return !session.providerSpecification().id()
                                                .equals(provenance.providerId())
                                        || !session.providerSpecification().version()
                                                .equals(provenance.providerVersion())
                                        || !session.selectedCategory()
                                                .orElseThrow()
                                                .id()
                                                .equals(provenance.categoryId())
                                        || !session.selectedCategory()
                                                .orElseThrow()
                                                .sourceIds()
                                                .contains(provenance.sourceId())
                                        || declaration == null
                                        || declaration.kind() != provenance.kind()
                                        || declaration.contentType() != provenance.contentType()
                                        || declaration.privacy() != provenance.privacy()
                                        || declaration.qualityRole() != provenance.qualityRole()
                                        || declaration.inclusionDefault()
                                                != provenance.inclusionDefault();
                            });
                    if (mismatched) {
                        throw failure(
                                ReviewedWorkspaceSnapshotCode.CATEGORY_MISMATCH,
                                session.id(),
                                artifact.artifactName(),
                                "Collected source provenance does not match the session",
                                null);
                    }
                    addCollected(session, collected, artifact);
                });
        generated.outcomes().stream()
                .flatMap(outcome -> outcome.result().stream())
                .flatMap(result -> result.artifacts().stream())
                .map(ReviewedWorkspaceArtifact.Generated::new)
                .forEach(artifact -> {
                    CollectedGeneratedArtifact value = artifact.collected();
                    DiagnosticGeneratorSpecification declaration = session
                            .providerSpecification()
                            .generators()
                            .get(value.generatorId());
                    if (!session.providerSpecification().version().equals(value.providerVersion())
                            || !session.selectedCategory()
                                    .orElseThrow()
                                    .generatorIds()
                                    .contains(value.generatorId())
                            || declaration == null
                            || declaration.contentType() != value.contentType()
                            || declaration.privacy() != value.privacy()
                            || declaration.qualityRole() != value.qualityRole()
                            || declaration.inclusionDefault() != value.inclusionDefault()) {
                        throw failure(
                                ReviewedWorkspaceSnapshotCode.CATEGORY_MISMATCH,
                                session.id(),
                                value.artifactName(),
                                "Generated artifact provider version does not match the session",
                                null);
                    }
                    addCollected(session, collected, artifact);
                });
        return collected;
    }

    private static void addCollected(
            ReportSessionSnapshot session,
            TreeMap<String, ReviewedWorkspaceArtifact> collected,
            ReviewedWorkspaceArtifact artifact) {
        if (collected.putIfAbsent(artifact.artifactName(), artifact) != null) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.DUPLICATE_ARTIFACT,
                    session.id(),
                    artifact.artifactName(),
                    "Collection results contain a duplicate workspace artifact",
                    null);
        }
    }

    private static void verifyWorkspaceInventory(
            ReportWorkspace workspace, Set<String> collectedNames) {
        try {
            workspace.requireCurrentOwnership();
            int expectedEntries = Math.addExact(collectedNames.size(), 1);
            List<Path> children = workspace.files()
                    .listDirectChildren(workspace.directory(), Math.addExact(expectedEntries, 1));
            Set<String> actual = new HashSet<>();
            for (Path child : children) {
                if (!workspace.directory().equals(child.normalize().getParent())) {
                    throw new IOException("Workspace enumeration escaped its direct children");
                }
                actual.add(child.getFileName().toString());
            }
            Set<String> expected = new HashSet<>(collectedNames);
            expected.add(FileReportWorkspaceStore.MARKER_FILENAME);
            if (actual.size() != children.size() || !actual.equals(expected)) {
                throw failure(
                        ReviewedWorkspaceSnapshotCode.WORKSPACE_CHANGED,
                        workspace,
                        null,
                        "Workspace entries do not match successful collection results",
                        null);
            }
        } catch (ReviewedWorkspaceSnapshotException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.WORKSPACE_CHANGED,
                    workspace,
                    null,
                    "Workspace inventory could not be safely verified",
                    exception);
        }
    }

    private static void verifyArtifact(
            ReportWorkspace workspace, ReviewedWorkspaceArtifact artifact) {
        Path path = workspace.directory().resolve(artifact.artifactName()).normalize();
        if (!workspace.directory().equals(path.getParent())) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.ARTIFACT_CHANGED,
                    workspace,
                    artifact.artifactName(),
                    "Reviewed artifact escaped its workspace",
                    null);
        }
        try {
            workspace.requireCurrentOwnership();
            ObservedArtifact before = observe(path, workspace);
            if (before.size() != artifact.byteCount()) {
                throw changed(workspace, artifact.artifactName());
            }
            Sha256Checksum checksum = checksum(path, workspace, artifact.byteCount());
            ObservedArtifact after = observe(path, workspace);
            if (!before.sameEntry(after)
                    || after.size() != artifact.byteCount()
                    || !checksum.equals(artifact.checksum())) {
                throw changed(workspace, artifact.artifactName());
            }
            workspace.requireCurrentOwnership();
        } catch (ReviewedWorkspaceSnapshotException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw failure(
                    ReviewedWorkspaceSnapshotCode.ARTIFACT_CHANGED,
                    workspace,
                    artifact.artifactName(),
                    "Reviewed artifact could not be safely verified",
                    exception);
        }
    }

    private static ObservedArtifact observe(Path path, ReportWorkspace workspace)
            throws IOException {
        BasicFileAttributes noFollow = workspace.files().readAttributes(path, false);
        BasicFileAttributes followed = workspace.files().readAttributes(path, true);
        Path realPath = workspace.files().realPath(path, true);
        if (!noFollow.isRegularFile()
                || noFollow.isSymbolicLink()
                || noFollow.isOther()
                || !sameObservedEntry(noFollow, followed)
                || !workspace.files().realPath(path, false).equals(realPath)
                || !path.equals(realPath)) {
            throw new IOException("Reviewed workspace artifact is not a safe regular file");
        }
        workspace.files().verifyPrivateFile(path);
        return new ObservedArtifact(
                realPath,
                noFollow.fileKey(),
                noFollow.creationTime().toMillis(),
                noFollow.size());
    }

    private static Sha256Checksum checksum(
            Path path, ReportWorkspace workspace, long expectedBytes) throws IOException {
        MessageDigest digest = sha256();
        ByteBuffer buffer = ByteBuffer.allocate(HASH_BUFFER_BYTES);
        long readBytes = 0;
        try (FileChannel input = workspace.files().openExistingPrivateFile(path)) {
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                if (read > expectedBytes - readBytes) {
                    throw new IOException("Reviewed workspace artifact exceeded its recorded size");
                }
                readBytes += read;
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        if (readBytes != expectedBytes) {
            throw new IOException("Reviewed workspace artifact size changed while hashing");
        }
        return new Sha256Checksum(HexFormat.of().formatHex(digest.digest()));
    }

    private static Sha256Checksum snapshotChecksum(
            ReportSessionSnapshot session,
            Identity identity,
            List<ReviewedWorkspaceArtifact> artifacts) {
        MessageDigest digest = sha256();
        update(digest, session.id().toString());
        update(digest, Long.toString(session.revision()));
        update(digest, identity.providerId().value());
        update(digest, identity.providerVersion().value());
        update(digest, identity.categoryId().value());
        for (ReviewedWorkspaceArtifact artifact : artifacts) {
            update(digest, artifact instanceof ReviewedWorkspaceArtifact.Source
                    ? "source"
                    : "generated");
            update(digest, artifact.artifactName());
            update(digest, Long.toString(artifact.byteCount()));
            update(digest, artifact.checksum().value());
            update(digest, artifact.contentType().name());
            update(digest, artifact.privacy().name());
            update(digest, artifact.qualityRole().name());
            switch (artifact) {
                case ReviewedWorkspaceArtifact.Source source -> {
                    update(digest, source.collected().inclusionDefault().name());
                    source.collected().provenances().forEach(provenance -> {
                        update(digest, provenance.providerId().value());
                        update(digest, provenance.providerVersion().value());
                        update(digest, provenance.categoryId().value());
                        update(digest, provenance.sourceId().value());
                        update(digest, provenance.kind().name());
                        update(digest, provenance.contentType().name());
                        update(digest, provenance.privacy().name());
                        update(digest, provenance.qualityRole().name());
                        update(digest, provenance.inclusionDefault().name());
                    });
                }
                case ReviewedWorkspaceArtifact.Generated generated -> {
                    CollectedGeneratedArtifact value = generated.collected();
                    update(digest, value.inclusionDefault().name());
                    update(digest, value.artifactId().value());
                    update(digest, value.providerId().value());
                    update(digest, value.providerVersion().value());
                    update(digest, value.categoryId().value());
                    update(digest, value.generatorId().value());
                }
            }
        }
        return new Sha256Checksum(HexFormat.of().formatHex(digest.digest()));
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static boolean sameObservedEntry(
            BasicFileAttributes first, BasicFileAttributes second) {
        Object firstKey = first.fileKey();
        Object secondKey = second.fileKey();
        if ((firstKey == null) != (secondKey == null)) {
            return false;
        }
        return firstKey != null
                ? firstKey.equals(secondKey)
                : first.isRegularFile() == second.isRegularFile()
                        && first.creationTime().equals(second.creationTime());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", exception);
        }
    }

    private static ReviewedWorkspaceSnapshotException changed(
            ReportWorkspace workspace, String artifactName) {
        return failure(
                ReviewedWorkspaceSnapshotCode.ARTIFACT_CHANGED,
                workspace,
                artifactName,
                "Reviewed artifact bytes or identity changed",
                null);
    }

    private static ReviewedWorkspaceSnapshotException failure(
            ReviewedWorkspaceSnapshotCode code,
            ReportWorkspace workspace,
            String artifactName,
            String message,
            Throwable cause) {
        return failure(code, workspace.sessionId(), artifactName, message, cause);
    }

    private static ReviewedWorkspaceSnapshotException failure(
            ReviewedWorkspaceSnapshotCode code,
            com.cybersammy.bugreport.core.session.ReportSessionId sessionId,
            String artifactName,
            String message,
            Throwable cause) {
        return new ReviewedWorkspaceSnapshotException(
                code, sessionId, artifactName, message, cause);
    }

    private record Identity(
            ProviderId providerId, ProviderVersion providerVersion, CategoryId categoryId) {}

    private record ObservedArtifact(
            Path realPath, Object fileKey, long creationMillis, long size) {
        private boolean sameEntry(ObservedArtifact other) {
            if (fileKey != null && other.fileKey != null) {
                return fileKey.equals(other.fileKey) && realPath.equals(other.realPath);
            }
            if ((fileKey == null) != (other.fileKey == null)) {
                return false;
            }
            return realPath.equals(other.realPath)
                    && creationMillis == other.creationMillis
                    && size == other.size;
        }
    }
}
