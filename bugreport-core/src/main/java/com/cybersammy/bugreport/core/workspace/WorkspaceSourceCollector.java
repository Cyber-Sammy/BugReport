package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.PlannedSourceFile;
import com.cybersammy.bugreport.core.source.ResolvedSourceFile;
import com.cybersammy.bugreport.core.source.SourceFileObservations;
import com.cybersammy.bugreport.core.source.SourcePathResolutionException;
import com.cybersammy.bugreport.core.source.SourcePathResolver;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Revalidates and streams one planned source into its isolated report workspace. */
public final class WorkspaceSourceCollector {
    /** Fixed bounded transfer buffer; source files are never loaded fully into memory. */
    public static final int COPY_BUFFER_BYTES = 64 * 1024;

    private static final String ARTIFACT_PREFIX = "source-";
    private static final String ARTIFACT_SUFFIX = ".data";

    private WorkspaceSourceCollector() {}

    /** Copies one trusted plan with its effective byte ceiling and computes SHA-256 in one pass. */
    public static CollectedSourceFile collect(
            PlannedSourceFile planned,
            ApprovedSourceRoots roots,
            ReportWorkspace workspace) {
        return collect(planned, roots, workspace, copiedBytes -> {});
    }

    static CollectedSourceFile collect(
            PlannedSourceFile planned,
            ApprovedSourceRoots roots,
            ReportWorkspace workspace,
            CopyChunkHook hook) {
        PlannedSourceFile source = Objects.requireNonNull(planned, "planned");
        ApprovedSourceRoots approvedRoots = Objects.requireNonNull(roots, "roots");
        ReportWorkspace destinationWorkspace = Objects.requireNonNull(workspace, "workspace");
        CopyChunkHook chunkHook = Objects.requireNonNull(hook, "hook");
        ResolvedSourceFile plannedFile = source.file();
        String artifactName = artifactName(source);
        Path destination = destinationWorkspace.directory().resolve(artifactName).normalize();
        Path temporary = destinationWorkspace.directory()
                .resolve("." + artifactName + "." + UUID.randomUUID() + ".part")
                .normalize();
        requireDirectChild(destinationWorkspace, destination);
        requireDirectChild(destinationWorkspace, temporary);

        List<CleanupEntry> cleanupEntries = new ArrayList<>();
        EntryIdentity copiedIdentity = null;
        try {
            requireWorkspace(destinationWorkspace, source);
            ResolvedSourceFile before = resolveCurrent(approvedRoots, plannedFile, source, destinationWorkspace);
            if (!SourceFileObservations.sameSnapshot(plannedFile, before)) {
                throw failure(
                        SourceCopyCode.SOURCE_CHANGED,
                        source,
                        destinationWorkspace,
                        "Source changed after planning");
            }

            CopyResult copied;
            try (FileChannel input = openSource(before.localPath());
                    FileChannel output = destinationWorkspace.files().openNewPrivateFile(temporary)) {
                copiedIdentity = observeRegularFile(temporary, destinationWorkspace.files());
                cleanupEntries.add(new CleanupEntry(temporary, copiedIdentity));
                ResolvedSourceFile opened = resolveCurrent(
                        approvedRoots, plannedFile, source, destinationWorkspace);
                if (!SourceFileObservations.sameSnapshot(before, opened)) {
                    throw failure(
                            SourceCopyCode.SOURCE_CHANGED,
                            source,
                            destinationWorkspace,
                            "Source changed while it was opened");
                }
                copied = copyBounded(input, output, source.maximumBytes(), chunkHook, source, destinationWorkspace);
                output.force(true);
            }

            ResolvedSourceFile after = resolveCurrent(
                    approvedRoots, plannedFile, source, destinationWorkspace);
            if (!SourceFileObservations.sameSnapshot(before, after)
                    || copied.byteCount() != after.observedSize()) {
                throw failure(
                        SourceCopyCode.SOURCE_CHANGED,
                        source,
                        destinationWorkspace,
                        "Source changed while it was copied");
            }
            requireWorkspace(destinationWorkspace, source);
            EntryIdentity reservationIdentity;
            try {
                try (FileChannel reservation =
                        destinationWorkspace.files().openNewPrivateFile(destination)) {
                    // CREATE_NEW reserves this exact destination without publishing partial bytes.
                    reservation.force(true);
                }
                reservationIdentity = observeRegularFile(
                        destination, destinationWorkspace.files());
                cleanupEntries.add(new CleanupEntry(destination, reservationIdentity));
            } catch (FileAlreadyExistsException exception) {
                throw failure(
                        SourceCopyCode.DESTINATION_ALREADY_EXISTS,
                        source,
                        destinationWorkspace,
                        "A collected artifact already exists for this source",
                        exception);
            }
            if (!reservationIdentity.sameEntry(
                    observeRegularFile(destination, destinationWorkspace.files()))) {
                throw failure(
                        SourceCopyCode.WORKSPACE_CHANGED,
                        source,
                        destinationWorkspace,
                        "Workspace artifact reservation changed before publication");
            }
            try {
                destinationWorkspace.files().replaceAtomically(temporary, destination);
            } catch (AtomicMoveNotSupportedException exception) {
                throw failure(
                        SourceCopyCode.ATOMIC_MOVE_UNSUPPORTED,
                        source,
                        destinationWorkspace,
                        "Workspace does not support atomic artifact publication",
                        exception);
            }
            EntryIdentity publishedIdentity = observeRegularFile(
                    destination, destinationWorkspace.files());
            if (copiedIdentity == null
                    || !copiedIdentity.sameUnderlyingEntry(publishedIdentity)) {
                throw new IOException(
                        "Published workspace artifact identity does not match its temporary file");
            }
            cleanupEntries.clear();
            cleanupEntries.add(new CleanupEntry(destination, publishedIdentity));
            destinationWorkspace.files().verifyPrivateFile(destination);
            requireWorkspace(destinationWorkspace, source);

            CollectedSourceFile result = new CollectedSourceFile(
                    artifactName,
                    copied.byteCount(),
                    copied.checksum(),
                    source.provenances(),
                    source.contentType(),
                    source.privacy(),
                    source.qualityRole(),
                    source.inclusionDefault());
            cleanupEntries.clear();
            return result;
        } catch (SourceCopyException exception) {
            throw cleanupOrPreserve(
                    cleanupEntries,
                    source,
                    destinationWorkspace,
                    exception);
        } catch (IOException | SecurityException exception) {
            SourceCopyException failure = failure(
                    SourceCopyCode.IO_FAILURE,
                    source,
                    destinationWorkspace,
                    "Could not stream planned source into report workspace",
                    exception);
            throw cleanupOrPreserve(
                    cleanupEntries,
                    source,
                    destinationWorkspace,
                    failure);
        }
    }

    private static CopyResult copyBounded(
            FileChannel input,
            FileChannel output,
            long maximumBytes,
            CopyChunkHook hook,
            PlannedSourceFile source,
            ReportWorkspace workspace)
            throws IOException {
        MessageDigest digest = sha256();
        ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
        long copied = 0;
        while (true) {
            int read = input.read(buffer);
            if (read < 0) {
                break;
            }
            if (read == 0) {
                continue;
            }
            if (read > maximumBytes - copied) {
                throw failure(
                        SourceCopyCode.BYTE_LIMIT_EXCEEDED,
                        source,
                        workspace,
                        "Source exceeded its effective byte ceiling during streaming");
            }
            copied += read;
            buffer.flip();
            digest.update(buffer.asReadOnlyBuffer());
            while (buffer.hasRemaining()) {
                output.write(buffer);
            }
            buffer.clear();
            hook.afterChunk(copied);
        }
        return new CopyResult(copied, new Sha256Checksum(HexFormat.of().formatHex(digest.digest())));
    }

    private static FileChannel openSource(Path path) throws IOException {
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        return FileChannel.open(path, options);
    }

    private static ResolvedSourceFile resolveCurrent(
            ApprovedSourceRoots roots,
            ResolvedSourceFile planned,
            PlannedSourceFile source,
            ReportWorkspace workspace) {
        try {
            return SourcePathResolver.resolveRegularFile(
                    roots, planned.root(), planned.relativePath());
        } catch (SourcePathResolutionException exception) {
            throw failure(
                    SourceCopyCode.SOURCE_UNSAFE,
                    source,
                    workspace,
                    "Source could not be safely revalidated",
                    exception);
        }
    }

    private static void requireWorkspace(
            ReportWorkspace workspace, PlannedSourceFile source) {
        try {
            workspace.requireCurrentOwnership();
        } catch (IOException | SecurityException exception) {
            throw failure(
                    SourceCopyCode.WORKSPACE_CHANGED,
                    source,
                    workspace,
                    "Report workspace ownership or identity changed",
                    exception);
        }
    }

    private static EntryIdentity observeRegularFile(
            Path path, WorkspaceFileOperations files) throws IOException {
        BasicFileAttributes noFollow = files.readAttributes(path, false);
        BasicFileAttributes followed = files.readAttributes(path, true);
        Path realPath = files.realPath(path, true);
        if (!noFollow.isRegularFile()
                || noFollow.isSymbolicLink()
                || noFollow.isOther()
                || !sameObservedEntry(noFollow, followed)
                || !files.realPath(path, false).equals(realPath)) {
            throw new IOException("Workspace artifact is not a safe regular file");
        }
        return new EntryIdentity(realPath, noFollow.fileKey(), noFollow.creationTime());
    }

    private static boolean sameObservedEntry(
            BasicFileAttributes noFollow, BasicFileAttributes followed) {
        Object firstKey = noFollow.fileKey();
        Object secondKey = followed.fileKey();
        if ((firstKey == null) != (secondKey == null)) {
            return false;
        }
        if (firstKey != null) {
            return firstKey.equals(secondKey);
        }
        return noFollow.isRegularFile() == followed.isRegularFile()
                && noFollow.creationTime().equals(followed.creationTime());
    }

    private static SourceCopyException cleanupOrPreserve(
            List<CleanupEntry> entries,
            PlannedSourceFile source,
            ReportWorkspace workspace,
            SourceCopyException original) {
        if (entries.isEmpty()) {
            return original;
        }
        List<IOException> cleanupFailures = new ArrayList<>();
        for (int index = entries.size() - 1; index >= 0; index--) {
            CleanupEntry entry = entries.get(index);
            try {
                if (!entry.identity().sameEntry(
                        observeRegularFile(entry.path(), workspace.files()))) {
                    throw new IOException("Workspace artifact changed before rollback");
                }
                workspace.files().deleteIfExists(entry.path());
            } catch (IOException | SecurityException cleanupFailure) {
                cleanupFailures.add(new IOException(
                        "Could not safely remove incomplete workspace artifact",
                        cleanupFailure));
            }
        }
        if (cleanupFailures.isEmpty()) {
            return original;
        }
        cleanupFailures.forEach(original::addSuppressed);
        return failure(
                SourceCopyCode.ROLLBACK_FAILED,
                source,
                workspace,
                "Incomplete workspace artifact could not be safely removed",
                original);
    }

    private static void requireDirectChild(ReportWorkspace workspace, Path path) {
        if (!workspace.directory().equals(path.getParent())) {
            throw new IllegalArgumentException("Workspace artifact path escaped its session directory");
        }
    }

    private static String artifactName(PlannedSourceFile source) {
        MessageDigest digest = sha256();
        ResolvedSourceFile file = source.file();
        updateUtf8(digest, file.root().name());
        digest.update((byte) 0);
        updateUtf8(digest, file.relativePath().value());
        return ARTIFACT_PREFIX + HexFormat.of().formatHex(digest.digest()) + ARTIFACT_SUFFIX;
    }

    private static void updateUtf8(MessageDigest digest, String value) {
        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", exception);
        }
    }

    private static SourceCopyException failure(
            SourceCopyCode code,
            PlannedSourceFile source,
            ReportWorkspace workspace,
            String message) {
        return new SourceCopyException(
                code,
                workspace.sessionId(),
                source.file().root(),
                source.file().relativePath(),
                message);
    }

    private static SourceCopyException failure(
            SourceCopyCode code,
            PlannedSourceFile source,
            ReportWorkspace workspace,
            String message,
            Throwable cause) {
        return new SourceCopyException(
                code,
                workspace.sessionId(),
                source.file().root(),
                source.file().relativePath(),
                message,
                cause);
    }

    @FunctionalInterface
    interface CopyChunkHook {
        void afterChunk(long copiedBytes) throws IOException;
    }

    private record CopyResult(long byteCount, Sha256Checksum checksum) {}

    private record EntryIdentity(Path realPath, Object fileKey, FileTime creationTime) {
        private boolean sameEntry(EntryIdentity other) {
            if (fileKey != null && other.fileKey != null) {
                return fileKey.equals(other.fileKey);
            }
            if ((fileKey == null) != (other.fileKey == null)) {
                return false;
            }
            return realPath.equals(other.realPath)
                    && creationTime.equals(other.creationTime);
        }

        private boolean sameUnderlyingEntry(EntryIdentity other) {
            if (fileKey != null && other.fileKey != null) {
                return fileKey.equals(other.fileKey);
            }
            if ((fileKey == null) != (other.fileKey == null)) {
                return false;
            }
            return creationTime.equals(other.creationTime);
        }
    }

    private record CleanupEntry(Path path, EntryIdentity identity) {}
}
