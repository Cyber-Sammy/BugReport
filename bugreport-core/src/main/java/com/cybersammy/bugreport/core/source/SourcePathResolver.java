package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemLoopException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves exact declarative files while failing closed on filesystem redirection. */
public final class SourcePathResolver {
    private SourcePathResolver() {}

    /**
     * Resolves one existing regular file below an approved logical root.
     *
     * <p>This is a planning-time check. Collection must repeat containment, link, type, and file
     * identity checks while opening and streaming the source into an owned workspace.
     */
    public static ResolvedSourceFile resolveRegularFile(
            ApprovedSourceRoots roots, LogicalRoot root, RelativePath relativePath) {
        return resolveRegularFile(
                roots, root, relativePath, NioSourcePathInspection.INSTANCE);
    }

    static ResolvedSourceFile resolveRegularFile(
            ApprovedSourceRoots roots,
            LogicalRoot root,
            RelativePath relativePath,
            SourcePathInspection inspection) {
        ApprovedSourceRoots approvedRoots = Objects.requireNonNull(roots, "roots");
        LogicalRoot logicalRoot = Objects.requireNonNull(root, "root");
        RelativePath declaredPath = Objects.requireNonNull(relativePath, "relativePath");
        SourcePathInspection pathInspection = Objects.requireNonNull(inspection, "inspection");
        Path rootPath = approvedRoots.path(logicalRoot);

        try {
            ObservedEntry rootEntry;
            try {
                rootEntry = inspectEntry(rootPath, pathInspection);
            } catch (NoSuchFileException exception) {
                throw failure(
                        SourcePathResolutionCode.ROOT_MISSING,
                        logicalRoot,
                        declaredPath,
                        "Approved source root does not exist",
                        exception);
            } catch (DetectedRedirectionException exception) {
                throw failure(
                        SourcePathResolutionCode.ROOT_UNSAFE,
                        logicalRoot,
                        declaredPath,
                        "Approved source root is redirected or has an unsupported file type",
                        exception);
            }
            if (!rootEntry.attributes().isDirectory()) {
                throw failure(
                        SourcePathResolutionCode.ROOT_UNSAFE,
                        logicalRoot,
                        declaredPath,
                        "Approved source root is not a directory");
            }

            FileStore rootStore = pathInspection.fileStore(rootPath);
            Path candidate = rootPath.resolve(declaredPath.value()).normalize();
            if (!candidate.startsWith(rootPath)) {
                throw failure(
                        SourcePathResolutionCode.TARGET_OUTSIDE_ROOT,
                        logicalRoot,
                        declaredPath,
                        "Declared source path escapes its approved logical root");
            }

            List<ObservedEntry> entries = new ArrayList<>();
            entries.add(rootEntry);
            Path current = rootPath;
            String[] segments = declaredPath.value().split("/");
            for (int index = 0; index < segments.length; index++) {
                current = current.resolve(segments[index]);
                ObservedEntry entry;
                try {
                    entry = inspectEntry(current, pathInspection);
                } catch (DetectedRedirectionException exception) {
                    throw failure(
                            SourcePathResolutionCode.PATH_REDIRECTION,
                            logicalRoot,
                            declaredPath,
                            "Declared source path crosses a filesystem redirection",
                            exception);
                }
                if (!rootStore.equals(pathInspection.fileStore(current))) {
                    throw failure(
                            SourcePathResolutionCode.PATH_REDIRECTION,
                            logicalRoot,
                            declaredPath,
                            "Declared source path crosses a filesystem-store boundary");
                }
                if (index < segments.length - 1 && !entry.attributes().isDirectory()) {
                    throw failure(
                            SourcePathResolutionCode.COMPONENT_NOT_DIRECTORY,
                            logicalRoot,
                            declaredPath,
                            "An intermediate source-path component is not a directory");
                }
                entries.add(entry);
            }

            ObservedEntry target = entries.getLast();
            if (!target.realPath().startsWith(rootEntry.realPath())) {
                throw failure(
                        SourcePathResolutionCode.TARGET_OUTSIDE_ROOT,
                        logicalRoot,
                        declaredPath,
                        "Resolved source path is outside its approved logical root");
            }
            if (!target.attributes().isRegularFile()) {
                throw failure(
                        SourcePathResolutionCode.TARGET_NOT_REGULAR_FILE,
                        logicalRoot,
                        declaredPath,
                        "Resolved source is not a regular file");
            }

            revalidateEntries(
                    entries,
                    rootStore,
                    pathInspection,
                    logicalRoot,
                    declaredPath);
            return new ResolvedSourceFile(
                    logicalRoot,
                    declaredPath,
                    target.realPath(),
                    target.attributes().size(),
                    target.attributes().lastModifiedTime(),
                    target.fileKey());
        } catch (SourcePathResolutionException exception) {
            throw exception;
        } catch (NoSuchFileException exception) {
            throw failure(
                    SourcePathResolutionCode.COMPONENT_MISSING,
                    logicalRoot,
                    declaredPath,
                    "Required source-path component does not exist",
                    exception);
        } catch (FileSystemLoopException exception) {
            throw failure(
                    SourcePathResolutionCode.PATH_REDIRECTION,
                    logicalRoot,
                    declaredPath,
                    "Declared source path contains a filesystem loop",
                    exception);
        } catch (AccessDeniedException | SecurityException exception) {
            throw failure(
                    SourcePathResolutionCode.ACCESS_DENIED,
                    logicalRoot,
                    declaredPath,
                    "Access to the declared source path was denied",
                    exception);
        } catch (IOException exception) {
            throw failure(
                    SourcePathResolutionCode.IO_FAILURE,
                    logicalRoot,
                    declaredPath,
                    "The declared source path could not be inspected",
                    exception);
        }
    }

    private static ObservedEntry inspectEntry(
            Path path, SourcePathInspection inspection)
            throws IOException, DetectedRedirectionException {
        BasicFileAttributes noFollow = inspection.readAttributes(path, false);
        if (noFollow.isSymbolicLink() || noFollow.isOther()) {
            throw new DetectedRedirectionException();
        }
        BasicFileAttributes followed = inspection.readAttributes(path, true);
        Object noFollowKey = noFollow.fileKey();
        Object followedKey = followed.fileKey();
        Path noFollowReal = inspection.realPath(path, false);
        Path followedReal = inspection.realPath(path, true);
        if ((noFollowKey == null) != (followedKey == null)
                || (noFollowKey != null && !noFollowKey.equals(followedKey))
                || !noFollowReal.equals(followedReal)) {
            throw new DetectedRedirectionException();
        }
        return new ObservedEntry(path, followedReal, noFollow, noFollowKey);
    }

    private static void revalidateEntries(
            List<ObservedEntry> entries,
            FileStore rootStore,
            SourcePathInspection inspection,
            LogicalRoot root,
            RelativePath relativePath)
            throws IOException {
        for (int index = entries.size() - 1; index >= 0; index--) {
            ObservedEntry original = entries.get(index);
            ObservedEntry current;
            FileStore currentStore;
            try {
                current = inspectEntry(original.path(), inspection);
                currentStore = inspection.fileStore(original.path());
            } catch (DetectedRedirectionException
                    | NoSuchFileException
                    | FileSystemLoopException exception) {
                throw failure(
                        SourcePathResolutionCode.PATH_CHANGED_DURING_RESOLUTION,
                        root,
                        relativePath,
                        "Source path changed while it was being inspected",
                        exception);
            }
            if (!rootStore.equals(currentStore)
                    || !sameIdentity(original, current)
                    || (index == entries.size() - 1
                            && !sameFileSnapshot(original, current))) {
                throw failure(
                        SourcePathResolutionCode.PATH_CHANGED_DURING_RESOLUTION,
                        root,
                        relativePath,
                        "Source path changed while it was being inspected");
            }
        }
    }

    private static boolean sameIdentity(
            ObservedEntry original, ObservedEntry current) {
        if (original.fileKey() != null && current.fileKey() != null) {
            return original.fileKey().equals(current.fileKey())
                    && original.realPath().equals(current.realPath());
        }
        if ((original.fileKey() == null) != (current.fileKey() == null)) {
            return false;
        }
        return sameType(original.attributes(), current.attributes())
                && original.realPath().equals(current.realPath())
                && original.attributes().creationTime()
                        .equals(current.attributes().creationTime());
    }

    private static boolean sameFileSnapshot(
            ObservedEntry original, ObservedEntry current) {
        return current.attributes().isRegularFile()
                && original.attributes().size() == current.attributes().size()
                && original.attributes().lastModifiedTime()
                        .equals(current.attributes().lastModifiedTime());
    }

    private static boolean sameType(
            BasicFileAttributes first, BasicFileAttributes second) {
        return first.isDirectory() == second.isDirectory()
                && first.isRegularFile() == second.isRegularFile()
                && first.isSymbolicLink() == second.isSymbolicLink()
                && first.isOther() == second.isOther();
    }

    private static SourcePathResolutionException failure(
            SourcePathResolutionCode code,
            LogicalRoot root,
            RelativePath relativePath,
            String message) {
        return new SourcePathResolutionException(code, root, relativePath, message);
    }

    private static SourcePathResolutionException failure(
            SourcePathResolutionCode code,
            LogicalRoot root,
            RelativePath relativePath,
            String message,
            Throwable cause) {
        return new SourcePathResolutionException(code, root, relativePath, message, cause);
    }

    private record ObservedEntry(
            Path path, Path realPath, BasicFileAttributes attributes, Object fileKey) {}

    private static final class DetectedRedirectionException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
