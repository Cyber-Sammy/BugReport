package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
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

/** Transactional product-owned implementation of one generated diagnostic sink. */
final class BoundedGeneratedDiagnosticSink implements GeneratedDiagnosticSink {
    private final GeneratedDiagnosticInvocation invocation;
    private final ReportWorkspace workspace;
    private final CancellationSignal cancellation;
    private final EffectiveLimits limits;
    private final TreeMap<GeneratedArtifactId, CollectedGeneratedArtifact> artifacts =
            new TreeMap<>();
    private final List<WorkspaceGeneratedArtifactPublisher.PublishedArtifact> published =
            new ArrayList<>();
    private long totalBytes;
    private GeneratedSinkViolation violation;
    private boolean closed;

    BoundedGeneratedDiagnosticSink(
            GeneratedDiagnosticInvocation invocation,
            ReportWorkspace workspace,
            CancellationSignal cancellation,
            long remainingCollectionBytes) {
        this.invocation = Objects.requireNonNull(invocation, "invocation");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        limits = EffectiveLimits.from(
                invocation.generator().constraints(), remainingCollectionBytes);
    }

    @Override
    public synchronized void emitText(GeneratedArtifactId id, CharSequence content) {
        Objects.requireNonNull(content, "content");
        emit(id, DiagnosticContentType.TEXT, output -> GeneratedContentEncoder.writeText(
                output, content, outputLimits(), cancellation));
    }

    @Override
    public synchronized void emitJson(GeneratedArtifactId id, ExtensionMetadata content) {
        Objects.requireNonNull(content, "content");
        emit(id, DiagnosticContentType.JSON, output -> GeneratedContentEncoder.writeJson(
                output, content, outputLimits(), cancellation));
    }

    synchronized GeneratedDiagnosticResult finish() {
        requireUsable(null);
        GeneratedDiagnosticCollector.requireNotCancelled(
                invocation, workspace, cancellation, null);
        closed = true;
        return new GeneratedDiagnosticResult(
                invocation.provider().id(),
                invocation.provider().version(),
                invocation.category().id(),
                invocation.generator().id(),
                List.copyOf(artifacts.values()),
                totalBytes);
    }

    GeneratedDiagnosticException rollback(GeneratedDiagnosticException original) {
        closed = true;
        try {
            WorkspaceGeneratedArtifactPublisher.rollback(workspace, published);
            return original;
        } catch (IOException | SecurityException exception) {
            original.addSuppressed(exception);
            return GeneratedDiagnosticCollector.failure(
                    GeneratedDiagnosticCode.ROLLBACK_FAILED,
                    invocation,
                    workspace,
                    original.artifactId().orElse(null),
                    "Generated artifacts could not be safely rolled back",
                    original);
        }
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
                    WorkspaceGeneratedArtifactPublisher.publish(workspace, artifactName, writer);
            published.add(stored);
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
        if (violation != null) {
            throw violation;
        }
        if (closed) {
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
        violation = new GeneratedSinkViolation(code, artifactId, message, cause);
        throw violation;
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

    private record EffectiveLimits(
            int maxArtifacts,
            long maxBytesPerArtifact,
            long maxTotalBytes,
            long remainingCollectionBytes) {
        private static EffectiveLimits from(
                CollectionConstraints constraints, long remainingCollectionBytes) {
            int requestedArtifacts = constraints.maxGeneratedArtifacts().isPresent()
                    ? constraints.maxGeneratedArtifacts().getAsInt()
                    : GeneratedDiagnosticCollector.PRODUCT_MAX_ARTIFACTS;
            long requestedPerArtifact = constraints.maxBytesPerFile().isPresent()
                    ? constraints.maxBytesPerFile().getAsLong()
                    : GeneratedDiagnosticCollector.PRODUCT_MAX_BYTES_PER_ARTIFACT;
            long requestedTotal = constraints.maxTotalBytes().isPresent()
                    ? constraints.maxTotalBytes().getAsLong()
                    : GeneratedDiagnosticCollector.PRODUCT_MAX_GENERATOR_BYTES;
            long effectiveTotal = Math.min(
                    GeneratedDiagnosticCollector.PRODUCT_MAX_GENERATOR_BYTES,
                    requestedTotal);
            return new EffectiveLimits(
                    Math.min(
                            GeneratedDiagnosticCollector.PRODUCT_MAX_ARTIFACTS,
                            requestedArtifacts),
                    Math.min(
                            Math.min(
                                    GeneratedDiagnosticCollector.PRODUCT_MAX_BYTES_PER_ARTIFACT,
                                    requestedPerArtifact),
                            effectiveTotal),
                    effectiveTotal,
                    remainingCollectionBytes);
        }
    }
}
