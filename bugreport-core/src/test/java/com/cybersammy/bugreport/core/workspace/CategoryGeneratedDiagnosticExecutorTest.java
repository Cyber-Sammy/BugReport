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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CategoryGeneratedDiagnosticExecutorTest {
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");

    @TempDir Path temporaryDirectory;

    @Test
    void isolatesFailureAndAppliesSharedBudgetInCanonicalOrder() throws IOException {
        DiagnosticGeneratorSpecification alpha = generator(
                "alpha",
                (request, sink) -> {
                    throw new IOException("provider detail");
                },
                Duration.ofSeconds(1),
                GeneratorExecutionContext.WORKER);
        DiagnosticGeneratorSpecification beta = generator(
                "beta",
                (request, sink) -> sink.emitText(GeneratedArtifactId.of("result"), "1234"),
                Duration.ofSeconds(1),
                GeneratorExecutionContext.WORKER);
        DiagnosticGeneratorSpecification gamma = generator(
                "gamma",
                (request, sink) -> sink.emitText(GeneratedArtifactId.of("result"), "5678"),
                Duration.ofSeconds(1),
                GeneratorExecutionContext.WORKER);
        Fixture fixture = fixture(gamma, beta, alpha);

        CategoryGeneratedDiagnosticResult result = execute(fixture, 6);

        assertEquals(
                List.of(
                        DiagnosticGeneratorId.of("alpha"),
                        DiagnosticGeneratorId.of("beta"),
                        DiagnosticGeneratorId.of("gamma")),
                result.outcomes().stream()
                        .map(GeneratedDiagnosticOutcome::generatorId)
                        .toList());
        assertEquals(
                List.of(
                        GeneratedDiagnosticOutcomeStatus.FAILED,
                        GeneratedDiagnosticOutcomeStatus.COLLECTED,
                        GeneratedDiagnosticOutcomeStatus.BUDGET_REJECTED),
                result.outcomes().stream().map(GeneratedDiagnosticOutcome::status).toList());
        assertEquals(4, result.retainedBytes());
        assertEquals(2, workspaceEntryCount(fixture.workspace()));
    }

    @Test
    void timeoutRevokesSinkAndRejectsLateEmissionFromUncooperativeCallback()
            throws Exception {
        CountDownLatch callbackStarted = new CountDownLatch(1);
        CountDownLatch allowLateEmission = new CountDownLatch(1);
        CountDownLatch callbackFinished = new CountDownLatch(1);
        AtomicBoolean lateEmissionRejected = new AtomicBoolean();
        DiagnosticGeneratorSpecification generator = generator(
                "slow",
                (request, sink) -> {
                    try {
                        sink.emitText(GeneratedArtifactId.of("early"), "temporary");
                        callbackStarted.countDown();
                        awaitIgnoringInterrupts(allowLateEmission);
                        try {
                            sink.emitText(GeneratedArtifactId.of("late"), "must-not-survive");
                        } catch (RuntimeException expected) {
                            lateEmissionRejected.set(true);
                        }
                    } finally {
                        callbackFinished.countDown();
                    }
                },
                Duration.ofMillis(30),
                GeneratorExecutionContext.WORKER);
        Fixture fixture = fixture(generator);

        CategoryGeneratedDiagnosticResult result = execute(fixture, 1_000);

        assertTrue(callbackStarted.await(1, TimeUnit.SECONDS));
        assertEquals(GeneratedDiagnosticOutcomeStatus.TIMED_OUT, result.outcomes().get(0).status());
        assertEquals(
                GeneratedDiagnosticCode.CALLBACK_TIMED_OUT,
                result.outcomes().get(0).failureCode().orElseThrow());
        awaitWorkspaceEntryCount(fixture.workspace(), 1);

        allowLateEmission.countDown();
        assertTrue(callbackFinished.await(1, TimeUnit.SECONDS));
        assertTrue(lateEmissionRejected.get());
        awaitWorkspaceEntryCount(fixture.workspace(), 1);
    }

    @Test
    void timeoutReturnsWhileProviderIsBlockedInsideTextEmission() throws Exception {
        CountDownLatch characterReadStarted = new CountDownLatch(1);
        CountDownLatch allowCharacterRead = new CountDownLatch(1);
        CountDownLatch callbackFinished = new CountDownLatch(1);
        CharSequence blockingContent = new CharSequence() {
            @Override
            public int length() {
                return 1;
            }

            @Override
            public char charAt(int index) {
                characterReadStarted.countDown();
                awaitIgnoringInterrupts(allowCharacterRead);
                return 'x';
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                throw new UnsupportedOperationException();
            }
        };
        DiagnosticGeneratorSpecification generator = generator(
                "blocked_emit",
                (request, sink) -> {
                    try {
                        sink.emitText(GeneratedArtifactId.of("blocked"), blockingContent);
                    } finally {
                        callbackFinished.countDown();
                    }
                },
                Duration.ofMillis(250),
                GeneratorExecutionContext.WORKER);
        Fixture fixture = fixture(generator);

        long startedAt = System.nanoTime();
        CategoryGeneratedDiagnosticResult result = execute(fixture, 1_000);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertTrue(characterReadStarted.await(1, TimeUnit.SECONDS));
        assertTrue(elapsed.compareTo(Duration.ofSeconds(1)) < 0, () -> "elapsed=" + elapsed);
        assertEquals(GeneratedDiagnosticOutcomeStatus.TIMED_OUT, result.outcomes().get(0).status());
        assertEquals(
                GeneratedDiagnosticCode.CALLBACK_TIMED_OUT,
                result.outcomes().get(0).failureCode().orElseThrow());

        allowCharacterRead.countDown();
        assertTrue(callbackFinished.await(1, TimeUnit.SECONDS));
        awaitWorkspaceEntryCount(fixture.workspace(), 1);
    }

    @Test
    void unsupportedGameThreadContextIsNotInvoked() throws IOException {
        AtomicBoolean invoked = new AtomicBoolean();
        DiagnosticGeneratorSpecification generator = generator(
                "snapshot",
                (request, sink) -> invoked.set(true),
                Duration.ofMillis(50),
                GeneratorExecutionContext.GAME_THREAD_SNAPSHOT);
        Fixture fixture = fixture(generator);

        CategoryGeneratedDiagnosticResult result = execute(fixture, 1_000);

        assertEquals(
                GeneratedDiagnosticOutcomeStatus.EXECUTION_CONTEXT_UNAVAILABLE,
                result.outcomes().get(0).status());
        assertEquals(
                GeneratedDiagnosticCode.EXECUTION_CONTEXT_UNAVAILABLE,
                result.outcomes().get(0).failureCode().orElseThrow());
        assertFalse(invoked.get());
    }

    @Test
    void cancellationSkipsEveryRemainingGenerator() throws IOException {
        AtomicBoolean invoked = new AtomicBoolean();
        Fixture fixture = fixture(
                generator(
                        "alpha",
                        (request, sink) -> invoked.set(true),
                        Duration.ofSeconds(1),
                        GeneratorExecutionContext.WORKER),
                generator(
                        "beta",
                        (request, sink) -> invoked.set(true),
                        Duration.ofSeconds(1),
                        GeneratorExecutionContext.WORKER));

        CategoryGeneratedDiagnosticResult result = CategoryGeneratedDiagnosticExecutor.execute(
                fixture.registry(),
                PROVIDER_ID,
                CATEGORY_ID,
                SupportedSide.PHYSICAL_CLIENT,
                fixture.workspace(),
                () -> true,
                1_000);

        assertEquals(
                List.of(
                        GeneratedDiagnosticOutcomeStatus.CANCELLED,
                        GeneratedDiagnosticOutcomeStatus.CANCELLED),
                result.outcomes().stream().map(GeneratedDiagnosticOutcome::status).toList());
        assertFalse(invoked.get());
    }

    @Test
    void fatalProviderErrorIsRethrownAfterInvocationRollback() throws IOException {
        Fixture fixture = fixture(generator(
                "fatal",
                (request, sink) -> {
                    sink.emitText(GeneratedArtifactId.of("temporary"), "discard");
                    throw new AssertionError("fatal provider failure");
                },
                Duration.ofSeconds(1),
                GeneratorExecutionContext.WORKER));

        AssertionError failure = assertThrows(AssertionError.class, () -> execute(fixture, 1_000));

        assertEquals("fatal provider failure", failure.getMessage());
        assertEquals(1, workspaceEntryCount(fixture.workspace()));
    }

    private CategoryGeneratedDiagnosticResult execute(Fixture fixture, long remainingBytes) {
        return CategoryGeneratedDiagnosticExecutor.execute(
                fixture.registry(),
                PROVIDER_ID,
                CATEGORY_ID,
                SupportedSide.PHYSICAL_CLIENT,
                fixture.workspace(),
                CancellationSignal.neverCancelled(),
                remainingBytes);
    }

    private Fixture fixture(DiagnosticGeneratorSpecification... generators)
            throws IOException {
        CategorySpecification.Builder category = CategorySpecification.builder(
                CATEGORY_ID, LocalizationKey.of("example.category.general"));
        ProviderSpecification.Builder provider = ProviderSpecification.builder(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT);
        Arrays.stream(generators).forEach(generator -> {
            provider.addGenerator(generator);
            category.useGenerator(generator.id());
        });
        ProviderSpecification specification = provider.addCategory(category.build()).build();
        BugReportProvider bridge = new BugReportProvider() {
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
                new DiscoveredProvider(PROVIDER_ID.namespace(), "GeneratorFixture", bridge)));
        ReportWorkspace workspace = new FileReportWorkspaceStore(
                        temporaryDirectory.resolve("workspace-" + System.nanoTime()).toAbsolutePath())
                .create(ReportSessionId.random());
        return new Fixture(registry, workspace);
    }

    private static DiagnosticGeneratorSpecification generator(
            String id,
            GeneratedDiagnosticProducer producer,
            Duration timeout,
            GeneratorExecutionContext context) {
        return DiagnosticGeneratorSpecification.builder(
                        DiagnosticGeneratorId.of(id), producer)
                .labelKey(LocalizationKey.of("example.generator." + id))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .executionContext(context)
                .constraints(CollectionConstraints.builder()
                        .maxGeneratedArtifacts(2)
                        .maxBytesPerFile(100)
                        .maxTotalBytes(100)
                        .callbackTimeout(timeout)
                        .build())
                .build();
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static int workspaceEntryCount(ReportWorkspace workspace) throws IOException {
        try (var entries = Files.list(workspace.directory())) {
            return Math.toIntExact(entries.count());
        }
    }

    private static void awaitWorkspaceEntryCount(ReportWorkspace workspace, int expected)
            throws IOException {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (workspaceEntryCount(workspace) != expected && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
        }
        assertEquals(expected, workspaceEntryCount(workspace));
    }

    private record Fixture(ProviderRegistrySnapshot registry, ReportWorkspace workspace) {}
}
