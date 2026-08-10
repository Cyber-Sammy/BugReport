package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.core.source.ScreenshotCollectionRequest;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Validates, metadata-strips, bounds, and privately publishes selected PNG/JPEG screenshots. */
final class ScreenshotAttachmentCollector {
    static final int PRODUCT_MAX_INPUT_BYTES = 32 * 1024 * 1024;
    static final int PRODUCT_MAX_OUTPUT_BYTES = 32 * 1024 * 1024;
    static final int PRODUCT_MAX_DIMENSION = 8192;
    static final long PRODUCT_MAX_PIXELS = 16_777_216L;
    private static final int COPY_BUFFER_BYTES = 64 * 1024;

    private ScreenshotAttachmentCollector() {}

    static FileCollectionResult collect(
            ScreenshotCollectionRequest request,
            Path screenshotsDirectory,
            ReportWorkspace workspace,
            CancellationSignal cancellation,
            long remainingBytes) {
        ScreenshotCollectionRequest trusted = Objects.requireNonNull(request, "request");
        Path root = Objects.requireNonNull(screenshotsDirectory, "screenshotsDirectory")
                .toAbsolutePath()
                .normalize();
        ReportWorkspace destination = Objects.requireNonNull(workspace, "workspace");
        CancellationSignal signal = Objects.requireNonNull(cancellation, "cancellation");
        if (remainingBytes < 0) {
            throw new IllegalArgumentException("Remaining collection bytes must be non-negative");
        }

        List<FileCollectionResult.SourceOutcome> outcomes = new ArrayList<>();
        long retained = 0;
        long processed = 0;
        int ordinal = 0;
        for (ScreenshotCollectionRequest.Entry entry : trusted.entries()) {
            ordinal++;
            if (signal.isCancellationRequested()) {
                outcomes.add(FileCollectionResult.SourceOutcome.cancelled(
                        ordinal, List.of(entry.provenance())));
                continue;
            }
            try {
                byte[] input = readStableSelectedImage(root, entry.relativePath().value());
                processed = Math.addExact(processed, input.length);
                byte[] normalized = normalize(input);
                if (normalized.length > remainingBytes - retained) {
                    outcomes.add(FileCollectionResult.SourceOutcome.failed(
                            ordinal,
                            List.of(entry.provenance()),
                            SourceCopyCode.COLLECTION_BYTE_LIMIT_EXCEEDED));
                    continue;
                }
                if (signal.isCancellationRequested()) {
                    outcomes.add(FileCollectionResult.SourceOutcome.cancelled(
                            ordinal, List.of(entry.provenance())));
                    continue;
                }
                CollectedSourceFile collected = publish(entry, normalized, destination);
                retained = Math.addExact(retained, collected.byteCount());
                outcomes.add(FileCollectionResult.SourceOutcome.collected(ordinal, collected));
            } catch (ImageLimitException failure) {
                outcomes.add(FileCollectionResult.SourceOutcome.failed(
                        ordinal, List.of(entry.provenance()), failure.code));
            } catch (IOException | RuntimeException failure) {
                outcomes.add(FileCollectionResult.SourceOutcome.failed(
                        ordinal, List.of(entry.provenance()), SourceCopyCode.SOURCE_UNSAFE));
            }
        }
        FileCollectionResult.Status status = status(outcomes, signal.isCancellationRequested());
        int successful = count(outcomes, FileCollectionResult.SourceStatus.COLLECTED);
        int failed = count(outcomes, FileCollectionResult.SourceStatus.FAILED);
        int cancelled = count(outcomes, FileCollectionResult.SourceStatus.CANCELLED);
        return new FileCollectionResult(
                trusted.providerId(),
                trusted.providerVersion(),
                trusted.categoryId(),
                status,
                outcomes,
                new CollectionProgressSnapshot(
                        progressState(status),
                        outcomes.size(),
                        outcomes.size(),
                        successful,
                        failed,
                        cancelled,
                        processed,
                        0,
                        OptionalInt.empty()));
    }

    private static byte[] readStableSelectedImage(Path root, String filename) throws IOException {
        requireTrustedRoot(root);
        Path selected = root.resolve(filename).normalize();
        if (!root.equals(selected.getParent())) {
            throw new IOException("Selected screenshot escaped the screenshots directory");
        }
        BasicFileAttributes before = safeAttributes(selected);
        if (before.size() > PRODUCT_MAX_INPUT_BYTES) {
            throw new ImageLimitException(SourceCopyCode.BYTE_LIMIT_EXCEEDED);
        }
        byte[] contents;
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (FileChannel channel = FileChannel.open(selected, options)) {
            contents = readBounded(channel, PRODUCT_MAX_INPUT_BYTES);
        }
        BasicFileAttributes after = safeAttributes(selected);
        if (!sameSnapshot(before, after) || contents.length != after.size()) {
            throw new IOException("Selected screenshot changed while it was read");
        }
        return contents;
    }

    private static void requireTrustedRoot(Path root) throws IOException {
        BasicFileAttributes noFollow = Files.readAttributes(
                root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        BasicFileAttributes followed = Files.readAttributes(root, BasicFileAttributes.class);
        if (!noFollow.isDirectory()
                || noFollow.isSymbolicLink()
                || noFollow.isOther()
                || !sameIdentity(noFollow, followed)
                || !root.equals(root.toRealPath(LinkOption.NOFOLLOW_LINKS))
                || !root.equals(root.toRealPath())) {
            throw new IOException("Screenshots directory is redirected or unsafe");
        }
    }

    private static BasicFileAttributes safeAttributes(Path selected) throws IOException {
        BasicFileAttributes noFollow = Files.readAttributes(
                selected, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        BasicFileAttributes followed = Files.readAttributes(selected, BasicFileAttributes.class);
        if (!noFollow.isRegularFile()
                || noFollow.isSymbolicLink()
                || noFollow.isOther()
                || !sameIdentity(noFollow, followed)
                || !selected.equals(selected.toRealPath(LinkOption.NOFOLLOW_LINKS))
                || !selected.equals(selected.toRealPath())) {
            throw new IOException("Selected screenshot is redirected or unsafe");
        }
        return noFollow;
    }

    private static boolean sameIdentity(BasicFileAttributes first, BasicFileAttributes second) {
        if (first.fileKey() != null && second.fileKey() != null) {
            return first.fileKey().equals(second.fileKey());
        }
        return first.isRegularFile() == second.isRegularFile()
                && first.isDirectory() == second.isDirectory()
                && first.creationTime().equals(second.creationTime());
    }

    private static boolean sameSnapshot(BasicFileAttributes first, BasicFileAttributes second) {
        return sameIdentity(first, second)
                && first.size() == second.size()
                && first.lastModifiedTime().equals(second.lastModifiedTime());
    }

    private static byte[] readBounded(FileChannel channel, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES);
        int total = 0;
        while (true) {
            int read = channel.read(buffer);
            if (read < 0) {
                return output.toByteArray();
            }
            if (read == 0) {
                continue;
            }
            total = Math.addExact(total, read);
            if (total > maximum) {
                throw new ImageLimitException(SourceCopyCode.BYTE_LIMIT_EXCEEDED);
            }
            buffer.flip();
            output.write(buffer.array(), 0, buffer.remaining());
            buffer.clear();
        }
    }

    private static byte[] normalize(byte[] input) throws IOException {
        String format = format(input);
        BufferedImage decoded;
        try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (!readers.hasNext()) {
                throw new IOException("Required image decoder is unavailable");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0
                        || height <= 0
                        || width > PRODUCT_MAX_DIMENSION
                        || height > PRODUCT_MAX_DIMENSION
                        || Math.multiplyExact((long) width, height) > PRODUCT_MAX_PIXELS) {
                    throw new ImageLimitException(SourceCopyCode.IMAGE_DIMENSIONS_EXCEEDED);
                }
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (decoded == null) {
            throw new IOException("Selected screenshot could not be decoded");
        }
        BufferedImage normalized = new BufferedImage(
                decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.drawImage(decoded, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        BoundedOutputStream bounded = new BoundedOutputStream(PRODUCT_MAX_OUTPUT_BYTES);
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bounded)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("png");
            if (!writers.hasNext()) {
                throw new IOException("Required PNG encoder is unavailable");
            }
            ImageWriter writer = writers.next();
            try {
                writer.setOutput(output);
                writer.write(null, new javax.imageio.IIOImage(normalized, null, null), null);
                output.flush();
            } finally {
                writer.dispose();
            }
        }
        return bounded.toByteArray();
    }

    private static String format(byte[] input) throws IOException {
        if (input.length >= 8
                && input[0] == (byte) 0x89
                && input[1] == 'P'
                && input[2] == 'N'
                && input[3] == 'G'
                && input[4] == 0x0D
                && input[5] == 0x0A
                && input[6] == 0x1A
                && input[7] == 0x0A) {
            return "png";
        }
        if (input.length >= 3
                && input[0] == (byte) 0xFF
                && input[1] == (byte) 0xD8
                && input[2] == (byte) 0xFF) {
            return "jpeg";
        }
        throw new ImageLimitException(SourceCopyCode.UNSUPPORTED_IMAGE);
    }

    private static CollectedSourceFile publish(
            ScreenshotCollectionRequest.Entry entry,
            byte[] normalized,
            ReportWorkspace workspace) throws IOException {
        String artifactName = artifactName(entry);
        Path destination = workspace.directory().resolve(artifactName);
        Path temporary = workspace.directory()
                .resolve("." + artifactName + "." + UUID.randomUUID() + ".part");
        WorkspaceMutationGate.Lease lease = workspace.beginMutation();
        boolean published = false;
        boolean destinationOwned = false;
        try {
            workspace.requireCurrentOwnership();
            try (FileChannel output = workspace.files().openNewPrivateFile(temporary)) {
                ByteBuffer contents = ByteBuffer.wrap(normalized);
                while (contents.hasRemaining()) {
                    output.write(contents);
                }
                output.force(true);
            }
            workspace.files().verifyPrivateFile(temporary);
            try (FileChannel reservation = workspace.files().openNewPrivateFile(destination)) {
                reservation.force(true);
                destinationOwned = true;
            } catch (FileAlreadyExistsException failure) {
                throw new IOException("Screenshot artifact destination already exists", failure);
            }
            workspace.files().replaceAtomically(temporary, destination);
            workspace.files().verifyPrivateFile(destination);
            workspace.requireCurrentOwnership();
            published = true;
            return new CollectedSourceFile(
                    artifactName,
                    normalized.length,
                    new Sha256Checksum(HexFormat.of().formatHex(sha256().digest(normalized))),
                    List.of(entry.provenance()),
                    DiagnosticContentType.BINARY,
                    entry.provenance().privacy(),
                    entry.provenance().qualityRole(),
                    entry.provenance().inclusionDefault());
        } finally {
            try {
                workspace.files().deleteIfExists(temporary);
                if (!published && destinationOwned) {
                    workspace.files().deleteIfExists(destination);
                }
            } finally {
                lease.close();
            }
        }
    }

    private static String artifactName(ScreenshotCollectionRequest.Entry entry) {
        MessageDigest digest = sha256();
        digest.update(entry.provenance().sourceId().toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(entry.relativePath().value()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "source-" + HexFormat.of().formatHex(digest.digest()) + ".png";
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 is unavailable", exception);
        }
    }

    private static int count(
            List<FileCollectionResult.SourceOutcome> outcomes,
            FileCollectionResult.SourceStatus status) {
        return (int) outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }

    private static FileCollectionResult.Status status(
            List<FileCollectionResult.SourceOutcome> outcomes, boolean cancellationRequested) {
        if (cancellationRequested
                || outcomes.stream().anyMatch(outcome ->
                        outcome.status() == FileCollectionResult.SourceStatus.CANCELLED)) {
            return FileCollectionResult.Status.CANCELLED;
        }
        int successes = count(outcomes, FileCollectionResult.SourceStatus.COLLECTED);
        int failures = count(outcomes, FileCollectionResult.SourceStatus.FAILED);
        if (failures == 0) {
            return FileCollectionResult.Status.COMPLETE;
        }
        return successes == 0 ? FileCollectionResult.Status.FAILED : FileCollectionResult.Status.PARTIAL;
    }

    private static CollectionProgressSnapshot.State progressState(FileCollectionResult.Status status) {
        return switch (status) {
            case COMPLETE -> CollectionProgressSnapshot.State.COMPLETE;
            case PARTIAL -> CollectionProgressSnapshot.State.PARTIAL;
            case FAILED -> CollectionProgressSnapshot.State.FAILED;
            case CANCELLED -> CollectionProgressSnapshot.State.CANCELLED;
        };
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final int maximum;
        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

        private BoundedOutputStream(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            delegate.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            requireCapacity(length);
            delegate.write(bytes, offset, length);
        }

        private void requireCapacity(int additional) throws IOException {
            if ((long) delegate.size() + additional > maximum) {
                throw new ImageLimitException(SourceCopyCode.BYTE_LIMIT_EXCEEDED);
            }
        }

        private byte[] toByteArray() {
            return delegate.toByteArray();
        }
    }

    private static final class ImageLimitException extends IOException {
        private static final long serialVersionUID = 1L;
        private final SourceCopyCode code;

        private ImageLimitException(SourceCopyCode code) {
            super("Selected screenshot violates a product image limit");
            this.code = Objects.requireNonNull(code, "code");
        }
    }
}
