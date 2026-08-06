package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.identifier.GeneratedArtifactId;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.GeneratedDiagnosticSink;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Capture-only sink that materializes immutable values without filesystem access. */
final class GameThreadSnapshotSink implements GeneratedDiagnosticSink {
    private static final Object COMPLETED = new Object();

    private final DiagnosticContentType contentType;
    private final GeneratedDiagnosticLimits limits;
    private final CancellationSignal cancellation;
    private final AtomicReference<Thread> ownerThread = new AtomicReference<>();
    private final List<CapturedGeneratedEmission> emissions = new ArrayList<>();
    private final Set<GeneratedArtifactId> artifactIds = new HashSet<>();
    private final AtomicReference<Object> terminal = new AtomicReference<>();
    private long capturedCharacters;

    GameThreadSnapshotSink(
            DiagnosticContentType contentType,
            GeneratedDiagnosticLimits limits,
            CancellationSignal cancellation) {
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    }

    void bindOwnerThread() {
        if (!ownerThread.compareAndSet(null, Thread.currentThread())) {
            throw new IllegalStateException("Game-thread snapshot sink is already dispatched");
        }
    }

    @Override
    public void emitText(GeneratedArtifactId id, CharSequence content) {
        requireOwnerThread();
        GeneratedArtifactId artifactId = beginEmission(id, DiagnosticContentType.TEXT);
        String immutable = materialize(Objects.requireNonNull(content, "content"), artifactId);
        requireAvailable(artifactId);
        emissions.add(new CapturedGeneratedEmission.Text(artifactId, immutable));
    }

    @Override
    public void emitJson(GeneratedArtifactId id, ExtensionMetadata content) {
        requireOwnerThread();
        GeneratedArtifactId artifactId = beginEmission(id, DiagnosticContentType.JSON);
        requireAvailable(artifactId);
        emissions.add(new CapturedGeneratedEmission.Json(
                artifactId, Objects.requireNonNull(content, "content")));
    }

    List<CapturedGeneratedEmission> finish() {
        requireOwnerThread();
        requireAvailable(null);
        if (!terminal.compareAndSet(null, COMPLETED)) {
            requireAvailable(null);
            throw new IllegalStateException("Game-thread snapshot completion raced");
        }
        return List.copyOf(emissions);
    }

    void revoke(GeneratedDiagnosticCode code, String message) {
        terminal.compareAndSet(null, new GeneratedSinkViolation(code, null, message));
    }

    CancellationSignal effectiveCancellation() {
        return () -> cancellation.isCancellationRequested()
                || terminal.get() instanceof GeneratedSinkViolation;
    }

    private GeneratedArtifactId beginEmission(
            GeneratedArtifactId id, DiagnosticContentType representation) {
        GeneratedArtifactId artifactId = Objects.requireNonNull(id, "id");
        requireAvailable(artifactId);
        if (contentType != representation) {
            fail(
                    GeneratedDiagnosticCode.CONTENT_TYPE_MISMATCH,
                    artifactId,
                    "Emission representation does not match the generator declaration");
        }
        if (!artifactIds.add(artifactId)) {
            fail(
                    GeneratedDiagnosticCode.DUPLICATE_ARTIFACT_ID,
                    artifactId,
                    "Generated artifact ID was emitted more than once");
        }
        if (artifactIds.size() > limits.maxArtifacts()) {
            artifactIds.remove(artifactId);
            fail(
                    GeneratedDiagnosticCode.ARTIFACT_COUNT_EXCEEDED,
                    artifactId,
                    "Generated diagnostic exceeded its artifact count ceiling");
        }
        return artifactId;
    }

    private String materialize(CharSequence content, GeneratedArtifactId artifactId) {
        int length = content.length();
        long generatorRemaining = limits.maxTotalBytes() - capturedCharacters;
        long collectionRemaining = limits.remainingCollectionBytes() - capturedCharacters;
        long remainingCharacters = Math.min(
                limits.maxBytesPerArtifact(),
                Math.min(generatorRemaining, collectionRemaining));
        if (length > remainingCharacters) {
            fail(
                    exceededCode(generatorRemaining, collectionRemaining),
                    artifactId,
                    "Generated snapshot exceeded its minimum encoded byte ceiling");
        }
        StringBuilder copy = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            requireAvailable(artifactId);
            copy.append(content.charAt(index));
        }
        capturedCharacters = Math.addExact(capturedCharacters, length);
        return copy.toString();
    }

    private GeneratedDiagnosticCode exceededCode(
            long generatorRemaining, long collectionRemaining) {
        if (collectionRemaining <= limits.maxBytesPerArtifact()
                && collectionRemaining <= generatorRemaining) {
            return GeneratedDiagnosticCode.COLLECTION_BYTE_LIMIT_EXCEEDED;
        }
        if (generatorRemaining <= limits.maxBytesPerArtifact()) {
            return GeneratedDiagnosticCode.TOTAL_BYTE_LIMIT_EXCEEDED;
        }
        return GeneratedDiagnosticCode.ARTIFACT_BYTE_LIMIT_EXCEEDED;
    }

    private void requireAvailable(GeneratedArtifactId artifactId) {
        if (cancellation.isCancellationRequested()) {
            fail(
                    GeneratedDiagnosticCode.CANCELLED,
                    artifactId,
                    "Generated diagnostic collection was cancelled");
        }
        Object state = terminal.get();
        if (state instanceof GeneratedSinkViolation violation) {
            throw violation;
        }
        if (state == COMPLETED) {
            throw new GeneratedSinkViolation(
                    GeneratedDiagnosticCode.PROVIDER_FAILURE,
                    artifactId,
                    "Generated snapshot sink is closed");
        }
    }

    private void fail(
            GeneratedDiagnosticCode code,
            GeneratedArtifactId artifactId,
            String message) {
        GeneratedSinkViolation proposed = new GeneratedSinkViolation(code, artifactId, message);
        terminal.compareAndSet(null, proposed);
        Object state = terminal.get();
        throw state instanceof GeneratedSinkViolation violation ? violation : proposed;
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread.get()) {
            throw new GeneratedSinkViolation(
                    GeneratedDiagnosticCode.PROVIDER_FAILURE,
                    null,
                    "Game-thread snapshot sink may only be used by its dispatch thread");
        }
    }
}
