package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.GeneratedDiagnosticSink;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Transactional product-owned implementation of one generated diagnostic sink. */
final class BoundedGeneratedDiagnosticSink implements GeneratedDiagnosticSink {
    private static final Object COMPLETED = new Object();

    private final GeneratedDiagnosticInvocation invocation;
    private final ReportWorkspace workspace;
    private final CancellationSignal cancellation;
    private final GeneratedDiagnosticLimits limits;
    private final TreeMap<GeneratedArtifactId, CollectedGeneratedArtifact> artifacts =
            new TreeMap<>();
    private final List<WorkspaceGeneratedArtifactPublisher.PublishedArtifact> published =
            new ArrayList<>();
    private final AtomicInteger activeEmissions = new AtomicInteger();
    private final AtomicBoolean externallyRevoked = new AtomicBoolean();
    private final AtomicBoolean revocationRollbackScheduled = new AtomicBoolean();
    private long totalBytes;
    private final AtomicReference<Object> terminal = new AtomicReference<>();
    private boolean rollbackAttempted;
    private GeneratedDiagnosticException rollbackFailure;

    BoundedGeneratedDiagnosticSink(
            GeneratedDiagnosticInvocation invocation,
            ReportWorkspace workspace,
            CancellationSignal cancellation,
            GeneratedDiagnosticLimits limits) {
        this.invocation = Objects.requireNonNull(invocation, "invocation");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public void emitText(GeneratedArtifactId id, CharSequence content) {
        Objects.requireNonNull(content, "content");
        emitSynchronized(id, DiagnosticContentType.TEXT, output ->
                GeneratedContentEncoder.writeText(
                        output, content, outputLimits(), effectiveCancellation()));
    }

    @Override
    public void emitJson(GeneratedArtifactId id, ExtensionMetadata content) {
        Objects.requireNonNull(content, "content");
        emitSynchronized(id, DiagnosticContentType.JSON, output ->
                GeneratedContentEncoder.writeJson(
                        output, content, outputLimits(), effectiveCancellation()));
    }

    synchronized GeneratedDiagnosticResult finish() {
        requireUsable(null);
        GeneratedDiagnosticCollector.requireNotCancelled(
                invocation, workspace, cancellation, null);
        if (!terminal.compareAndSet(null, COMPLETED)) {
            requireUsable(null);
            throw new IllegalStateException("Generated diagnostic sink completion raced");
        }
        return new GeneratedDiagnosticResult(
                invocation.provider().id(),
                invocation.provider().version(),
                invocation.category().id(),
                invocation.generator().id(),
                List.copyOf(artifacts.values()),
                totalBytes);
    }

    synchronized GeneratedDiagnosticException rollback(GeneratedDiagnosticException original) {
        Objects.requireNonNull(original, "original");
        terminal.compareAndSet(
                null,
                new GeneratedSinkViolation(
                        original.code(),
                        original.artifactId().orElse(null),
                        original.getMessage(),
                        original));
        if (rollbackAttempted) {
            return rollbackFailure == null ? original : rollbackFailure;
        }
        rollbackAttempted = true;
        try {
            WorkspaceGeneratedArtifactPublisher.rollback(workspace, published);
            published.clear();
            return original;
        } catch (IOException | RuntimeException exception) {
            original.addSuppressed(exception);
            rollbackFailure = GeneratedDiagnosticCollector.failure(
                    GeneratedDiagnosticCode.ROLLBACK_FAILED,
                    invocation,
                    workspace,
                    original.artifactId().orElse(null),
                    "Generated artifacts could not be safely rolled back",
                    original);
            return rollbackFailure;
        }
    }

    GeneratedDiagnosticCode requestRevocation(
            GeneratedDiagnosticCode code, String message) {
        if (!terminal.compareAndSet(
                null, new GeneratedSinkViolation(code, null, message))) {
            return null;
        }
        externallyRevoked.set(true);
        scheduleRevocationRollbackIfIdle();
        return code;
    }

    private void emitSynchronized(
            GeneratedArtifactId artifactId,
            DiagnosticContentType representation,
            WorkspaceGeneratedArtifactPublisher.ContentWriter writer) {
        activeEmissions.incrementAndGet();
        try {
            synchronized (this) {
                emit(artifactId, representation, writer);
            }
        } finally {
            if (activeEmissions.decrementAndGet() == 0) {
                scheduleRevocationRollbackIfIdle();
            }
        }
    }

    private void scheduleRevocationRollbackIfIdle() {
        Object state = terminal.get();
        if (!externallyRevoked.get()
                || activeEmissions.get() != 0
                || !(state instanceof GeneratedSinkViolation violation)
                || !revocationRollbackScheduled.compareAndSet(false, true)) {
            return;
        }
        Thread.startVirtualThread(() -> rollback(GeneratedDiagnosticCollector.failure(
                violation.code(),
                invocation,
                workspace,
                violation.artifactId(),
                violation.getMessage(),
                violation)));
    }

    private void emit(
            GeneratedArtifactId artifactId,
            DiagnosticContentType representation,
            WorkspaceGeneratedArtifactPublisher.ContentWriter writer) {
        GeneratedArtifactId id = Objects.requireNonNull(artifactId, "id");
        requireUsable(id);
        if (invocation.generator().contentType() != representation) {
            fail(
                    GeneratedDiagnosticCode.CONTENT_TYPE_MISMATCH,
                    id,
                    "Emission representation does not match the generator declaration");
        }
        if (artifacts.containsKey(id)) {
            fail(
                    GeneratedDiagnosticCode.DUPLICATE_ARTIFACT_ID,
                    id,
                    "Generated artifact ID was emitted more than once");
        }
        if (artifacts.size() >= limits.maxArtifacts()) {
            fail(
                    GeneratedDiagnosticCode.ARTIFACT_COUNT_EXCEEDED,
                    id,
                    "Generated diagnostic exceeded its artifact count ceiling");
        }
        GeneratedDiagnosticCollector.requireNotCancelled(
                invocation, workspace, cancellation, id);
        String artifactName = artifactName(id, representation);
        try {
            WorkspaceGeneratedArtifactPublisher.PublishedArtifact stored =
                    WorkspaceGeneratedArtifactPublisher.publish(
                            workspace, artifactName, writer, effectiveCancellation());
            published.add(stored);
            requireUsable(id);
            WorkspaceGeneratedArtifactPublisher.WriteResult result = stored.result();
            totalBytes = Math.addExact(totalBytes, result.byteCount());
            artifacts.put(id, metadata(artifactName, id, result, representation));
        } catch (WorkspaceGeneratedArtifactPublisher.PublicationException exception) {
            fail(exception.code(), id, exception.getMessage(), exception);
        } catch (GeneratedContentEncoder.EncodingException exception) {
            fail(exception.code(), id, exception.getMessage(), exception);
        } catch (IOException | SecurityException exception) {
            fail(
                    GeneratedDiagnosticCode.IO_FAILURE,
                    id,
                    "Could not publish generated diagnostic artifact",
                    exception);
        }
    }

    private CollectedGeneratedArtifact metadata(
            String artifactName,
            GeneratedArtifactId id,
            WorkspaceGeneratedArtifactPublisher.WriteResult result,
            DiagnosticContentType representation) {
        return new CollectedGeneratedArtifact(
                artifactName,
                id,
                result.byteCount(),
                result.checksum(),
                invocation.provider().id(),
                invocation.provider().version(),
                invocation.category().id(),
                invocation.generator().id(),
                representation,
                invocation.generator().privacy(),
                invocation.generator().qualityRole(),
                invocation.generator().inclusionDefault());
    }

    private GeneratedContentEncoder.OutputLimits outputLimits() {
        return new GeneratedContentEncoder.OutputLimits(
                limits.maxBytesPerArtifact(),
                limits.maxTotalBytes() - totalBytes,
                limits.remainingCollectionBytes() - totalBytes);
    }

    private void requireUsable(GeneratedArtifactId artifactId) {
        Object state = terminal.get();
        if (state instanceof GeneratedSinkViolation violation) {
            throw violation;
        }
        if (state == COMPLETED) {
            throw new GeneratedSinkViolation(
                    GeneratedDiagnosticCode.PROVIDER_FAILURE,
                    artifactId,
                    "Generated diagnostic sink is closed");
        }
    }

    private void fail(
            GeneratedDiagnosticCode code,
            GeneratedArtifactId artifactId,
            String message) {
        fail(code, artifactId, message, null);
    }

    private void fail(
            GeneratedDiagnosticCode code,
            GeneratedArtifactId artifactId,
            String message,
            Throwable cause) {
        GeneratedSinkViolation proposed =
                new GeneratedSinkViolation(code, artifactId, message, cause);
        terminal.compareAndSet(null, proposed);
        Object state = terminal.get();
        if (state instanceof GeneratedSinkViolation violation) {
            throw violation;
        }
        throw proposed;
    }

    private CancellationSignal effectiveCancellation() {
        return () -> cancellation.isCancellationRequested()
                || terminal.get() instanceof GeneratedSinkViolation;
    }

    boolean isRevoked() {
        return terminal.get() instanceof GeneratedSinkViolation;
    }

    private String artifactName(
            GeneratedArtifactId artifactId, DiagnosticContentType contentType) {
        MessageDigest digest = sha256();
        updateUtf8(digest, invocation.provider().id().value());
        updateUtf8(digest, invocation.provider().version().value());
        updateUtf8(digest, invocation.category().id().value());
        updateUtf8(digest, invocation.generator().id().value());
        updateUtf8(digest, artifactId.value());
        updateUtf8(digest, contentType.name());
        String suffix = contentType == DiagnosticContentType.TEXT ? ".txt" : ".json";
        return "generated-" + HexFormat.of().formatHex(digest.digest()) + suffix;
    }

    private static void updateUtf8(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Required SHA-256 implementation is unavailable", exception);
        }
    }

}
