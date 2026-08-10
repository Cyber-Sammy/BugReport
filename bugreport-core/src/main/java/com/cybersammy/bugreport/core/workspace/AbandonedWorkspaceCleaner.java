package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Deletes only explicitly authorized, ownership-verified abandoned workspaces. */
final class AbandonedWorkspaceCleaner {
    static final int MAXIMUM_REQUESTED_SESSIONS = 1_024;
    private static final int MAXIMUM_ROOT_ENTRIES = 4_096;
    private static final int MAXIMUM_WORKSPACE_ENTRIES = 257;
    private static final Pattern OWNED_ARTIFACT = Pattern.compile(
            "(?:source-[0-9a-f]{64}\\.(?:data|png)|generated-[0-9a-f]{64}\\.(?:txt|json))");
    private static final Pattern OWNED_TEMPORARY = Pattern.compile(
            "\\.(?:source-[0-9a-f]{64}\\.(?:data|png)|generated-[0-9a-f]{64}\\.(?:txt|json))"
                    + "\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.part");

    private final Path root;
    private final WorkspaceFileOperations files;

    AbandonedWorkspaceCleaner(Path root, WorkspaceFileOperations files) {
        this.root = root;
        this.files = files;
    }

    AbandonedWorkspaceCleanupResult cleanup(
            Set<ReportSessionId> requested, Set<ReportSessionId> active) {
        if (requested.size() > MAXIMUM_REQUESTED_SESSIONS) {
            throw new IllegalArgumentException("Too many abandoned sessions requested");
        }
        List<ReportSessionId> sessions = requested.stream()
                .sorted(Comparator.comparing(ReportSessionId::toString))
                .toList();
        List<AbandonedWorkspaceCleanupOutcome> outcomes = new ArrayList<>(sessions.size());
        RootObservation rootObservation;
        try {
            rootObservation = inspectRoot();
            files.listDirectChildren(root, MAXIMUM_ROOT_ENTRIES);
        } catch (NoSuchFileException exception) {
            sessions.forEach(session -> outcomes.add(outcome(
                    session, AbandonedWorkspaceCleanupCode.NOT_FOUND)));
            return new AbandonedWorkspaceCleanupResult(outcomes);
        } catch (UnsafePathException | SecurityException exception) {
            sessions.forEach(session -> outcomes.add(outcome(
                    session, AbandonedWorkspaceCleanupCode.ROOT_UNSAFE)));
            return new AbandonedWorkspaceCleanupResult(outcomes);
        } catch (IOException exception) {
            sessions.forEach(session -> outcomes.add(outcome(
                    session, AbandonedWorkspaceCleanupCode.IO_FAILURE)));
            return new AbandonedWorkspaceCleanupResult(outcomes);
        }

        for (ReportSessionId session : sessions) {
            outcomes.add(active.contains(session)
                    ? outcome(session, AbandonedWorkspaceCleanupCode.ACTIVE_SESSION)
                    : cleanupOne(session, rootObservation));
        }
        return new AbandonedWorkspaceCleanupResult(outcomes);
    }

    private AbandonedWorkspaceCleanupOutcome cleanupOne(
            ReportSessionId session, RootObservation rootObservation) {
        Path workspace = root.resolve(session.toString()).normalize();
        if (!root.equals(workspace.getParent())) {
            return outcome(session, AbandonedWorkspaceCleanupCode.WORKSPACE_UNSAFE);
        }
        DirectoryObservation directory;
        try {
            directory = inspectWorkspace(workspace, rootObservation);
        } catch (NoSuchFileException exception) {
            return outcome(session, AbandonedWorkspaceCleanupCode.NOT_FOUND);
        } catch (UnsafePathException | SecurityException exception) {
            return outcome(session, AbandonedWorkspaceCleanupCode.WORKSPACE_UNSAFE);
        } catch (IOException exception) {
            return outcome(session, AbandonedWorkspaceCleanupCode.IO_FAILURE);
        }

        Path marker = workspace.resolve(FileReportWorkspaceStore.MARKER_FILENAME);
        FileObservation markerObservation;
        try {
            markerObservation = inspectFile(marker, rootObservation.store());
            byte[] expected = FileReportWorkspaceStore.markerContents(session);
            if (markerObservation.size() != expected.length
                    || !Arrays.equals(expected, files.readBounded(marker, expected.length))) {
                return outcome(session, AbandonedWorkspaceCleanupCode.MARKER_INVALID);
            }
        } catch (NoSuchFileException | UnsafePathException exception) {
            return outcome(session, AbandonedWorkspaceCleanupCode.MARKER_INVALID);
        } catch (IOException | SecurityException exception) {
            return outcome(session, AbandonedWorkspaceCleanupCode.IO_FAILURE);
        }

        List<FileObservation> artifacts = new ArrayList<>();
        try {
            for (Path entry : files.listDirectChildren(workspace, MAXIMUM_WORKSPACE_ENTRIES)) {
                String name = entry.getFileName().toString();
                if (name.equals(FileReportWorkspaceStore.MARKER_FILENAME)) {
                    continue;
                }
                if (!OWNED_ARTIFACT.matcher(name).matches()
                        && !OWNED_TEMPORARY.matcher(name).matches()) {
                    return outcome(session, AbandonedWorkspaceCleanupCode.UNEXPECTED_ENTRY);
                }
                artifacts.add(inspectFile(entry, rootObservation.store()));
            }
            artifacts.sort(Comparator.comparing(observation ->
                    observation.path().getFileName().toString()));
            requireUnchanged(rootObservation, directory, markerObservation, session);
            for (FileObservation artifact : artifacts) {
                if (!sameFile(artifact, inspectFile(artifact.path(), rootObservation.store()))) {
                    return outcome(session, AbandonedWorkspaceCleanupCode.ENTRY_CHANGED);
                }
            }
        } catch (UnsafePathException | SecurityException exception) {
            return outcome(session, AbandonedWorkspaceCleanupCode.WORKSPACE_UNSAFE);
        } catch (NoSuchFileException exception) {
            return outcome(session, AbandonedWorkspaceCleanupCode.ENTRY_CHANGED);
        } catch (IOException exception) {
            return outcome(session, AbandonedWorkspaceCleanupCode.IO_FAILURE);
        }

        boolean changed = false;
        try {
            for (FileObservation artifact : artifacts) {
                if (!sameFile(artifact, inspectFile(artifact.path(), rootObservation.store()))) {
                    return outcome(session, changed
                            ? AbandonedWorkspaceCleanupCode.PARTIAL_FAILURE
                            : AbandonedWorkspaceCleanupCode.ENTRY_CHANGED);
                }
                files.deleteIfExists(artifact.path());
                changed = true;
            }
            requireUnchanged(rootObservation, directory, markerObservation, session);
            files.deleteIfExists(markerObservation.path());
            changed = true;
            if (!sameDirectory(directory, inspectWorkspace(workspace, rootObservation))) {
                return outcome(session, AbandonedWorkspaceCleanupCode.PARTIAL_FAILURE);
            }
            files.deleteIfExists(workspace);
            return outcome(session, AbandonedWorkspaceCleanupCode.REMOVED);
        } catch (NoSuchFileException | UnsafePathException | SecurityException exception) {
            return outcome(session, changed
                    ? AbandonedWorkspaceCleanupCode.PARTIAL_FAILURE
                    : AbandonedWorkspaceCleanupCode.ENTRY_CHANGED);
        } catch (IOException exception) {
            return outcome(session, changed
                    ? AbandonedWorkspaceCleanupCode.PARTIAL_FAILURE
                    : AbandonedWorkspaceCleanupCode.IO_FAILURE);
        }
    }

    private void requireUnchanged(
            RootObservation expectedRoot,
            DirectoryObservation expectedDirectory,
            FileObservation expectedMarker,
            ReportSessionId session)
            throws IOException, UnsafePathException {
        RootObservation currentRoot = inspectRoot();
        DirectoryObservation currentDirectory =
                inspectWorkspace(expectedDirectory.path(), currentRoot);
        FileObservation currentMarker = inspectFile(expectedMarker.path(), currentRoot.store());
        byte[] expectedContents = FileReportWorkspaceStore.markerContents(session);
        if (!sameRoot(expectedRoot, currentRoot)
                || !sameDirectory(expectedDirectory, currentDirectory)
                || !sameFile(expectedMarker, currentMarker)
                || !Arrays.equals(
                        expectedContents,
                        files.readBounded(expectedMarker.path(), expectedContents.length))) {
            throw new UnsafePathException();
        }
    }

    private RootObservation inspectRoot() throws IOException, UnsafePathException {
        Path filesystemRoot = root.getRoot();
        if (filesystemRoot == null) {
            throw new UnsafePathException();
        }
        DirectoryObservation observed = inspectDirectory(filesystemRoot);
        Path current = filesystemRoot;
        for (Path segment : filesystemRoot.relativize(root)) {
            current = current.resolve(segment);
            observed = inspectDirectory(current);
        }
        files.verifyPrivateDirectory(root);
        return new RootObservation(
                observed.realPath(),
                observed.store(),
                observed.fileKey(),
                observed.creationTime());
    }

    private DirectoryObservation inspectWorkspace(Path path, RootObservation rootObservation)
            throws IOException, UnsafePathException {
        DirectoryObservation observed = inspectDirectory(path);
        if (!observed.store().equals(rootObservation.store())
                || !rootObservation.realPath().equals(observed.realPath().getParent())) {
            throw new UnsafePathException();
        }
        files.verifyPrivateDirectory(path);
        return observed;
    }

    private DirectoryObservation inspectDirectory(Path path)
            throws IOException, UnsafePathException {
        BasicFileAttributes noFollow = files.readAttributes(path, false);
        BasicFileAttributes followed = files.readAttributes(path, true);
        Path direct = files.realPath(path, false);
        Path resolved = files.realPath(path, true);
        if (!noFollow.isDirectory() || noFollow.isSymbolicLink() || noFollow.isOther()
                || !sameEntry(noFollow, followed) || !direct.equals(resolved)) {
            throw new UnsafePathException();
        }
        return new DirectoryObservation(
                path, resolved, files.fileStore(path), noFollow.fileKey(), noFollow.creationTime());
    }

    private FileObservation inspectFile(Path path, FileStore expectedStore)
            throws IOException, UnsafePathException {
        BasicFileAttributes noFollow = files.readAttributes(path, false);
        BasicFileAttributes followed = files.readAttributes(path, true);
        Path direct = files.realPath(path, false);
        Path resolved = files.realPath(path, true);
        if (!noFollow.isRegularFile() || noFollow.isSymbolicLink() || noFollow.isOther()
                || !sameEntry(noFollow, followed) || !direct.equals(resolved)
                || !expectedStore.equals(files.fileStore(path))) {
            throw new UnsafePathException();
        }
        files.verifyPrivateFile(path);
        return new FileObservation(
                path,
                resolved,
                noFollow.fileKey(),
                noFollow.creationTime(),
                noFollow.size());
    }

    private static boolean sameEntry(BasicFileAttributes first, BasicFileAttributes second) {
        if (first.fileKey() != null && second.fileKey() != null) {
            return first.fileKey().equals(second.fileKey());
        }
        return first.fileKey() == null && second.fileKey() == null
                && first.isDirectory() == second.isDirectory()
                && first.isRegularFile() == second.isRegularFile()
                && first.creationTime().equals(second.creationTime());
    }

    private static boolean sameRoot(RootObservation first, RootObservation second) {
        return sameIdentity(first.realPath(), first.fileKey(), first.creationTime(),
                second.realPath(), second.fileKey(), second.creationTime());
    }

    private static boolean sameDirectory(DirectoryObservation first, DirectoryObservation second) {
        return sameIdentity(first.realPath(), first.fileKey(), first.creationTime(),
                second.realPath(), second.fileKey(), second.creationTime());
    }

    private static boolean sameFile(FileObservation first, FileObservation second) {
        return first.size() == second.size()
                && sameIdentity(first.realPath(), first.fileKey(), first.creationTime(),
                        second.realPath(), second.fileKey(), second.creationTime());
    }

    private static boolean sameIdentity(
            Path firstPath, Object firstKey, FileTime firstCreation,
            Path secondPath, Object secondKey, FileTime secondCreation) {
        if (firstKey != null && secondKey != null) {
            return firstKey.equals(secondKey) && firstPath.equals(secondPath);
        }
        return firstKey == null && secondKey == null
                && firstPath.equals(secondPath) && firstCreation.equals(secondCreation);
    }

    private static AbandonedWorkspaceCleanupOutcome outcome(
            ReportSessionId session, AbandonedWorkspaceCleanupCode code) {
        return new AbandonedWorkspaceCleanupOutcome(session, code);
    }

    private record RootObservation(
            Path realPath, FileStore store, Object fileKey, FileTime creationTime) {}

    private record DirectoryObservation(
            Path path,
            Path realPath,
            FileStore store,
            Object fileKey,
            FileTime creationTime) {}

    private record FileObservation(
            Path path,
            Path realPath,
            Object fileKey,
            FileTime creationTime,
            long size) {}
    private static final class UnsafePathException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
