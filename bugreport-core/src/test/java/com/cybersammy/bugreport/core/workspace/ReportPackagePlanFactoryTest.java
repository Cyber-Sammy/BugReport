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
import com.cybersammy.bugreport.api.specification.CancellationSignal;
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
import com.cybersammy.bugreport.core.sanitization.ProductSanitization;
import com.cybersammy.bugreport.core.sanitization.SanitizationArtifactPolicy;
import com.cybersammy.bugreport.core.sanitization.SanitizationCaseSensitivity;
import com.cybersammy.bugreport.core.sanitization.SanitizationPolicy;
import com.cybersammy.bugreport.core.sanitization.SanitizationResult;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.source.SourceProvenance;
import java.io.StringReader;
import java.io.StringWriter;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final String RAW_ARTIFACT_TEXT =
            "Authorization: Bearer secret_token_123456\n";
    private static final byte[] ARTIFACT_BYTES =
            "Authorization: <bearer-token>\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GENERATED_BYTES = "{\"safe\":true}".getBytes(StandardCharsets.UTF_8);

    @TempDir Path temporaryDirectory;

    @Test
    void createsDeterministicPlanBoundToCurrentReviewedBytes() throws Exception {
        Fixture fixture = fixture();
        ReportManifest manifest = manifest(fixture, checksum(ARTIFACT_BYTES));

        ReportPackagePlan first = ReportPackagePlanFactory.create(
                fixture.prepared(), fixture.workspace(), manifest, true);
        ReportPackagePlan repeated = ReportPackagePlanFactory.create(
                fixture.prepared(), fixture.workspace(), manifest, true);

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
        assertFalse(Files.readString(
                        fixture.workspace().directory().resolve(ARTIFACT_NAME),
                        StandardCharsets.UTF_8)
                .contains("secret_token_123456"));

        byte[] mutableCopy = first.manifestDocument();
        mutableCopy[0] = 0;
        assertEquals('{', first.manifestDocument()[0]);
    }

    @Test
    void rejectsManifestIdentityAndEntryMetadataOutsideReviewedSnapshot() throws Exception {
        Fixture fixture = fixture();
        ReportManifest wrongEntry =
                manifest(fixture, checksum("different".getBytes(StandardCharsets.UTF_8)));

        ReportPackagePlanException entryFailure = assertThrows(
                ReportPackagePlanException.class,
                () -> ReportPackagePlanFactory.create(
                        fixture.prepared(), fixture.workspace(), wrongEntry, false));
        assertEquals(ReportPackagePlanCode.ENTRY_MISMATCH, entryFailure.code());
        assertEquals(Optional.of(ARTIFACT_NAME), entryFailure.artifactName());
        assertFalse(entryFailure.getMessage().contains(fixture.workspace().directory().toString()));

        ReportManifest wrongReport = manifestBuilder(
                        ReportSessionId.parse("22222222-2222-4222-8222-222222222222"))
                .entries(List.of(
                        manifestEntry(fixture, checksum(ARTIFACT_BYTES)),
                        generatedManifestEntry(fixture)))
                .build();
        ReportPackagePlanException identityFailure = assertThrows(
                ReportPackagePlanException.class,
                () -> ReportPackagePlanFactory.create(
                        fixture.prepared(), fixture.workspace(), wrongReport, false));
        assertEquals(ReportPackagePlanCode.SNAPSHOT_MISMATCH, identityFailure.code());
    }

    @Test
    void changedWorkspaceCannotProduceAnotherPlan() throws Exception {
        Fixture fixture = fixture();
        ReportPackagePlanFactory.create(
                fixture.prepared(),
                fixture.workspace(),
                manifest(fixture, checksum(ARTIFACT_BYTES)),
                false);
        Files.writeString(
                fixture.workspace().directory().resolve(ARTIFACT_NAME),
                "changed bytes",
                StandardCharsets.UTF_8);

        ReviewedWorkspaceSnapshotException failure = assertThrows(
                ReviewedWorkspaceSnapshotException.class,
                () -> ReportPackagePlanFactory.create(
                        fixture.prepared(),
                        fixture.workspace(),
                        manifest(fixture, checksum(ARTIFACT_BYTES)),
                        false));
        assertEquals(ReviewedWorkspaceSnapshotCode.ARTIFACT_CHANGED, failure.code());
    }

    @Test
    void ordinarySnapshotCannotClaimSanitizedWithoutTrustedEvidence() throws Exception {
        Fixture fixture = fixture();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> PreparedWorkspaceSnapshotFactory.issue(
                        fixture.snapshot(),
                        Map.of(GENERATED_NAME, fixture.generatedSanitization()),
                        Set.of()));

        assertEquals("Text artifact requires trusted sanitization evidence", failure.getMessage());
    }

    private Fixture fixture() throws Exception {
        Sanitized source = sanitize(ARTIFACT_NAME, RAW_ARTIFACT_TEXT);
        Sanitized generated = sanitize(GENERATED_NAME, new String(
                GENERATED_BYTES, StandardCharsets.UTF_8));
        assertArrayEquals(ARTIFACT_BYTES, source.output().getBytes(StandardCharsets.UTF_8));
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("workspaces").toAbsolutePath())
                .create(SESSION_ID);
        writePrivate(
                workspace, ARTIFACT_NAME, source.output().getBytes(StandardCharsets.UTF_8));
        writePrivate(
                workspace, GENERATED_NAME, generated.output().getBytes(StandardCharsets.UTF_8));
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
        PreparedWorkspaceSnapshot prepared = PreparedWorkspaceSnapshotFactory.issue(
                snapshot,
                Map.of(
                        ARTIFACT_NAME, source.result(),
                        GENERATED_NAME, generated.result()),
                Set.of());
        return new Fixture(workspace, snapshot, prepared, generated.result());
    }

    private static ReportManifest manifest(Fixture fixture, Sha256Checksum checksum) {
        return manifestBuilder(SESSION_ID)
                .entries(List.of(
                        manifestEntry(fixture, checksum), generatedManifestEntry(fixture)))
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

    private static ManifestEntry manifestEntry(Fixture fixture, Sha256Checksum checksum) {
        PreparedWorkspaceArtifact prepared = fixture.prepared().artifacts().stream()
                .filter(artifact -> artifact.artifact().artifactName().equals(ARTIFACT_NAME))
                .findFirst()
                .orElseThrow();
        return new ManifestEntry(
                "content/" + ARTIFACT_NAME,
                ARTIFACT_BYTES.length,
                checksum,
                DiagnosticContentType.TEXT,
                Optional.of("text/plain"),
                prepared.effectivePrivacy(),
                ReportQualityRole.RECOMMENDED,
                ManifestCollectionStatus.SOURCE_COLLECTED,
                prepared.sanitizationStatus(),
                List.of(ManifestEntryProvenance.source(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        CATEGORY_ID,
                        DiagnosticSourceId.of("latest_log"),
                        DiagnosticSourceKind.EXACT_FILE,
                        PrivacyClassification.PERSONAL)),
                prepared.sanitizationFindings(),
                ExtensionMetadata.empty());
    }

    private static ManifestEntry generatedManifestEntry(Fixture fixture) {
        PreparedWorkspaceArtifact prepared = fixture.prepared().artifacts().stream()
                .filter(artifact -> artifact.artifact().artifactName().equals(GENERATED_NAME))
                .findFirst()
                .orElseThrow();
        return new ManifestEntry(
                "content/" + GENERATED_NAME,
                GENERATED_BYTES.length,
                checksum(GENERATED_BYTES),
                DiagnosticContentType.JSON,
                Optional.of("application/json"),
                prepared.effectivePrivacy(),
                ReportQualityRole.OPTIONAL,
                ManifestCollectionStatus.GENERATOR_COMPLETED,
                prepared.sanitizationStatus(),
                List.of(ManifestEntryProvenance.generator(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        CATEGORY_ID,
                        DiagnosticGeneratorId.of("runtime"),
                        PrivacyClassification.LOW)),
                prepared.sanitizationFindings(),
                ExtensionMetadata.empty());
    }

    private static Sanitized sanitize(String artifactName, String input) {
        StringWriter output = new StringWriter();
        SanitizationResult result = ProductSanitization.textPipeline(
                        SanitizationPolicy.strictPrivacy(SanitizationArtifactPolicy.LOG),
                        "C:\\Users\\Alice",
                        "Alice",
                        SanitizationCaseSensitivity.INSENSITIVE)
                .sanitize(
                        artifactName,
                        new StringReader(input),
                        output,
                        CancellationSignal.neverCancelled());
        return new Sanitized(output.toString(), result);
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

    private record Sanitized(String output, SanitizationResult result) {}

    private record Fixture(
            ReportWorkspace workspace,
            ReviewedWorkspaceSnapshot snapshot,
            PreparedWorkspaceSnapshot prepared,
            SanitizationResult generatedSanitization) {}
}
