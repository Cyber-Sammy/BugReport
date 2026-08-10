package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.CategoryCollectionFingerprint;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
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
        ProviderRegistrySnapshot trustedRegistry = Objects.requireNonNull(registry, "registry");
        ReviewedCollectionPlan reviewed = Objects.requireNonNull(plan, "plan");
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
                CategoryCollectionFingerprint.from(reviewed));
    }

    private static long retainedFileBytes(FileCollectionResult files) {
        return files.outcomes().stream()
                .flatMap(outcome -> outcome.collectedFile().stream())
                .mapToLong(CollectedSourceFile::byteCount)
                .reduce(0L, Math::addExact);
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
