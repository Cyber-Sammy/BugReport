package com.cybersammy.bugreport.core.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CapabilityRequirement;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.FilenamePattern;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CategorySourcePlannerTest {
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");

    @TempDir Path temporaryDirectory;

    @Test
    void retainsCanonicalProvenanceAndIsolatesMissingSources() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/present.log"), "present");
        DiagnosticSourceSpecification requiredMissing =
                exact(
                        "required_missing",
                        "missing-required.log",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.REQUIRED,
                        InclusionDefault.EXCLUDED);
        DiagnosticSourceSpecification optionalMissing =
                exact(
                        "optional_missing",
                        "missing-optional.log",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.OPTIONAL,
                        InclusionDefault.EXCLUDED);
        DiagnosticSourceSpecification present =
                exact(
                        "present",
                        "present.log",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.RECOMMENDED,
                        InclusionDefault.EXCLUDED);

        CategorySourcePlan plan =
                planner(specification(present, requiredMissing, optionalMissing), roots)
                        .plan(PROVIDER_ID, CATEGORY_ID);

        assertEquals(
                List.of("optional_missing", "present", "required_missing"),
                plan.sources().stream()
                        .map(source -> source.provenance().sourceId().value())
                        .toList());
        assertEquals(ReportQualityRole.OPTIONAL, plan.sources().get(0).provenance().qualityRole());
        assertInstanceOf(FileSourcePlan.class, plan.sources().get(1).selection());
        UnavailableSourcePlan missing =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        plan.sources().get(2).selection());
        assertEquals(SourceSelectionFailureCode.SOURCE_MISSING, missing.code());
        assertEquals(ReportQualityRole.REQUIRED, plan.sources().get(2).provenance().qualityRole());
        assertEquals(1, plan.files().size());
        assertEquals("present", plan.files().getFirst().provenances().getFirst().sourceId().value());
    }

    @Test
    void reviewedPlanAllowsOnlyDeclaredAvailableSources() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/included.log"), "included");
        DiagnosticSourceSpecification included = exact(
                "included", "included.log", DiagnosticContentType.TEXT,
                PrivacyClassification.PERSONAL, ReportQualityRole.OPTIONAL,
                InclusionDefault.EXCLUDED);
        DiagnosticSourceSpecification unavailable = exact(
                "unavailable", "missing.log", DiagnosticContentType.TEXT,
                PrivacyClassification.PERSONAL, ReportQualityRole.OPTIONAL,
                InclusionDefault.EXCLUDED);
        CategorySourcePlan plan = planner(specification(included, unavailable), roots)
                .plan(PROVIDER_ID, CATEGORY_ID);

        ReviewedCollectionPlan defaults = ReviewedCollectionPlan.defaults(plan);

        assertEquals(Set.of(), defaults.includedSourceIds());
        assertEquals(Set.of(DiagnosticSourceId.of("included")), ReviewedCollectionPlan.of(
                plan, Set.of(DiagnosticSourceId.of("included"))).includedSourceIds());
        assertThrows(IllegalArgumentException.class, () -> ReviewedCollectionPlan.of(
                plan, Set.of(DiagnosticSourceId.of("unavailable"))));
        assertThrows(IllegalArgumentException.class, () -> ReviewedCollectionPlan.of(
                plan, Set.of(DiagnosticSourceId.of("unknown"))));
    }

    @Test
    void mergesDuplicateFileProvenanceAndUsesConservativeMetadata() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/shared.log"), "shared");
        DiagnosticSourceSpecification optional =
                filtered(
                        "a_optional",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.OPTIONAL,
                        InclusionDefault.EXCLUDED);
        DiagnosticSourceSpecification required =
                exact(
                        "b_required",
                        "shared.log",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.SENSITIVE,
                        ReportQualityRole.REQUIRED,
                        InclusionDefault.EXCLUDED);

        CategorySourcePlan plan =
                planner(specification(required, optional), roots)
                        .plan(PROVIDER_ID, CATEGORY_ID);

        assertEquals(1, plan.files().size());
        PlannedSourceFile file = plan.files().getFirst();
        assertTrue(file.duplicate());
        assertEquals(
                List.of("a_optional", "b_required"),
                file.provenances().stream()
                        .map(provenance -> provenance.sourceId().value())
                        .toList());
        assertEquals(PrivacyClassification.SENSITIVE, file.privacy());
        assertEquals(ReportQualityRole.REQUIRED, file.qualityRole());
        assertEquals(InclusionDefault.EXCLUDED, file.inclusionDefault());
        assertEquals(List.of(), plan.conflicts());
    }

    @Test
    void excludesDuplicateFileWithConflictingContentTypes() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/shared.log"), "shared");
        DiagnosticSourceSpecification json =
                exact(
                        "as_json",
                        "shared.log",
                        DiagnosticContentType.JSON,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.RECOMMENDED,
                        InclusionDefault.EXCLUDED);
        DiagnosticSourceSpecification text =
                exact(
                        "as_text",
                        "shared.log",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.RECOMMENDED,
                        InclusionDefault.EXCLUDED);

        CategorySourcePlan plan =
                planner(specification(text, json), roots).plan(PROVIDER_ID, CATEGORY_ID);

        assertEquals(List.of(), plan.files());
        SourcePlanConflict conflict = plan.conflicts().getFirst();
        assertEquals(SourcePlanConflictCode.CONTENT_TYPE_MISMATCH, conflict.code());
        assertEquals(LogicalRoot.GAME_LOGS, conflict.root());
        assertEquals(RelativePath.of("shared.log"), conflict.relativePath());
        assertEquals(
                List.of("as_json", "as_text"),
                conflict.provenances().stream()
                        .map(provenance -> provenance.sourceId().value())
                        .toList());
    }

    @Test
    void excludesPathWhoseIdentityChangesBetweenSelectors() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path shared = temporaryDirectory.resolve("logs/shared.log");
        Files.writeString(shared, "first");
        DiagnosticSourceSpecification first =
                exact(
                        "a_first",
                        "shared.log",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.RECOMMENDED,
                        InclusionDefault.EXCLUDED);
        DiagnosticSourceSpecification second =
                exact(
                        "b_second",
                        "shared.log",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.RECOMMENDED,
                        InclusionDefault.EXCLUDED);
        SourcePathInspection changingInspection =
                new DelegatingInspection() {
                    private int noFollowReads;

                    @Override
                    public BasicFileAttributes readAttributes(Path path, boolean followLinks)
                            throws IOException {
                        if (path.equals(shared) && !followLinks && ++noFollowReads == 3) {
                            Files.writeString(shared, "replacement-with-a-different-size");
                        }
                        return super.readAttributes(path, followLinks);
                    }
                };
        CategorySourcePlanner planner =
                new CategorySourcePlanner(
                        registry(specification(first, second)),
                        roots,
                        SupportedSide.PHYSICAL_CLIENT,
                        changingInspection);

        CategorySourcePlan plan = planner.plan(PROVIDER_ID, CATEGORY_ID);

        assertEquals(List.of(), plan.files());
        SourcePlanConflict conflict = plan.conflicts().getFirst();
        assertEquals(
                SourcePlanConflictCode.PATH_CHANGED_BETWEEN_SELECTORS,
                conflict.code());
        assertEquals(
                List.of("a_first", "b_second"),
                conflict.provenances().stream()
                        .map(provenance -> provenance.sourceId().value())
                        .toList());
        FileSourcePlan firstPlan =
                assertInstanceOf(FileSourcePlan.class, plan.sources().get(0).selection());
        FileSourcePlan secondPlan =
                assertInstanceOf(FileSourcePlan.class, plan.sources().get(1).selection());
        assertEquals(5, firstPlan.files().getFirst().observedSize());
        assertTrue(secondPlan.files().getFirst().observedSize() > 5);
    }

    @Test
    void outputDoesNotDependOnProviderBuilderInsertionOrder() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/a.log"), "a");
        Files.writeString(temporaryDirectory.resolve("logs/b.log"), "b");
        DiagnosticSourceSpecification first =
                exact(
                        "first",
                        "a.log",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.OPTIONAL,
                        InclusionDefault.EXCLUDED);
        DiagnosticSourceSpecification second =
                exact(
                        "second",
                        "b.log",
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.OPTIONAL,
                        InclusionDefault.EXCLUDED);

        CategorySourcePlan forward =
                planner(specification(first, second), roots).plan(PROVIDER_ID, CATEGORY_ID);
        CategorySourcePlan reverse =
                planner(specification(second, first), roots).plan(PROVIDER_ID, CATEGORY_ID);

        assertEquals(sourceIds(forward), sourceIds(reverse));
        assertEquals(filePaths(forward), filePaths(reverse));
    }

    @Test
    void rejectsUntrustedProviderAndUnknownCategoryBeforePlanning() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        ProviderRegistrySnapshot registry = registry(specification());
        CategorySourcePlanner planner = new CategorySourcePlanner(
                registry, roots, SupportedSide.PHYSICAL_CLIENT);

        CategorySourcePlanException providerFailure =
                assertThrows(
                        CategorySourcePlanException.class,
                        () -> planner.plan(ProviderId.parse("other"), CATEGORY_ID));
        assertEquals(
                CategorySourcePlanRequestCode.PROVIDER_NOT_REGISTERED,
                providerFailure.code());

        CategoryId unknown = CategoryId.of("unknown");
        CategorySourcePlanException categoryFailure =
                assertThrows(
                        CategorySourcePlanException.class,
                        () -> planner.plan(PROVIDER_ID, unknown));
        assertEquals(
                CategorySourcePlanRequestCode.CATEGORY_NOT_DECLARED,
                categoryFailure.code());
        assertEquals(PROVIDER_ID, categoryFailure.providerId());
        assertEquals(unknown, categoryFailure.categoryId());

        ProviderSpecification disabledSpecification = disabledSpecification();
        CategorySourcePlanner disabledPlanner =
                new CategorySourcePlanner(
                        registry(disabledSpecification),
                        roots,
                        SupportedSide.PHYSICAL_CLIENT);
        CategorySourcePlanException disabledFailure =
                assertThrows(
                        CategorySourcePlanException.class,
                        () -> disabledPlanner.plan(PROVIDER_ID, CATEGORY_ID));
        assertEquals(CategorySourcePlanRequestCode.PROVIDER_DISABLED, disabledFailure.code());
    }

    @Test
    void propagatesPhysicalSideToDynamicSourcePlanning() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/server.log"), "server");
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<SupportedSide> observedSide = new AtomicReference<>();
        DiagnosticSourceSpecification dynamic = DiagnosticSourceSpecification.dynamicFiles(
                        DiagnosticSourceId.of("server_dynamic"),
                        LogicalRoot.GAME_LOGS,
                        (request, sink) -> {
                            invocations.incrementAndGet();
                            observedSide.set(request.side());
                            sink.emit(RelativePath.of("server.log"));
                        })
                .labelKey(LocalizationKey.of("example.source.server_dynamic"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.DEDICATED_SERVER)
                .constraints(CollectionConstraints.builder()
                        .maxMatchedFiles(1)
                        .maxBytesPerFile(1024)
                        .maxTotalBytes(1024)
                        .callbackTimeout(Duration.ofMillis(250))
                        .build())
                .build();
        ProviderSpecification specification = specificationForSide(
                SupportedSide.DEDICATED_SERVER, dynamic);

        CategorySourcePlan serverPlan = new CategorySourcePlanner(
                        registry(specification),
                        roots,
                        SupportedSide.DEDICATED_SERVER)
                .plan(PROVIDER_ID, CATEGORY_ID);

        assertInstanceOf(FileSourcePlan.class, serverPlan.sources().getFirst().selection());
        assertEquals(SupportedSide.DEDICATED_SERVER, observedSide.get());
        assertEquals(1, invocations.get());

        CategorySourcePlan clientPlan = new CategorySourcePlanner(
                        registry(specification),
                        roots,
                        SupportedSide.PHYSICAL_CLIENT)
                .plan(PROVIDER_ID, CATEGORY_ID);
        UnavailableSourcePlan unsupported = assertInstanceOf(
                UnavailableSourcePlan.class,
                clientPlan.sources().getFirst().selection());
        assertEquals(SourceSelectionFailureCode.UNSUPPORTED_SIDE, unsupported.code());
        assertEquals(1, invocations.get());
    }

    private static List<String> sourceIds(CategorySourcePlan plan) {
        return plan.sources().stream()
                .map(source -> source.provenance().sourceId().value())
                .toList();
    }

    private static List<String> filePaths(CategorySourcePlan plan) {
        return plan.files().stream().map(file -> file.file().relativePath().value()).toList();
    }

    private static DiagnosticSourceSpecification exact(
            String id,
            String path,
            DiagnosticContentType contentType,
            PrivacyClassification privacy,
            ReportQualityRole qualityRole,
            InclusionDefault inclusionDefault) {
        return DiagnosticSourceSpecification.exactFile(
                        DiagnosticSourceId.of(id),
                        LogicalRoot.GAME_LOGS,
                        RelativePath.of(path))
                .labelKey(LocalizationKey.of("example.source." + id))
                .privacy(privacy)
                .contentType(contentType)
                .qualityRole(qualityRole)
                .inclusionDefault(inclusionDefault)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .build();
    }

    private static DiagnosticSourceSpecification filtered(
            String id,
            DiagnosticContentType contentType,
            PrivacyClassification privacy,
            ReportQualityRole qualityRole,
            InclusionDefault inclusionDefault) {
        return DiagnosticSourceSpecification.filteredLogDirectory(
                        DiagnosticSourceId.of(id), FilenamePattern.of("shared.log"))
                .labelKey(LocalizationKey.of("example.source." + id))
                .privacy(privacy)
                .contentType(contentType)
                .qualityRole(qualityRole)
                .inclusionDefault(inclusionDefault)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .build();
    }

    private static ProviderSpecification specification(
            DiagnosticSourceSpecification... sources) {
        return specificationForSide(SupportedSide.PHYSICAL_CLIENT, sources);
    }

    private static ProviderSpecification specificationForSide(
            SupportedSide side, DiagnosticSourceSpecification... sources) {
        CategorySpecification.Builder category = CategorySpecification.builder(
                CATEGORY_ID, LocalizationKey.of("example.category.general"));
        Arrays.stream(sources).forEach(source -> category.useSource(source.id()));
        ProviderSpecification.Builder provider = ProviderSpecification.builder(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        LocalizationKey.of("example.provider"))
                .supportSide(side)
                .addCategory(category.build());
        Arrays.stream(sources).forEach(provider::addSource);
        return provider.build();
    }

    private static ProviderSpecification disabledSpecification() {
        return ProviderSpecification.builder(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addCategory(
                        CategorySpecification.builder(
                                        CATEGORY_ID,
                                        LocalizationKey.of("example.category.general"))
                                .build())
                .requireCapability(
                        new CapabilityRequirement(
                                CapabilityId.of("bugreport:missing"),
                                CapabilityVersion.parse("1.0"),
                                true))
                .build();
    }

    private static CategorySourcePlanner planner(
            ProviderSpecification specification, ApprovedSourceRoots roots) {
        return new CategorySourcePlanner(
                registry(specification), roots, SupportedSide.PHYSICAL_CLIENT);
    }

    private static ProviderRegistrySnapshot registry(ProviderSpecification specification) {
        BugReportProvider provider = new BugReportProvider() {
            @Override
            public String providerId() {
                return specification.id().value();
            }

            @Override
            public String providerVersion() {
                return specification.version().value();
            }

            @Override
            public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        return ProviderRegistry.createSnapshot(
                List.of(
                        new DiscoveredProvider(
                                specification.id().namespace(),
                                "CategorySourcePlannerFixture",
                                provider)));
    }

    private ApprovedSourceRoots createRoots() throws IOException {
        return ApprovedSourceRoots.of(
                Files.createDirectory(temporaryDirectory.resolve("logs")),
                Files.createDirectory(temporaryDirectory.resolve("crash-reports")),
                Files.createDirectory(temporaryDirectory.resolve("config")));
    }

    private static class DelegatingInspection implements SourcePathInspection {
        @Override
        public BasicFileAttributes readAttributes(Path path, boolean followLinks)
                throws IOException {
            return NioSourcePathInspection.INSTANCE.readAttributes(path, followLinks);
        }

        @Override
        public Path realPath(Path path, boolean followLinks) throws IOException {
            return NioSourcePathInspection.INSTANCE.realPath(path, followLinks);
        }

        @Override
        public FileStore fileStore(Path path) throws IOException {
            return NioSourcePathInspection.INSTANCE.fileStore(path);
        }
    }
}
