package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.CategorySourcePlan;
import com.cybersammy.bugreport.core.source.PlannedSourceFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Executes conflict-free planned files sequentially with cancellation and failure isolation. */
public final class FileCollectionCoordinator {
    /** Hard ceiling for all unique file bytes in one report collection run. */
    public static final long PRODUCT_MAX_COLLECTION_BYTES = 128L * 1024L * 1024L;

    private FileCollectionCoordinator() {}

    public static FileCollectionResult collect(
            CategorySourcePlan plan,
            ApprovedSourceRoots roots,
            ReportWorkspace workspace,
            CollectionRunControl control) {
        return collect(
                plan,
                roots,
                workspace,
                control,
                NioSourceReadOperations.INSTANCE,
                PRODUCT_MAX_COLLECTION_BYTES);
    }

    static FileCollectionResult collect(
            CategorySourcePlan plan,
            ApprovedSourceRoots roots,
            ReportWorkspace workspace,
            CollectionRunControl control,
            SourceReadOperations sourceReads) {
        return collect(
                plan,
                roots,
                workspace,
                control,
                sourceReads,
                PRODUCT_MAX_COLLECTION_BYTES);
    }

    static FileCollectionResult collect(
            CategorySourcePlan plan,
            ApprovedSourceRoots roots,
            ReportWorkspace workspace,
            CollectionRunControl control,
            SourceReadOperations sourceReads,
            long maximumCollectionBytes) {
        CategorySourcePlan trustedPlan = Objects.requireNonNull(plan, "plan");
        ApprovedSourceRoots approvedRoots = Objects.requireNonNull(roots, "roots");
        ReportWorkspace reportWorkspace = Objects.requireNonNull(workspace, "workspace");
        CollectionRunControl runControl = Objects.requireNonNull(control, "control");
        SourceReadOperations readOperations = Objects.requireNonNull(sourceReads, "sourceReads");
        if (maximumCollectionBytes <= 0
                || maximumCollectionBytes > PRODUCT_MAX_COLLECTION_BYTES) {
            throw new IllegalArgumentException(
                    "Collection byte ceiling must be within the product maximum");
        }
        List<PlannedSourceFile> files = trustedPlan.files();
        long plannedBytes = plannedBytes(files);
        Counters counters = new Counters(files.size(), plannedBytes);
        runControl.begin(counters.snapshot(CollectionProgressSnapshot.State.RUNNING));

        List<FileCollectionResult.SourceOutcome> outcomes = new ArrayList<>(files.size());
        if (runControl.isCancellationRequested() && !files.isEmpty()) {
            cancelRemaining(files, 0, outcomes, counters);
            return terminalResult(trustedPlan, outcomes, counters, runControl);
        }
        if (plannedBytes > maximumCollectionBytes) {
            failRemainingForBudget(files, 0, outcomes, counters);
            return terminalResult(trustedPlan, outcomes, counters, runControl);
        }
        for (int index = 0; index < files.size(); index++) {
            if (runControl.isCancellationRequested()) {
                cancelRemaining(files, index, outcomes, counters);
                break;
            }

            PlannedSourceFile file = files.get(index);
            counters.activeOrdinal = OptionalInt.of(index + 1);
            runControl.publish(counters.snapshot(CollectionProgressSnapshot.State.RUNNING));
            long bytesBeforeFile = counters.processedBytes;
            try {
                CollectedSourceFile collected = WorkspaceSourceCollector.collect(
                        file,
                        approvedRoots,
                        reportWorkspace,
                        readOperations,
                        runControl,
                        copiedBytes -> {
                            if (copiedBytes > maximumCollectionBytes - bytesBeforeFile) {
                                throw new SourceCopyException(
                                        SourceCopyCode.COLLECTION_BYTE_LIMIT_EXCEEDED,
                                        reportWorkspace.sessionId(),
                                        file.file().root(),
                                        file.file().relativePath(),
                                        "File collection exceeded its workspace byte ceiling");
                            }
                            counters.processedBytes = Math.addExact(bytesBeforeFile, copiedBytes);
                            runControl.publish(counters.snapshot(
                                    CollectionProgressSnapshot.State.RUNNING));
                        });
                counters.processedBytes = Math.addExact(bytesBeforeFile, collected.byteCount());
                counters.successfulFiles++;
                counters.completedFiles++;
                counters.activeOrdinal = OptionalInt.empty();
                outcomes.add(FileCollectionResult.SourceOutcome.collected(index + 1, collected));
                runControl.publish(counters.snapshot(CollectionProgressSnapshot.State.RUNNING));
            } catch (SourceCopyException exception) {
                counters.activeOrdinal = OptionalInt.empty();
                if (exception.code() == SourceCopyCode.CANCELLED) {
                    cancelRemaining(files, index, outcomes, counters);
                    break;
                }
                if (exception.code() == SourceCopyCode.COLLECTION_BYTE_LIMIT_EXCEEDED) {
                    failRemainingForBudget(files, index, outcomes, counters);
                    break;
                }
                counters.failedFiles++;
                counters.completedFiles++;
                outcomes.add(FileCollectionResult.SourceOutcome.failed(
                        index + 1, file.provenances(), exception.code()));
                runControl.publish(counters.snapshot(CollectionProgressSnapshot.State.RUNNING));
            }
        }

        return terminalResult(trustedPlan, outcomes, counters, runControl);
    }

    private static long plannedBytes(List<PlannedSourceFile> files) {
        long total = 0;
        try {
            for (PlannedSourceFile file : files) {
                total = Math.addExact(total, file.file().observedSize());
            }
            return total;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Planned collection bytes overflow long", exception);
        }
    }

    private static void cancelRemaining(
            List<PlannedSourceFile> files,
            int firstCancelled,
            List<FileCollectionResult.SourceOutcome> outcomes,
            Counters counters) {
        for (int index = firstCancelled; index < files.size(); index++) {
            outcomes.add(FileCollectionResult.SourceOutcome.cancelled(
                    index + 1, files.get(index).provenances()));
            counters.cancelledFiles++;
            counters.completedFiles++;
        }
        counters.activeOrdinal = OptionalInt.empty();
    }

    private static void failRemainingForBudget(
            List<PlannedSourceFile> files,
            int firstFailed,
            List<FileCollectionResult.SourceOutcome> outcomes,
            Counters counters) {
        for (int index = firstFailed; index < files.size(); index++) {
            outcomes.add(FileCollectionResult.SourceOutcome.failed(
                    index + 1,
                    files.get(index).provenances(),
                    SourceCopyCode.COLLECTION_BYTE_LIMIT_EXCEEDED));
            counters.failedFiles++;
            counters.completedFiles++;
        }
        counters.activeOrdinal = OptionalInt.empty();
    }

    private static FileCollectionResult terminalResult(
            CategorySourcePlan plan,
            List<FileCollectionResult.SourceOutcome> outcomes,
            Counters counters,
            CollectionRunControl control) {
        FileCollectionResult.Status status = status(counters);
        CollectionProgressSnapshot terminal = counters.snapshot(
                CollectionProgressSnapshot.State.valueOf(status.name()));
        control.publish(terminal);
        return new FileCollectionResult(
                plan.providerId(),
                plan.providerVersion(),
                plan.categoryId(),
                status,
                outcomes,
                terminal);
    }

    private static FileCollectionResult.Status status(Counters counters) {
        if (counters.cancelledFiles > 0) {
            return FileCollectionResult.Status.CANCELLED;
        }
        if (counters.failedFiles == 0) {
            return FileCollectionResult.Status.COMPLETE;
        }
        return counters.successfulFiles == 0
                ? FileCollectionResult.Status.FAILED
                : FileCollectionResult.Status.PARTIAL;
    }

    private static final class Counters {
        private final int totalFiles;
        private final long plannedBytes;
        private int completedFiles;
        private int successfulFiles;
        private int failedFiles;
        private int cancelledFiles;
        private long processedBytes;
        private OptionalInt activeOrdinal = OptionalInt.empty();

        private Counters(int totalFiles, long plannedBytes) {
            this.totalFiles = totalFiles;
            this.plannedBytes = plannedBytes;
        }

        private CollectionProgressSnapshot snapshot(CollectionProgressSnapshot.State state) {
            return new CollectionProgressSnapshot(
                    state,
                    totalFiles,
                    completedFiles,
                    successfulFiles,
                    failedFiles,
                    cancelledFiles,
                    processedBytes,
                    plannedBytes,
                    activeOrdinal);
        }
    }
}
