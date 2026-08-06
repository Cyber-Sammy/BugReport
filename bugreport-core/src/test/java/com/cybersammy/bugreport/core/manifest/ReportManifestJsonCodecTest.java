package com.cybersammy.bugreport.core.manifest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.extension.ExtensionValue;
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.ExtensionMetadataKey;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.api.version.ApiVersion;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.sanitization.SanitizationAction;
import com.cybersammy.bugreport.core.sanitization.SanitizationFinding;
import com.cybersammy.bugreport.core.sanitization.SanitizationStageId;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ReportManifestJsonCodecTest {
    private static final ReportSessionId REPORT_ID =
            ReportSessionId.parse("123e4567-e89b-12d3-a456-426614174000");
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example_mod");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.2.3");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");
    private static final String SOURCE_NAME = "source-" + "a".repeat(64) + ".data";
    private static final String GENERATED_NAME = "generated-" + "b".repeat(64) + ".json";

    @Test
    void canonicalRoundTripPreservesTheCompleteModel() {
        ReportManifest manifest = completeManifest(false);

        byte[] encoded = ReportManifestJsonCodec.encode(manifest);
        DecodedReportManifest decoded = ReportManifestJsonCodec.decode(encoded);

        assertEquals(manifest, decoded.manifest());
        assertEquals(ReportManifestJsonCodec.CURRENT_SCHEMA_VERSION, decoded.sourceVersion());
        assertFalse(decoded.newerMinorVersion());
        assertArrayEquals(encoded, ReportManifestJsonCodec.encode(decoded.manifest()));
    }

    @Test
    void canonicalVersionOneFixtureRoundTripsByteForByte() {
        byte[] fixture = readFixture("report-manifest-v1.0.json");
        byte[] canonical = stripFinalNewline(fixture);

        DecodedReportManifest decoded = ReportManifestJsonCodec.decode(fixture);

        assertArrayEquals(canonical, ReportManifestJsonCodec.encode(decoded.manifest()));
        assertEquals(REPORT_ID, decoded.manifest().reportId());
    }

    @Test
    void developmentMajorFixtureIsExplicitlyRejected() {
        ManifestFormatException failure = assertThrows(
                ManifestFormatException.class,
                () -> ReportManifestJsonCodec.decode(readFixture("report-manifest-v0.1.json")));

        assertEquals(ManifestFormatCode.UNSUPPORTED_SCHEMA_MAJOR, failure.code());
    }

    @Test
    void canonicalOrderingDoesNotDependOnBuilderInputOrder() {
        ManifestEntry source = sourceEntry();
        ManifestEntry generated = generatedEntry();
        ManifestCapability alpha = new ManifestCapability(
                CapabilityId.of("bugreport:alpha"), new CapabilityVersion(1, 0));
        ManifestCapability zeta = new ManifestCapability(
                CapabilityId.of("bugreport:zeta"), new CapabilityVersion(1, 1));
        ManifestError first = new ManifestError(
                "collection_failed", "collection", Optional.of(PROVIDER_ID), Optional.empty());
        ManifestError second = new ManifestError(
                "source_missing", "planning", Optional.of(PROVIDER_ID), Optional.of("latest_log"));

        ReportManifest forward = baseBuilder()
                .incomplete(true)
                .capabilities(List.of(alpha, zeta), List.of())
                .entries(List.of(generated, source))
                .errors(List.of(second, first))
                .build();
        ReportManifest reverse = baseBuilder()
                .incomplete(true)
                .capabilities(List.of(zeta, alpha), List.of())
                .entries(List.of(source, generated))
                .errors(List.of(first, second))
                .build();

        assertArrayEquals(
                ReportManifestJsonCodec.encode(forward),
                ReportManifestJsonCodec.encode(reverse));
    }

    @Test
    void compatibleNewerMinorSkipsBoundedUnknownMembers() {
        String current = encodedText(completeManifest(false));
        String newer = current.replace("\"minor\":0", "\"minor\":7")
                .replace(
                        "\"incomplete\":false",
                        "\"future\":{\"safe\":[1,true,null]},\"incomplete\":false");

        DecodedReportManifest decoded = ReportManifestJsonCodec.decode(
                newer.getBytes(StandardCharsets.UTF_8));

        assertEquals(new CapabilityVersion(1, 7).toString(), decoded.sourceVersion().toString());
        assertTrue(decoded.newerMinorVersion());
        assertFalse(encodedText(decoded.manifest()).contains("future"));
    }

    @Test
    void duplicateMembersAreRejectedAtEveryParsedObjectBoundary() {
        ManifestFormatException root = assertThrows(
                ManifestFormatException.class,
                () -> ReportManifestJsonCodec.decode(
                        "{\"schema\":{},\"schema\":{}}"
                                .getBytes(StandardCharsets.UTF_8)));
        String duplicatedExtension = encodedText(completeManifest(false)).replace(
                "\"extensions\":{}",
                "\"extensions\":{\"bugreport:test\":1,\"bugreport:test\":2}");
        ManifestFormatException extension = assertThrows(
                ManifestFormatException.class,
                () -> ReportManifestJsonCodec.decode(
                        duplicatedExtension.getBytes(StandardCharsets.UTF_8)));

        assertEquals(ManifestFormatCode.DUPLICATE_MEMBER, root.code());
        assertEquals(ManifestFormatCode.DUPLICATE_MEMBER, extension.code());
    }

    @Test
    void schemaIdentityAndMajorAreTypedFailures() {
        String current = encodedText(completeManifest(false));
        ManifestFormatException wrongId = decodeFailure(
                current.replace(
                        "bugreport:report_manifest", "other_mod:report_manifest"));
        ManifestFormatException wrongMajor = decodeFailure(
                current.replace("\"major\":1", "\"major\":2"));
        ManifestFormatException developmentMajor = decodeFailure(
                current.replace("\"major\":1", "\"major\":0"));

        assertEquals(ManifestFormatCode.UNSUPPORTED_SCHEMA_ID, wrongId.code());
        assertEquals(ManifestFormatCode.UNSUPPORTED_SCHEMA_MAJOR, wrongMajor.code());
        assertEquals(ManifestFormatCode.UNSUPPORTED_SCHEMA_MAJOR, developmentMajor.code());
    }

    @Test
    void malformedUtf8TrailingContentAndNonCanonicalNumbersAreRejected() {
        ManifestFormatException malformedUtf8 = assertThrows(
                ManifestFormatException.class,
                () -> ReportManifestJsonCodec.decode(new byte[] {'{', '"', (byte) 0xC3, '}'}));
        ManifestFormatException trailing = decodeFailure(
                encodedText(completeManifest(false)) + "{} ");
        ManifestFormatException number = decodeFailure(
                encodedText(completeManifest(false))
                        .replace("\"uncompressedBytes\":7", "\"uncompressedBytes\":07"));

        assertEquals(ManifestFormatCode.MALFORMED_JSON, malformedUtf8.code());
        assertEquals(ManifestFormatCode.MALFORMED_JSON, trailing.code());
        assertEquals(ManifestFormatCode.MALFORMED_JSON, number.code());
    }

    @Test
    void encodedAndUnknownNestingLimitsFailClosed() {
        assertEquals(
                ManifestFormatCode.LIMIT_EXCEEDED,
                assertThrows(
                                ManifestFormatException.class,
                                () -> ReportManifestJsonCodec.decode(
                                        new byte[ReportManifestJsonCodec.MAX_ENCODED_BYTES + 1]))
                        .code());
        String current = encodedText(completeManifest(false));
        String deep = current.replace(
                "\"incomplete\":false",
                "\"future\":" + "[".repeat(18) + "0" + "]".repeat(18)
                        + ",\"incomplete\":false");

        assertEquals(ManifestFormatCode.LIMIT_EXCEEDED, decodeFailure(deep).code());
    }

    @Test
    void aggregateModelRejectsDuplicateAndConflictingIdentity() {
        ManifestCapability capability = new ManifestCapability(
                CapabilityId.of("bugreport:test"), new CapabilityVersion(1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> baseBuilder()
                        .capabilities(List.of(capability), List.of(capability))
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> baseBuilder().entries(List.of(sourceEntry(), sourceEntry())).build());
        ManifestEntryProvenance firstObservation = ManifestEntryProvenance.source(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                DiagnosticSourceId.of("latest_log"),
                DiagnosticSourceKind.LATEST_LOG,
                PrivacyClassification.PERSONAL);
        ManifestEntryProvenance conflictingObservation = ManifestEntryProvenance.source(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                DiagnosticSourceId.of("latest_log"),
                DiagnosticSourceKind.EXACT_FILE,
                PrivacyClassification.SENSITIVE);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ManifestEntry(
                        "content/conflict.data",
                        1,
                        checksum('e'),
                        DiagnosticContentType.TEXT,
                        Optional.empty(),
                        PrivacyClassification.SENSITIVE,
                        ReportQualityRole.OPTIONAL,
                        ManifestCollectionStatus.SOURCE_COLLECTED,
                        ManifestSanitizationStatus.SANITIZED,
                        List.of(firstObservation, conflictingObservation),
                        List.of(),
                        ExtensionMetadata.empty()));

        ManifestEntry mismatched = entry(
                "content/other.data",
                ManifestEntryProvenance.source(
                        ProviderId.parse("other_mod"),
                        PROVIDER_VERSION,
                        CATEGORY_ID,
                        DiagnosticSourceId.of("latest_log"),
                        DiagnosticSourceKind.LATEST_LOG,
                        PrivacyClassification.PERSONAL),
                PrivacyClassification.PERSONAL,
                ManifestSanitizationStatus.SANITIZED,
                List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> baseBuilder().entries(List.of(mismatched)).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ManifestError(
                        "source_missing",
                        "planning",
                        Optional.empty(),
                        Optional.of("latest_log")));
    }

    @Test
    void aggregateBoundsAndErrorProvenanceAreEnforced() {
        ManifestError foreignError = new ManifestError(
                "collection_failed",
                "collection",
                Optional.of(ProviderId.parse("other_mod")),
                Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> baseBuilder().incomplete(true).errors(List.of(foreignError)).build());

        ManifestEntry first = entryWithFindings("first.data", 5_001);
        ManifestEntry second = entryWithFindings("second.data", 5_001);
        assertThrows(
                IllegalArgumentException.class,
                () -> baseBuilder().entries(List.of(first, second)).build());
    }

    @Test
    void privacyAndSanitizationInvariantsPreventUnsafeEntries() {
        ManifestEntryProvenance sensitive = ManifestEntryProvenance.source(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                DiagnosticSourceId.of("config"),
                DiagnosticSourceKind.MOD_CONFIGURATION,
                PrivacyClassification.SENSITIVE);
        assertThrows(
                IllegalArgumentException.class,
                () -> entry(
                        "content/config.data",
                        sensitive,
                        PrivacyClassification.PERSONAL,
                        ManifestSanitizationStatus.SANITIZED,
                        List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> entry(
                        "content/config.data",
                        sensitive,
                        PrivacyClassification.PROHIBITED,
                        ManifestSanitizationStatus.SANITIZED,
                        List.of()));

        SanitizationFinding warning = finding(
                "config.data", SanitizationAction.UNRESOLVED_WARNING);
        assertThrows(
                IllegalArgumentException.class,
                () -> entry(
                        "content/config.data",
                        sensitive,
                        PrivacyClassification.SENSITIVE,
                        ManifestSanitizationStatus.SANITIZED,
                        List.of(warning)));

        SanitizationFinding sensitiveWarning = new SanitizationFinding(
                "config.data",
                new SanitizationStageId("server_address"),
                1,
                1,
                10,
                PrivacyClassification.SENSITIVE,
                SanitizationAction.UNRESOLVED_WARNING);
        assertThrows(
                IllegalArgumentException.class,
                () -> entry(
                        "content/config.data",
                        sensitive,
                        PrivacyClassification.PERSONAL,
                        ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS,
                        List.of(sensitiveWarning)));

        SanitizationFinding prohibitedWarning = new SanitizationFinding(
                "config.data",
                new SanitizationStageId("credential"),
                1,
                1,
                10,
                PrivacyClassification.PROHIBITED,
                SanitizationAction.UNRESOLVED_WARNING);
        assertThrows(
                IllegalArgumentException.class,
                () -> entry(
                        "content/config.data",
                        sensitive,
                        PrivacyClassification.SENSITIVE,
                        ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS,
                        List.of(prohibitedWarning)));
    }

    @Test
    void portableJsonContainsNoExcludedPathSecretOrExceptionMessage() {
        String encoded = encodedText(completeManifest(true));

        assertFalse(encoded.contains("C:\\Users\\Alice"));
        assertFalse(encoded.contains("api_key_secret_value"));
        assertFalse(encoded.contains("provider threw with private details"));
        assertTrue(encoded.contains("collection_failed"));
        assertTrue(encoded.contains("content/" + SOURCE_NAME));
    }

    @Test
    void binaryEntriesRemainSensitiveAndExplicitlyReviewed() {
        ManifestEntryProvenance provenance = ManifestEntryProvenance.source(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                DiagnosticSourceId.of("screenshot"),
                DiagnosticSourceKind.USER_SELECTED_SCREENSHOT,
                PrivacyClassification.SENSITIVE);
        ManifestEntry binary = new ManifestEntry(
                "content/screenshot.png",
                10,
                checksum('c'),
                DiagnosticContentType.BINARY,
                Optional.of("image/png"),
                PrivacyClassification.SENSITIVE,
                ReportQualityRole.OPTIONAL,
                ManifestCollectionStatus.SOURCE_COLLECTED,
                ManifestSanitizationStatus.REVIEWED_WITH_WARNINGS,
                List.of(provenance),
                List.of(),
                ExtensionMetadata.empty());

        assertEquals(
                binary,
                ReportManifestJsonCodec.decode(ReportManifestJsonCodec.encode(
                                baseBuilder().entries(List.of(binary)).build()))
                        .manifest()
                        .entries()
                        .getFirst());
    }

    private static ReportManifest completeManifest(boolean withError) {
        ReportManifest.Builder builder = baseBuilder()
                .incomplete(withError)
                .reviewedFields(FormSubmission.builder()
                        .put(FieldId.of("summary"), new FieldValue.Text("Crash on join"))
                        .put(FieldId.of("confirmed"), new FieldValue.Checkbox(true))
                        .build())
                .capabilities(
                        List.of(new ManifestCapability(
                                CapabilityId.of("bugreport:generated_json"),
                                new CapabilityVersion(1, 0))),
                        List.of(new ManifestCapability(
                                CapabilityId.of("example_mod:extra_context"),
                                new CapabilityVersion(1, 2))))
                .entries(List.of(generatedEntry(), sourceEntry()))
                .extensions(ExtensionMetadata.builder()
                        .put(
                                ExtensionMetadataKey.of("example_mod:manifest_note"),
                                ExtensionValue.object(Map.of(
                                        "enabled", ExtensionValue.of(true),
                                        "count", ExtensionValue.of(new java.math.BigDecimal("2")))))
                        .build());
        if (withError) {
            builder.errors(List.of(new ManifestError(
                    "collection_failed",
                    "collection",
                    Optional.of(PROVIDER_ID),
                    Optional.of("optional_log"))));
        }
        return builder.build();
    }

    private static ReportManifest.Builder baseBuilder() {
        return ReportManifest.builder(
                        REPORT_ID,
                        Instant.parse("2026-08-07T00:00:00Z"),
                        new ManifestProducer("0.0.1-spike", ApiVersion.parse("0.2.0")),
                        new ManifestEnvironment(
                                "1.21.1",
                                "neoforge",
                                "21.1.227",
                                SupportedSide.PHYSICAL_CLIENT))
                .target(new ManifestTarget(PROVIDER_ID, PROVIDER_VERSION, CATEGORY_ID));
    }

    private static ManifestEntry sourceEntry() {
        ManifestEntryProvenance first = ManifestEntryProvenance.source(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                DiagnosticSourceId.of("latest_log"),
                DiagnosticSourceKind.LATEST_LOG,
                PrivacyClassification.PERSONAL);
        ManifestEntryProvenance second = ManifestEntryProvenance.source(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                DiagnosticSourceId.of("debug_log"),
                DiagnosticSourceKind.EXACT_FILE,
                PrivacyClassification.LOW);
        return new ManifestEntry(
                "content/" + SOURCE_NAME,
                7,
                checksum('a'),
                DiagnosticContentType.TEXT,
                Optional.of("text/plain"),
                PrivacyClassification.PERSONAL,
                ReportQualityRole.REQUIRED,
                ManifestCollectionStatus.SOURCE_COLLECTED,
                ManifestSanitizationStatus.SANITIZED,
                List.of(first, second),
                List.of(finding(SOURCE_NAME, SanitizationAction.AUTOMATIC_REDACTION)),
                ExtensionMetadata.empty());
    }

    private static ManifestEntry generatedEntry() {
        ManifestEntryProvenance provenance = ManifestEntryProvenance.generator(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                DiagnosticGeneratorId.of("environment"),
                PrivacyClassification.LOW);
        return new ManifestEntry(
                "content/" + GENERATED_NAME,
                5,
                checksum('b'),
                DiagnosticContentType.JSON,
                Optional.of("application/json"),
                PrivacyClassification.LOW,
                ReportQualityRole.RECOMMENDED,
                ManifestCollectionStatus.GENERATOR_COMPLETED,
                ManifestSanitizationStatus.NOT_REQUIRED,
                List.of(provenance),
                List.of(),
                ExtensionMetadata.empty());
    }

    private static ManifestEntry entry(
            String path,
            ManifestEntryProvenance provenance,
            PrivacyClassification privacy,
            ManifestSanitizationStatus status,
            List<SanitizationFinding> findings) {
        return new ManifestEntry(
                path,
                1,
                checksum('d'),
                DiagnosticContentType.TEXT,
                Optional.of("text/plain"),
                privacy,
                ReportQualityRole.OPTIONAL,
                ManifestCollectionStatus.SOURCE_COLLECTED,
                status,
                List.of(provenance),
                findings,
                ExtensionMetadata.empty());
    }

    private static ManifestEntry entryWithFindings(String artifactName, int findingCount) {
        ManifestEntryProvenance provenance = ManifestEntryProvenance.source(
                PROVIDER_ID,
                PROVIDER_VERSION,
                CATEGORY_ID,
                DiagnosticSourceId.of("latest_log"),
                DiagnosticSourceKind.LATEST_LOG,
                PrivacyClassification.PERSONAL);
        return entry(
                "content/" + artifactName,
                provenance,
                PrivacyClassification.PERSONAL,
                ManifestSanitizationStatus.SANITIZED,
                Collections.nCopies(
                        findingCount,
                        finding(artifactName, SanitizationAction.AUTOMATIC_REDACTION)));
    }

    private static SanitizationFinding finding(
            String artifactName, SanitizationAction action) {
        return new SanitizationFinding(
                artifactName,
                new SanitizationStageId("email_address"),
                2,
                4,
                12,
                PrivacyClassification.PERSONAL,
                action);
    }

    private static Sha256Checksum checksum(char digit) {
        return new Sha256Checksum(String.valueOf(digit).repeat(64));
    }

    private static String encodedText(ReportManifest manifest) {
        return new String(ReportManifestJsonCodec.encode(manifest), StandardCharsets.UTF_8);
    }

    private static ManifestFormatException decodeFailure(String encoded) {
        return assertThrows(
                ManifestFormatException.class,
                () -> ReportManifestJsonCodec.decode(encoded.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] readFixture(String name) {
        try (InputStream input = ReportManifestJsonCodecTest.class.getResourceAsStream(
                "/manifests/" + name)) {
            if (input == null) {
                throw new AssertionError("Missing manifest fixture: " + name);
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new AssertionError("Cannot read manifest fixture: " + name, exception);
        }
    }

    private static byte[] stripFinalNewline(byte[] value) {
        if (value.length > 0 && value[value.length - 1] == '\n') {
            return java.util.Arrays.copyOf(value, value.length - 1);
        }
        return value;
    }
}
