package com.cybersammy.bugreport.core.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.DynamicSourcePathProducer;
import com.cybersammy.bugreport.api.specification.DynamicSourcePathSink;
import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DynamicSourcePathPlannerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void resolvesCallbackPathsInCanonicalOrderBelowDeclaredRoot() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/a.log"), "a");
        Files.writeString(temporaryDirectory.resolve("logs/b.log"), "bb");
        AtomicReference<SupportedSide> observedSide = new AtomicReference<>();
        DiagnosticSourceSpecification source = source(
                (request, sink) -> {
                    observedSide.set(request.side());
                    assertFalse(request.cancellation().isCancellationRequested());
                    sink.emit(RelativePath.of("b.log"));
                    sink.emit(RelativePath.of("a.log"));
                },
                2,
                Duration.ofMillis(500),
                SupportedSide.PHYSICAL_CLIENT);

        FileSourcePlan plan = assertInstanceOf(
                FileSourcePlan.class, SourceSelectorPlanner.plan(source, roots));

        assertEquals(SupportedSide.PHYSICAL_CLIENT, observedSide.get());
        assertEquals(
                List.of(RelativePath.of("a.log"), RelativePath.of("b.log")),
                plan.files().stream().map(ResolvedSourceFile::relativePath).toList());
        assertEquals(3, plan.estimate().knownBytes());
    }

    @Test
    void isolatesEmptyThrowingDuplicateNullAndOverLimitResults() throws IOException {
        ApprovedSourceRoots roots = createRoots();

        assertFailure(
                SourceSelectionFailureCode.NO_MATCH,
                source((request, sink) -> {}, 1, Duration.ofMillis(100), client()),
                roots);
        assertFailure(
                SourceSelectionFailureCode.DYNAMIC_CALLBACK_FAILED,
                source(
                        (request, sink) -> {
                            throw new IllegalStateException("provider failure");
                        },
                        1,
                        Duration.ofMillis(100),
                        client()),
                roots);
        assertFailure(
                SourceSelectionFailureCode.DYNAMIC_RESULT_INVALID,
                source(
                        (request, sink) -> {
                            sink.emit(RelativePath.of("same.log"));
                            sink.emit(RelativePath.of("same.log"));
                        },
                        2,
                        Duration.ofMillis(100),
                        client()),
                roots);
        assertFailure(
                SourceSelectionFailureCode.DYNAMIC_RESULT_INVALID,
                source(
                        (request, sink) -> sink.emit(null),
                        1,
                        Duration.ofMillis(100),
                        client()),
                roots);
        assertFailure(
                SourceSelectionFailureCode.MATCH_LIMIT_EXCEEDED,
                source(
                        (request, sink) -> {
                            sink.emit(RelativePath.of("first.log"));
                            sink.emit(RelativePath.of("second.log"));
                        },
                        1,
                        Duration.ofMillis(100),
                        client()),
                roots);
    }

    @Test
    void appliesProductCountCeilingAndCallbackTimeout() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        DiagnosticSourceSpecification tooMany = source(
                (request, sink) -> {
                    sink.emit(RelativePath.of("first.log"));
                    sink.emit(RelativePath.of("second.log"));
                },
                10,
                Duration.ofSeconds(30),
                client());
        UnavailableSourcePlan limited = assertInstanceOf(
                UnavailableSourcePlan.class,
                SourceSelectorPlanner.plan(
                        tooMany,
                        roots,
                        SupportedSide.PHYSICAL_CLIENT,
                        NioSourcePathInspection.INSTANCE,
                        new SourcePlanningLimits(1, 1024, 2048)));
        assertEquals(SourceSelectionFailureCode.MATCH_LIMIT_EXCEEDED, limited.code());

        DiagnosticSourceSpecification slow = source(
                (request, sink) -> {
                    while (!request.cancellation().isCancellationRequested()) {
                        Thread.onSpinWait();
                    }
                },
                1,
                Duration.ofMillis(20),
                client());
        assertFailure(
                SourceSelectionFailureCode.DYNAMIC_CALLBACK_TIMED_OUT, slow, roots);
    }

    @Test
    void rejectsUnsupportedSideBeforeInvokingProvider() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        AtomicBoolean invoked = new AtomicBoolean();
        DiagnosticSourceSpecification serverOnly = source(
                (request, sink) -> invoked.set(true),
                1,
                Duration.ofMillis(100),
                SupportedSide.DEDICATED_SERVER);

        assertFailure(
                SourceSelectionFailureCode.UNSUPPORTED_SIDE, serverOnly, roots);
        assertFalse(invoked.get());
    }

    @Test
    void rejectsCrossThreadAndLateSinkEmissions() throws Exception {
        ApprovedSourceRoots roots = createRoots();
        DiagnosticSourceSpecification crossThread = source(
                (request, sink) -> {
                    Thread other = Thread.ofVirtual().start(
                            () -> {
                                try {
                                    sink.emit(RelativePath.of("async.log"));
                                } catch (RuntimeException expected) {
                                    // The sink records the violation before rejecting the caller.
                                }
                            });
                    other.join();
                },
                1,
                Duration.ofMillis(500),
                client());
        assertFailure(
                SourceSelectionFailureCode.DYNAMIC_RESULT_INVALID,
                crossThread,
                roots);

        AtomicReference<DynamicSourcePathSink> retainedSink = new AtomicReference<>();
        DiagnosticSourceSpecification retained = source(
                (request, sink) -> retainedSink.set(sink),
                1,
                Duration.ofMillis(100),
                client());
        assertFailure(SourceSelectionFailureCode.NO_MATCH, retained, roots);
        assertThrows(
                RuntimeException.class,
                () -> retainedSink.get().emit(RelativePath.of("late.log")));
    }

    @Test
    void rejectsMixedValidAndMissingResultsAtomically() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/present.log"), "present");
        DiagnosticSourceSpecification source = source(
                (request, sink) -> {
                    sink.emit(RelativePath.of("present.log"));
                    sink.emit(RelativePath.of("missing.log"));
                },
                2,
                Duration.ofMillis(100),
                client());

        UnavailableSourcePlan unavailable = assertInstanceOf(
                UnavailableSourcePlan.class, SourceSelectorPlanner.plan(source, roots));

        assertEquals(SourceSelectionFailureCode.SOURCE_MISSING, unavailable.code());
        assertEquals(
                SourcePathResolutionCode.COMPONENT_MISSING,
                unavailable.pathCode().orElseThrow());
    }

    @Test
    void reusesPathResolverForRedirectedDynamicResult() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Files.writeString(outside.resolve("secret.log"), "secret");
        try {
            Files.createSymbolicLink(temporaryDirectory.resolve("logs/redirect"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }
        DiagnosticSourceSpecification source = source(
                (request, sink) -> sink.emit(RelativePath.of("redirect/secret.log")),
                1,
                Duration.ofMillis(100),
                client());

        UnavailableSourcePlan unavailable = assertInstanceOf(
                UnavailableSourcePlan.class, SourceSelectorPlanner.plan(source, roots));

        assertEquals(SourceSelectionFailureCode.PATH_REJECTED, unavailable.code());
        assertEquals(
                SourcePathResolutionCode.PATH_REDIRECTION,
                unavailable.pathCode().orElseThrow());
    }

    private DiagnosticSourceSpecification source(
            DynamicSourcePathProducer producer,
            int maxResults,
            Duration timeout,
            SupportedSide side) {
        return DiagnosticSourceSpecification.dynamicFiles(
                        DiagnosticSourceId.of("dynamic"),
                        LogicalRoot.GAME_LOGS,
                        producer)
                .labelKey(LocalizationKey.of("example.source.dynamic"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(side)
                .constraints(CollectionConstraints.builder()
                        .maxMatchedFiles(maxResults)
                        .maxBytesPerFile(1024)
                        .maxTotalBytes(2048)
                        .callbackTimeout(timeout)
                        .build())
                .build();
    }

    private static SupportedSide client() {
        return SupportedSide.PHYSICAL_CLIENT;
    }

    private static void assertFailure(
            SourceSelectionFailureCode expected,
            DiagnosticSourceSpecification source,
            ApprovedSourceRoots roots) {
        UnavailableSourcePlan unavailable = assertInstanceOf(
                UnavailableSourcePlan.class, SourceSelectorPlanner.plan(source, roots));
        assertEquals(expected, unavailable.code());
    }

    private ApprovedSourceRoots createRoots() throws IOException {
        return ApprovedSourceRoots.of(
                Files.createDirectory(temporaryDirectory.resolve("logs")),
                Files.createDirectory(temporaryDirectory.resolve("crash-reports")),
                Files.createDirectory(temporaryDirectory.resolve("config")));
    }
}
