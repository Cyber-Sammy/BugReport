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
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.GeneratorExecutionContext;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.session.ReportSession;
import com.cybersammy.bugreport.core.session.ReportSessionFactory;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionSnapshot;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import com.cybersammy.bugreport.core.source.SourceProvenance;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReviewedWorkspaceSnapshotFactoryTest {
    private static final ReportSessionId SESSION_ID =
            ReportSessionId.parse("11111111-1111-4111-8111-111111111111");
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example_mod");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");
    private static final String SOURCE_NAME = "source-" + "1".repeat(64) + ".data";
    private static final String GENERATED_NAME = "generated-" + "2".repeat(64) + ".txt";
    private static final byte[] SOURCE_BYTES = "source-log\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GENERATED_BYTES = "generated\n".getBytes(StandardCharsets.UTF_8);

    @TempDir Path temporaryDirectory;

    @Test
    void createsCanonicalByteBoundSnapshotAndSealsWorkspace() throws Exception {
        Fixture fixture = fixture();

        ReviewedWorkspaceSnapshot first = ReviewedWorkspaceSnapshotFactory.create(
                fixture.session(),
                fixture.workspace(),
                fixture.files(),
                fixture.generated(),
                Set.of(GENERATED_NAME, SOURCE_NAME));
        ReviewedWorkspaceSnapshot repeated = ReviewedWorkspaceSnapshotFactory.create(
                fixture.session(),
                fixture.workspace(),
                fixture.files(),
                fixture.generated(),
                Set.of(SOURCE_NAME, GENERATED_NAME));

        assertEquals(List.of(GENERATED_NAME, SOURCE_NAME), first.artifacts().stream()
                .map(ReviewedWorkspaceArtifact::artifactName)
                .toList());
        assertEquals(SOURCE_BYTES.length + GENERATED_BYTES.length, first.totalBytes());
        assertEquals(first, repeated);
        assertTrue(fixture.workspace().sealed());
        assertThrows(
                WorkspaceMutationRejectedException.class,
                fixture.workspace()::beginMutation);
    }

    @Test
    void rejectsArtifactChangedAfterCollectionAndDoesNotExposeItsPath() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(
                fixture.workspace().directory().resolve(SOURCE_NAME),
                "other-data\n",
                StandardCharsets.UTF_8);

        ReviewedWorkspaceSnapshotException failure = assertThrows(
                ReviewedWorkspaceSnapshotException.class,
                () -> ReviewedWorkspaceSnapshotFactory.create(
                        fixture.session(),
                        fixture.workspace(),
                        fixture.files(),
                        fixture.generated(),
                        Set.of(SOURCE_NAME)));

        assertEquals(ReviewedWorkspaceSnapshotCode.ARTIFACT_CHANGED, failure.code());
        assertEquals(Optional.of(SOURCE_NAME), failure.artifactName());
        assertFalse(failure.getMessage().contains(fixture.workspace().directory().toString()));
    }

    @Test
    void laterWorkspaceMutationInvalidatesReviewedSnapshot() throws Exception {
        Fixture fixture = fixture();
        ReviewedWorkspaceSnapshot snapshot = ReviewedWorkspaceSnapshotFactory.create(
                fixture.session(),
                fixture.workspace(),
                fixture.files(),
                fixture.generated(),
                Set.of(SOURCE_NAME));
        ReviewedWorkspaceSnapshotFactory.requireCurrent(snapshot, fixture.workspace());

        Files.writeString(
                fixture.workspace().directory().resolve(SOURCE_NAME),
                "mutated-after-review\n",
                StandardCharsets.UTF_8);

        ReviewedWorkspaceSnapshotException failure = assertThrows(
                ReviewedWorkspaceSnapshotException.class,
                () -> ReviewedWorkspaceSnapshotFactory.requireCurrent(
                        snapshot, fixture.workspace()));
        assertEquals(ReviewedWorkspaceSnapshotCode.ARTIFACT_CHANGED, failure.code());
    }

    @Test
    void rejectsUntrackedWorkspaceEntriesBeforeCreatingSnapshot() throws Exception {
        Fixture fixture = fixture();
        writePrivate(fixture.workspace(), "unexpected.data", new byte[] {1});

        ReviewedWorkspaceSnapshotException failure = assertThrows(
                ReviewedWorkspaceSnapshotException.class,
                () -> ReviewedWorkspaceSnapshotFactory.create(
                        fixture.session(),
                        fixture.workspace(),
                        fixture.files(),
                        fixture.generated(),
                        Set.of(SOURCE_NAME)));

        assertEquals(ReviewedWorkspaceSnapshotCode.WORKSPACE_CHANGED, failure.code());
    }

    @Test
    void sealingWaitsForOutstandingMutationBeforeReadingWorkspace() throws Exception {
        Fixture fixture = fixture();
        WorkspaceMutationGate.Lease outstanding = fixture.workspace().beginMutation();
        CompletableFuture<ReviewedWorkspaceSnapshot> snapshot = CompletableFuture.supplyAsync(() ->
                ReviewedWorkspaceSnapshotFactory.create(
                        fixture.session(),
                        fixture.workspace(),
                        fixture.files(),
                        fixture.generated(),
                        Set.of(SOURCE_NAME),
                        Duration.ofSeconds(1)));

        awaitMutationRejection(fixture.workspace());
        assertFalse(snapshot.isDone());
        outstanding.close();

        assertEquals(1, snapshot.get(1, TimeUnit.SECONDS).artifacts().size());
    }

    @Test
    void quiescenceTimeoutQuarantinesWorkspaceAndPreventsRetry() throws Exception {
        Fixture fixture = fixture();
        WorkspaceMutationGate.Lease outstanding = fixture.workspace().beginMutation();
        try {
            ReviewedWorkspaceSnapshotException first = assertThrows(
                    ReviewedWorkspaceSnapshotException.class,
                    () -> ReviewedWorkspaceSnapshotFactory.create(
                            fixture.session(),
                            fixture.workspace(),
                            fixture.files(),
                            fixture.generated(),
                            Set.of(SOURCE_NAME),
                            Duration.ofMillis(20)));
            assertEquals(ReviewedWorkspaceSnapshotCode.WORKSPACE_BUSY, first.code());
        } finally {
            outstanding.close();
        }

        ReviewedWorkspaceSnapshotException retry = assertThrows(
                ReviewedWorkspaceSnapshotException.class,
                () -> ReviewedWorkspaceSnapshotFactory.create(
                        fixture.session(),
                        fixture.workspace(),
                        fixture.files(),
                        fixture.generated(),
                        Set.of(SOURCE_NAME)));
        assertEquals(ReviewedWorkspaceSnapshotCode.WORKSPACE_BUSY, retry.code());
        assertThrows(
                WorkspaceMutationRejectedException.class,
                fixture.workspace()::beginMutation);
    }

    @Test
    void rejectsUnknownSelectionBeforeSealingWorkspace() throws Exception {
        Fixture fixture = fixture();

        ReviewedWorkspaceSnapshotException failure = assertThrows(
                ReviewedWorkspaceSnapshotException.class,
                () -> ReviewedWorkspaceSnapshotFactory.create(
                        fixture.session(),
                        fixture.workspace(),
                        fixture.files(),
                        fixture.generated(),
                        Set.of("source-" + "f".repeat(64) + ".data")));

        assertEquals(ReviewedWorkspaceSnapshotCode.ARTIFACT_NOT_COLLECTED, failure.code());
        assertFalse(fixture.workspace().sealed());
        fixture.workspace().beginMutation().close();
    }

    private Fixture fixture() throws IOException {
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("workspaces").toAbsolutePath())
                .create(SESSION_ID);
        writePrivate(workspace, SOURCE_NAME, SOURCE_BYTES);
        writePrivate(workspace, GENERATED_NAME, GENERATED_BYTES);
        return new Fixture(session(), workspace, fileResult(), generatedResult());
    }

    private static ReportSessionSnapshot session() {
        DiagnosticSourceSpecification source = DiagnosticSourceSpecification.exactFile(
                        DiagnosticSourceId.of("latest_log"),
                        LogicalRoot.GAME_LOGS,
                        RelativePath.of("latest.log"))
                .labelKey(LocalizationKey.of("example_mod.bugreport.source.latest_log"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .qualityRole(ReportQualityRole.RECOMMENDED)
                .inclusionDefault(InclusionDefault.EXCLUDED)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .constraints(CollectionConstraints.defaults())
                .build();
        DiagnosticGeneratorSpecification generator = DiagnosticGeneratorSpecification.builder(
                        DiagnosticGeneratorId.of("runtime"), (request, sink) -> {})
                .labelKey(LocalizationKey.of("example_mod.bugreport.generator.runtime"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .qualityRole(ReportQualityRole.OPTIONAL)
                .inclusionDefault(InclusionDefault.EXCLUDED)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .executionContext(GeneratorExecutionContext.WORKER)
                .constraints(CollectionConstraints.defaults())
                .build();
        ProviderSpecification specification = ProviderSpecification.builder(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        LocalizationKey.of("example_mod.bugreport.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addSource(source)
                .addGenerator(generator)
                .addCategory(CategorySpecification.builder(
                                CATEGORY_ID,
                                LocalizationKey.of("example_mod.bugreport.category.general"))
                        .useSource(source.id())
                        .useGenerator(generator.id())
                        .build())
                .build();
        BugReportProvider provider = new BugReportProvider() {
            @Override
            public String providerId() {
                return PROVIDER_ID.value();
            }

            @Override
            public String providerVersion() {
                return PROVIDER_VERSION.value();
            }

            @Override
            public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        ProviderRegistrySnapshot registry = ProviderRegistry.createSnapshot(List.of(
                new DiscoveredProvider(PROVIDER_ID.namespace(), "SnapshotProvider", provider)));
        ReportSession session = new ReportSessionFactory(registry).create(SESSION_ID, PROVIDER_ID);
        session.selectCategory(CATEGORY_ID);
        session.transitionTo(ReportSessionState.COLLECTION_PLANNED);
        session.transitionTo(ReportSessionState.COLLECTING);
        session.transitionTo(ReportSessionState.SANITIZING);
        return session.transitionTo(ReportSessionState.REVIEW_REQUIRED);
    }

    private static FileCollectionResult fileResult() {
        CollectedSourceFile source = new CollectedSourceFile(
                SOURCE_NAME,
                SOURCE_BYTES.length,
                checksum(SOURCE_BYTES),
                List.of(sourceProvenance()),
                DiagnosticContentType.TEXT,
                PrivacyClassification.PERSONAL,
                ReportQualityRole.RECOMMENDED,
                InclusionDefault.EXCLUDED);
        CollectionProgressSnapshot progress = new CollectionProgressSnapshot(
                CollectionProgressSnapshot.State.COMPLETE,
                1,
                1,
                1,
                0,
                0,
                SOURCE_BYTES.length,
                SOURCE_BYTES.length,
                OptionalInt.empty());
        return new FileCollectionResult(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                FileCollectionResult.Status.COMPLETE,
                List.of(FileCollectionResult.SourceOutcome.collected(1, source)),
                progress);
    }

    private static CategoryGeneratedDiagnosticResult generatedResult() {
        DiagnosticGeneratorId generatorId = DiagnosticGeneratorId.of("runtime");
        CollectedGeneratedArtifact artifact = new CollectedGeneratedArtifact(
                GENERATED_NAME,
                GeneratedArtifactId.of("state"),
                GENERATED_BYTES.length,
                checksum(GENERATED_BYTES),
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                generatorId,
                DiagnosticContentType.TEXT,
                PrivacyClassification.PERSONAL,
                ReportQualityRole.OPTIONAL,
                InclusionDefault.EXCLUDED);
        GeneratedDiagnosticResult result = new GeneratedDiagnosticResult(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                generatorId,
                List.of(artifact),
                GENERATED_BYTES.length);
        return new CategoryGeneratedDiagnosticResult(
                PROVIDER_ID,
                CATEGORY_ID,
                List.of(GeneratedDiagnosticOutcome.collected(result)),
                GENERATED_BYTES.length);
    }

    private static SourceProvenance sourceProvenance() {
        return new SourceProvenance(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                DiagnosticSourceId.of("latest_log"),
                DiagnosticSourceKind.EXACT_FILE,
                DiagnosticContentType.TEXT,
                PrivacyClassification.PERSONAL,
                ReportQualityRole.RECOMMENDED,
                InclusionDefault.EXCLUDED);
    }

    private static void writePrivate(ReportWorkspace workspace, String name, byte[] bytes)
            throws IOException {
        try (FileChannel output = workspace.files()
                .openNewPrivateFile(workspace.directory().resolve(name))) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                output.write(buffer);
            }
            output.force(true);
        }
    }

    private static Sha256Checksum checksum(byte[] bytes) {
        try {
            return new Sha256Checksum(HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void awaitMutationRejection(ReportWorkspace workspace) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            try {
                workspace.beginMutation().close();
                Thread.onSpinWait();
            } catch (WorkspaceMutationRejectedException expected) {
                return;
            }
        }
        throw new AssertionError("Workspace did not start sealing");
    }

    private record Fixture(
            ReportSessionSnapshot session,
            ReportWorkspace workspace,
            FileCollectionResult files,
            CategoryGeneratedDiagnosticResult generated) {}
}
