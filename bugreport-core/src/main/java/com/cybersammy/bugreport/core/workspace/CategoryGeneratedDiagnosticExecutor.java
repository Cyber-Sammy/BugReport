package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticGeneratorId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticGeneratorSpecification;
import com.cybersammy.bugreport.api.specification.GeneratorExecutionContext;
import com.cybersammy.bugreport.api.specification.GeneratedDiagnosticRequest;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.registry.ProviderSupportState;
import com.cybersammy.bugreport.core.registry.RegisteredProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Isolates and bounds worker generators declared by one trusted report category. */
public final class CategoryGeneratedDiagnosticExecutor {
    public static final Duration PRODUCT_MAX_CALLBACK_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration PRODUCT_MAX_GAME_THREAD_CAPTURE = Duration.ofMillis(50);
    private static final long CANCELLATION_POLL_NANOS = Duration.ofMillis(10).toNanos();

    private CategoryGeneratedDiagnosticExecutor() {}

    /**
     * Executes worker generators in canonical ID order through revocable virtual workers.
     *
     * <p>Ordinary provider failures and timeouts are isolated to one generator. A successful
     * result consumes the shared remaining byte budget; rolled-back output does not. This
     * compatibility entrypoint has no platform dispatcher, so game-thread snapshot callbacks are
     * reported as unavailable. Production callers should use {@link #executeAsync}.
     */
    public static CategoryGeneratedDiagnosticResult execute(
            ProviderRegistrySnapshot registry,
            ProviderId providerId,
            CategoryId categoryId,
            SupportedSide side,
            ReportWorkspace workspace,
            CancellationSignal cancellation,
            long remainingCollectionBytes) {
        return executeInternal(
                registry,
                providerId,
                categoryId,
                side,
                workspace,
                cancellation,
                remainingCollectionBytes,
                null);
    }

    /**
     * Executes all category orchestration and workspace I/O on a product-owned virtual worker.
     *
     * <p>The supplied dispatcher is used only for short capture-only callbacks. It must enqueue
     * without blocking and return {@code false} when the owning game thread is unavailable.
     */
    public static CompletableFuture<CategoryGeneratedDiagnosticResult> executeAsync(
            ProviderRegistrySnapshot registry,
            ProviderId providerId,
            CategoryId categoryId,
            SupportedSide side,
            ReportWorkspace workspace,
            CancellationSignal cancellation,
            long remainingCollectionBytes,
            GameThreadDispatcher gameThreadDispatcher) {
        Objects.requireNonNull(gameThreadDispatcher, "gameThreadDispatcher");
        CompletableFuture<CategoryGeneratedDiagnosticResult> completion =
                new CompletableFuture<>();
        Thread.startVirtualThread(() -> {
            try {
                completion.complete(executeInternal(
                        registry,
                        providerId,
                        categoryId,
                        side,
                        workspace,
                        cancellation,
                        remainingCollectionBytes,
                        gameThreadDispatcher));
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
        });
        return completion;
    }

    private static CategoryGeneratedDiagnosticResult executeInternal(
            ProviderRegistrySnapshot registry,
            ProviderId providerId,
            CategoryId categoryId,
            SupportedSide side,
            ReportWorkspace workspace,
            CancellationSignal cancellation,
            long remainingCollectionBytes,
            GameThreadDispatcher gameThreadDispatcher) {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(workspace, "workspace");
        CancellationSignal signal = Objects.requireNonNull(cancellation, "cancellation");
        if (remainingCollectionBytes < 0
                || remainingCollectionBytes
                        > FileCollectionCoordinator.PRODUCT_MAX_COLLECTION_BYTES) {
            throw new IllegalArgumentException(
                    "Remaining collection bytes must be within the report product ceiling");
        }
        RegisteredProvider provider = resolveProvider(registry, providerId, categoryId, workspace);
        CategorySpecification category = provider.specification().categories().get(categoryId);
        if (category == null) {
            throw requestFailure(
                    GeneratedDiagnosticCode.CATEGORY_NOT_DECLARED,
                    providerId,
                    categoryId,
                    workspace,
                    "Cannot generate diagnostics for an undeclared category");
        }

        List<GeneratedDiagnosticOutcome> outcomes = new ArrayList<>();
        long retainedBytes = 0;
        for (DiagnosticGeneratorId generatorId : category.generatorIds()) {
            if (signal.isCancellationRequested()) {
                outcomes.add(GeneratedDiagnosticOutcome.failed(
                        generatorId,
                        GeneratedDiagnosticOutcomeStatus.CANCELLED,
                        GeneratedDiagnosticCode.CANCELLED));
                continue;
            }
            DiagnosticGeneratorSpecification generator =
                    provider.specification().generators().get(generatorId);
            if (generator.executionContext() == GeneratorExecutionContext.GAME_THREAD_SNAPSHOT
                    && gameThreadDispatcher == null) {
                outcomes.add(GeneratedDiagnosticOutcome.failed(
                        generatorId,
                        GeneratedDiagnosticOutcomeStatus.EXECUTION_CONTEXT_UNAVAILABLE,
                        GeneratedDiagnosticCode.EXECUTION_CONTEXT_UNAVAILABLE));
                continue;
            }
            long availableBytes = remainingCollectionBytes - retainedBytes;
            GeneratedDiagnosticTask task;
            try {
                task = GeneratedDiagnosticCollector.prepare(
                        registry,
                        providerId,
                        categoryId,
                        generatorId,
                        side,
                        workspace,
                        signal,
                        availableBytes);
            } catch (GeneratedDiagnosticException exception) {
                outcomes.add(failureOutcome(generatorId, exception));
                continue;
            }
            GeneratedDiagnosticOutcome outcome = generator.executionContext()
                            == GeneratorExecutionContext.WORKER
                    ? executeWorker(task, effectiveTimeout(generator))
                    : executeGameThreadSnapshot(
                            task,
                            gameThreadDispatcher,
                            gameThreadCaptureTimeout(generator));
            outcomes.add(outcome);
            if (outcome.result().isPresent()) {
                retainedBytes = Math.addExact(
                        retainedBytes, outcome.result().orElseThrow().byteCount());
            }
        }
        return new CategoryGeneratedDiagnosticResult(
                providerId, categoryId, outcomes, retainedBytes);
    }

    private static GeneratedDiagnosticOutcome executeGameThreadSnapshot(
            GeneratedDiagnosticTask task,
            GameThreadDispatcher dispatcher,
            Duration timeout) {
        DiagnosticGeneratorSpecification generator = task.invocation().generator();
        GameThreadSnapshotSink snapshotSink = new GameThreadSnapshotSink(
                generator.contentType(),
                task.limits(),
                task.effectiveCancellation());
        CompletableFuture<List<CapturedGeneratedEmission>> captured = new CompletableFuture<>();
        boolean accepted;
        try {
            accepted = dispatcher.dispatch(() -> captureOnGameThread(task, snapshotSink, captured));
        } catch (RuntimeException failure) {
            accepted = false;
        }
        if (!accepted) {
            snapshotSink.revoke(
                    GeneratedDiagnosticCode.EXECUTION_CONTEXT_UNAVAILABLE,
                    "Owning game thread is unavailable");
            task.requestRevocation(
                    GeneratedDiagnosticCode.EXECUTION_CONTEXT_UNAVAILABLE,
                    "Owning game thread is unavailable");
            return GeneratedDiagnosticOutcome.failed(
                    generator.id(),
                    GeneratedDiagnosticOutcomeStatus.EXECUTION_CONTEXT_UNAVAILABLE,
                    GeneratedDiagnosticCode.EXECUTION_CONTEXT_UNAVAILABLE);
        }

        long deadline = saturatedDeadline(timeout);
        while (true) {
            if (task.cancellation().isCancellationRequested()) {
                return revokeGameThreadSnapshot(
                        task,
                        snapshotSink,
                        GeneratedDiagnosticOutcomeStatus.CANCELLED,
                        GeneratedDiagnosticCode.CANCELLED,
                        "Generated diagnostic collection was cancelled");
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return revokeGameThreadSnapshot(
                        task,
                        snapshotSink,
                        GeneratedDiagnosticOutcomeStatus.TIMED_OUT,
                        GeneratedDiagnosticCode.CALLBACK_TIMED_OUT,
                        "Game-thread diagnostic snapshot timed out");
            }
            try {
                List<CapturedGeneratedEmission> emissions = captured.get(
                        Math.min(remainingNanos, CANCELLATION_POLL_NANOS),
                        TimeUnit.NANOSECONDS);
                return GeneratedDiagnosticOutcome.collected(
                        GeneratedDiagnosticCollector.executeCaptured(task, emissions));
            } catch (TimeoutException ignored) {
                // Poll cancellation while retaining one absolute capture deadline.
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                return revokeGameThreadSnapshot(
                        task,
                        snapshotSink,
                        GeneratedDiagnosticOutcomeStatus.CANCELLED,
                        GeneratedDiagnosticCode.CANCELLED,
                        "Generated diagnostic executor was interrupted");
            } catch (ExecutionException failure) {
                return completedCaptureFailure(generator.id(), failure.getCause());
            } catch (GeneratedDiagnosticException failure) {
                return failureOutcome(generator.id(), failure);
            }
        }
    }

    private static void captureOnGameThread(
            GeneratedDiagnosticTask task,
            GameThreadSnapshotSink sink,
            CompletableFuture<List<CapturedGeneratedEmission>> captured) {
        try {
            sink.bindOwnerThread();
            GeneratedDiagnosticCollector.requireNotCancelled(
                    task.invocation(), task.workspace(), task.effectiveCancellation(), null);
            task.invocation()
                    .generator()
                    .producer()
                    .generate(
                            new GeneratedDiagnosticRequest(
                                    task.side(), sink.effectiveCancellation()),
                            sink);
            captured.complete(sink.finish());
        } catch (Throwable failure) {
            captured.completeExceptionally(failure);
        }
    }

    private static GeneratedDiagnosticOutcome revokeGameThreadSnapshot(
            GeneratedDiagnosticTask task,
            GameThreadSnapshotSink sink,
            GeneratedDiagnosticOutcomeStatus status,
            GeneratedDiagnosticCode code,
            String message) {
        sink.revoke(code, message);
        task.requestRevocation(code, message);
        return GeneratedDiagnosticOutcome.failed(
                task.invocation().generator().id(), status, code);
    }

    private static GeneratedDiagnosticOutcome completedCaptureFailure(
            DiagnosticGeneratorId generatorId, Throwable cause) {
        if (cause instanceof Error fatal) {
            throw fatal;
        }
        if (cause instanceof GeneratedSinkViolation violation) {
            return failureOutcome(generatorId, violation.code());
        }
        return GeneratedDiagnosticOutcome.failed(
                generatorId,
                GeneratedDiagnosticOutcomeStatus.FAILED,
                GeneratedDiagnosticCode.PROVIDER_FAILURE);
    }

    private static GeneratedDiagnosticOutcome executeWorker(
            GeneratedDiagnosticTask task, Duration timeout) {
        FutureTask<GeneratedDiagnosticResult> future =
                new FutureTask<>(() -> GeneratedDiagnosticCollector.execute(task));
        Thread worker = Thread.ofVirtual()
                .name("bugreport-generated-diagnostic")
                .unstarted(future);
        worker.start();
        long deadline = saturatedDeadline(timeout);
        while (true) {
            if (task.cancellation().isCancellationRequested()) {
                return revoke(
                        task,
                        future,
                        GeneratedDiagnosticOutcomeStatus.CANCELLED,
                        GeneratedDiagnosticCode.CANCELLED,
                        "Generated diagnostic collection was cancelled");
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return revoke(
                        task,
                        future,
                        GeneratedDiagnosticOutcomeStatus.TIMED_OUT,
                        GeneratedDiagnosticCode.CALLBACK_TIMED_OUT,
                        "Generated diagnostic provider callback timed out");
            }
            try {
                GeneratedDiagnosticResult result = future.get(
                        Math.min(remainingNanos, CANCELLATION_POLL_NANOS),
                        TimeUnit.NANOSECONDS);
                return GeneratedDiagnosticOutcome.collected(result);
            } catch (TimeoutException ignored) {
                // Poll cancellation while retaining one absolute callback deadline.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return revoke(
                        task,
                        future,
                        GeneratedDiagnosticOutcomeStatus.CANCELLED,
                        GeneratedDiagnosticCode.CANCELLED,
                        "Generated diagnostic executor was interrupted");
            } catch (ExecutionException exception) {
                return completedFailure(task.invocation().generator().id(), exception.getCause());
            }
        }
    }

    private static GeneratedDiagnosticOutcome revoke(
            GeneratedDiagnosticTask task,
            FutureTask<GeneratedDiagnosticResult> future,
            GeneratedDiagnosticOutcomeStatus status,
            GeneratedDiagnosticCode code,
            String message) {
        GeneratedDiagnosticCode revocationCode = task.requestRevocation(code, message);
        if (revocationCode == null) {
            try {
                return GeneratedDiagnosticOutcome.collected(future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return GeneratedDiagnosticOutcome.failed(
                        task.invocation().generator().id(),
                        GeneratedDiagnosticOutcomeStatus.CANCELLED,
                        GeneratedDiagnosticCode.CANCELLED);
            } catch (ExecutionException exception) {
                return completedFailure(
                        task.invocation().generator().id(), exception.getCause());
            }
        }
        future.cancel(true);
        return GeneratedDiagnosticOutcome.failed(
                task.invocation().generator().id(), status, revocationCode);
    }

    private static GeneratedDiagnosticOutcome completedFailure(
            DiagnosticGeneratorId generatorId, Throwable cause) {
        if (cause instanceof Error fatal) {
            throw fatal;
        }
        if (cause instanceof GeneratedDiagnosticException failure) {
            return failureOutcome(generatorId, failure);
        }
        return GeneratedDiagnosticOutcome.failed(
                generatorId,
                GeneratedDiagnosticOutcomeStatus.FAILED,
                GeneratedDiagnosticCode.PROVIDER_FAILURE);
    }

    private static GeneratedDiagnosticOutcome failureOutcome(
            DiagnosticGeneratorId generatorId, GeneratedDiagnosticException failure) {
        return failureOutcome(generatorId, failure.code());
    }

    private static GeneratedDiagnosticOutcome failureOutcome(
            DiagnosticGeneratorId generatorId, GeneratedDiagnosticCode code) {
        GeneratedDiagnosticOutcomeStatus status = switch (code) {
            case CANCELLED -> GeneratedDiagnosticOutcomeStatus.CANCELLED;
            case CALLBACK_TIMED_OUT -> GeneratedDiagnosticOutcomeStatus.TIMED_OUT;
            case COLLECTION_BYTE_LIMIT_EXCEEDED ->
                GeneratedDiagnosticOutcomeStatus.BUDGET_REJECTED;
            case EXECUTION_CONTEXT_UNAVAILABLE ->
                GeneratedDiagnosticOutcomeStatus.EXECUTION_CONTEXT_UNAVAILABLE;
            default -> GeneratedDiagnosticOutcomeStatus.FAILED;
        };
        return GeneratedDiagnosticOutcome.failed(generatorId, status, code);
    }

    private static Duration effectiveTimeout(DiagnosticGeneratorSpecification generator) {
        Duration requested = generator.constraints()
                .callbackTimeout()
                .orElse(PRODUCT_MAX_CALLBACK_TIMEOUT);
        return requested.compareTo(PRODUCT_MAX_CALLBACK_TIMEOUT) < 0
                ? requested
                : PRODUCT_MAX_CALLBACK_TIMEOUT;
    }

    private static Duration gameThreadCaptureTimeout(
            DiagnosticGeneratorSpecification generator) {
        Duration callbackTimeout = effectiveTimeout(generator);
        return callbackTimeout.compareTo(PRODUCT_MAX_GAME_THREAD_CAPTURE) < 0
                ? callbackTimeout
                : PRODUCT_MAX_GAME_THREAD_CAPTURE;
    }

    private static long saturatedDeadline(Duration timeout) {
        long now = System.nanoTime();
        long duration = timeout.toNanos();
        return Long.MAX_VALUE - now < duration ? Long.MAX_VALUE : now + duration;
    }

    private static RegisteredProvider resolveProvider(
            ProviderRegistrySnapshot registry,
            ProviderId providerId,
            CategoryId categoryId,
            ReportWorkspace workspace) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(categoryId, "categoryId");
        RegisteredProvider provider = registry.find(providerId)
                .orElseThrow(() -> requestFailure(
                        GeneratedDiagnosticCode.PROVIDER_NOT_REGISTERED,
                        providerId,
                        categoryId,
                        workspace,
                        "Cannot generate diagnostics for an unregistered provider"));
        if (provider.support().state() == ProviderSupportState.DISABLED) {
            throw requestFailure(
                    GeneratedDiagnosticCode.PROVIDER_DISABLED,
                    providerId,
                    categoryId,
                    workspace,
                    "Cannot generate diagnostics for a disabled provider");
        }
        return provider;
    }

    private static CategoryGeneratedDiagnosticException requestFailure(
            GeneratedDiagnosticCode code,
            ProviderId providerId,
            CategoryId categoryId,
            ReportWorkspace workspace,
            String message) {
        return new CategoryGeneratedDiagnosticException(
                code,
                workspace.sessionId(),
                providerId,
                categoryId,
                message);
    }
}
