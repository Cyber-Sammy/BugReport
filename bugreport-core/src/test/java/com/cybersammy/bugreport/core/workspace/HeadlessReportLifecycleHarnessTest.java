package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ApiVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.manifest.ManifestCollectionStatus;
import com.cybersammy.bugreport.core.manifest.ManifestEntry;
import com.cybersammy.bugreport.core.manifest.ManifestEntryProvenance;
import com.cybersammy.bugreport.core.manifest.ManifestEnvironment;
import com.cybersammy.bugreport.core.manifest.ManifestProducer;
import com.cybersammy.bugreport.core.manifest.ManifestSanitizationStatus;
import com.cybersammy.bugreport.core.manifest.ManifestTarget;
import com.cybersammy.bugreport.core.manifest.ReportManifest;
import com.cybersammy.bugreport.core.manifest.ReportManifestJsonCodec;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlanFactory;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.sanitization.ProductSanitization;
import com.cybersammy.bugreport.core.sanitization.SanitizationArtifactPolicy;
import com.cybersammy.bugreport.core.sanitization.SanitizationCaseSensitivity;
import com.cybersammy.bugreport.core.sanitization.SanitizationPolicy;
import com.cybersammy.bugreport.core.session.ReportSession;
import com.cybersammy.bugreport.core.session.ReportSessionFactory;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.CategorySourcePlan;
import com.cybersammy.bugreport.core.source.CategorySourcePlanner;
import com.cybersammy.bugreport.core.transport.LocalArchiveDestination;
import com.cybersammy.bugreport.core.transport.LocalZipTransport;
import com.cybersammy.bugreport.core.transport.ReportTransportRequest;
import com.cybersammy.bugreport.core.transport.ReportTransportResult;
import com.cybersammy.bugreport.core.transport.TransportConsentTestIssuer;
import com.cybersammy.bugreport.core.transport.TransportRunControl;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HeadlessReportLifecycleHarnessTest {
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example_mod");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");
    private static final ReportSessionId SESSION_ID =
            ReportSessionId.parse("00000000-0000-4000-8000-000000000401");

    @TempDir Path temporaryDirectory;

    @Test
    void producesReviewedSanitizedZipThroughExplicitLocalExport() throws Exception {
        DiagnosticSourceSpecification source = source();
        ProviderSpecification specification = specification(source);
        ProviderRegistrySnapshot registry = registry(specification);
        ReportSession session = new ReportSessionFactory(registry).create(SESSION_ID, PROVIDER_ID);
        session.selectCategory(CATEGORY_ID);
        session.transitionTo(ReportSessionState.COLLECTION_PLANNED);
        session.transitionTo(ReportSessionState.COLLECTING);

        ApprovedSourceRoots roots = gameRootsWithSensitiveLog();
        CategorySourcePlan categoryPlan = new CategorySourcePlanner(
                        registry, roots, SupportedSide.PHYSICAL_CLIENT)
                .plan(PROVIDER_ID, CATEGORY_ID);
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("workspaces").toAbsolutePath())
                .create(SESSION_ID);
        FileCollectionResult collected = FileCollectionCoordinator.collect(
                categoryPlan, roots, workspace, new CollectionRunControl());

        session.transitionTo(ReportSessionState.SANITIZING);
        WorkspaceSanitizationCoordinator.SanitizedSource sanitized = sanitize(
                collected.outcomes().getFirst().collectedFile().orElseThrow(), workspace);
        FileCollectionResult sanitizedCollection = sanitizedCollection(sanitized, collected);

        session.transitionTo(ReportSessionState.REVIEW_REQUIRED);
        WorkspaceSanitizationCoordinator.discardReviewOriginals(
                workspace, List.of(sanitized.reviewOriginal()));
        PreparedWorkspaceSnapshot prepared = WorkspacePreparationCoordinator.prepare(
                ReviewedWorkspaceSnapshotFactory.create(
                        session.snapshot(),
                        workspace,
                        sanitizedCollection,
                        new CategoryGeneratedDiagnosticResult(PROVIDER_ID, CATEGORY_ID, List.of(), 0),
                        Set.of(sanitized.source().artifactName())),
                List.of(sanitized),
                Set.of());
        ReportPackagePlan packagePlan = ReportPackagePlanFactory.create(
                prepared, workspace, manifest(sanitized, source, prepared), true);

        session.transitionTo(ReportSessionState.READY);
        session.transitionTo(ReportSessionState.DELIVERING);
        LocalArchiveDestination destination = new LocalArchiveDestination(
                temporaryDirectory.resolve("report.bugreport.zip"));
        ReportTransportResult result = new LocalZipTransport().execute(
                new ReportTransportRequest(
                        packagePlan,
                        workspace,
                        destination,
                        TransportConsentTestIssuer.issueConfirmedLocalExport(
                                packagePlan, destination)),
                new TransportRunControl());

        assertEquals(ReportTransportResult.Status.SUCCESS, result.status());
        session.transitionTo(ReportSessionState.COMPLETED);
        assertEquals(ReportSessionState.COMPLETED, session.snapshot().state());
        assertSanitizedArchive(destination, sanitized.source());
    }

    private ApprovedSourceRoots gameRootsWithSensitiveLog() throws Exception {
        Path game = Files.createDirectory(temporaryDirectory.resolve("game"));
        Path logs = Files.createDirectory(game.resolve("logs"));
        Files.createDirectory(game.resolve("crash-reports"));
        Files.createDirectory(game.resolve("config"));
        Files.writeString(
                logs.resolve("latest.log"),
                "Authorization: Bearer secret_token_123456\n",
                StandardCharsets.UTF_8);
        return ApprovedSourceRoots.forGameDirectory(game.toAbsolutePath());
    }

    private static DiagnosticSourceSpecification source() {
        return DiagnosticSourceSpecification.exactFile(
                        DiagnosticSourceId.of("latest_log"),
                        LogicalRoot.GAME_LOGS,
                        RelativePath.of("latest.log"))
                .labelKey(LocalizationKey.of("example.source.latest_log"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .constraints(CollectionConstraints.defaults())
                .build();
    }

    private static ProviderSpecification specification(DiagnosticSourceSpecification source) {
        return ProviderSpecification.builder(
                        PROVIDER_ID, PROVIDER_VERSION, LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addSource(source)
                .addCategory(CategorySpecification.builder(
                                CATEGORY_ID, LocalizationKey.of("example.category"))
                        .useSource(source.id())
                        .build())
                .build();
    }

    private static ProviderRegistrySnapshot registry(ProviderSpecification specification) {
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
        return ProviderRegistry.createSnapshot(List.of(new DiscoveredProvider(
                PROVIDER_ID.namespace(), "EndToEndProvider", provider)));
    }

    private static WorkspaceSanitizationCoordinator.SanitizedSource sanitize(
            CollectedSourceFile collected,
            ReportWorkspace workspace) {
        return WorkspaceSanitizationCoordinator.sanitize(
                collected,
                workspace,
                ProductSanitization.textPipeline(
                        SanitizationPolicy.strictPrivacy(SanitizationArtifactPolicy.LOG),
                        "C:\\Users\\Alice",
                        "Alice",
                        SanitizationCaseSensitivity.INSENSITIVE),
                CancellationSignal.neverCancelled());
    }

    private static FileCollectionResult sanitizedCollection(
            WorkspaceSanitizationCoordinator.SanitizedSource sanitized,
            FileCollectionResult collected) {
        return new FileCollectionResult(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                FileCollectionResult.Status.COMPLETE,
                List.of(FileCollectionResult.SourceOutcome.collected(1, sanitized.source())),
                collected.progress());
    }

    private static ReportManifest manifest(
            WorkspaceSanitizationCoordinator.SanitizedSource sanitized,
            DiagnosticSourceSpecification source,
            PreparedWorkspaceSnapshot prepared) {
        CollectedSourceFile artifact = sanitized.source();
        PreparedWorkspaceArtifact preparedArtifact = prepared.artifacts().getFirst();
        ManifestEntry entry = new ManifestEntry(
                "content/" + artifact.artifactName(),
                artifact.byteCount(),
                artifact.checksum(),
                artifact.contentType(),
                Optional.empty(),
                preparedArtifact.effectivePrivacy(),
                artifact.qualityRole(),
                ManifestCollectionStatus.SOURCE_COLLECTED,
                preparedArtifact.sanitizationStatus(),
                List.of(ManifestEntryProvenance.source(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        CATEGORY_ID,
                        source.id(),
                        source.kind(),
                        source.privacy())),
                preparedArtifact.sanitizationFindings(),
                ExtensionMetadata.empty());
        return ReportManifest.builder(
                        SESSION_ID,
                        Instant.parse("2026-08-08T00:00:00Z"),
                        new ManifestProducer("0.0.1-spike", ApiVersion.parse("0.3.0")),
                        new ManifestEnvironment(
                                "1.21.1", "neoforge", "21.1.227", SupportedSide.PHYSICAL_CLIENT))
                .target(new ManifestTarget(PROVIDER_ID, PROVIDER_VERSION, CATEGORY_ID))
                .reviewedFields(FormSubmission.builder()
                        .put(FieldId.of("summary"), new FieldValue.Text("test"))
                        .build())
                .entries(List.of(entry))
                .build();
    }

    private static void assertSanitizedArchive(
            LocalArchiveDestination destination,
            CollectedSourceFile artifact) throws Exception {
        try (ZipFile zip = new ZipFile(destination.path().toFile())) {
            String output = new String(
                    zip.getInputStream(zip.getEntry("content/" + artifact.artifactName())).readAllBytes(),
                    StandardCharsets.UTF_8);
            assertTrue(output.contains("<bearer-token>"));
            assertFalse(output.contains("secret_token_123456"));
            ReportManifest manifest = ReportManifestJsonCodec.decode(
                    zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes()).manifest();
            assertEquals(SESSION_ID, manifest.reportId());
            assertEquals(
                    new ManifestTarget(PROVIDER_ID, PROVIDER_VERSION, CATEGORY_ID),
                    manifest.target().orElseThrow());
            ManifestEntry entry = manifest.entries().getFirst();
            assertEquals("content/" + artifact.artifactName(), entry.archivePath());
            assertEquals(artifact.checksum(), entry.checksum());
            assertEquals(artifact.byteCount(), entry.uncompressedBytes());
            assertEquals(ManifestSanitizationStatus.SANITIZED, entry.sanitizationStatus());
            assertFalse(entry.sanitizationFindings().isEmpty());
            assertEquals(artifact.privacy(), entry.effectivePrivacy());
        }
    }
}
