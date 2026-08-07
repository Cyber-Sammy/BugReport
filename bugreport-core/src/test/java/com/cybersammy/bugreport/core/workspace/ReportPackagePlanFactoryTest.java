package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.InclusionDefault;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
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
import com.cybersammy.bugreport.core.packaging.PackagePlanEntryKind;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlan;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlanCode;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlanException;
import com.cybersammy.bugreport.core.packaging.ReportPackagePlanFactory;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.source.SourceProvenance;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReportPackagePlanFactoryTest {
    private static final ReportSessionId SESSION_ID =
            ReportSessionId.parse("11111111-1111-4111-8111-111111111111");
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example_mod");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");
    private static final String ARTIFACT_NAME = "source-" + "1".repeat(64) + ".data";
    private static final String GENERATED_NAME = "generated-" + "2".repeat(64) + ".json";
    private static final byte[] ARTIFACT_BYTES = "reviewed log\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GENERATED_BYTES = "{\"safe\":true}".getBytes(StandardCharsets.UTF_8);

    @TempDir Path temporaryDirectory;

    @Test
    void createsDeterministicPlanBoundToCurrentReviewedBytes() throws Exception {
        Fixture fixture = fixture();
        ReportManifest manifest = manifest(checksum(ARTIFACT_BYTES));

        ReportPackagePlan first = ReportPackagePlanFactory.create(
                fixture.snapshot(), fixture.workspace(), manifest, true);
        ReportPackagePlan repeated = ReportPackagePlanFactory.create(
                fixture.snapshot(), fixture.workspace(), manifest, true);

        assertEquals(
                List.of(
                        PackagePlanEntryKind.MANIFEST,
                        PackagePlanEntryKind.MARKDOWN,
                        PackagePlanEntryKind.WORKSPACE_ARTIFACT,
                        PackagePlanEntryKind.WORKSPACE_ARTIFACT),
                first.entries().stream().map(entry -> entry.kind()).toList());
        assertEquals(
                List.of(
                        "manifest.json",
                        "report.md",
                        "content/" + GENERATED_NAME,
                        "content/" + ARTIFACT_NAME),
                first.entries().stream().map(entry -> entry.archivePath()).toList());
        assertArrayEquals(first.manifestDocument(), repeated.manifestDocument());
        assertArrayEquals(
                first.markdownDocument().orElseThrow(),
                repeated.markdownDocument().orElseThrow());
        String markdown = new String(
                first.markdownDocument().orElseThrow(), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("&lt;script&gt;"));
        assertTrue(markdown.contains("\\# heading"));
        assertFalse(markdown.contains("<script>"));

        byte[] mutableCopy = first.manifestDocument();
        mutableCopy[0] = 0;
        assertEquals('{', first.manifestDocument()[0]);
    }

    @Test
    void rejectsManifestIdentityAndEntryMetadataOutsideReviewedSnapshot() throws Exception {
        Fixture fixture = fixture();
        ReportManifest wrongEntry = manifest(checksum("different".getBytes(StandardCharsets.UTF_8)));

        ReportPackagePlanException entryFailure = assertThrows(
                ReportPackagePlanException.class,
                () -> ReportPackagePlanFactory.create(
                        fixture.snapshot(), fixture.workspace(), wrongEntry, false));
        assertEquals(ReportPackagePlanCode.ENTRY_MISMATCH, entryFailure.code());
        assertEquals(Optional.of(ARTIFACT_NAME), entryFailure.artifactName());
        assertFalse(entryFailure.getMessage().contains(fixture.workspace().directory().toString()));

        ReportManifest wrongReport = manifestBuilder(
                        ReportSessionId.parse("22222222-2222-4222-8222-222222222222"))
                .entries(List.of(
                        manifestEntry(checksum(ARTIFACT_BYTES)), generatedManifestEntry()))
                .build();
        ReportPackagePlanException identityFailure = assertThrows(
                ReportPackagePlanException.class,
                () -> ReportPackagePlanFactory.create(
                        fixture.snapshot(), fixture.workspace(), wrongReport, false));
        assertEquals(ReportPackagePlanCode.SNAPSHOT_MISMATCH, identityFailure.code());
    }

    @Test
    void changedWorkspaceCannotProduceAnotherPlan() throws Exception {
        Fixture fixture = fixture();
        ReportPackagePlanFactory.create(
                fixture.snapshot(), fixture.workspace(), manifest(checksum(ARTIFACT_BYTES)), false);
        Files.writeString(
                fixture.workspace().directory().resolve(ARTIFACT_NAME),
                "changed bytes",
                StandardCharsets.UTF_8);

        ReviewedWorkspaceSnapshotException failure = assertThrows(
                ReviewedWorkspaceSnapshotException.class,
                () -> ReportPackagePlanFactory.create(
                        fixture.snapshot(),
                        fixture.workspace(),
                        manifest(checksum(ARTIFACT_BYTES)),
                        false));
        assertEquals(ReviewedWorkspaceSnapshotCode.ARTIFACT_CHANGED, failure.code());
    }

    private Fixture fixture() throws Exception {
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("workspaces").toAbsolutePath())
                .create(SESSION_ID);
        writePrivate(workspace, ARTIFACT_NAME, ARTIFACT_BYTES);
        writePrivate(workspace, GENERATED_NAME, GENERATED_BYTES);
        workspace.seal(Duration.ofSeconds(1));
        ReviewedWorkspaceArtifact sourceArtifact = new ReviewedWorkspaceArtifact.Source(
                new CollectedSourceFile(
                        ARTIFACT_NAME,
                        ARTIFACT_BYTES.length,
                        checksum(ARTIFACT_BYTES),
                        List.of(sourceProvenance()),
                        DiagnosticContentType.TEXT,
                        PrivacyClassification.PERSONAL,
                        ReportQualityRole.RECOMMENDED,
                        InclusionDefault.EXCLUDED));
        ReviewedWorkspaceArtifact generatedArtifact = new ReviewedWorkspaceArtifact.Generated(
                new CollectedGeneratedArtifact(
                        GENERATED_NAME,
                        GeneratedArtifactId.of("environment"),
                        GENERATED_BYTES.length,
                        checksum(GENERATED_BYTES),
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        CATEGORY_ID,
                        DiagnosticGeneratorId.of("runtime"),
                        DiagnosticContentType.JSON,
                        PrivacyClassification.LOW,
                        ReportQualityRole.OPTIONAL,
                        InclusionDefault.EXCLUDED));
        ReviewedWorkspaceSnapshot snapshot = new ReviewedWorkspaceSnapshot(
                SESSION_ID,
                7,
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                List.of(generatedArtifact, sourceArtifact),
                ARTIFACT_BYTES.length + GENERATED_BYTES.length,
                checksum("snapshot".getBytes(StandardCharsets.UTF_8)));
        return new Fixture(workspace, snapshot);
    }

    private static ReportManifest manifest(Sha256Checksum checksum) {
        return manifestBuilder(SESSION_ID)
                .entries(List.of(manifestEntry(checksum), generatedManifestEntry()))
                .build();
    }

    private static ReportManifest.Builder manifestBuilder(ReportSessionId sessionId) {
        return ReportManifest.builder(
                        sessionId,
                        Instant.parse("2026-08-07T00:00:00Z"),
                        new ManifestProducer("0.0.1-spike", ApiVersion.parse("0.2.0")),
                        new ManifestEnvironment(
                                "1.21.1",
                                "neoforge",
                                "21.1.227",
                                SupportedSide.PHYSICAL_CLIENT))
                .target(new ManifestTarget(PROVIDER_ID, PROVIDER_VERSION, CATEGORY_ID))
                .reviewedFields(FormSubmission.builder()
                        .put(FieldId.of("summary"), new FieldValue.Text("<script>\n# heading"))
                        .build());
    }

    private static ManifestEntry manifestEntry(Sha256Checksum checksum) {
        return new ManifestEntry(
                "content/" + ARTIFACT_NAME,
                ARTIFACT_BYTES.length,
                checksum,
                DiagnosticContentType.TEXT,
                Optional.of("text/plain"),
                PrivacyClassification.PERSONAL,
                ReportQualityRole.RECOMMENDED,
                ManifestCollectionStatus.SOURCE_COLLECTED,
                ManifestSanitizationStatus.SANITIZED,
                List.of(ManifestEntryProvenance.source(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        CATEGORY_ID,
                        DiagnosticSourceId.of("latest_log"),
                        DiagnosticSourceKind.EXACT_FILE,
                        PrivacyClassification.PERSONAL)),
                List.of(),
                ExtensionMetadata.empty());
    }

    private static ManifestEntry generatedManifestEntry() {
        return new ManifestEntry(
                "content/" + GENERATED_NAME,
                GENERATED_BYTES.length,
                checksum(GENERATED_BYTES),
                DiagnosticContentType.JSON,
                Optional.of("application/json"),
                PrivacyClassification.LOW,
                ReportQualityRole.OPTIONAL,
                ManifestCollectionStatus.GENERATOR_COMPLETED,
                ManifestSanitizationStatus.NOT_REQUIRED,
                List.of(ManifestEntryProvenance.generator(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        CATEGORY_ID,
                        DiagnosticGeneratorId.of("runtime"),
                        PrivacyClassification.LOW)),
                List.of(),
                ExtensionMetadata.empty());
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
            throws Exception {
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

    private record Fixture(ReportWorkspace workspace, ReviewedWorkspaceSnapshot snapshot) {}
}
