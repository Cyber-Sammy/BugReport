package com.cybersammy.bugreport.core.packaging;

import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** Independently validates a ZIP against one exact package plan using bounded streaming reads. */
public final class ReportZipValidator {
    private static final int END_RECORD_MIN_BYTES = 22;
    private static final int MAX_ZIP_COMMENT_BYTES = 65_535;
    private static final int CENTRAL_HEADER_BYTES = 46;
    private static final int END_RECORD_SIGNATURE = 0x06054b50;
    private static final int CENTRAL_HEADER_SIGNATURE = 0x02014b50;

    private ReportZipValidator() {}

    /**
     * Validates archive structure and every uncompressed byte against the trusted plan.
     *
     * <p>This operation performs blocking filesystem I/O and must run off UI and game threads.
     */
    public static ReportZipArchive validate(Path archive, ReportPackagePlan plan) {
        ReportPackagePlan expectedPlan = Objects.requireNonNull(plan, "plan");
        Path path = safeArchivePath(archive);
        validatePlanLimits(expectedPlan);
        long archiveBytes = archiveSize(path);
        validateCentralDirectory(path, expectedPlan.entries(), archiveBytes);
        try (InputStream raw = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                BoundedDigestInputStream hashing = new BoundedDigestInputStream(
                        raw, ReportZipLimits.MAX_ARCHIVE_BYTES);
                ZipInputStream zip = new ZipInputStream(
                        hashing, java.nio.charset.StandardCharsets.UTF_8)) {
            validateEntries(zip, expectedPlan.entries());
            while (hashing.read() >= 0) {
                // Consume central-directory and trailing bytes into the archive checksum.
            }
            if (hashing.bytesRead() != archiveBytes) {
                throw failure(
                        ReportZipCode.ARCHIVE_INVALID,
                        null,
                        "Report archive size changed during validation",
                        null);
            }
            return new ReportZipArchive(
                    archiveBytes, hashing.checksum(), expectedPlan.entries().size());
        } catch (ReportZipException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw failure(
                    ReportZipCode.ARCHIVE_INVALID,
                    null,
                    "Report archive is not a valid ZIP",
                    exception);
        } catch (IOException | SecurityException exception) {
            throw failure(
                    ReportZipCode.ARCHIVE_INVALID,
                    null,
                    "Report archive could not be safely validated",
                    exception);
        }
    }

    private static void validateCentralDirectory(
            Path path, List<PackagePlanEntry> expected, long archiveBytes) {
        try (FileChannel input = FileChannel.open(
                path, Set.of(java.nio.file.StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            EndRecord end = readEndRecord(input, archiveBytes, expected.size());
            input.position(end.centralOffset());
            Set<String> names = new HashSet<>();
            for (int index = 0; index < expected.size(); index++) {
                ByteBuffer header = readExact(input, CENTRAL_HEADER_BYTES)
                        .order(ByteOrder.LITTLE_ENDIAN);
                if (header.getInt(0) != CENTRAL_HEADER_SIGNATURE) {
                    throw invalid("Report archive central directory is malformed");
                }
                int flags = Short.toUnsignedInt(header.getShort(8));
                int method = Short.toUnsignedInt(header.getShort(10));
                long uncompressed = Integer.toUnsignedLong(header.getInt(24));
                int nameBytes = Short.toUnsignedInt(header.getShort(28));
                int extraBytes = Short.toUnsignedInt(header.getShort(30));
                int commentBytes = Short.toUnsignedInt(header.getShort(32));
                long localOffset = Integer.toUnsignedLong(header.getInt(42));
                if ((flags & 1) != 0
                        || method != ZipEntry.DEFLATED
                        || nameBytes <= 0
                        || nameBytes > 512
                        || extraBytes != ReportZipLimits.CANONICAL_TIMESTAMP_EXTRA.length
                        || commentBytes != 0
                        || localOffset >= end.centralOffset()) {
                    throw invalid("Report archive central entry metadata is not canonical");
                }
                String name = decodeUtf8(readExact(input, nameBytes));
                requireSafeName(name);
                byte[] extra = new byte[extraBytes];
                readExact(input, extraBytes).get(extra);
                if (!Arrays.equals(extra, ReportZipLimits.CANONICAL_TIMESTAMP_EXTRA)) {
                    throw invalid("Report archive timestamp metadata is not canonical");
                }
                if (!names.add(name.toLowerCase(Locale.ROOT))) {
                    throw failure(
                            ReportZipCode.DUPLICATE_ENTRY,
                            null,
                            "Report archive contains duplicate entry names",
                            null);
                }
                PackagePlanEntry planned = expected.get(index);
                if (!planned.archivePath().equals(name)
                        || uncompressed != planned.uncompressedBytes()) {
                    throw mismatch(
                            planned.archivePath(),
                            "Report archive central directory differs from the plan");
                }
            }
            if (input.position() != Math.addExact(end.centralOffset(), end.centralSize())) {
                throw invalid("Report archive central directory length is invalid");
            }
        } catch (ReportZipException exception) {
            throw exception;
        } catch (IOException | ArithmeticException | SecurityException exception) {
            throw failure(
                    ReportZipCode.ARCHIVE_INVALID,
                    null,
                    "Report archive central directory could not be safely validated",
                    exception);
        }
    }

    private static EndRecord readEndRecord(
            FileChannel input, long archiveBytes, int expectedEntries) throws IOException {
        int tailBytes = (int) Math.min(
                archiveBytes, (long) END_RECORD_MIN_BYTES + MAX_ZIP_COMMENT_BYTES);
        long tailOffset = archiveBytes - tailBytes;
        input.position(tailOffset);
        ByteBuffer tail = readExact(input, tailBytes).order(ByteOrder.LITTLE_ENDIAN);
        int endIndex = -1;
        for (int index = tailBytes - END_RECORD_MIN_BYTES; index >= 0; index--) {
            if (tail.getInt(index) == END_RECORD_SIGNATURE) {
                int commentBytes = Short.toUnsignedInt(tail.getShort(index + 20));
                if (index + END_RECORD_MIN_BYTES + commentBytes == tailBytes) {
                    endIndex = index;
                    break;
                }
            }
        }
        if (endIndex < 0
                || Short.toUnsignedInt(tail.getShort(endIndex + 4)) != 0
                || Short.toUnsignedInt(tail.getShort(endIndex + 6)) != 0
                || Short.toUnsignedInt(tail.getShort(endIndex + 8)) != expectedEntries
                || Short.toUnsignedInt(tail.getShort(endIndex + 10)) != expectedEntries
                || Short.toUnsignedInt(tail.getShort(endIndex + 20)) != 0) {
            throw invalid("Report archive end record is missing or non-canonical");
        }
        long centralSize = Integer.toUnsignedLong(tail.getInt(endIndex + 12));
        long centralOffset = Integer.toUnsignedLong(tail.getInt(endIndex + 16));
        long endOffset = tailOffset + endIndex;
        if (centralOffset > archiveBytes
                || centralSize > archiveBytes - centralOffset
                || centralOffset + centralSize != endOffset) {
            throw invalid("Report archive central directory bounds are invalid");
        }
        return new EndRecord(centralOffset, centralSize);
    }

    private static ByteBuffer readExact(FileChannel input, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            int read = input.read(buffer);
            if (read < 0) {
                throw new IOException("Unexpected end of report archive");
            }
            if (read == 0) {
                continue;
            }
        }
        return buffer.flip();
    }

    private static String decodeUtf8(ByteBuffer encoded) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(encoded)
                    .toString();
        } catch (CharacterCodingException exception) {
            throw invalid("Report archive entry name is not valid UTF-8");
        }
    }

    private static void validateEntries(ZipInputStream zip, List<PackagePlanEntry> expected)
            throws IOException {
        Set<String> names = new HashSet<>();
        long totalBytes = 0;
        int index = 0;
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();
            requireSafeName(name);
            if (!names.add(name.toLowerCase(Locale.ROOT))) {
                throw failure(
                        ReportZipCode.DUPLICATE_ENTRY,
                        null,
                        "Report archive contains duplicate entry names",
                        null);
            }
            if (index >= expected.size()) {
                throw mismatch(null, "Report archive contains an unexpected entry");
            }
            PackagePlanEntry planned = expected.get(index++);
            if (entry.isDirectory() || !planned.archivePath().equals(name)) {
                throw mismatch(
                        planned.archivePath(),
                        "Report archive entry order or identity differs from the plan");
            }
            requireCanonicalMetadata(entry, planned.archivePath());
            long bytes = validateContents(zip, entry, planned);
            zip.closeEntry();
            if (bytes > ReportZipLimits.MAX_TOTAL_UNCOMPRESSED_BYTES - totalBytes) {
                throw limit(name, "Report archive exceeds the uncompressed byte limit");
            }
            totalBytes += bytes;
        }
        if (index != expected.size()) {
            throw mismatch(null, "Report archive is missing planned entries");
        }
    }

    private static long validateContents(
            ZipInputStream zip, ZipEntry entry, PackagePlanEntry planned) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[ReportZipLimits.BUFFER_BYTES];
        long bytes = 0;
        while (true) {
            int read = zip.read(buffer);
            if (read < 0) {
                break;
            }
            if (read == 0) {
                continue;
            }
            if (read > planned.uncompressedBytes() - bytes) {
                throw limit(entry.getName(), "Report archive data exceeds its byte limit");
            }
            digest.update(buffer, 0, read);
            bytes += read;
        }
        if (bytes != planned.uncompressedBytes()) {
            throw mismatch(entry.getName(), "Report archive entry size differs from the plan");
        }
        long declared = entry.getSize();
        if (declared >= 0 && declared != bytes) {
            throw mismatch(entry.getName(), "Report archive entry size differs from the plan");
        }
        Sha256Checksum checksum =
                new Sha256Checksum(HexFormat.of().formatHex(digest.digest()));
        if (!checksum.equals(planned.checksum())) {
            throw mismatch(entry.getName(), "Report archive entry checksum differs from the plan");
        }
        return bytes;
    }

    private static void requireCanonicalMetadata(ZipEntry entry, String plannedPath) {
        byte[] extra = entry.getExtra();
        if (entry.getMethod() != ZipEntry.DEFLATED
                || entry.getComment() != null
                || entry.getTime() != ReportZipLimits.CANONICAL_ENTRY_TIME_MILLIS
                || !Arrays.equals(extra, ReportZipLimits.CANONICAL_TIMESTAMP_EXTRA)) {
            throw mismatch(plannedPath, "Report archive entry metadata is not canonical");
        }
    }

    private static Path safeArchivePath(Path archive) {
        Path path = Objects.requireNonNull(archive, "archive").toAbsolutePath().normalize();
        try {
            var attributes = Files.readAttributes(
                    path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()
                    || attributes.isSymbolicLink()
                    || attributes.isOther()
                    || !path.equals(path.toRealPath(LinkOption.NOFOLLOW_LINKS))
                    || !path.equals(path.toRealPath())) {
                throw failure(
                        ReportZipCode.ARCHIVE_INVALID,
                        null,
                        "Report archive is not a safe regular file",
                        null);
            }
            return path;
        } catch (ReportZipException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw failure(
                    ReportZipCode.ARCHIVE_INVALID,
                    null,
                    "Report archive identity could not be verified",
                    exception);
        }
    }

    private static long archiveSize(Path path) {
        try {
            long size = Files.size(path);
            if (size <= 0 || size > ReportZipLimits.MAX_ARCHIVE_BYTES) {
                throw limit(null, "Report archive exceeds the encoded byte limit");
            }
            return size;
        } catch (ReportZipException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw failure(
                    ReportZipCode.ARCHIVE_INVALID,
                    null,
                    "Report archive size could not be verified",
                    exception);
        }
    }

    static void validatePlanLimits(ReportPackagePlan plan) {
        if (plan.entries().size() > ReportZipLimits.MAX_ENTRIES
                || plan.totalUncompressedBytes() > ReportZipLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw failure(
                    ReportZipCode.PLAN_LIMIT_EXCEEDED,
                    null,
                    "Report package plan exceeds product archive limits",
                    null);
        }
    }

    private static void requireSafeName(String name) {
        if (name == null
                || name.isEmpty()
                || name.startsWith("/")
                || name.startsWith("\\")
                || name.contains("\\")
                || name.indexOf('\0') >= 0
                || name.matches("^[A-Za-z]:.*")) {
            throw unsafe(name);
        }
        for (String segment : name.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw unsafe(name);
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", exception);
        }
    }

    private static ReportZipException unsafe(String entry) {
        return failure(
                ReportZipCode.UNSAFE_ENTRY,
                null,
                "Report archive contains an unsafe entry name",
                null);
    }

    private static ReportZipException mismatch(String entry, String message) {
        return failure(ReportZipCode.VALIDATION_MISMATCH, entry, message, null);
    }

    private static ReportZipException limit(String entry, String message) {
        return failure(ReportZipCode.ARCHIVE_LIMIT_EXCEEDED, entry, message, null);
    }

    private static ReportZipException invalid(String message) {
        return failure(ReportZipCode.ARCHIVE_INVALID, null, message, null);
    }

    static ReportZipException failure(
            ReportZipCode code, String entry, String message, Throwable cause) {
        return new ReportZipException(code, entry, message, cause);
    }

    private static final class BoundedDigestInputStream extends InputStream {
        private final InputStream delegate;
        private final long maximum;
        private final MessageDigest digest = sha256();
        private long bytesRead;

        private BoundedDigestInputStream(InputStream delegate, long maximum) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.maximum = maximum;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                record(1);
                digest.update((byte) value);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                record(read);
                digest.update(buffer, offset, read);
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private long bytesRead() {
            return bytesRead;
        }

        private Sha256Checksum checksum() {
            return new Sha256Checksum(HexFormat.of().formatHex(digest.digest()));
        }

        private void record(int count) throws IOException {
            if (count > maximum - bytesRead) {
                throw new IOException("Encoded report archive exceeds the product limit");
            }
            bytesRead += count;
        }
    }

    private record EndRecord(long centralOffset, long centralSize) {}
}
