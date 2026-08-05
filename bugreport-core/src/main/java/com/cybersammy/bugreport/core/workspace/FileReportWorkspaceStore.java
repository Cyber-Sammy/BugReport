package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Creates isolated report workspaces below one product-owned local directory. */
public final class FileReportWorkspaceStore {
    static final String MARKER_FILENAME = ".bugreport-workspace";
    static final int MARKER_SCHEMA_VERSION = 1;

    private static final String MARKER_MAGIC = "bugreport-workspace";

    private final Path root;
    private final WorkspaceFileOperations files;

    /** Binds workspace creation to one absolute product-owned directory without touching it. */
    public FileReportWorkspaceStore(Path root) {
        this(root, NioWorkspaceFileOperations.INSTANCE);
    }

    FileReportWorkspaceStore(Path root, WorkspaceFileOperations files) {
        Path supplied = Objects.requireNonNull(root, "root");
        if (!supplied.isAbsolute()) {
            throw new IllegalArgumentException("Report workspace root must be absolute");
        }
        this.root = supplied.normalize();
        this.files = Objects.requireNonNull(files, "files");
    }

    /**
     * Atomically claims and initializes a new workspace for one report session.
     *
     * <p>An existing session path is never opened or reused, even when it contains a plausible
     * ownership marker. Provider code receives neither this store nor the returned local path.
     */
    public synchronized ReportWorkspace create(ReportSessionId sessionId) {
        ReportSessionId id = Objects.requireNonNull(sessionId, "sessionId");
        ObservedDirectory rootObservation = ensureSafeRoot(id);
        Path workspace = root.resolve(id.toString()).normalize();
        if (!root.equals(workspace.getParent())) {
            throw failure(
                    WorkspaceCreationCode.ROOT_INVALID,
                    id,
                    "Report workspace path escaped its configured root");
        }

        boolean workspaceCreated = false;
        boolean markerCreated = false;
        ObservedDirectory workspaceObservation = null;
        ObservedFile markerObservation = null;
        Path marker = workspace.resolve(MARKER_FILENAME);
        try {
            files.createDirectory(workspace);
            workspaceCreated = true;
            workspaceObservation = inspectDirectory(workspace);
            requireSameStore(rootObservation, workspaceObservation);
            requireContained(rootObservation, workspaceObservation);

            byte[] markerBytes = markerContents(id);
            files.writeNewMarker(marker, markerBytes);
            markerCreated = true;
            markerObservation = inspectMarker(
                    marker, rootObservation.fileStore(), markerBytes);

            ObservedDirectory currentRoot = inspectSafeRoot(false);
            ObservedDirectory currentWorkspace = inspectDirectory(workspace);
            if (!sameIdentity(rootObservation, currentRoot)
                    || !sameIdentity(workspaceObservation, currentWorkspace)) {
                throw new UnsafeWorkspacePathException(
                        "Workspace path changed while it was being initialized");
            }
            requireSameStore(currentRoot, currentWorkspace);
            requireContained(currentRoot, currentWorkspace);
            ObservedFile currentMarker = inspectMarker(
                    marker, currentRoot.fileStore(), markerBytes);
            if (!sameIdentity(markerObservation, currentMarker)) {
                throw new UnsafeWorkspacePathException(
                        "Workspace marker changed while it was being initialized");
            }
            return new ReportWorkspace(id, currentWorkspace.realPath());
        } catch (FileAlreadyExistsException exception) {
            if (!workspaceCreated) {
                throw failure(
                        WorkspaceCreationCode.WORKSPACE_ALREADY_EXISTS,
                        id,
                        "A report workspace already exists for this session",
                        exception);
            }
            throw rollbackFailure(
                    id,
                    workspace,
                    marker,
                    workspaceCreated,
                    markerCreated,
                    workspaceObservation,
                    markerObservation,
                    "A workspace marker already existed during initialization",
                    exception);
        } catch (UnsafeWorkspacePathException | SecurityException exception) {
            throw rollbackFailure(
                    id,
                    workspace,
                    marker,
                    workspaceCreated,
                    markerCreated,
                    workspaceObservation,
                    markerObservation,
                    "Report workspace failed filesystem safety validation",
                    exception);
        } catch (IOException exception) {
            throw rollbackFailure(
                    id,
                    workspace,
                    marker,
                    workspaceCreated,
                    markerCreated,
                    workspaceObservation,
                    markerObservation,
                    "Could not initialize report workspace",
                    exception);
        }
    }

    private ObservedDirectory ensureSafeRoot(ReportSessionId sessionId) {
        try {
            return inspectSafeRoot(true);
        } catch (UnsafeWorkspacePathException | SecurityException exception) {
            throw failure(
                    WorkspaceCreationCode.ROOT_INVALID,
                    sessionId,
                    "Report workspace root failed filesystem safety validation",
                    exception);
        } catch (IOException exception) {
            throw failure(
                    WorkspaceCreationCode.IO_FAILURE,
                    sessionId,
                    "Could not prepare report workspace root",
                    exception);
        }
    }

    private ObservedDirectory inspectSafeRoot(boolean createMissing)
            throws IOException, UnsafeWorkspacePathException {
        Path filesystemRoot = root.getRoot();
        if (filesystemRoot == null) {
            throw new UnsafeWorkspacePathException("Workspace root is not absolute");
        }
        ObservedDirectory currentObservation = inspectDirectory(filesystemRoot);
        Path current = filesystemRoot;
        for (Path segment : filesystemRoot.relativize(root)) {
            current = current.resolve(segment);
            try {
                currentObservation = inspectDirectory(current);
            } catch (NoSuchFileException exception) {
                if (!createMissing) {
                    throw exception;
                }
                try {
                    files.createDirectory(current);
                } catch (FileAlreadyExistsException race) {
                    // A concurrent creator is acceptable only if the resulting entry is safe.
                }
                currentObservation = inspectDirectory(current);
            }
        }
        return currentObservation;
    }

    private ObservedDirectory inspectDirectory(Path path)
            throws IOException, UnsafeWorkspacePathException {
        BasicFileAttributes noFollow = files.readAttributes(path, false);
        if (!noFollow.isDirectory() || noFollow.isSymbolicLink() || noFollow.isOther()) {
            throw new UnsafeWorkspacePathException("Workspace path is not a real directory");
        }
        BasicFileAttributes followed = files.readAttributes(path, true);
        Path noFollowReal = files.realPath(path, false);
        Path followedReal = files.realPath(path, true);
        if (!sameObservedEntry(noFollow, followed)
                || !noFollowReal.equals(followedReal)) {
            throw new UnsafeWorkspacePathException("Workspace path crosses a redirection");
        }
        return new ObservedDirectory(
                path,
                followedReal,
                files.fileStore(path),
                noFollow.fileKey(),
                noFollow.creationTime());
    }

    private ObservedFile inspectMarker(Path marker, FileStore rootStore, byte[] expectedContents)
            throws IOException, UnsafeWorkspacePathException {
        BasicFileAttributes noFollow = files.readAttributes(marker, false);
        BasicFileAttributes followed = files.readAttributes(marker, true);
        if (!noFollow.isRegularFile()
                || noFollow.isSymbolicLink()
                || noFollow.isOther()
                || noFollow.size() != expectedContents.length
                || !sameObservedEntry(noFollow, followed)
                || !files.realPath(marker, false).equals(files.realPath(marker, true))
                || !rootStore.equals(files.fileStore(marker))) {
            throw new UnsafeWorkspacePathException("Workspace marker is not a safe regular file");
        }
        byte[] actualContents = files.readBounded(marker, expectedContents.length);
        if (!Arrays.equals(expectedContents, actualContents)) {
            throw new UnsafeWorkspacePathException("Workspace marker contents changed");
        }
        return new ObservedFile(
                marker,
                files.realPath(marker, true),
                noFollow.fileKey(),
                noFollow.creationTime(),
                noFollow.size());
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

    private static boolean sameIdentity(
            ObservedDirectory first, ObservedDirectory second) {
        if (first.fileKey() != null && second.fileKey() != null) {
            return first.fileKey().equals(second.fileKey())
                    && first.realPath().equals(second.realPath());
        }
        if ((first.fileKey() == null) != (second.fileKey() == null)) {
            return false;
        }
        return first.realPath().equals(second.realPath())
                && first.creationTime().equals(second.creationTime());
    }

    private static boolean sameIdentity(ObservedFile first, ObservedFile second) {
        if (first.fileKey() != null && second.fileKey() != null) {
            return first.fileKey().equals(second.fileKey())
                    && first.realPath().equals(second.realPath());
        }
        if ((first.fileKey() == null) != (second.fileKey() == null)) {
            return false;
        }
        return first.realPath().equals(second.realPath())
                && first.creationTime().equals(second.creationTime())
                && first.size() == second.size();
    }

    private static void requireSameStore(
            ObservedDirectory rootObservation,
            ObservedDirectory workspaceObservation)
            throws UnsafeWorkspacePathException {
        if (!rootObservation.fileStore().equals(workspaceObservation.fileStore())) {
            throw new UnsafeWorkspacePathException(
                    "Report workspace crossed a filesystem-store boundary");
        }
    }

    private static void requireContained(
            ObservedDirectory rootObservation,
            ObservedDirectory workspaceObservation)
            throws UnsafeWorkspacePathException {
        if (!workspaceObservation.realPath().getParent().equals(rootObservation.realPath())) {
            throw new UnsafeWorkspacePathException(
                    "Report workspace resolved outside its configured root");
        }
    }

    private WorkspaceCreationException rollbackFailure(
            ReportSessionId sessionId,
            Path workspace,
            Path marker,
            boolean workspaceCreated,
            boolean markerCreated,
            ObservedDirectory workspaceObservation,
            ObservedFile markerObservation,
            String message,
            Throwable cause) {
        if (workspaceCreated
                && !rollback(
                        workspace,
                        marker,
                        markerCreated,
                        workspaceObservation,
                        markerObservation,
                        cause)) {
            return failure(
                    WorkspaceCreationCode.ROLLBACK_FAILED,
                    sessionId,
                    message + "; the incomplete workspace could not be removed",
                    cause);
        }
        WorkspaceCreationCode code =
                cause instanceof UnsafeWorkspacePathException
                                || cause instanceof SecurityException
                        ? WorkspaceCreationCode.WORKSPACE_UNSAFE
                        : WorkspaceCreationCode.IO_FAILURE;
        return failure(code, sessionId, message, cause);
    }

    private boolean rollback(
            Path workspace,
            Path marker,
            boolean markerCreated,
            ObservedDirectory workspaceObservation,
            ObservedFile markerObservation,
            Throwable originalFailure) {
        List<IOException> failures = new ArrayList<>();
        if (markerCreated) {
            if (markerObservation == null
                    || !sameMarkerIdentity(markerObservation, failures)) {
                failures.add(new IOException(
                        "Created workspace marker could not be safely identified for rollback"));
            } else {
                deleteCreatedPath(marker, failures);
            }
        }
        if (workspaceObservation == null
                || !sameDirectoryIdentity(workspaceObservation, failures)) {
            failures.add(new IOException(
                    "Created workspace directory could not be safely identified for rollback"));
        } else {
            deleteCreatedPath(workspace, failures);
        }
        failures.forEach(originalFailure::addSuppressed);
        return failures.isEmpty();
    }

    private boolean sameMarkerIdentity(ObservedFile original, List<IOException> failures) {
        try {
            ObservedFile current = inspectMarker(
                    original.path(),
                    files.fileStore(original.path()),
                    markerContents(ReportSessionId.parse(
                            original.path().getParent().getFileName().toString())));
            return sameIdentity(original, current);
        } catch (IOException | UnsafeWorkspacePathException | IllegalArgumentException exception) {
            failures.add(new IOException("Could not revalidate workspace marker", exception));
            return false;
        }
    }

    private boolean sameDirectoryIdentity(
            ObservedDirectory original, List<IOException> failures) {
        try {
            return sameIdentity(original, inspectDirectory(original.path()));
        } catch (IOException | UnsafeWorkspacePathException exception) {
            failures.add(new IOException("Could not revalidate workspace directory", exception));
            return false;
        }
    }

    private void deleteCreatedPath(Path path, List<IOException> failures) {
        try {
            files.deleteIfExists(path);
        } catch (IOException exception) {
            failures.add(exception);
        }
    }

    static byte[] markerContents(ReportSessionId sessionId) {
        String contents = MARKER_MAGIC
                + "\nformat="
                + MARKER_SCHEMA_VERSION
                + "\nsession="
                + sessionId
                + "\n";
        return contents.getBytes(StandardCharsets.UTF_8);
    }

    private static WorkspaceCreationException failure(
            WorkspaceCreationCode code,
            ReportSessionId sessionId,
            String message) {
        return new WorkspaceCreationException(code, sessionId, message);
    }

    private static WorkspaceCreationException failure(
            WorkspaceCreationCode code,
            ReportSessionId sessionId,
            String message,
            Throwable cause) {
        return new WorkspaceCreationException(code, sessionId, message, cause);
    }

    private record ObservedDirectory(
            Path path,
            Path realPath,
            FileStore fileStore,
            Object fileKey,
            java.nio.file.attribute.FileTime creationTime) {}

    private record ObservedFile(
            Path path,
            Path realPath,
            Object fileKey,
            java.nio.file.attribute.FileTime creationTime,
            long size) {}

    private static final class UnsafeWorkspacePathException extends Exception {
        private static final long serialVersionUID = 1L;

        private UnsafeWorkspacePathException(String message) {
            super(message);
        }
    }
}
