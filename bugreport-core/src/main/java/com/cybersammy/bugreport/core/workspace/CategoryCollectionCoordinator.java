package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.CategoryCollectionFingerprint;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
import com.cybersammy.bugreport.core.source.ScreenshotCollectionRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/** Executes one reviewed file and generated diagnostic plan under a shared report byte budget. */
public final class CategoryCollectionCoordinator {
    private CategoryCollectionCoordinator() {}

    public static CategoryCollectionResult collect(
            ProviderRegistrySnapshot registry,
            ReviewedCollectionPlan plan,
            ApprovedSourceRoots roots,
            SupportedSide side,
            ReportWorkspace workspace,
            CategoryCollectionRunControl control,
            GameThreadDispatcher dispatcher) {
        return collect(
                registry,
                plan,
                ScreenshotCollectionRequest.from(plan, List.of()),
                roots,
                side,
                workspace,
                control,
                dispatcher,
                workspace.directory());
    }

    /** Executes exact files, explicit screenshots, and generators under one shared byte budget. */
    public static CategoryCollectionResult collect(
            ProviderRegistrySnapshot registry,
            ReviewedCollectionPlan plan,
            ScreenshotCollectionRequest screenshots,
            ApprovedSourceRoots roots,
            SupportedSide side,
            ReportWorkspace workspace,
            CategoryCollectionRunControl control,
            GameThreadDispatcher dispatcher,
            Path screenshotsDirectory) {
        ProviderRegistrySnapshot trustedRegistry = Objects.requireNonNull(registry, "registry");
        ReviewedCollectionPlan reviewed = Objects.requireNonNull(plan, "plan");
        ScreenshotCollectionRequest selectedScreenshots =
                Objects.requireNonNull(screenshots, "screenshots");
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(workspace, "workspace");
        CategoryCollectionRunControl run = Objects.requireNonNull(control, "control");
        Objects.requireNonNull(dispatcher, "dispatcher");
        if (reviewed.collectionPlan().side() != side) {
            throw new IllegalArgumentException("Reviewed collection plan belongs to another physical side");
        }

        run.begin(reviewed.includedGenerators().size());
        FileCollectionResult files = FileCollectionCoordinator.collect(
                reviewed.selectedFilePlan(), roots, workspace, run.fileControl());
        long remainingBytes = FileCollectionCoordinator.PRODUCT_MAX_COLLECTION_BYTES
                - retainedFileBytes(files);
        FileCollectionResult screenshotFiles = ScreenshotAttachmentCollector.collect(
                selectedScreenshots,
                Objects.requireNonNull(screenshotsDirectory, "screenshotsDirectory"),
                workspace,
                run,
                remainingBytes);
        files = mergeFiles(files, screenshotFiles);
        remainingBytes = FileCollectionCoordinator.PRODUCT_MAX_COLLECTION_BYTES
                - retainedFileBytes(files);
        run.beginGenerated();
        CategoryGeneratedDiagnosticResult generated;
        try {
            generated = CategoryGeneratedDiagnosticExecutor.executeAsync(
                            trustedRegistry,
                            files.providerId(),
                            files.categoryId(),
                            side,
                            workspace,
                            run,
                            remainingBytes,
                            dispatcher,
                            reviewed.includedGeneratorIds())
                    .join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof Error fatal) {
                throw fatal;
            }
            throw failure.getCause() instanceof RuntimeException runtime ? runtime : failure;
        }
        run.generatedComplete(generated.outcomes().size());
        CategoryCollectionResult.Status status = run.finish(
                status(files, generated), generated.outcomes().size());
        return new CategoryCollectionResult(
                status,
                files,
                generated,
                CategoryCollectionFingerprint.from(reviewed, selectedScreenshots));
    }

    /**
     * Adapts a genuine file-only coordinator result for compatibility callers.
     *
     * <p>The reviewed plan must select no generators and the file result must carry the exact
     * selected file fingerprint. No caller-provided category status or fingerprint is accepted.
     */
    public static CategoryCollectionResult fromFileOnly(
            ReviewedCollectionPlan plan, FileCollectionResult files) {
        ReviewedCollectionPlan reviewed = Objects.requireNonNull(plan, "plan");
        FileCollectionResult result = Objects.requireNonNull(files, "files");
        if (!reviewed.plan().providerId().equals(result.providerId())
                || !reviewed.plan().providerVersion().equals(result.providerVersion())
                || !reviewed.plan().categoryId().equals(result.categoryId())
                || !reviewed.includedGeneratorIds().isEmpty()
                || result.planFingerprint()
                        .filter(com.cybersammy.bugreport.core.source.CollectionPlanFingerprint
                                .from(reviewed.selectedFilePlan())::equals)
                        .isEmpty()) {
            throw new IllegalArgumentException(
                    "File-only result must match an exact plan without generators");
        }
        return new CategoryCollectionResult(
                switch (result.status()) {
                    case COMPLETE -> CategoryCollectionResult.Status.COMPLETE;
                    case PARTIAL -> CategoryCollectionResult.Status.PARTIAL;
                    case FAILED -> CategoryCollectionResult.Status.FAILED;
                    case CANCELLED -> CategoryCollectionResult.Status.CANCELLED;
                },
                result,
                new CategoryGeneratedDiagnosticResult(
                        result.providerId(), result.categoryId(), java.util.List.of(), 0),
                CategoryCollectionFingerprint.from(reviewed));
    }

    private static long retainedFileBytes(FileCollectionResult files) {
        return files.outcomes().stream()
                .flatMap(outcome -> outcome.collectedFile().stream())
                .mapToLong(CollectedSourceFile::byteCount)
                .reduce(0L, Math::addExact);
    }

    private static FileCollectionResult mergeFiles(
            FileCollectionResult plannedFiles, FileCollectionResult screenshots) {
        if (screenshots.outcomes().isEmpty()) {
            return plannedFiles;
        }
        if (!plannedFiles.providerId().equals(screenshots.providerId())
                || !plannedFiles.providerVersion().equals(screenshots.providerVersion())
                || !plannedFiles.categoryId().equals(screenshots.categoryId())) {
            throw new IllegalArgumentException("Screenshot result belongs to another category");
        }
        List<FileCollectionResult.SourceOutcome> outcomes = new ArrayList<>();
        plannedFiles.outcomes().forEach(outcome -> outcomes.add(copyOutcome(outcome, outcomes.size() + 1)));
        screenshots.outcomes().forEach(outcome -> outcomes.add(copyOutcome(outcome, outcomes.size() + 1)));
        int successes = (int) outcomes.stream()
                .filter(outcome -> outcome.status() == FileCollectionResult.SourceStatus.COLLECTED)
                .count();
        int failures = (int) outcomes.stream()
                .filter(outcome -> outcome.status() == FileCollectionResult.SourceStatus.FAILED)
                .count();
        int cancellations = outcomes.size() - successes - failures;
        FileCollectionResult.Status status = cancellations > 0
                ? FileCollectionResult.Status.CANCELLED
                : failures == 0
                        ? FileCollectionResult.Status.COMPLETE
                        : successes == 0
                                ? FileCollectionResult.Status.FAILED
                                : FileCollectionResult.Status.PARTIAL;
        long processed = Math.addExact(
                plannedFiles.progress().processedBytes(), screenshots.progress().processedBytes());
        long planned = Math.addExact(
                plannedFiles.progress().plannedBytes(), screenshots.progress().plannedBytes());
        return new FileCollectionResult(
                plannedFiles.providerId(),
                plannedFiles.providerVersion(),
                plannedFiles.categoryId(),
                plannedFiles.planFingerprint().orElse(null),
                status,
                outcomes,
                new CollectionProgressSnapshot(
                        switch (status) {
                            case COMPLETE -> CollectionProgressSnapshot.State.COMPLETE;
                            case PARTIAL -> CollectionProgressSnapshot.State.PARTIAL;
                            case FAILED -> CollectionProgressSnapshot.State.FAILED;
                            case CANCELLED -> CollectionProgressSnapshot.State.CANCELLED;
                        },
                        outcomes.size(),
                        outcomes.size(),
                        successes,
                        failures,
                        cancellations,
                        processed,
                        planned,
                        OptionalInt.empty()));
    }

    private static FileCollectionResult.SourceOutcome copyOutcome(
            FileCollectionResult.SourceOutcome source, int ordinal) {
        return switch (source.status()) {
            case COLLECTED -> FileCollectionResult.SourceOutcome.collected(
                    ordinal, source.collectedFile().orElseThrow());
            case FAILED -> FileCollectionResult.SourceOutcome.failed(
                    ordinal, source.provenances(), source.failureCode().orElseThrow());
            case CANCELLED -> FileCollectionResult.SourceOutcome.cancelled(
                    ordinal, source.provenances());
        };
    }

    private static CategoryCollectionResult.Status status(
            FileCollectionResult files,
            CategoryGeneratedDiagnosticResult generated) {
        if (files.status() == FileCollectionResult.Status.CANCELLED
                || generated.outcomes().stream().anyMatch(outcome ->
                        outcome.status() == GeneratedDiagnosticOutcomeStatus.CANCELLED)) {
            return CategoryCollectionResult.Status.CANCELLED;
        }
        long successes = files.outcomes().stream()
                        .filter(outcome -> outcome.status() == FileCollectionResult.SourceStatus.COLLECTED)
                        .count()
                + generated.outcomes().stream()
                        .filter(outcome -> outcome.status() == GeneratedDiagnosticOutcomeStatus.COLLECTED)
                        .count();
        long failures = files.outcomes().size()
                        - files.outcomes().stream()
                                .filter(outcome -> outcome.status()
                                        == FileCollectionResult.SourceStatus.COLLECTED)
                                .count()
                + generated.outcomes().stream()
                        .filter(outcome -> outcome.status() != GeneratedDiagnosticOutcomeStatus.COLLECTED)
                        .count();
        if (failures == 0) {
            return CategoryCollectionResult.Status.COMPLETE;
        }
        return successes == 0
                ? CategoryCollectionResult.Status.FAILED
                : CategoryCollectionResult.Status.PARTIAL;
    }
}
