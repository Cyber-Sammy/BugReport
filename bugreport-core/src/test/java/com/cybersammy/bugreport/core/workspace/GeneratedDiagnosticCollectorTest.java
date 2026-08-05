package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.extension.ExtensionValue;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.ExtensionMetadataKey;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.GeneratedDiagnosticProducer;
import com.cybersammy.bugreport.api.specification.GeneratorExecutionContext;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class GeneratedDiagnosticCollectorTest {
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");
    private static final DiagnosticGeneratorId GENERATOR_ID =
            DiagnosticGeneratorId.of("environment");

    @TempDir Path temporaryDirectory;

    @Test
    void publishesCanonicalJsonWithChecksumAndTrustedProvenance() throws IOException {
        ExtensionMetadata metadata = ExtensionMetadata.builder()
                .put(ExtensionMetadataKey.of("example:z"), ExtensionValue.of(new java.math.BigDecimal("2.00")))
                .put(ExtensionMetadataKey.of("example:a"), ExtensionValue.of("value"))
                .build();
        Fixture fixture = fixture(
                DiagnosticContentType.JSON,
                (request, sink) -> sink.emitJson(GeneratedArtifactId.of("snapshot"), metadata),
                constraints(2, 1_024, 2_048),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticResult result = collect(fixture);

        assertEquals(1, result.artifacts().size());
        CollectedGeneratedArtifact artifact = result.artifacts().getFirst();
        String json = Files.readString(
                fixture.workspace().directory().resolve(artifact.artifactName()));
        assertEquals("{\"example:a\":\"value\",\"example:z\":2.00}", json);
        assertEquals(json.getBytes(StandardCharsets.UTF_8).length, artifact.byteCount());
        assertEquals(sha256(json), artifact.checksum());
        assertEquals(PROVIDER_ID, artifact.providerId());
        assertEquals(PROVIDER_VERSION, artifact.providerVersion());
        assertEquals(CATEGORY_ID, artifact.categoryId());
        assertEquals(GENERATOR_ID, artifact.generatorId());
        assertEquals(DiagnosticContentType.JSON, artifact.contentType());
        assertEquals(PrivacyClassification.PERSONAL, artifact.privacy());
        assertEquals(2, workspaceEntryCount(fixture.workspace()));
    }

    @Test
    void returnsArtifactsInCanonicalIdOrderIndependentOfEmissionOrder() throws IOException {
        Fixture fixture = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> {
                    sink.emitText(GeneratedArtifactId.of("zeta"), "z");
                    sink.emitText(GeneratedArtifactId.of("alpha"), "a");
                },
                constraints(2, 10, 20),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticResult result = collect(fixture);

        assertEquals(
                List.of(GeneratedArtifactId.of("alpha"), GeneratedArtifactId.of("zeta")),
                result.artifacts().stream()
                        .map(CollectedGeneratedArtifact::artifactId)
                        .toList());
        assertEquals(2, result.byteCount());
    }

    @Test
    void rejectsTextGeneratorCallingEmitJsonAndRollsBackEarlierArtifacts()
            throws IOException {
        Fixture fixture = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> {
                    sink.emitText(GeneratedArtifactId.of("accepted"), "safe");
                    sink.emitJson(GeneratedArtifactId.of("wrong"), ExtensionMetadata.empty());
                },
                constraints(2, 100, 200),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class, () -> collect(fixture));

        assertEquals(GeneratedDiagnosticCode.CONTENT_TYPE_MISMATCH, failure.code());
        assertEquals(GeneratedArtifactId.of("wrong"), failure.artifactId().orElseThrow());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void rejectsJsonGeneratorCallingEmitText() throws IOException {
        Fixture fixture = fixture(
                DiagnosticContentType.JSON,
                (request, sink) -> sink.emitText(GeneratedArtifactId.of("wrong"), "text"),
                constraints(1, 100, 100),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class, () -> collect(fixture));

        assertEquals(GeneratedDiagnosticCode.CONTENT_TYPE_MISMATCH, failure.code());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void providerCannotRecoverByCatchingASinkViolation() throws IOException {
        Fixture fixture = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> assertThrows(
                        RuntimeException.class,
                        () -> sink.emitJson(
                                GeneratedArtifactId.of("wrong"), ExtensionMetadata.empty())),
                constraints(1, 100, 100),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class, () -> collect(fixture));

        assertEquals(GeneratedDiagnosticCode.CONTENT_TYPE_MISMATCH, failure.code());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void enforcesArtifactCountPerArtifactGeneratorAndReportByteLimits() throws IOException {
        assertFailure(
                GeneratedDiagnosticCode.ARTIFACT_COUNT_EXCEEDED,
                (request, sink) -> {
                    sink.emitText(GeneratedArtifactId.of("one"), "1");
                    sink.emitText(GeneratedArtifactId.of("two"), "2");
                },
                constraints(1, 10, 10),
                10);
        assertFailure(
                GeneratedDiagnosticCode.ARTIFACT_BYTE_LIMIT_EXCEEDED,
                (request, sink) -> sink.emitText(GeneratedArtifactId.of("one"), "12345"),
                constraints(1, 4, 10),
                10);
        assertFailure(
                GeneratedDiagnosticCode.TOTAL_BYTE_LIMIT_EXCEEDED,
                (request, sink) -> {
                    sink.emitText(GeneratedArtifactId.of("one"), "123");
                    sink.emitText(GeneratedArtifactId.of("two"), "456");
                },
                constraints(2, 4, 5),
                10);
        assertFailure(
                GeneratedDiagnosticCode.COLLECTION_BYTE_LIMIT_EXCEEDED,
                (request, sink) -> sink.emitText(GeneratedArtifactId.of("one"), "12345"),
                constraints(1, 10, 10),
                4);
    }

    @Test
    void duplicateArtifactIdFailsAndRollsBackFirstPublication() throws IOException {
        Fixture fixture = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> {
                    sink.emitText(GeneratedArtifactId.of("same"), "first");
                    sink.emitText(GeneratedArtifactId.of("same"), "second");
                },
                constraints(2, 100, 200),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class, () -> collect(fixture));

        assertEquals(GeneratedDiagnosticCode.DUPLICATE_ARTIFACT_ID, failure.code());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void cancellationAfterOneEmissionRollsBackEveryGeneratedArtifact() throws IOException {
        AtomicBoolean cancelled = new AtomicBoolean();
        Fixture fixture = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> {
                    sink.emitText(GeneratedArtifactId.of("first"), "retained only on success");
                    cancelled.set(true);
                },
                constraints(2, 100, 200),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class,
                () -> collect(fixture, cancelled::get, 200));

        assertEquals(GeneratedDiagnosticCode.CANCELLED, failure.code());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void callbackFailureAfterEmissionRollsBackPublishedArtifact() throws IOException {
        Fixture fixture = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> {
                    sink.emitText(GeneratedArtifactId.of("first"), "temporary");
                    throw new IOException("provider details must not escape");
                },
                constraints(1, 100, 100),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class, () -> collect(fixture));

        assertEquals(GeneratedDiagnosticCode.PROVIDER_FAILURE, failure.code());
        assertFalse(failure.getMessage().contains("provider details"));
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void rejectsUnsupportedPhysicalSideBeforeInvokingProvider() throws IOException {
        AtomicBoolean invoked = new AtomicBoolean();
        Fixture fixture = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> invoked.set(true),
                constraints(1, 100, 100),
                SupportedSide.DEDICATED_SERVER);

        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class,
                () -> collect(
                        fixture,
                        CancellationSignal.neverCancelled(),
                        100,
                        SupportedSide.PHYSICAL_CLIENT));

        assertEquals(GeneratedDiagnosticCode.UNSUPPORTED_SIDE, failure.code());
        assertFalse(invoked.get());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void malformedUnicodeIsRejectedWithoutPublishingArtifact() throws IOException {
        Fixture fixture = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> sink.emitText(GeneratedArtifactId.of("invalid"), "\uD800"),
                constraints(1, 100, 100),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class, () -> collect(fixture));

        assertEquals(GeneratedDiagnosticCode.INVALID_TEXT, failure.code());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void appliesByteLimitsToUtf8RatherThanJavaCharacterCount() throws IOException {
        Fixture rejected = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> sink.emitText(GeneratedArtifactId.of("emoji"), "🙂"),
                constraints(1, 3, 100),
                SupportedSide.PHYSICAL_CLIENT);

        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class, () -> collect(rejected));
        assertEquals(GeneratedDiagnosticCode.ARTIFACT_BYTE_LIMIT_EXCEEDED, failure.code());
        assertOnlyMarker(rejected.workspace());

        Fixture accepted = fixture(
                DiagnosticContentType.TEXT,
                (request, sink) -> sink.emitText(GeneratedArtifactId.of("emoji"), "🙂"),
                constraints(1, 4, 100),
                SupportedSide.PHYSICAL_CLIENT);
        GeneratedDiagnosticResult result = collect(accepted);
        assertEquals(4, result.byteCount());
    }

    private void assertFailure(
            GeneratedDiagnosticCode expected,
            GeneratedDiagnosticProducer producer,
            CollectionConstraints constraints,
            long remainingBytes)
            throws IOException {
        Fixture fixture = fixture(
                DiagnosticContentType.TEXT,
                producer,
                constraints,
                SupportedSide.PHYSICAL_CLIENT);
        GeneratedDiagnosticException failure = assertThrows(
                GeneratedDiagnosticException.class,
                () -> collect(fixture, CancellationSignal.neverCancelled(), remainingBytes));
        assertEquals(expected, failure.code());
        assertOnlyMarker(fixture.workspace());
    }

    private GeneratedDiagnosticResult collect(Fixture fixture) {
        return collect(
                fixture,
                CancellationSignal.neverCancelled(),
                FileCollectionCoordinator.PRODUCT_MAX_COLLECTION_BYTES);
    }

    private GeneratedDiagnosticResult collect(
            Fixture fixture, CancellationSignal cancellation, long remainingBytes) {
        return collect(
                fixture,
                cancellation,
                remainingBytes,
                SupportedSide.PHYSICAL_CLIENT);
    }

    private GeneratedDiagnosticResult collect(
            Fixture fixture,
            CancellationSignal cancellation,
            long remainingBytes,
            SupportedSide side) {
        return GeneratedDiagnosticCollector.collect(
                fixture.registry(),
                PROVIDER_ID,
                CATEGORY_ID,
                GENERATOR_ID,
                side,
                fixture.workspace(),
                cancellation,
                remainingBytes);
    }

    private Fixture fixture(
            DiagnosticContentType contentType,
            GeneratedDiagnosticProducer producer,
            CollectionConstraints constraints,
            SupportedSide generatorSide)
            throws IOException {
        DiagnosticGeneratorSpecification generator = DiagnosticGeneratorSpecification.builder(
                        GENERATOR_ID, producer)
                .labelKey(LocalizationKey.of("example.generator.environment"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(contentType)
                .supportSide(generatorSide)
                .executionContext(GeneratorExecutionContext.WORKER)
                .constraints(constraints)
                .build();
        ProviderSpecification specification = ProviderSpecification.builder(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .supportSide(SupportedSide.DEDICATED_SERVER)
                .addGenerator(generator)
                .addCategory(CategorySpecification.builder(
                                CATEGORY_ID,
                                LocalizationKey.of("example.category.general"))
                        .useGenerator(GENERATOR_ID)
                        .build())
                .build();
        ProviderRegistrySnapshot registry = registry(specification);
        ReportSessionId sessionId = ReportSessionId.parse(
                "22222222-2222-2222-2222-" + String.format("%012d", Suffix.next()));
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("workspaces-" + Suffix.next()).toAbsolutePath())
                .create(sessionId);
        return new Fixture(registry, workspace);
    }

    private static CollectionConstraints constraints(
            int artifacts, long perArtifactBytes, long totalBytes) {
        return CollectionConstraints.builder()
                .maxGeneratedArtifacts(artifacts)
                .maxBytesPerFile(perArtifactBytes)
                .maxTotalBytes(totalBytes)
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
                specification.id().namespace(), "GeneratedDiagnosticFixture", provider)));
    }

    private static Sha256Checksum sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new Sha256Checksum(HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
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

    private record Fixture(ProviderRegistrySnapshot registry, ReportWorkspace workspace) {}

    private static final class Suffix {
        private static int value;

        private Suffix() {}

        private static synchronized int next() {
            return value++;
        }
    }
}
