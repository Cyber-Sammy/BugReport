package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
        ApprovedSourceRoots approvedRoots = Objects.requireNonNull(roots, "roots");
        LogicalRoot logicalRoot = Objects.requireNonNull(root, "root");
        RelativePath declaredPath = Objects.requireNonNull(relativePath, "relativePath");
        Path rootPath = approvedRoots.path(logicalRoot);

        try {
            BasicFileAttributes rootAttributes;
            try {
                rootAttributes = readAttributes(rootPath);
            } catch (NoSuchFileException exception) {
                throw failure(
                        SourcePathResolutionCode.ROOT_MISSING,
                        logicalRoot,
                        declaredPath,
                        "Approved source root does not exist",
                        exception);
            }
            if (isRedirection(rootAttributes)) {
                throw failure(
                        SourcePathResolutionCode.ROOT_UNSAFE,
                        logicalRoot,
                        declaredPath,
                        "Approved source root is redirected or has an unsupported file type");
            }
            if (!rootAttributes.isDirectory()) {
                throw failure(
                        SourcePathResolutionCode.ROOT_UNSAFE,
                        logicalRoot,
                        declaredPath,
                        "Approved source root is not a directory");
            }

            Path realRoot = rootPath.toRealPath();
            FileStore rootStore = Files.getFileStore(rootPath);
            Path candidate = rootPath.resolve(declaredPath.value()).normalize();
            if (!candidate.startsWith(rootPath)) {
                throw failure(
                        SourcePathResolutionCode.TARGET_OUTSIDE_ROOT,
                        logicalRoot,
                        declaredPath,
                        "Declared source path escapes its approved logical root");
            }

            Path current = rootPath;
            String[] segments = declaredPath.value().split("/");
            BasicFileAttributes targetAttributes = null;
            for (int index = 0; index < segments.length; index++) {
                current = current.resolve(segments[index]);
                targetAttributes = readAttributes(current);
                if (isRedirection(targetAttributes)
                        || !rootStore.equals(Files.getFileStore(current))) {
                    throw failure(
                            SourcePathResolutionCode.PATH_REDIRECTION,
                            logicalRoot,
                            declaredPath,
                            "Declared source path crosses a filesystem redirection");
                }
                if (index < segments.length - 1 && !targetAttributes.isDirectory()) {
                    throw failure(
                            SourcePathResolutionCode.COMPONENT_NOT_DIRECTORY,
                            logicalRoot,
                            declaredPath,
                            "An intermediate source-path component is not a directory");
                }
            }

            Path noFollowRealCandidate = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path followedRealCandidate = candidate.toRealPath();
            if (!noFollowRealCandidate.startsWith(realRoot)
                    || !followedRealCandidate.startsWith(realRoot)) {
                throw failure(
                        SourcePathResolutionCode.TARGET_OUTSIDE_ROOT,
                        logicalRoot,
                        declaredPath,
                        "Resolved source path is outside its approved logical root");
            }
            if (targetAttributes == null || !targetAttributes.isRegularFile()) {
                throw failure(
                        SourcePathResolutionCode.TARGET_NOT_REGULAR_FILE,
                        logicalRoot,
                        declaredPath,
                        "Resolved source is not a regular file");
            }
            return new ResolvedSourceFile(
                    logicalRoot,
                    declaredPath,
                    noFollowRealCandidate,
                    targetAttributes.size(),
                    targetAttributes.lastModifiedTime());
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

    private static BasicFileAttributes readAttributes(Path path) throws IOException {
        return Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isRedirection(BasicFileAttributes attributes) {
        return attributes.isSymbolicLink() || attributes.isOther();
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
}
