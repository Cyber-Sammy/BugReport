package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Coordinates isolated selectors through one trusted immutable provider registry. */
public final class CategorySourcePlanner {
    private static final Comparator<PlannedSourceFile> FILE_ORDER = Comparator
            .comparing((PlannedSourceFile file) -> file.file().root())
            .thenComparing(file -> file.file().relativePath());
    private static final Comparator<SourcePlanConflict> CONFLICT_ORDER = Comparator
            .comparing(SourcePlanConflict::root)
            .thenComparing(SourcePlanConflict::relativePath);

    private final ProviderRegistrySnapshot registry;
    private final ApprovedSourceRoots roots;
    private final SupportedSide side;
    private final SourcePathInspection inspection;

    /** Binds category source planning to accepted providers, approved roots, and one physical side. */
    public CategorySourcePlanner(
            ProviderRegistrySnapshot registry,
            ApprovedSourceRoots roots,
            SupportedSide side) {
        this(registry, roots, side, NioSourcePathInspection.INSTANCE);
    }

    CategorySourcePlanner(
            ProviderRegistrySnapshot registry,
            ApprovedSourceRoots roots,
            SupportedSide side,
            SourcePathInspection inspection) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.roots = Objects.requireNonNull(roots, "roots");
        this.side = Objects.requireNonNull(side, "side");
        this.inspection = Objects.requireNonNull(inspection, "inspection");
    }

    /** Plans every source referenced by one trusted provider category. */
    public CategorySourcePlan plan(ProviderId providerId, CategoryId categoryId) {
        ProviderId requestedProvider = Objects.requireNonNull(providerId, "providerId");
        CategoryId requestedCategory = Objects.requireNonNull(categoryId, "categoryId");
        RegisteredProvider provider = registry.find(requestedProvider)
                .orElseThrow(
                        () ->
                                requestFailure(
                                        CategorySourcePlanRequestCode.PROVIDER_NOT_REGISTERED,
                                        requestedProvider,
                                        requestedCategory,
                                        "Cannot plan sources for an unregistered provider"));
        if (provider.support().state() == ProviderSupportState.DISABLED) {
            throw requestFailure(
                    CategorySourcePlanRequestCode.PROVIDER_DISABLED,
                    requestedProvider,
                    requestedCategory,
                    "Cannot plan sources for a disabled provider");
        }
        ProviderSpecification specification = provider.specification();
        CategorySpecification category =
                Optional.ofNullable(specification.categories().get(requestedCategory))
                        .orElseThrow(
                                () ->
                                        requestFailure(
                                                CategorySourcePlanRequestCode.CATEGORY_NOT_DECLARED,
                                                requestedProvider,
                                                requestedCategory,
                                                "Cannot plan sources for an undeclared category"));

        List<CoordinatedSourcePlan> sources = new ArrayList<>();
        Map<Path, FileGroup> groups = new LinkedHashMap<>();
        category.sourceIds().forEach(
                sourceId -> {
                    DiagnosticSourceSpecification source =
                            specification.sources().get(sourceId);
                    SourceProvenance provenance = provenance(specification, category, source);
                    SourceSelectionPlan selection =
                            SourceSelectorPlanner.plan(source, roots, side, inspection);
                    sources.add(new CoordinatedSourcePlan(provenance, selection));
                    if (selection instanceof FileSourcePlan filePlan) {
                        filePlan.files().forEach(
                                file ->
                                        groups.computeIfAbsent(
                                                        file.localPath(),
                                                        ignored -> new FileGroup(file))
                                                .add(file, provenance));
                    }
                });

        List<PlannedSourceFile> files = new ArrayList<>();
        List<SourcePlanConflict> conflicts = new ArrayList<>();
        groups.values().forEach(group -> group.publish(files, conflicts));
        files.sort(FILE_ORDER);
        conflicts.sort(CONFLICT_ORDER);
        return new CategorySourcePlan(
                specification.id(),
                specification.version(),
                category.id(),
                sources,
                files,
                conflicts);
    }

    private static SourceProvenance provenance(
            ProviderSpecification provider,
            CategorySpecification category,
            DiagnosticSourceSpecification source) {
        return new SourceProvenance(
                provider.id(),
                provider.version(),
                category.id(),
                source.id(),
                source.kind(),
                source.contentType(),
                source.privacy(),
                source.qualityRole(),
                source.inclusionDefault());
    }

    private static CategorySourcePlanException requestFailure(
            CategorySourcePlanRequestCode code,
            ProviderId providerId,
            CategoryId categoryId,
            String message) {
        return new CategorySourcePlanException(code, providerId, categoryId, message);
    }

    private static final class FileGroup {
        private final ResolvedSourceFile file;
        private final List<SourceProvenance> provenances = new ArrayList<>();
        private boolean identityMismatch;

        private FileGroup(ResolvedSourceFile file) {
            this.file = Objects.requireNonNull(file, "file");
        }

        private void add(ResolvedSourceFile candidate, SourceProvenance provenance) {
            identityMismatch |= !sameObservation(file, candidate);
            provenances.add(Objects.requireNonNull(provenance, "provenance"));
        }

        private void publish(
                List<PlannedSourceFile> files, List<SourcePlanConflict> conflicts) {
            if (identityMismatch) {
                conflicts.add(
                        new SourcePlanConflict(
                                SourcePlanConflictCode.PATH_CHANGED_BETWEEN_SELECTORS,
                                file.root(),
                                file.relativePath(),
                                provenances));
                return;
            }
            long representations = provenances.stream()
                    .map(SourceProvenance::contentType)
                    .distinct()
                    .count();
            if (representations > 1) {
                conflicts.add(
                        new SourcePlanConflict(
                                SourcePlanConflictCode.CONTENT_TYPE_MISMATCH,
                                file.root(),
                                file.relativePath(),
                                provenances));
                return;
            }
            files.add(new PlannedSourceFile(file, provenances));
        }

        private static boolean sameObservation(
                ResolvedSourceFile first, ResolvedSourceFile second) {
            return first.localPath().equals(second.localPath())
                    && first.root() == second.root()
                    && first.relativePath().equals(second.relativePath())
                    && first.observedFileKey().equals(second.observedFileKey())
                    && first.observedSize() == second.observedSize()
                    && first.observedLastModified().equals(second.observedLastModified());
        }
    }
}
