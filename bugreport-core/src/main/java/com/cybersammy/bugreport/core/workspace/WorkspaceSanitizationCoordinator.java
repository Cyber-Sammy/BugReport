package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.core.sanitization.SanitizationException;
import com.cybersammy.bugreport.core.sanitization.SanitizationPipeline;
import com.cybersammy.bugreport.core.sanitization.SanitizationResult;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Publishes sanitized text and retains a private exact original only for bounded user review. */
final class WorkspaceSanitizationCoordinator {
    static final int MAX_SANITIZED_BYTES = 128 * 1024 * 1024;
    private WorkspaceSanitizationCoordinator() {}

    static SanitizedSource sanitize(
            CollectedSourceFile source,
            ReportWorkspace workspace,
            SanitizationPipeline pipeline,
            CancellationSignal cancellation) {
        return sanitize(source, workspace, pipeline, cancellation, MAX_SANITIZED_BYTES);
    }

    static SanitizedSource sanitize(
            CollectedSourceFile source,
            ReportWorkspace workspace,
            SanitizationPipeline pipeline,
            CancellationSignal cancellation,
            long maximumBytes) {
        CollectedSourceFile collected = Objects.requireNonNull(source, "source");
        SanitizedBytes sanitized = sanitizeArtifact(
                collected.artifactName(),
                collected.contentType(),
                new ArtifactDigest(collected.byteCount(), collected.checksum()),
                workspace,
                pipeline,
                cancellation,
                maximumBytes);
        CollectedSourceFile value = new CollectedSourceFile(
                collected.artifactName(),
                sanitized.digest().byteCount(),
                sanitized.digest().checksum(),
                collected.provenances(),
                collected.contentType(),
                collected.privacy(),
                collected.qualityRole(),
                collected.inclusionDefault());
        return new SanitizedSource(value, sanitized.result(), sanitized.reviewOriginal());
    }

    static SanitizedGenerated sanitize(
            CollectedGeneratedArtifact artifact,
            ReportWorkspace workspace,
            SanitizationPipeline pipeline,
            CancellationSignal cancellation) {
        CollectedGeneratedArtifact collected = Objects.requireNonNull(artifact, "artifact");
        SanitizedBytes sanitized = sanitizeArtifact(
                collected.artifactName(),
                collected.contentType(),
                new ArtifactDigest(collected.byteCount(), collected.checksum()),
                workspace,
                pipeline,
                cancellation,
                MAX_SANITIZED_BYTES);
        CollectedGeneratedArtifact value = new CollectedGeneratedArtifact(
                collected.artifactName(),
                collected.artifactId(),
                sanitized.digest().byteCount(),
                sanitized.digest().checksum(),
                collected.providerId(),
                collected.providerVersion(),
                collected.categoryId(),
                collected.generatorId(),
                collected.contentType(),
                collected.privacy(),
                collected.qualityRole(),
                collected.inclusionDefault());
        return new SanitizedGenerated(value, sanitized.result(), sanitized.reviewOriginal());
    }

    private static SanitizedBytes sanitizeArtifact(
            String artifactName,
            DiagnosticContentType contentType,
            ArtifactDigest expectedOriginal,
            ReportWorkspace workspace,
            SanitizationPipeline pipeline,
            CancellationSignal cancellation,
            long maximumBytes) {
        if (contentType == DiagnosticContentType.BINARY) {
            throw new IllegalArgumentException("Binary artifacts must not enter text sanitization");
        }
        ReportWorkspace trustedWorkspace = Objects.requireNonNull(workspace, "workspace");
        SanitizationPipeline trustedPipeline = Objects.requireNonNull(pipeline, "pipeline");
        CancellationSignal signal = Objects.requireNonNull(cancellation, "cancellation");
        if (maximumBytes < 1 || maximumBytes > MAX_SANITIZED_BYTES) {
            throw new IllegalArgumentException("Sanitized artifact byte limit is outside the product ceiling");
        }
        Path target = trustedWorkspace.directory().resolve(artifactName).normalize();
        if (!trustedWorkspace.directory().equals(target.getParent())) {
            throw new IllegalArgumentException("Sanitization artifact escaped its workspace");
        }
        Path temporary = trustedWorkspace.directory()
                .resolve(".sanitize-" + UUID.randomUUID() + ".tmp");
        Path reviewOriginal = trustedWorkspace.directory()
                .resolve(".review-original-" + UUID.randomUUID() + "-" + artifactName);
        WorkspaceMutationGate.Lease lease = trustedWorkspace.beginMutation();
        boolean retainReviewOriginal = false;
        try {
            trustedWorkspace.requireCurrentOwnership();
            ArtifactDigest originalDigest = copyOriginal(
                    trustedWorkspace, target, reviewOriginal, MAX_SANITIZED_BYTES);
            if (!originalDigest.equals(expectedOriginal)) {
                throw new IOException("Collected artifact changed before sanitization");
            }
            trustedWorkspace.files().verifyPrivateFile(reviewOriginal);
            SanitizationResult result;
            try (var inputChannel = trustedWorkspace.files().openExistingPrivateFile(reviewOriginal);
                    var outputChannel = trustedWorkspace.files().openNewPrivateFile(temporary);
                    var input = new BufferedReader(Channels.newReader(inputChannel, StandardCharsets.UTF_8));
                    var output = new BufferedWriter(new OutputStreamWriter(
                            new BoundedOutputStream(
                                    Channels.newOutputStream(outputChannel), maximumBytes),
                            StandardCharsets.UTF_8))) {
                result = trustedPipeline.sanitize(artifactName, input, output, signal);
                output.flush();
                outputChannel.force(true);
            }
            trustedWorkspace.files().verifyPrivateFile(temporary);
            ArtifactDigest digest = digest(trustedWorkspace, temporary, maximumBytes);
            trustedWorkspace.requireCurrentOwnership();
            trustedWorkspace.files().replaceAtomically(temporary, target);
            trustedWorkspace.files().verifyPrivateFile(target);
            retainReviewOriginal = true;
            return new SanitizedBytes(
                    digest, result, new ReviewOriginal(reviewOriginal, originalDigest));
        } catch (SanitizationException exception) {
            throw exception;
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalStateException("Could not publish sanitized workspace artifact", exception);
        } finally {
            try {
                trustedWorkspace.files().deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A later workspace cleanup treats unrecognized leftovers as unsafe.
            } finally {
                try {
                    if (!retainReviewOriginal) {
                        trustedWorkspace.files().deleteIfExists(reviewOriginal);
                    }
                } catch (IOException ignored) {
                    // Abandoned-workspace cleanup recognizes product-owned review copies.
                } finally {
                    lease.close();
                }
            }
        }
    }

    private static ArtifactDigest copyOriginal(
            ReportWorkspace workspace, Path source, Path destination, long maximumBytes)
            throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long byteCount = 0;
            ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
            try (var input = workspace.files().openExistingPrivateFile(source);
                    var output = workspace.files().openNewPrivateFile(destination)) {
                while (input.read(buffer) != -1) {
                    buffer.flip();
                    byteCount = Math.addExact(byteCount, buffer.remaining());
                    if (byteCount > maximumBytes) {
                        throw new IOException("Original workspace artifact exceeds the product byte limit");
                    }
                    digest.update(buffer.asReadOnlyBuffer());
                    while (buffer.hasRemaining()) {
                        output.write(buffer);
                    }
                    buffer.clear();
                }
                output.force(true);
            }
            return new ArtifactDigest(
                    byteCount,
                    new Sha256Checksum(HexFormat.of().formatHex(digest.digest())));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", exception);
        } catch (ArithmeticException exception) {
            throw new IOException("Original workspace artifact exceeds the product byte limit", exception);
        }
    }

    static ArtifactDigest digest(
            ReportWorkspace workspace,
            Path path,
            long maximumBytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long byteCount = 0;
            ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
            try (var channel = workspace.files().openExistingPrivateFile(path)) {
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    byteCount = Math.addExact(byteCount, buffer.remaining());
                    if (byteCount > maximumBytes) {
                        throw new IOException("Sanitized workspace artifact exceeds the product byte limit");
                    }
                    digest.update(buffer);
                    buffer.clear();
                }
            }
            return new ArtifactDigest(
                    byteCount,
                    new Sha256Checksum(HexFormat.of().formatHex(digest.digest())));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", exception);
        }
    }

    static void discardReviewOriginals(
            ReportWorkspace workspace, java.util.List<ReviewOriginal> originals) {
        ReportWorkspace trustedWorkspace = Objects.requireNonNull(workspace, "workspace");
        java.util.List<ReviewOriginal> retained = java.util.List.copyOf(
                Objects.requireNonNull(originals, "originals"));
        if (retained.isEmpty()) {
            return;
        }
        WorkspaceMutationGate.Lease lease = trustedWorkspace.beginMutation();
        try {
            trustedWorkspace.requireCurrentOwnership();
            for (ReviewOriginal original : retained) {
                Path path = Objects.requireNonNull(original, "review original").path();
                if (!trustedWorkspace.directory().equals(path.normalize().getParent())) {
                    throw new IOException("Review copy escaped its workspace");
                }
                trustedWorkspace.files().deleteIfExists(path);
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Could not remove private review copies", failure);
        } finally {
            lease.close();
        }
    }

    static final class SanitizedSource {
        private final CollectedSourceFile source;
        private final SanitizationResult result;
        private final ReviewOriginal reviewOriginal;

        private SanitizedSource(
                CollectedSourceFile source,
                SanitizationResult result,
                ReviewOriginal reviewOriginal) {
            this.source = Objects.requireNonNull(source, "source");
            this.result = Objects.requireNonNull(result, "result");
            this.reviewOriginal = Objects.requireNonNull(reviewOriginal, "reviewOriginal");
            if (!source.artifactName().equals(result.artifactName())) {
                throw new IllegalArgumentException("Sanitization evidence artifact identity is inconsistent");
            }
        }

        CollectedSourceFile source() {
            return source;
        }

        SanitizationResult result() {
            return result;
        }

        ReviewOriginal reviewOriginal() {
            return reviewOriginal;
        }

        boolean matches(ReviewedWorkspaceArtifact artifact) {
            return source.artifactName().equals(artifact.artifactName())
                    && source.byteCount() == artifact.byteCount()
                    && source.checksum().equals(artifact.checksum());
        }
    }

    static final class SanitizedGenerated {
        private final CollectedGeneratedArtifact artifact;
        private final SanitizationResult result;
        private final ReviewOriginal reviewOriginal;

        private SanitizedGenerated(
                CollectedGeneratedArtifact artifact,
                SanitizationResult result,
                ReviewOriginal reviewOriginal) {
            this.artifact = Objects.requireNonNull(artifact, "artifact");
            this.result = Objects.requireNonNull(result, "result");
            this.reviewOriginal = Objects.requireNonNull(reviewOriginal, "reviewOriginal");
            if (!artifact.artifactName().equals(result.artifactName())) {
                throw new IllegalArgumentException(
                        "Sanitization evidence artifact identity is inconsistent");
            }
        }

        CollectedGeneratedArtifact artifact() {
            return artifact;
        }

        SanitizationResult result() {
            return result;
        }

        ReviewOriginal reviewOriginal() {
            return reviewOriginal;
        }

        boolean matches(ReviewedWorkspaceArtifact value) {
            return artifact.artifactName().equals(value.artifactName())
                    && artifact.byteCount() == value.byteCount()
                    && artifact.checksum().equals(value.checksum());
        }
    }

    record ArtifactDigest(long byteCount, Sha256Checksum checksum) {
        ArtifactDigest {
            Objects.requireNonNull(checksum, "checksum");
            if (byteCount < 0) {
                throw new IllegalArgumentException("Artifact digest byte count must not be negative");
            }
        }
    }

    record ReviewOriginal(Path path, ArtifactDigest digest) {
        ReviewOriginal {
            path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
            Objects.requireNonNull(digest, "digest");
        }
    }

    private record SanitizedBytes(
            ArtifactDigest digest, SanitizationResult result, ReviewOriginal reviewOriginal) {}

    private static final class BoundedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long maximumBytes;
        private long byteCount;

        private BoundedOutputStream(OutputStream delegate, long maximumBytes) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.maximumBytes = maximumBytes;
        }

        @Override
        public void write(int value) throws IOException {
            reserve(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            reserve(length);
            delegate.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void reserve(int length) throws IOException {
            try {
                long next = Math.addExact(byteCount, length);
                if (next > maximumBytes) {
                    throw new IOException("Sanitized workspace artifact exceeds the product byte limit");
                }
                byteCount = next;
            } catch (ArithmeticException exception) {
                throw new IOException("Sanitized workspace artifact exceeds the product byte limit", exception);
            }
        }
    }
}
