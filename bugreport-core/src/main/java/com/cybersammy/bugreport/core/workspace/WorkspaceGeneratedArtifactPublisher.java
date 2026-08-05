package com.cybersammy.bugreport.core.workspace;

import java.io.IOException;
import java.io.Serial;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Atomically publishes private generated artifacts inside one trusted workspace. */
final class WorkspaceGeneratedArtifactPublisher {
    private WorkspaceGeneratedArtifactPublisher() {}

    static PublishedArtifact publish(
            ReportWorkspace workspace, String artifactName, ContentWriter writer)
            throws IOException {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(artifactName, "artifactName");
        Objects.requireNonNull(writer, "writer");
        Path destination = directChild(workspace, artifactName);
        Path temporary = directChild(
                workspace, "." + artifactName + "." + UUID.randomUUID() + ".part");
        List<OwnedEntry> cleanup = new ArrayList<>();
        EntryIdentity temporaryIdentity = null;
        try {
            workspace.requireCurrentOwnership();
            WriteResult result;
            try (FileChannel output = workspace.files().openNewPrivateFile(temporary)) {
                temporaryIdentity = observeRegularFile(temporary, workspace.files());
                cleanup.add(new OwnedEntry(temporary, temporaryIdentity));
                result = Objects.requireNonNull(writer.write(output), "writer result");
                output.force(true);
            }
            workspace.files().verifyPrivateFile(temporary);
            workspace.requireCurrentOwnership();

            EntryIdentity reservationIdentity;
            try {
                try (FileChannel reservation = workspace.files().openNewPrivateFile(destination)) {
                    reservation.force(true);
                }
                reservationIdentity = observeRegularFile(destination, workspace.files());
                cleanup.add(new OwnedEntry(destination, reservationIdentity));
            } catch (FileAlreadyExistsException exception) {
                throw new PublicationException(
                        GeneratedDiagnosticCode.DESTINATION_ALREADY_EXISTS,
                        "A generated workspace artifact already exists",
                        exception);
            }
            if (!reservationIdentity.sameEntry(
                    observeRegularFile(destination, workspace.files()))) {
                throw new PublicationException(
                        GeneratedDiagnosticCode.WORKSPACE_CHANGED,
                        "Generated artifact reservation changed before publication");
            }
            try {
                workspace.files().replaceAtomically(temporary, destination);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new PublicationException(
                        GeneratedDiagnosticCode.ATOMIC_MOVE_UNSUPPORTED,
                        "Workspace does not support atomic generated artifact publication",
                        exception);
            }
            EntryIdentity publishedIdentity =
                    observeRegularFile(destination, workspace.files());
            if (temporaryIdentity == null
                    || !temporaryIdentity.sameUnderlyingEntry(publishedIdentity)) {
                throw new PublicationException(
                        GeneratedDiagnosticCode.WORKSPACE_CHANGED,
                        "Published generated artifact identity changed");
            }
            cleanup.clear();
            cleanup.add(new OwnedEntry(destination, publishedIdentity));
            workspace.files().verifyPrivateFile(destination);
            workspace.requireCurrentOwnership();
            cleanup.clear();
            return new PublishedArtifact(destination, publishedIdentity, result);
        } catch (IOException exception) {
            IOException rollbackFailure = cleanup(workspace, cleanup);
            if (rollbackFailure != null) {
                exception.addSuppressed(rollbackFailure);
                throw new PublicationException(
                        GeneratedDiagnosticCode.ROLLBACK_FAILED,
                        "Generated artifact publication could not be safely rolled back",
                        exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            IOException rollbackFailure = cleanup(workspace, cleanup);
            if (rollbackFailure != null) {
                exception.addSuppressed(rollbackFailure);
                throw new PublicationException(
                        GeneratedDiagnosticCode.ROLLBACK_FAILED,
                        "Generated artifact publication could not be safely rolled back",
                        exception);
            }
            throw exception;
        }
    }

    static void rollback(ReportWorkspace workspace, List<PublishedArtifact> artifacts)
            throws IOException {
        IOException failure = null;
        for (int index = artifacts.size() - 1; index >= 0; index--) {
            PublishedArtifact artifact = artifacts.get(index);
            try {
                if (!artifact.identity().sameEntry(
                        observeRegularFile(artifact.path(), workspace.files()))) {
                    throw new IOException("Generated artifact changed before rollback");
                }
                workspace.files().deleteIfExists(artifact.path());
            } catch (IOException | SecurityException exception) {
                if (failure == null) {
                    failure = new IOException("Could not safely roll back generated artifacts");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static IOException cleanup(
            ReportWorkspace workspace, List<OwnedEntry> entries) {
        IOException failure = null;
        for (int index = entries.size() - 1; index >= 0; index--) {
            OwnedEntry entry = entries.get(index);
            try {
                if (!entry.identity().sameEntry(
                        observeRegularFile(entry.path(), workspace.files()))) {
                    throw new IOException("Generated artifact changed before rollback");
                }
                workspace.files().deleteIfExists(entry.path());
            } catch (IOException | SecurityException cleanupFailure) {
                if (failure == null) {
                    failure = new IOException(
                            "Generated artifact publication could not be safely rolled back");
                }
                failure.addSuppressed(cleanupFailure);
            }
        }
        return failure;
    }

    private static Path directChild(ReportWorkspace workspace, String name) {
        Path path = workspace.directory().resolve(name).normalize();
        if (!workspace.directory().equals(path.getParent())) {
            throw new IllegalArgumentException("Generated artifact escaped its workspace");
        }
        return path;
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
            throw new IOException("Generated workspace artifact is not a safe regular file");
        }
        return new EntryIdentity(realPath, noFollow.fileKey(), noFollow.creationTime());
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

    @FunctionalInterface
    interface ContentWriter {
        WriteResult write(FileChannel output) throws IOException;
    }

    record WriteResult(long byteCount, Sha256Checksum checksum) {
        WriteResult {
            if (byteCount < 0) {
                throw new IllegalArgumentException("Published byte count must be non-negative");
            }
            Objects.requireNonNull(checksum, "checksum");
        }
    }

    record PublishedArtifact(Path path, EntryIdentity identity, WriteResult result) {
        PublishedArtifact {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(result, "result");
        }
    }

    static final class PublicationException extends IOException {
        @Serial private static final long serialVersionUID = 1L;

        private final GeneratedDiagnosticCode code;

        PublicationException(GeneratedDiagnosticCode code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }

        PublicationException(GeneratedDiagnosticCode code, String message, Throwable cause) {
            super(message, cause);
            this.code = Objects.requireNonNull(code, "code");
        }

        GeneratedDiagnosticCode code() {
            return code;
        }
    }

    record EntryIdentity(Path realPath, Object fileKey, FileTime creationTime) {
        EntryIdentity {
            Objects.requireNonNull(realPath, "realPath");
            Objects.requireNonNull(creationTime, "creationTime");
        }

        boolean sameEntry(EntryIdentity other) {
            if (fileKey != null && other.fileKey != null) {
                return fileKey.equals(other.fileKey) && realPath.equals(other.realPath);
            }
            if ((fileKey == null) != (other.fileKey == null)) {
                return false;
            }
            return realPath.equals(other.realPath)
                    && creationTime.equals(other.creationTime);
        }

        boolean sameUnderlyingEntry(EntryIdentity other) {
            if (fileKey != null && other.fileKey != null) {
                return fileKey.equals(other.fileKey);
            }
            if ((fileKey == null) != (other.fileKey == null)) {
                return false;
            }
            return creationTime.equals(other.creationTime);
        }
    }

    private record OwnedEntry(Path path, EntryIdentity identity) {}
}
