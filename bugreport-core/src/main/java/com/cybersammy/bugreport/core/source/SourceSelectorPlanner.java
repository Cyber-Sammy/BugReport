package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.DynamicSourcePathProducer;
import com.cybersammy.bugreport.api.specification.DynamicSourcePathRequest;
import com.cybersammy.bugreport.api.specification.DynamicSourcePathSink;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Builds one bounded deterministic source-selection plan from a trusted declaration. */
public final class SourceSelectorPlanner {
    /** Hard product ceiling for files returned by one multi-file selector. */
    public static final int MAX_MATCHED_FILES = SourcePlanningLimits.PRODUCT_MAX_MATCHED_FILES;
    /** Hard product ceiling for one selected file's observed size. */
    public static final long MAX_BYTES_PER_FILE =
            SourcePlanningLimits.PRODUCT_MAX_BYTES_PER_FILE;
    /** Hard product ceiling for one source plan's aggregate observed size. */
    public static final long MAX_TOTAL_BYTES = SourcePlanningLimits.PRODUCT_MAX_TOTAL_BYTES;
    /** Hard ceiling for entries inspected during one non-recursive directory scan. */
    public static final int MAX_SCANNED_DIRECTORY_ENTRIES = 512;
    /** Hard product ceiling for one dynamic path callback. */
    public static final Duration MAX_DYNAMIC_CALLBACK_TIMEOUT = Duration.ofSeconds(2);

    private static final RelativePath LATEST_LOG_PATH = RelativePath.of("latest.log");
    private static final FilenamePattern LATEST_CRASH_PATTERN =
            FilenamePattern.of("crash-*.txt");

    private SourceSelectorPlanner() {}

    /** Plans one source without reading file contents or granting collection authority. */
    public static SourceSelectionPlan plan(
            DiagnosticSourceSpecification source, ApprovedSourceRoots roots) {
        return plan(source, roots, SupportedSide.PHYSICAL_CLIENT);
    }

    /** Plans one source for an explicit physical side. */
    public static SourceSelectionPlan plan(
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots,
            SupportedSide side) {
        return plan(
                source,
                roots,
                side,
                NioSourcePathInspection.INSTANCE,
                SourcePlanningLimits.productDefaults());
    }

    static SourceSelectionPlan plan(
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots,
            SourcePathInspection inspection) {
        return plan(
                source,
                roots,
                SupportedSide.PHYSICAL_CLIENT,
                inspection,
                SourcePlanningLimits.productDefaults());
    }

    static SourceSelectionPlan plan(
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots,
            SourcePathInspection inspection,
            SourcePlanningLimits limits) {
        return plan(
                source,
                roots,
                SupportedSide.PHYSICAL_CLIENT,
                inspection,
                limits);
    }

    static SourceSelectionPlan plan(
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots,
            SupportedSide side,
            SourcePathInspection inspection,
            SourcePlanningLimits limits) {
        DiagnosticSourceSpecification declaration = Objects.requireNonNull(source, "source");
        ApprovedSourceRoots approvedRoots = Objects.requireNonNull(roots, "roots");
        SupportedSide physicalSide = Objects.requireNonNull(side, "side");
        SourcePathInspection pathInspection = Objects.requireNonNull(inspection, "inspection");
        SourcePlanningLimits productLimits = Objects.requireNonNull(limits, "limits");
        return switch (declaration.kind()) {
            case EXACT_FILE, MOD_CONFIGURATION ->
                    planExact(
                            declaration,
                            approvedRoots,
                            declaration.root().orElseThrow(),
                            declaration.path().orElseThrow(),
                            pathInspection,
                            productLimits);
            case LATEST_FILE ->
                    planScan(
                            declaration,
                            approvedRoots,
                            declaration.root().orElseThrow(),
                            Optional.empty(),
                            declaration.pattern().orElseThrow(),
                            true,
                            pathInspection,
                            productLimits);
            case FILTERED_DIRECTORY ->
                    planScan(
                            declaration,
                            approvedRoots,
                            LogicalRoot.GAME_LOGS,
                            declaration.path(),
                            declaration.pattern().orElseThrow(),
                            false,
                            pathInspection,
                            productLimits);
            case DYNAMIC_FILES ->
                    planDynamic(
                            declaration,
                            approvedRoots,
                            physicalSide,
                            pathInspection,
                            productLimits);
            case LATEST_LOG ->
                    planExact(
                            declaration,
                            approvedRoots,
                            LogicalRoot.GAME_LOGS,
                            LATEST_LOG_PATH,
                            pathInspection,
                            productLimits);
            case LATEST_CRASH_REPORT ->
                    planScan(
                            declaration,
                            approvedRoots,
                            LogicalRoot.CRASH_REPORTS,
                            Optional.empty(),
                            LATEST_CRASH_PATTERN,
                            true,
                            pathInspection,
                            productLimits);
            case USER_SELECTED_SCREENSHOT -> new UserSelectionSourcePlan(declaration);
            case MOD_LIST, ENVIRONMENT_SUMMARY -> new BuiltInSourcePlan(declaration);
        };
    }

    private static SourceSelectionPlan planDynamic(
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots,
            SupportedSide side,
            SourcePathInspection inspection,
            SourcePlanningLimits limits) {
        if (!source.supportedSides().contains(side)) {
            return new UnavailableSourcePlan(
                    source, SourceSelectionFailureCode.UNSUPPORTED_SIDE, null);
        }
        int resultLimit = effectiveMatchLimit(source, false, limits);
        Duration requestedTimeout = source.constraints().callbackTimeout().orElseThrow();
        Duration timeout = requestedTimeout.compareTo(MAX_DYNAMIC_CALLBACK_TIMEOUT) > 0
                ? MAX_DYNAMIC_CALLBACK_TIMEOUT
                : requestedTimeout;
        DynamicPathInvocation invocation = invokeDynamicPaths(
                source.dynamicPathProducer().orElseThrow(), side, resultLimit, timeout);
        if (invocation.failureCode() != null) {
            return new UnavailableSourcePlan(source, invocation.failureCode(), null);
        }
        if (invocation.paths().isEmpty()) {
            return new UnavailableSourcePlan(
                    source, SourceSelectionFailureCode.NO_MATCH, null);
        }

        List<ResolvedSourceFile> files = new ArrayList<>();
        for (RelativePath path : invocation.paths()) {
            try {
                ResolvedSourceFile resolved = SourcePathResolver.resolveRegularFile(
                        roots,
                        source.root().orElseThrow(),
                        path,
                        inspection);
                if (files.stream()
                        .anyMatch(file -> file.localPath().equals(resolved.localPath()))) {
                    return new UnavailableSourcePlan(
                            source,
                            SourceSelectionFailureCode.DYNAMIC_RESULT_INVALID,
                            null);
                }
                files.add(resolved);
            } catch (SourcePathResolutionException exception) {
                return unavailable(source, exception);
            }
        }
        return filePlan(source, files, limits);
    }

    private static DynamicPathInvocation invokeDynamicPaths(
            DynamicSourcePathProducer producer,
            SupportedSide side,
            int resultLimit,
            Duration timeout) {
        DynamicCancellation cancellation = new DynamicCancellation();
        BoundedDynamicPathSink sink = new BoundedDynamicPathSink(resultLimit, cancellation);
        FutureTask<Void> invocation = new FutureTask<>(
                () -> {
                    sink.openForCurrentThread();
                    producer.produce(
                            new DynamicSourcePathRequest(side, cancellation), sink);
                    return null;
                });
        Thread worker = Thread.ofVirtual()
                .name("bugreport-dynamic-source-path")
                .unstarted(invocation);
        worker.start();
        try {
            invocation.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return sink.closeAndSnapshot();
        } catch (TimeoutException exception) {
            cancellation.cancel();
            sink.close();
            invocation.cancel(true);
            return DynamicPathInvocation.failure(
                    SourceSelectionFailureCode.DYNAMIC_CALLBACK_TIMED_OUT);
        } catch (InterruptedException exception) {
            cancellation.cancel();
            sink.close();
            invocation.cancel(true);
            Thread.currentThread().interrupt();
            return DynamicPathInvocation.failure(
                    SourceSelectionFailureCode.DYNAMIC_CALLBACK_CANCELLED);
        } catch (CancellationException exception) {
            cancellation.cancel();
            sink.close();
            return DynamicPathInvocation.failure(
                    SourceSelectionFailureCode.DYNAMIC_CALLBACK_CANCELLED);
        } catch (ExecutionException exception) {
            return sink.closeAfterFailure();
        }
    }

    private static SourceSelectionPlan planExact(
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots,
            LogicalRoot root,
            RelativePath path,
            SourcePathInspection inspection,
            SourcePlanningLimits limits) {
        try {
            return filePlan(
                    source,
                    List.of(
                            SourcePathResolver.resolveRegularFile(
                                    roots, root, path, inspection)),
                    limits);
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
            SourcePathInspection inspection,
            SourcePlanningLimits limits) {
        try {
            List<ResolvedSourceFile> matches =
                    scan(
                            root,
                            directory,
                            pattern,
                            roots,
                            effectiveMatchLimit(source, latestOnly, limits),
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
                return filePlan(source, List.of(latest), limits);
            }
            matches.sort(Comparator.comparing(ResolvedSourceFile::relativePath));
            return filePlan(source, matches, limits);
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
            DiagnosticSourceSpecification source,
            boolean latestOnly,
            SourcePlanningLimits limits) {
        if (latestOnly) {
            return MAX_SCANNED_DIRECTORY_ENTRIES;
        }
        return source.constraints().maxMatchedFiles().isPresent()
                ? Math.min(
                        limits.maxMatchedFiles(),
                        source.constraints().maxMatchedFiles().getAsInt())
                : limits.maxMatchedFiles();
    }

    private static SourceSelectionPlan filePlan(
            DiagnosticSourceSpecification source,
            List<ResolvedSourceFile> files,
            SourcePlanningLimits limits) {
        try {
            SourceSizeEstimate estimate =
                    SourceSizeEstimator.estimate(files, source.constraints(), limits);
            return new FileSourcePlan(source, files, estimate);
        } catch (SourceSizeEstimator.SourceSizeLimitException exception) {
            return new UnavailableSourcePlan(source, exception.code(), null);
        }
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

    private record DynamicPathInvocation(
            List<RelativePath> paths, SourceSelectionFailureCode failureCode) {
        private DynamicPathInvocation {
            paths = List.copyOf(paths);
            if ((failureCode == null) == paths.isEmpty()) {
                throw new IllegalArgumentException(
                        "A dynamic invocation must contain either paths or a failure");
            }
        }

        private static DynamicPathInvocation success(List<RelativePath> paths) {
            if (paths.isEmpty()) {
                return new DynamicPathInvocation(
                        List.of(), SourceSelectionFailureCode.NO_MATCH);
            }
            return new DynamicPathInvocation(paths, null);
        }

        private static DynamicPathInvocation failure(SourceSelectionFailureCode code) {
            return new DynamicPathInvocation(List.of(), Objects.requireNonNull(code, "code"));
        }
    }

    private static final class DynamicCancellation implements CancellationSignal {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public boolean isCancellationRequested() {
            return cancelled.get();
        }

        private void cancel() {
            cancelled.set(true);
        }
    }

    private static final class BoundedDynamicPathSink implements DynamicSourcePathSink {
        private final int resultLimit;
        private final DynamicCancellation cancellation;
        private final TreeSet<RelativePath> paths = new TreeSet<>();
        private Thread owner;
        private SourceSelectionFailureCode failureCode;
        private boolean closed;

        private BoundedDynamicPathSink(
                int resultLimit, DynamicCancellation cancellation) {
            if (resultLimit <= 0) {
                throw new IllegalArgumentException("Dynamic result limit must be positive");
            }
            this.resultLimit = resultLimit;
            this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        }

        private synchronized void openForCurrentThread() {
            if (owner != null || closed) {
                fail(SourceSelectionFailureCode.DYNAMIC_RESULT_INVALID);
            }
            owner = Thread.currentThread();
        }

        @Override
        public synchronized void emit(RelativePath path) {
            if (closed
                    || cancellation.isCancellationRequested()
                    || owner != Thread.currentThread()
                    || path == null) {
                fail(SourceSelectionFailureCode.DYNAMIC_RESULT_INVALID);
            }
            if (paths.contains(path)) {
                fail(SourceSelectionFailureCode.DYNAMIC_RESULT_INVALID);
            }
            if (paths.size() == resultLimit) {
                fail(SourceSelectionFailureCode.MATCH_LIMIT_EXCEEDED);
            }
            paths.add(path);
        }

        private synchronized DynamicPathInvocation closeAndSnapshot() {
            closed = true;
            if (failureCode != null) {
                return DynamicPathInvocation.failure(failureCode);
            }
            return DynamicPathInvocation.success(List.copyOf(paths));
        }

        private synchronized DynamicPathInvocation closeAfterFailure() {
            closed = true;
            return DynamicPathInvocation.failure(
                    failureCode == null
                            ? SourceSelectionFailureCode.DYNAMIC_CALLBACK_FAILED
                            : failureCode);
        }

        private synchronized void close() {
            closed = true;
        }

        private void fail(SourceSelectionFailureCode code) {
            failureCode = Objects.requireNonNull(code, "code");
            throw new DynamicPathResultException();
        }
    }

    private static final class DynamicPathResultException extends RuntimeException {
        private static final long serialVersionUID = 1L;
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
