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

/** Publishes one sanitized text artifact through a private temporary workspace file. */
final class WorkspaceSanitizationCoordinator {
    private static final int MAX_SANITIZED_BYTES = 128 * 1024 * 1024;
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
        if (collected.contentType() == DiagnosticContentType.BINARY) {
            throw new IllegalArgumentException("Binary artifacts must not enter text sanitization");
        }
        ReportWorkspace trustedWorkspace = Objects.requireNonNull(workspace, "workspace");
        SanitizationPipeline trustedPipeline = Objects.requireNonNull(pipeline, "pipeline");
        CancellationSignal signal = Objects.requireNonNull(cancellation, "cancellation");
        if (maximumBytes < 1 || maximumBytes > MAX_SANITIZED_BYTES) {
            throw new IllegalArgumentException("Sanitized artifact byte limit is outside the product ceiling");
        }
        Path target = trustedWorkspace.directory().resolve(collected.artifactName()).normalize();
        if (!trustedWorkspace.directory().equals(target.getParent())) {
            throw new IllegalArgumentException("Sanitization artifact escaped its workspace");
        }
        Path temporary = trustedWorkspace.directory()
                .resolve(".sanitize-" + UUID.randomUUID() + ".tmp");
        WorkspaceMutationGate.Lease lease = trustedWorkspace.beginMutation();
        try {
            trustedWorkspace.requireCurrentOwnership();
            SanitizationResult result;
            try (var inputChannel = trustedWorkspace.files().openExistingPrivateFile(target);
                    var outputChannel = trustedWorkspace.files().openNewPrivateFile(temporary);
                    var input = new BufferedReader(Channels.newReader(inputChannel, StandardCharsets.UTF_8));
                    var output = new BufferedWriter(new OutputStreamWriter(
                            new BoundedOutputStream(
                                    Channels.newOutputStream(outputChannel), maximumBytes),
                            StandardCharsets.UTF_8))) {
                result = trustedPipeline.sanitize(collected.artifactName(), input, output, signal);
                output.flush();
                outputChannel.force(true);
            }
            trustedWorkspace.files().verifyPrivateFile(temporary);
            ArtifactDigest digest = digest(trustedWorkspace, temporary, maximumBytes);
            trustedWorkspace.requireCurrentOwnership();
            trustedWorkspace.files().replaceAtomically(temporary, target);
            trustedWorkspace.files().verifyPrivateFile(target);
            CollectedSourceFile sanitized = new CollectedSourceFile(
                    collected.artifactName(),
                    digest.byteCount(),
                    digest.checksum(),
                    collected.provenances(),
                    collected.contentType(),
                    collected.privacy(),
                    collected.qualityRole(),
                    collected.inclusionDefault());
            return new SanitizedSource(sanitized, result);
        } catch (SanitizationException exception) {
            throw exception;
        } catch (IOException | ArithmeticException exception) {
            throw new IllegalStateException("Could not publish sanitized workspace artifact", exception);
        } finally {
            lease.close();
            try {
                trustedWorkspace.files().deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A later workspace cleanup treats unrecognized leftovers as unsafe.
            }
        }
    }

    private static ArtifactDigest digest(
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

    record SanitizedSource(CollectedSourceFile source, SanitizationResult result) {}

    private record ArtifactDigest(long byteCount, Sha256Checksum checksum) {}

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
