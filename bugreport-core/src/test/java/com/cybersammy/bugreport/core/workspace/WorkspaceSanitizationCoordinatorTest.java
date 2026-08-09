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
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.sanitization.ProductSanitization;
import com.cybersammy.bugreport.core.sanitization.SanitizationArtifactPolicy;
import com.cybersammy.bugreport.core.sanitization.SanitizationCaseSensitivity;
import com.cybersammy.bugreport.core.sanitization.SanitizationException;
import com.cybersammy.bugreport.core.sanitization.SanitizationPipeline;
import com.cybersammy.bugreport.core.sanitization.SanitizationPolicy;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.CategorySourcePlanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceSanitizationCoordinatorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void atomicallyPublishesBoundedSanitizedBytesAndPreservesPreviousArtifactOnOverflow()
            throws Exception {
        Path game = Files.createDirectory(temporaryDirectory.resolve("game"));
        Path logs = Files.createDirectory(game.resolve("logs"));
        Files.createDirectory(game.resolve("crash-reports"));
        Files.createDirectory(game.resolve("config"));
        Path log = logs.resolve("latest.log");
        Files.writeString(log, "Authorization: Bearer secret_token_123456\n");
        ApprovedSourceRoots roots = ApprovedSourceRoots.forGameDirectory(game.toAbsolutePath());
        DiagnosticSourceSpecification declaration = DiagnosticSourceSpecification.exactFile(
                        DiagnosticSourceId.of("latest_log"),
                        com.cybersammy.bugreport.api.specification.LogicalRoot.GAME_LOGS,
                        com.cybersammy.bugreport.api.specification.RelativePath.of("latest.log"))
                .labelKey(LocalizationKey.of("example.source.latest_log"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .constraints(CollectionConstraints.defaults())
                .build();
        ProviderSpecification specification = ProviderSpecification.builder(
                        ProviderId.parse("example_mod"), ProviderVersion.parse("1.0.0"),
                        LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addSource(declaration)
                .addCategory(CategorySpecification.builder(CategoryId.of("general"), LocalizationKey.of("example.category"))
                        .useSource(declaration.id()).build())
                .build();
        BugReportProvider provider = new BugReportProvider() {
            @Override public String providerId() { return "example_mod"; }
            @Override public String providerVersion() { return "1.0.0"; }
            @Override public Optional<ProviderSpecification> specification() { return Optional.of(specification); }
        };
        var plan = new CategorySourcePlanner(
                ProviderRegistry.createSnapshot(List.of(new DiscoveredProvider(
                        specification.id().namespace(), "SanitizationFixture", provider))),
                roots, SupportedSide.PHYSICAL_CLIENT).plan(specification.id(), CategoryId.of("general"));
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("workspaces").toAbsolutePath())
                .create(com.cybersammy.bugreport.core.session.ReportSessionId.parse(
                        "00000000-0000-4000-8000-000000000301"));
        CollectedSourceFile copied = WorkspaceSourceCollector.collect(plan.files().getFirst(), roots, workspace);
        var session = new com.cybersammy.bugreport.core.session.ReportSessionFactory(
                        ProviderRegistry.createSnapshot(List.of(new DiscoveredProvider(
                                specification.id().namespace(),
                                "SanitizationFixture",
                                provider))))
                .create(workspace.sessionId(), specification.id());
        session.selectCategory(CategoryId.of("general"));

        FileCollectionResult collectedResult = new FileCollectionResult(
                specification.id(),
                specification.version(),
                CategoryId.of("general"),
                FileCollectionResult.Status.COMPLETE,
                List.of(FileCollectionResult.SourceOutcome.collected(1, copied)),
                new CollectionProgressSnapshot(
                        CollectionProgressSnapshot.State.COMPLETE,
                        1,
                        1,
                        1,
                        0,
                        0,
                        copied.byteCount(),
                        copied.byteCount(),
                        OptionalInt.empty()));
        WorkspaceReviewCoordinator.SanitizationBatch cancelledBatch =
                WorkspaceReviewCoordinator.sanitize(
                        session.snapshot(),
                        collectedResult,
                        workspace,
                        ignored -> new SanitizationPipeline(List.of()),
                        () -> true);
        assertEquals(
                WorkspaceReviewCoordinator.ArtifactReviewStatus.FAILED,
                cancelledBatch.artifacts().getFirst().status());
        assertEquals(
                "Authorization: Bearer secret_token_123456\n",
                Files.readString(workspace.directory().resolve(copied.artifactName())));

        WorkspaceSanitizationCoordinator.SanitizedSource sanitized =
                WorkspaceSanitizationCoordinator.sanitize(
                        copied,
                        workspace,
                        ProductSanitization.textPipeline(
                                SanitizationPolicy.strictPrivacy(SanitizationArtifactPolicy.LOG),
                                "C:\\Users\\Alice",
                                "Alice",
                                SanitizationCaseSensitivity.INSENSITIVE),
                        CancellationSignal.neverCancelled());

        String output = Files.readString(workspace.directory().resolve(copied.artifactName()));
        assertEquals("Authorization: <bearer-token>\n", output);
        assertFalse(output.contains("secret_token_123456"));
        assertTrue(sanitized.result().findings().stream().anyMatch(finding ->
                finding.action() == com.cybersammy.bugreport.core.sanitization.SanitizationAction.AUTOMATIC_REDACTION));
        assertEquals(output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, sanitized.source().byteCount());
        assertFalse(sanitized.source().checksum().equals(copied.checksum()));

        CollectedSourceFile mismatched = new CollectedSourceFile(
                sanitized.source().artifactName(),
                sanitized.source().byteCount(),
                new Sha256Checksum("0".repeat(64)),
                sanitized.source().provenances(),
                sanitized.source().contentType(),
                sanitized.source().privacy(),
                sanitized.source().qualityRole(),
                sanitized.source().inclusionDefault());
        var provenance = mismatched.provenances().getFirst();
        ReviewedWorkspaceSnapshot mismatchedReview = new ReviewedWorkspaceSnapshot(
                com.cybersammy.bugreport.core.session.ReportSessionId.parse(
                        "00000000-0000-4000-8000-000000000302"),
                1,
                provenance.providerId(),
                provenance.providerVersion(),
                provenance.categoryId(),
                List.of(new ReviewedWorkspaceArtifact.Source(mismatched)),
                mismatched.byteCount(),
                new Sha256Checksum("1".repeat(64)));
        IllegalArgumentException mismatch = assertThrows(
                IllegalArgumentException.class,
                () -> WorkspacePreparationCoordinator.prepare(
                        mismatchedReview, List.of(sanitized), Set.of()));
        assertEquals(
                "Text artifact requires matching coordinator sanitization evidence",
                mismatch.getMessage());

        assertThrows(
                SanitizationException.class,
                () -> WorkspaceSanitizationCoordinator.sanitize(
                        sanitized.source(),
                        workspace,
                        new SanitizationPipeline(List.of()),
                        CancellationSignal.neverCancelled(),
                        5));
        assertEquals(output, Files.readString(workspace.directory().resolve(copied.artifactName())));
        try (var files = Files.list(workspace.directory())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".sanitize-")));
        }
    }
}
