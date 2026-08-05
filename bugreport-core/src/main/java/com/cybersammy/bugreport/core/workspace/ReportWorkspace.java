package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Objects;

/** Trusted handle to one newly created product-owned report workspace. */
public final class ReportWorkspace {
    private final ReportSessionId sessionId;
    private final Path directory;
    private final WorkspaceFileOperations files;
    private final FileStore fileStore;
    private final EntryIdentity directoryIdentity;
    private final EntryIdentity markerIdentity;

    ReportWorkspace(
            ReportSessionId sessionId,
            Path directory,
            WorkspaceFileOperations files,
            FileStore fileStore,
            Object directoryFileKey,
            FileTime directoryCreationTime,
            Object markerFileKey,
            FileTime markerCreationTime) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        Path normalized = Objects.requireNonNull(directory, "directory")
                .toAbsolutePath()
                .normalize();
        if (normalized.getFileName() == null
                || !normalized.getFileName().toString().equals(sessionId.toString())) {
            throw new IllegalArgumentException(
                    "A report workspace directory must use its canonical session ID");
        }
        this.directory = normalized;
        this.files = Objects.requireNonNull(files, "files");
        this.fileStore = Objects.requireNonNull(fileStore, "fileStore");
        directoryIdentity = new EntryIdentity(
                normalized,
                directoryFileKey,
                Objects.requireNonNull(directoryCreationTime, "directoryCreationTime"),
                true);
        markerIdentity = new EntryIdentity(
                normalized.resolve(FileReportWorkspaceStore.MARKER_FILENAME),
                markerFileKey,
                Objects.requireNonNull(markerCreationTime, "markerCreationTime"),
                false);
    }

    /** Returns the report session that exclusively owns this workspace. */
    public ReportSessionId sessionId() {
        return sessionId;
    }

    /** Returns the Core-owned directory; this path must never be exposed to providers. */
    public Path directory() {
        return directory;
    }

    WorkspaceFileOperations files() {
        return files;
    }

    void requireCurrentOwnership() throws IOException {
        requireSameIdentity(directoryIdentity);
        requireSameIdentity(markerIdentity);
        files.verifyPrivateDirectory(directory);
        files.verifyPrivateFile(markerIdentity.path());
        byte[] expectedMarker = FileReportWorkspaceStore.markerContents(sessionId);
        if (!Arrays.equals(
                expectedMarker,
                files.readBounded(markerIdentity.path(), expectedMarker.length))) {
            throw new IOException("Report workspace ownership marker changed");
        }
        if (!fileStore.equals(files.fileStore(directory))
                || !fileStore.equals(files.fileStore(markerIdentity.path()))) {
            throw new IOException("Report workspace crossed a filesystem-store boundary");
        }
    }

    private void requireSameIdentity(EntryIdentity expected) throws IOException {
        BasicFileAttributes noFollow = files.readAttributes(expected.path(), false);
        BasicFileAttributes followed = files.readAttributes(expected.path(), true);
        if (noFollow.isSymbolicLink()
                || noFollow.isOther()
                || noFollow.isDirectory() != expected.directory()
                || noFollow.isRegularFile() == expected.directory()
                || !sameObservedEntry(noFollow, followed)
                || !files.realPath(expected.path(), false)
                        .equals(files.realPath(expected.path(), true))
                || !files.realPath(expected.path(), true).equals(expected.path())
                || !expected.sameIdentity(noFollow)) {
            throw new IOException("Report workspace entry identity changed");
        }
    }

    private static boolean sameObservedEntry(
            BasicFileAttributes noFollow, BasicFileAttributes followed) {
        Object noFollowKey = noFollow.fileKey();
        Object followedKey = followed.fileKey();
        if ((noFollowKey == null) != (followedKey == null)) {
            return false;
        }
        if (noFollowKey != null) {
            return noFollowKey.equals(followedKey);
        }
        return noFollow.isDirectory() == followed.isDirectory()
                && noFollow.isRegularFile() == followed.isRegularFile()
                && noFollow.creationTime().equals(followed.creationTime());
    }

    private record EntryIdentity(
            Path path, Object fileKey, FileTime creationTime, boolean directory) {
        private EntryIdentity {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(creationTime, "creationTime");
        }

        private boolean sameIdentity(BasicFileAttributes current) {
            if (fileKey != null && current.fileKey() != null) {
                return fileKey.equals(current.fileKey());
            }
            if ((fileKey == null) != (current.fileKey() == null)) {
                return false;
            }
            return creationTime.equals(current.creationTime());
        }
    }
}
