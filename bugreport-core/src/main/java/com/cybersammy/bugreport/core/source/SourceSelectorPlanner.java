package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.FilenamePattern;
import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Builds one bounded deterministic source-selection plan from a trusted declaration. */
public final class SourceSelectorPlanner {
    /** Hard product ceiling for files returned by one filtered-directory selector. */
    public static final int MAX_MATCHED_FILES = 16;
    /** Hard ceiling for entries inspected during one non-recursive directory scan. */
    public static final int MAX_SCANNED_DIRECTORY_ENTRIES = 512;

    private static final RelativePath LATEST_LOG_PATH = RelativePath.of("latest.log");
    private static final FilenamePattern LATEST_CRASH_PATTERN =
            FilenamePattern.of("crash-*.txt");

    private SourceSelectorPlanner() {}

    /** Plans one source without reading file contents or granting collection authority. */
    public static SourceSelectionPlan plan(
            DiagnosticSourceSpecification source, ApprovedSourceRoots roots) {
        return plan(source, roots, NioSourcePathInspection.INSTANCE);
    }

    static SourceSelectionPlan plan(
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots,
            SourcePathInspection inspection) {
        DiagnosticSourceSpecification declaration = Objects.requireNonNull(source, "source");
        ApprovedSourceRoots approvedRoots = Objects.requireNonNull(roots, "roots");
        SourcePathInspection pathInspection = Objects.requireNonNull(inspection, "inspection");
        return switch (declaration.kind()) {
            case EXACT_FILE, MOD_CONFIGURATION ->
                    planExact(
                            declaration,
                            approvedRoots,
                            declaration.root().orElseThrow(),
                            declaration.path().orElseThrow(),
                            pathInspection);
            case LATEST_FILE ->
                    planScan(
                            declaration,
                            approvedRoots,
                            declaration.root().orElseThrow(),
                            Optional.empty(),
                            declaration.pattern().orElseThrow(),
                            true,
                            pathInspection);
            case FILTERED_DIRECTORY ->
                    planScan(
                            declaration,
                            approvedRoots,
                            LogicalRoot.GAME_LOGS,
                            declaration.path(),
                            declaration.pattern().orElseThrow(),
                            false,
                            pathInspection);
            case LATEST_LOG ->
                    planExact(
                            declaration,
                            approvedRoots,
                            LogicalRoot.GAME_LOGS,
                            LATEST_LOG_PATH,
                            pathInspection);
            case LATEST_CRASH_REPORT ->
                    planScan(
                            declaration,
                            approvedRoots,
                            LogicalRoot.CRASH_REPORTS,
                            Optional.empty(),
                            LATEST_CRASH_PATTERN,
                            true,
                            pathInspection);
            case USER_SELECTED_SCREENSHOT -> new UserSelectionSourcePlan(declaration);
            case MOD_LIST, ENVIRONMENT_SUMMARY -> new BuiltInSourcePlan(declaration);
        };
    }

    private static SourceSelectionPlan planExact(
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots,
            LogicalRoot root,
            RelativePath path,
            SourcePathInspection inspection) {
        try {
            return new FileSourcePlan(
                    source,
                    List.of(
                            SourcePathResolver.resolveRegularFile(
                                    roots, root, path, inspection)));
        } catch (SourcePathResolutionException exception) {
            return unavailable(source, exception);
        }
    }

    private static SourceSelectionPlan planScan(
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots,
            LogicalRoot root,
            Optional<RelativePath> directory,
            FilenamePattern pattern,
            boolean latestOnly,
            SourcePathInspection inspection) {
        try {
            List<ResolvedSourceFile> matches =
                    scan(
                            root,
                            directory,
                            pattern,
                            roots,
                            effectiveMatchLimit(source, latestOnly),
                            inspection);
            if (matches.isEmpty()) {
                return new UnavailableSourcePlan(
                        source, SourceSelectionFailureCode.NO_MATCH, null);
            }
            if (latestOnly) {
                ResolvedSourceFile latest =
                        matches.stream()
                                .min(
                                        Comparator.comparing(
                                                        ResolvedSourceFile::observedLastModified,
                                                        Comparator.reverseOrder())
                                                .thenComparing(ResolvedSourceFile::relativePath))
                                .orElseThrow();
                return new FileSourcePlan(source, List.of(latest));
            }
            matches.sort(Comparator.comparing(ResolvedSourceFile::relativePath));
            return new FileSourcePlan(source, matches);
        } catch (SelectorScanException exception) {
            return new UnavailableSourcePlan(source, exception.code, exception.pathCode);
        }
    }

    private static List<ResolvedSourceFile> scan(
            LogicalRoot logicalRoot,
            Optional<RelativePath> relativeDirectory,
            FilenamePattern pattern,
            ApprovedSourceRoots roots,
            int matchLimit,
            SourcePathInspection inspection)
            throws SelectorScanException {
        Path rootPath = roots.path(logicalRoot);
        DirectoryObservation root = observeDirectory(rootPath, null, true, inspection);
        DirectoryObservation directory = root;
        if (relativeDirectory.isPresent()) {
            try {
                ResolvedSourceDirectory resolved =
                        SourcePathResolver.resolveDirectory(
                                roots,
                                logicalRoot,
                                relativeDirectory.orElseThrow(),
                                inspection);
                directory =
                        new DirectoryObservation(
                                resolved.declaredPath(),
                                resolved.realPath(),
                                resolved.fileStore(),
                                resolved.fileKey(),
                                resolved.creationTime());
            } catch (SourcePathResolutionException exception) {
                throw selectorFailure(exception);
            }
        }

        List<Path> scannedEntries = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory.realPath)) {
            for (Path entry : entries) {
                if (scannedEntries.size() == MAX_SCANNED_DIRECTORY_ENTRIES) {
                    throw new SelectorScanException(
                            SourceSelectionFailureCode.SCAN_LIMIT_EXCEEDED, null);
                }
                scannedEntries.add(entry);
            }
        } catch (SelectorScanException exception) {
            throw exception;
        } catch (AccessDeniedException | SecurityException exception) {
            throw new SelectorScanException(
                    SourceSelectionFailureCode.PATH_REJECTED,
                    SourcePathResolutionCode.ACCESS_DENIED);
        } catch (IOException exception) {
            throw new SelectorScanException(SourceSelectionFailureCode.IO_FAILURE, null);
        }
        scannedEntries.sort(Comparator.comparing(path -> path.getFileName().toString()));
        List<ResolvedSourceFile> matches = new ArrayList<>();
        for (Path entry : scannedEntries) {
            String filename = entry.getFileName().toString();
            if (!FilenamePatternMatcher.matches(pattern, filename)) {
                continue;
            }
            RelativePath candidate;
            try {
                candidate =
                        RelativePath.of(
                                relativeDirectory
                                    .map(path -> path.value() + "/" + filename)
                                    .orElse(filename));
            } catch (IllegalArgumentException exception) {
                throw new SelectorScanException(
                        SourceSelectionFailureCode.UNSUPPORTED_MATCH, null);
            }
            try {
                matches.add(
                        SourcePathResolver.resolveRegularFile(
                                roots, logicalRoot, candidate, inspection));
            } catch (SourcePathResolutionException exception) {
                throw selectorFailure(exception);
            }
            if (matches.size() > matchLimit) {
                throw new SelectorScanException(
                        SourceSelectionFailureCode.MATCH_LIMIT_EXCEEDED, null);
            }
        }
        revalidateDirectory(root, inspection);
        if (directory != root) {
            revalidateDirectory(directory, inspection);
        }
        return matches;
    }

    private static DirectoryObservation observeDirectory(
            Path path,
            SourcePathResolutionCode missingCode,
            boolean root,
            SourcePathInspection inspection)
            throws SelectorScanException {
        try {
            BasicFileAttributes noFollow = inspection.readAttributes(path, false);
            BasicFileAttributes followed = inspection.readAttributes(path, true);
            Path noFollowReal = inspection.realPath(path, false);
            Path followedReal = inspection.realPath(path, true);
            Object noFollowKey = noFollow.fileKey();
            Object followedKey = followed.fileKey();
            if (noFollow.isSymbolicLink()
                    || noFollow.isOther()
                    || !noFollow.isDirectory()
                    || !followed.isDirectory()
                    || (noFollowKey == null) != (followedKey == null)
                    || (noFollowKey != null && !noFollowKey.equals(followedKey))
                    || (noFollowKey == null
                            && !noFollow.creationTime().equals(followed.creationTime()))
                    || !noFollowReal.equals(followedReal)) {
                throw pathFailure(
                        root
                                ? SourcePathResolutionCode.ROOT_UNSAFE
                                : SourcePathResolutionCode.PATH_REDIRECTION);
            }
            return new DirectoryObservation(
                    path,
                    followedReal,
                    inspection.fileStore(path),
                    noFollowKey,
                    noFollow.creationTime());
        } catch (NoSuchFileException exception) {
            throw pathFailure(
                    root ? SourcePathResolutionCode.ROOT_MISSING : missingCode);
        } catch (FileSystemLoopException exception) {
            throw pathFailure(SourcePathResolutionCode.PATH_REDIRECTION);
        } catch (AccessDeniedException | SecurityException exception) {
            throw new SelectorScanException(
                    SourceSelectionFailureCode.PATH_REJECTED,
                    SourcePathResolutionCode.ACCESS_DENIED);
        } catch (IOException exception) {
            throw new SelectorScanException(SourceSelectionFailureCode.IO_FAILURE, null);
        }
    }

    private static void revalidateDirectory(
            DirectoryObservation original, SourcePathInspection inspection)
            throws SelectorScanException {
        DirectoryObservation current;
        try {
            current = observeDirectory(
                    original.path,
                    SourcePathResolutionCode.COMPONENT_MISSING,
                    false,
                    inspection);
        } catch (SelectorScanException exception) {
            throw new SelectorScanException(
                    SourceSelectionFailureCode.PATH_CHANGED_DURING_SCAN, null);
        }
        boolean sameIdentity =
                original.fileKey != null && current.fileKey != null
                        ? original.fileKey.equals(current.fileKey)
                        : original.fileKey == null
                                && current.fileKey == null
                                && original.creationTime.equals(current.creationTime);
        if (!sameIdentity
                || !original.realPath.equals(current.realPath)
                || !original.fileStore.equals(current.fileStore)) {
            throw new SelectorScanException(
                    SourceSelectionFailureCode.PATH_CHANGED_DURING_SCAN, null);
        }
    }

    private static int effectiveMatchLimit(
            DiagnosticSourceSpecification source, boolean latestOnly) {
        if (latestOnly) {
            return MAX_SCANNED_DIRECTORY_ENTRIES;
        }
        return source.constraints().maxMatchedFiles().isPresent()
                ? Math.min(
                        MAX_MATCHED_FILES,
                        source.constraints().maxMatchedFiles().getAsInt())
                : MAX_MATCHED_FILES;
    }

    private static UnavailableSourcePlan unavailable(
            DiagnosticSourceSpecification source, SourcePathResolutionException exception) {
        SourceSelectionFailureCode code =
                isMissing(exception.code())
                        ? SourceSelectionFailureCode.SOURCE_MISSING
                        : SourceSelectionFailureCode.PATH_REJECTED;
        return new UnavailableSourcePlan(source, code, exception.code());
    }

    private static SelectorScanException selectorFailure(
            SourcePathResolutionException exception) {
        if (exception.code() == SourcePathResolutionCode.PATH_CHANGED_DURING_RESOLUTION) {
            return new SelectorScanException(
                    SourceSelectionFailureCode.PATH_CHANGED_DURING_SCAN, null);
        }
        return new SelectorScanException(
                isMissing(exception.code())
                        ? SourceSelectionFailureCode.SOURCE_MISSING
                        : SourceSelectionFailureCode.PATH_REJECTED,
                exception.code());
    }

    private static boolean isMissing(SourcePathResolutionCode code) {
        return code == SourcePathResolutionCode.ROOT_MISSING
                || code == SourcePathResolutionCode.COMPONENT_MISSING;
    }

    private static SelectorScanException pathFailure(SourcePathResolutionCode code) {
        return new SelectorScanException(
                isMissing(code)
                        ? SourceSelectionFailureCode.SOURCE_MISSING
                        : SourceSelectionFailureCode.PATH_REJECTED,
                code);
    }

    private record DirectoryObservation(
            Path path,
            Path realPath,
            FileStore fileStore,
            Object fileKey,
            java.nio.file.attribute.FileTime creationTime) {}

    private static final class SelectorScanException extends Exception {
        private static final long serialVersionUID = 1L;

        private final SourceSelectionFailureCode code;
        private final SourcePathResolutionCode pathCode;

        private SelectorScanException(
                SourceSelectionFailureCode code, SourcePathResolutionCode pathCode) {
            this.code = Objects.requireNonNull(code, "code");
            this.pathCode = pathCode;
        }
    }
}
