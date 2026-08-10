package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.GeneratorExecutionContext;
import com.cybersammy.bugreport.api.specification.GeneratedDiagnosticProducer;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.sanitization.SanitizationCaseSensitivity;
import com.cybersammy.bugreport.core.session.ReportSession;
import com.cybersammy.bugreport.core.session.ReportSessionFactory;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.CategoryCollectionPlanner;
import com.cybersammy.bugreport.core.source.CategoryCollectionFingerprint;
import com.cybersammy.bugreport.core.source.ReviewedCollectionPlan;
import com.cybersammy.bugreport.core.source.ScreenshotCollectionRequest;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CategoryCollectionCoordinatorTest {
    private static final ProviderId PROVIDER = ProviderId.parse("example");
    private static final ProviderVersion VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY = CategoryId.of("general");
    private static final DiagnosticGeneratorId GENERATOR = DiagnosticGeneratorId.of("runtime");
    private static final DiagnosticSourceId SCREENSHOT = DiagnosticSourceId.of("screenshot");

    @TempDir Path temporaryDirectory;

    @Test
    void executesOnlyReviewedGeneratorsAndCarriesExactSanitizedBytesIntoPreparation()
            throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        Fixture fixture = fixture(invoked);
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.of(
                fixture.plan(), Set.of(), Set.of(GENERATOR));
        CategoryCollectionRunControl control = new CategoryCollectionRunControl();

        CategoryCollectionResult collected = CategoryCollectionCoordinator.collect(
                fixture.registry(),
                reviewed,
                fixture.roots(),
                SupportedSide.PHYSICAL_CLIENT,
                fixture.workspace(),
                control,
                command -> false);

        assertTrue(invoked.get());
        assertEquals(CategoryCollectionResult.Status.COMPLETE, collected.status());
        assertEquals(1, collected.generated().outcomes().size());
        CollectedGeneratedArtifact artifact = collected.generated()
                .outcomes().getFirst().result().orElseThrow().artifacts().getFirst();
        assertTrue(Files.readString(fixture.workspace().directory().resolve(artifact.artifactName()))
                .contains("secret_token"));
        assertEquals(
                CategoryCollectionProgressSnapshot.Phase.COMPLETE,
                control.progress().phase());

        ReportSession session = new ReportSessionFactory(fixture.registry())
                .create(fixture.workspace().sessionId(), PROVIDER);
        session.selectCategory(CATEGORY);
        session.transitionTo(ReportSessionState.COLLECTION_PLANNED);
        session.transitionTo(ReportSessionState.COLLECTING);
        session.transitionTo(ReportSessionState.SANITIZING);
        var batch = WorkspaceReviewCoordinator.sanitizeProduct(
                session.snapshot(),
                collected,
                fixture.workspace(),
                "C:/Users/example",
                "example",
                SanitizationCaseSensitivity.INSENSITIVE,
                CancellationSignal.neverCancelled());
        assertEquals(1, batch.artifacts().size());
        assertFalse(Files.readString(fixture.workspace().directory().resolve(artifact.artifactName()))
                .contains("secret_token"));

        session.transitionTo(ReportSessionState.REVIEW_REQUIRED);
        var prepared = WorkspaceReviewCoordinator.prepare(
                session.snapshot(),
                batch,
                Set.of(artifact.artifactName()),
                Set.of());
        assertEquals(1, prepared.snapshot().artifacts().size());
        assertTrue(prepared.snapshot().artifacts().getFirst().artifact()
                instanceof ReviewedWorkspaceArtifact.Generated);
    }

    @Test
    void excludedGeneratorIsNeverInvoked() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        Fixture fixture = fixture(invoked);
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.of(
                fixture.plan(), Set.of(), Set.of());

        CategoryCollectionResult collected = CategoryCollectionCoordinator.collect(
                fixture.registry(),
                reviewed,
                fixture.roots(),
                SupportedSide.PHYSICAL_CLIENT,
                fixture.workspace(),
                new CategoryCollectionRunControl(),
                command -> false);

        assertFalse(invoked.get());
        assertTrue(collected.generated().outcomes().isEmpty());
        assertEquals(CategoryCollectionResult.Status.COMPLETE, collected.status());
        assertFalse(CategoryCollectionFingerprint.from(reviewed).equals(
                CategoryCollectionFingerprint.from(ReviewedCollectionPlan.of(
                        fixture.plan(), Set.of(), Set.of(GENERATOR)))));
    }

    @Test
    void cancellationBeforeStartPreventsGeneratorInvocation() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        Fixture fixture = fixture(invoked);
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.of(
                fixture.plan(), Set.of(), Set.of(GENERATOR));
        CategoryCollectionRunControl control = new CategoryCollectionRunControl();
        assertTrue(control.requestCancellation());

        CategoryCollectionResult collected = CategoryCollectionCoordinator.collect(
                fixture.registry(),
                reviewed,
                fixture.roots(),
                SupportedSide.PHYSICAL_CLIENT,
                fixture.workspace(),
                control,
                command -> false);

        assertFalse(invoked.get());
        assertEquals(CategoryCollectionResult.Status.CANCELLED, collected.status());
        assertEquals(
                GeneratedDiagnosticOutcomeStatus.CANCELLED,
                collected.generated().outcomes().getFirst().status());
    }

    @Test
    void terminalPublicationAndCancellationRemainConsistent() {
        CategoryCollectionRunControl completed = new CategoryCollectionRunControl();
        completed.begin(0);
        completed.beginGenerated();
        assertEquals(
                CategoryCollectionResult.Status.COMPLETE,
                completed.finish(CategoryCollectionResult.Status.COMPLETE, 0));
        assertFalse(completed.requestCancellation());
        assertFalse(completed.isCancellationRequested());

        CategoryCollectionRunControl cancelled = new CategoryCollectionRunControl();
        assertTrue(cancelled.requestCancellation());
        cancelled.begin(0);
        cancelled.beginGenerated();
        assertEquals(
                CategoryCollectionResult.Status.CANCELLED,
                cancelled.finish(CategoryCollectionResult.Status.COMPLETE, 0));
        assertEquals(
                CategoryCollectionProgressSnapshot.Phase.CANCELLED,
                cancelled.progress().phase());
    }

    @Test
    void resultCannotClaimCompleteWhenChildOutcomesFailed() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        Fixture fixture = fixture(invoked, (request, sink) -> {
            invoked.set(true);
            throw new java.io.IOException("provider failure");
        });
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.of(
                fixture.plan(), Set.of(), Set.of(GENERATOR));
        CategoryCollectionResult failed = CategoryCollectionCoordinator.collect(
                fixture.registry(),
                reviewed,
                fixture.roots(),
                SupportedSide.PHYSICAL_CLIENT,
                fixture.workspace(),
                new CategoryCollectionRunControl(),
                command -> false);

        assertEquals(CategoryCollectionResult.Status.FAILED, failed.status());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CategoryCollectionResult(
                        CategoryCollectionResult.Status.COMPLETE,
                        failed.files(),
                        failed.generated(),
                        failed.fingerprint()));
        assertTrue(java.util.Arrays.stream(CategoryCollectionResult.class.getDeclaredConstructors())
                .noneMatch(constructor -> java.lang.reflect.Modifier.isPublic(
                        constructor.getModifiers())));
    }

    @Test
    void selectedJpegIsMetadataStrippedPublishedAsPngAndBoundToFingerprint() throws Exception {
        ScreenshotFixture fixture = screenshotFixture();
        Path selected = fixture.screenshots().resolve("external-image.jpg");
        BufferedImage original = new BufferedImage(24, 12, BufferedImage.TYPE_INT_RGB);
        original.setRGB(3, 4, 0x00AA44);
        assertTrue(ImageIO.write(original, "jpeg", selected.toFile()));

        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.of(
                fixture.plan(), Set.of(SCREENSHOT), Set.of());
        ScreenshotCollectionRequest screenshots = ScreenshotCollectionRequest.from(
                reviewed, List.of(RelativePath.of("external-image.jpg")));
        CategoryCollectionResult result = CategoryCollectionCoordinator.collect(
                fixture.registry(),
                reviewed,
                screenshots,
                fixture.roots(),
                SupportedSide.PHYSICAL_CLIENT,
                fixture.workspace(),
                new CategoryCollectionRunControl(),
                command -> false,
                fixture.screenshots());

        assertEquals(CategoryCollectionResult.Status.COMPLETE, result.status());
        CollectedSourceFile collected = result.files().outcomes().getFirst()
                .collectedFile().orElseThrow();
        assertTrue(collected.artifactName().endsWith(".png"));
        byte[] bytes = Files.readAllBytes(fixture.workspace().directory().resolve(collected.artifactName()));
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals("PNG", new String(bytes, 1, 3, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(24, ImageIO.read(new java.io.ByteArrayInputStream(bytes)).getWidth());
        assertEquals(CategoryCollectionFingerprint.from(reviewed, screenshots), result.fingerprint());

        ReportSession session = new ReportSessionFactory(fixture.registry())
                .create(fixture.workspace().sessionId(), PROVIDER);
        session.selectCategory(CATEGORY);
        session.transitionTo(ReportSessionState.COLLECTION_PLANNED);
        session.transitionTo(ReportSessionState.COLLECTING);
        session.transitionTo(ReportSessionState.SANITIZING);
        var batch = WorkspaceReviewCoordinator.sanitizeProduct(
                session.snapshot(),
                result,
                fixture.workspace(),
                "C:/Users/example",
                "example",
                SanitizationCaseSensitivity.INSENSITIVE,
                CancellationSignal.neverCancelled());
        assertEquals(1, batch.artifacts().size());
        assertTrue(batch.artifacts().getFirst().explicitReviewRequired());
        session.transitionTo(ReportSessionState.REVIEW_REQUIRED);
        var prepared = WorkspaceReviewCoordinator.prepare(
                session.snapshot(),
                batch,
                Set.of(collected.artifactName()),
                Set.of(collected.artifactName()));
        assertEquals(
                collected.checksum(),
                prepared.snapshot().artifacts().getFirst().artifact().checksum());
    }

    @Test
    void unsupportedSelectedImageFailsWithoutPublishingArtifact() throws Exception {
        ScreenshotFixture fixture = screenshotFixture();
        Files.writeString(fixture.screenshots().resolve("fake.png"), "not an image");
        ReviewedCollectionPlan reviewed = ReviewedCollectionPlan.of(
                fixture.plan(), Set.of(SCREENSHOT), Set.of());
        ScreenshotCollectionRequest screenshots = ScreenshotCollectionRequest.from(
                reviewed, List.of(RelativePath.of("fake.png")));

        CategoryCollectionResult result = CategoryCollectionCoordinator.collect(
                fixture.registry(),
                reviewed,
                screenshots,
                fixture.roots(),
                SupportedSide.PHYSICAL_CLIENT,
                fixture.workspace(),
                new CategoryCollectionRunControl(),
                command -> false,
                fixture.screenshots());

        assertEquals(CategoryCollectionResult.Status.FAILED, result.status());
        assertEquals(
                SourceCopyCode.UNSUPPORTED_IMAGE,
                result.files().outcomes().getFirst().failureCode().orElseThrow());
        try (var children = Files.list(fixture.workspace().directory())) {
            assertEquals(1, children.count());
        }
    }

    private Fixture fixture(AtomicBoolean invoked) throws Exception {
        return fixture(invoked, (request, sink) -> {
            invoked.set(true);
            sink.emitText(
                    GeneratedArtifactId.of("state"),
                    "Authorization: Bearer secret_token_123456");
        });
    }

    private ScreenshotFixture screenshotFixture() throws Exception {
        Path game = temporaryDirectory.resolve("screenshot-game-" + System.nanoTime());
        Files.createDirectories(game.resolve("logs"));
        Files.createDirectories(game.resolve("crash-reports"));
        Files.createDirectories(game.resolve("config"));
        Path screenshots = Files.createDirectories(game.resolve("screenshots"));
        ApprovedSourceRoots roots = ApprovedSourceRoots.forGameDirectory(game.toAbsolutePath());
        DiagnosticSourceSpecification screenshot =
                DiagnosticSourceSpecification.userSelectedScreenshot(SCREENSHOT)
                        .labelKey(LocalizationKey.of("example.source.screenshot"))
                        .privacy(PrivacyClassification.SENSITIVE)
                        .contentType(DiagnosticContentType.BINARY)
                        .qualityRole(ReportQualityRole.OPTIONAL)
                        .supportSide(SupportedSide.PHYSICAL_CLIENT)
                        .build();
        CategorySpecification category = CategorySpecification.builder(
                        CATEGORY, LocalizationKey.of("example.category.general"))
                .useSource(SCREENSHOT)
                .build();
        ProviderSpecification specification = ProviderSpecification.builder(
                        PROVIDER, VERSION, LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addSource(screenshot)
                .addCategory(category)
                .build();
        BugReportProvider bridge = new BugReportProvider() {
            @Override public String providerId() { return PROVIDER.value(); }
            @Override public String providerVersion() { return VERSION.value(); }
            @Override public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        ProviderRegistrySnapshot registry = ProviderRegistry.createSnapshot(List.of(
                new DiscoveredProvider(PROVIDER.namespace(), "ScreenshotFixture", bridge)));
        var plan = new CategoryCollectionPlanner(
                        registry, roots, SupportedSide.PHYSICAL_CLIENT)
                .plan(PROVIDER, CATEGORY);
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("screenshot-workspaces-" + System.nanoTime())
                                .toAbsolutePath())
                .create(ReportSessionId.random());
        return new ScreenshotFixture(registry, roots, plan, workspace, screenshots);
    }

    private Fixture fixture(AtomicBoolean invoked, GeneratedDiagnosticProducer producer)
            throws Exception {
        Path game = temporaryDirectory.resolve("game-" + System.nanoTime());
        Files.createDirectories(game.resolve("logs"));
        Files.createDirectories(game.resolve("crash-reports"));
        Files.createDirectories(game.resolve("config"));
        ApprovedSourceRoots roots = ApprovedSourceRoots.forGameDirectory(game.toAbsolutePath());
        DiagnosticGeneratorSpecification generator = DiagnosticGeneratorSpecification.builder(
                        GENERATOR,
                        producer)
                .labelKey(LocalizationKey.of("example.generator.runtime"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .executionContext(GeneratorExecutionContext.WORKER)
                .constraints(CollectionConstraints.builder()
                        .maxGeneratedArtifacts(1)
                        .maxBytesPerFile(4096)
                        .maxTotalBytes(4096)
                        .callbackTimeout(Duration.ofSeconds(1))
                        .build())
                .build();
        CategorySpecification category = CategorySpecification.builder(
                        CATEGORY, LocalizationKey.of("example.category.general"))
                .useGenerator(GENERATOR)
                .build();
        ProviderSpecification specification = ProviderSpecification.builder(
                        PROVIDER, VERSION, LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addGenerator(generator)
                .addCategory(category)
                .build();
        BugReportProvider bridge = new BugReportProvider() {
            @Override public String providerId() { return PROVIDER.value(); }
            @Override public String providerVersion() { return VERSION.value(); }
            @Override public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        ProviderRegistrySnapshot registry = ProviderRegistry.createSnapshot(List.of(
                new DiscoveredProvider(PROVIDER.namespace(), "CategoryCollectionFixture", bridge)));
        var plan = new CategoryCollectionPlanner(
                        registry, roots, SupportedSide.PHYSICAL_CLIENT)
                .plan(PROVIDER, CATEGORY);
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("workspaces-" + System.nanoTime()).toAbsolutePath())
                .create(ReportSessionId.random());
        return new Fixture(registry, roots, plan, workspace);
    }

    private record Fixture(
            ProviderRegistrySnapshot registry,
            ApprovedSourceRoots roots,
            com.cybersammy.bugreport.core.source.CategoryCollectionPlan plan,
            ReportWorkspace workspace) {}

    private record ScreenshotFixture(
            ProviderRegistrySnapshot registry,
            ApprovedSourceRoots roots,
            com.cybersammy.bugreport.core.source.CategoryCollectionPlan plan,
            ReportWorkspace workspace,
            Path screenshots) {}
}
