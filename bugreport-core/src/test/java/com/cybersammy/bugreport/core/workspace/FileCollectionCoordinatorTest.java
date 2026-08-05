package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.FilenamePattern;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.CategorySourcePlan;
import com.cybersammy.bugreport.core.source.CategorySourcePlanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileCollectionCoordinatorTest {
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");

    @TempDir Path temporaryDirectory;

    @Test
    void collectsEveryUniqueFileAndPublishesTerminalProgress() throws IOException {
        Fixture fixture = fixture("aa", "bbb");
        CollectionRunControl control = new CollectionRunControl();

        FileCollectionResult result = FileCollectionCoordinator.collect(
                fixture.plan(), fixture.roots(), fixture.workspace(), control);

        assertEquals(FileCollectionResult.Status.COMPLETE, result.status());
        assertEquals(
                List.of(
                        FileCollectionResult.SourceStatus.COLLECTED,
                        FileCollectionResult.SourceStatus.COLLECTED),
                result.outcomes().stream()
                        .map(FileCollectionResult.SourceOutcome::status)
                        .toList());
        assertEquals(CollectionProgressSnapshot.State.COMPLETE, result.progress().state());
        assertEquals(2, result.progress().totalFiles());
        assertEquals(2, result.progress().completedFiles());
        assertEquals(2, result.progress().successfulFiles());
        assertEquals(5, result.progress().processedBytes());
        assertEquals(5, result.progress().plannedBytes());
        assertEquals(OptionalInt.empty(), result.progress().activeFileOrdinal());
        assertEquals(result.progress(), control.progress());
        assertFalse(control.requestCancellation());
        assertEquals(3, workspaceEntryCount(fixture.workspace()));
        assertThrows(
                IllegalStateException.class,
                () -> FileCollectionCoordinator.collect(
                        fixture.plan(), fixture.roots(), fixture.workspace(), control));
    }

    @Test
    void cancellationBeforeStartMarksEveryFileWithoutCreatingArtifacts()
            throws IOException {
        Fixture fixture = fixture("first", "second");
        CollectionRunControl control = new CollectionRunControl();
        assertTrue(control.requestCancellation());
        assertFalse(control.requestCancellation());

        FileCollectionResult result = FileCollectionCoordinator.collect(
                fixture.plan(), fixture.roots(), fixture.workspace(), control);

        assertEquals(FileCollectionResult.Status.CANCELLED, result.status());
        assertEquals(2, result.progress().cancelledFiles());
        assertEquals(0, result.progress().processedBytes());
        assertTrue(result.outcomes().stream()
                .allMatch(outcome ->
                        outcome.status() == FileCollectionResult.SourceStatus.CANCELLED
                                && outcome.failureCode().orElseThrow()
                                        == SourceCopyCode.CANCELLED));
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void cancellationAfterStartingAFileCancelsItAndEveryRemainingFile()
            throws IOException {
        Fixture fixture = fixture("first", "second");
        CollectionRunControl control = new CollectionRunControl();
        SourceReadOperations cancellingOpen = (roots, planned) -> {
            SourceReadHandle handle = NioSourceReadOperations.INSTANCE.open(roots, planned);
            control.requestCancellation();
            return handle;
        };

        FileCollectionResult result = FileCollectionCoordinator.collect(
                fixture.plan(),
                fixture.roots(),
                fixture.workspace(),
                control,
                cancellingOpen);

        assertEquals(FileCollectionResult.Status.CANCELLED, result.status());
        assertEquals(2, result.progress().cancelledFiles());
        assertEquals(0, result.progress().processedBytes());
        assertTrue(result.outcomes().stream()
                .allMatch(outcome ->
                        outcome.status() == FileCollectionResult.SourceStatus.CANCELLED));
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void oneChangedSourceDoesNotPreventUnrelatedCollection() throws IOException {
        Fixture fixture = fixture("first", "second");
        Files.writeString(fixture.firstSource(), "changed");

        FileCollectionResult result = FileCollectionCoordinator.collect(
                fixture.plan(),
                fixture.roots(),
                fixture.workspace(),
                new CollectionRunControl());

        assertEquals(FileCollectionResult.Status.PARTIAL, result.status());
        assertEquals(
                List.of(
                        FileCollectionResult.SourceStatus.FAILED,
                        FileCollectionResult.SourceStatus.COLLECTED),
                result.outcomes().stream()
                        .map(FileCollectionResult.SourceOutcome::status)
                        .toList());
        assertEquals(
                SourceCopyCode.SOURCE_CHANGED,
                result.outcomes().getFirst().failureCode().orElseThrow());
        assertEquals(1, result.progress().successfulFiles());
        assertEquals(1, result.progress().failedFiles());
        assertEquals("second".length(), result.progress().processedBytes());
        assertEquals(2, workspaceEntryCount(fixture.workspace()));
    }

    @Test
    void noSuccessfulFilesProducesFailedResult() throws IOException {
        Fixture fixture = fixture("first", "second");
        Files.writeString(fixture.firstSource(), "changed-first");
        Files.writeString(fixture.secondSource(), "changed-second");

        FileCollectionResult result = FileCollectionCoordinator.collect(
                fixture.plan(),
                fixture.roots(),
                fixture.workspace(),
                new CollectionRunControl());

        assertEquals(FileCollectionResult.Status.FAILED, result.status());
        assertEquals(CollectionProgressSnapshot.State.FAILED, result.progress().state());
        assertEquals(2, result.progress().failedFiles());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void plannedAggregateAboveCollectionBudgetFailsBeforeWriting() throws IOException {
        Fixture fixture = fixture("aaa", "bbb");

        FileCollectionResult result = FileCollectionCoordinator.collect(
                fixture.plan(),
                fixture.roots(),
                fixture.workspace(),
                new CollectionRunControl(),
                NioSourceReadOperations.INSTANCE,
                5);

        assertEquals(FileCollectionResult.Status.FAILED, result.status());
        assertEquals(0, result.progress().processedBytes());
        assertEquals(6, result.progress().plannedBytes());
        assertTrue(result.outcomes().stream()
                .allMatch(outcome ->
                        outcome.failureCode().orElseThrow()
                                == SourceCopyCode.COLLECTION_BYTE_LIMIT_EXCEEDED));
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void cancellationDuringStreamingRemovesPartialArtifact() throws IOException {
        String contents = "x".repeat(WorkspaceSourceCollector.COPY_BUFFER_BYTES * 2);
        Fixture fixture = fixture(contents, "second");
        CollectionRunControl control = new CollectionRunControl();
        AtomicBoolean cancellationRequested = new AtomicBoolean();

        SourceCopyException failure = assertThrows(
                SourceCopyException.class,
                () -> WorkspaceSourceCollector.collect(
                        fixture.plan().files().getFirst(),
                        fixture.roots(),
                        fixture.workspace(),
                        NioSourceReadOperations.INSTANCE,
                        control,
                        copiedBytes -> {
                            if (cancellationRequested.compareAndSet(false, true)) {
                                control.requestCancellation();
                            }
                        }));

        assertEquals(SourceCopyCode.CANCELLED, failure.code());
        assertOnlyMarker(fixture.workspace());
    }

    private Fixture fixture(String firstContents, String secondContents) throws IOException {
        Path game = Files.createDirectory(temporaryDirectory.resolve("game-" + Suffix.next()));
        Path logs = Files.createDirectory(game.resolve("logs"));
        Files.createDirectory(game.resolve("crash-reports"));
        Files.createDirectory(game.resolve("config"));
        Path first = logs.resolve("a.log");
        Path second = logs.resolve("b.log");
        Files.writeString(first, firstContents);
        Files.writeString(second, secondContents);
        ApprovedSourceRoots roots = ApprovedSourceRoots.forGameDirectory(game.toAbsolutePath());
        DiagnosticSourceSpecification source = DiagnosticSourceSpecification.filteredLogDirectory(
                        DiagnosticSourceId.of("logs"), FilenamePattern.of("*.log"))
                .labelKey(LocalizationKey.of("example.source.logs"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .constraints(CollectionConstraints.builder()
                        .maxMatchedFiles(4)
                        .maxBytesPerFile(4L * 1024 * 1024)
                        .maxTotalBytes(8L * 1024 * 1024)
                        .build())
                .build();
        ProviderSpecification specification = specification(source);
        CategorySourcePlan plan = new CategorySourcePlanner(
                        registry(specification), roots, SupportedSide.PHYSICAL_CLIENT)
                .plan(PROVIDER_ID, CATEGORY_ID);
        ReportSessionId sessionId = ReportSessionId.parse(
                "11111111-1111-1111-1111-" + String.format("%012d", Suffix.next()));
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("workspaces-" + Suffix.next()).toAbsolutePath())
                .create(sessionId);
        return new Fixture(first, second, roots, plan, workspace);
    }

    private static ProviderSpecification specification(
            DiagnosticSourceSpecification source) {
        return ProviderSpecification.builder(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addSource(source)
                .addCategory(CategorySpecification.builder(
                                CATEGORY_ID,
                                LocalizationKey.of("example.category.general"))
                        .useSource(source.id())
                        .build())
                .build();
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
        return ProviderRegistry.createSnapshot(List.of(new DiscoveredProvider(
                specification.id().namespace(), "FileCollectionCoordinatorFixture", provider)));
    }

    private static int workspaceEntryCount(ReportWorkspace workspace) throws IOException {
        try (var entries = Files.list(workspace.directory())) {
            return Math.toIntExact(entries.count());
        }
    }

    private static void assertOnlyMarker(ReportWorkspace workspace) throws IOException {
        try (var entries = Files.list(workspace.directory())) {
            assertEquals(
                    List.of(FileReportWorkspaceStore.MARKER_FILENAME),
                    entries.map(path -> path.getFileName().toString()).sorted().toList());
        }
    }

    private record Fixture(
            Path firstSource,
            Path secondSource,
            ApprovedSourceRoots roots,
            CategorySourcePlan plan,
            ReportWorkspace workspace) {}

    private static final class Suffix {
        private static int value;

        private Suffix() {}

        private static synchronized int next() {
            return value++;
        }
    }
}
