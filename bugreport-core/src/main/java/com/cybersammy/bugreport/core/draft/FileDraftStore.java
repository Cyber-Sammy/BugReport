package com.cybersammy.bugreport.core.draft;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Atomic bounded storage for drafts under one Bug Report-owned directory. */
public final class FileDraftStore {
    /** Maximum canonical draft files processed by one scan. */
    public static final int MAX_DRAFT_FILES = 256;
    /** Maximum directory entries inspected by one scan. */
    public static final int MAX_DIRECTORY_ENTRIES = 512;

    private static final String DRAFT_SUFFIX = ".json";
    private static final Pattern DRAFT_FILE =
            Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.json");
    private static final Pattern OWN_TEMP_FILE =
            Pattern.compile("\\.([0-9a-f-]{36})-[^.]+\\.tmp");

    private final Path root;

    /** Binds storage to one application-owned directory; no files are touched yet. */
    public FileDraftStore(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /** Atomically saves a strictly newer revision without replacing an unreadable original. */
    public synchronized void save(ReportDraft draft) {
        ReportDraft value = Objects.requireNonNull(draft, "draft");
        byte[] encoded = DraftJsonCodec.encode(value);
        ensureWritableRoot();
        Path target = draftPath(value.sessionId());
        validateExistingRevision(target, value);

        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, "." + value.sessionId() + "-", ".tmp");
            try (FileChannel channel =
                    FileChannel.open(
                            temporary,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                temporary = null;
            } catch (AtomicMoveNotSupportedException exception) {
                throw new DraftStoreException(
                        DraftStoreCode.ATOMIC_MOVE_UNSUPPORTED,
                        "Draft storage does not support same-directory atomic replacement",
                        exception);
            }
        } catch (DraftStoreException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DraftStoreException(
                    DraftStoreCode.IO_FAILURE,
                    "Could not persist report draft " + value.sessionId(),
                    exception);
        } finally {
            deleteTemporaryQuietly(temporary);
        }
    }

    /** Loads canonical draft files independently and cleans only this store's temp files. */
    public synchronized DraftLoadBatch loadAll() {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return new DraftLoadBatch(List.of(), 0, false);
        }
        requireSafeRoot();
        List<Path> drafts = new ArrayList<>();
        int temporaryFilesDeleted = 0;
        int inspected = 0;
        boolean limitReached = false;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                if (++inspected > MAX_DIRECTORY_ENTRIES) {
                    limitReached = true;
                    break;
                }
                String filename = entry.getFileName().toString();
                if (OWN_TEMP_FILE.matcher(filename).matches()) {
                    if (deleteRecognizedTemporary(entry)) {
                        temporaryFilesDeleted++;
                    }
                    continue;
                }
                if (DRAFT_FILE.matcher(filename).matches()) {
                    if (drafts.size() == MAX_DRAFT_FILES) {
                        limitReached = true;
                    } else {
                        drafts.add(entry);
                    }
                }
            }
        } catch (IOException exception) {
            throw new DraftStoreException(
                    DraftStoreCode.IO_FAILURE, "Could not scan report draft directory", exception);
        }

        drafts.sort(Comparator.comparing(path -> path.getFileName().toString()));
        List<DraftLoadOutcome> outcomes = new ArrayList<>(drafts.size());
        for (Path draft : drafts) {
            outcomes.add(loadOne(draft));
        }
        return new DraftLoadBatch(outcomes, temporaryFilesDeleted, limitReached);
    }

    private DraftLoadOutcome loadOne(Path path) {
        ReportSessionId filenameId = sessionIdFromFilename(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            return new DraftLoadOutcome.Rejected(filenameId, DraftLoadFailureCode.UNSAFE_FILE);
        }
        try {
            DecodedReportDraft decoded = DraftJsonCodec.decode(readBounded(path));
            if (!filenameId.equals(decoded.draft().sessionId())) {
                return new DraftLoadOutcome.Rejected(
                        filenameId, DraftLoadFailureCode.SESSION_ID_MISMATCH);
            }
            return new DraftLoadOutcome.Loaded(filenameId, decoded);
        } catch (IllegalArgumentException exception) {
            return new DraftLoadOutcome.Rejected(filenameId, DraftLoadFailureCode.FORMAT_INVALID);
        } catch (IOException exception) {
            return new DraftLoadOutcome.Rejected(filenameId, DraftLoadFailureCode.IO_FAILURE);
        }
    }

    private void validateExistingRevision(Path target, ReportDraft incoming) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(target)) {
            throw new DraftStoreException(
                    DraftStoreCode.EXISTING_DRAFT_INVALID,
                    "Existing draft path is not a safe regular file: " + incoming.sessionId());
        }
        try {
            ReportDraft existing = DraftJsonCodec.decode(readBounded(target)).draft();
            if (!existing.sessionId().equals(incoming.sessionId())) {
                throw new DraftStoreException(
                        DraftStoreCode.EXISTING_DRAFT_INVALID,
                        "Existing draft identity does not match its filename: " + incoming.sessionId());
            }
            if (existing.revision() >= incoming.revision()) {
                throw new DraftStoreException(
                        DraftStoreCode.STALE_REVISION,
                        "Draft revision is not newer than the persisted revision: "
                                + incoming.sessionId());
            }
        } catch (DraftStoreException exception) {
            throw exception;
        } catch (IllegalArgumentException | IOException exception) {
            throw new DraftStoreException(
                    DraftStoreCode.EXISTING_DRAFT_INVALID,
                    "Existing draft cannot be safely replaced: " + incoming.sessionId(),
                    exception);
        }
    }

    private byte[] readBounded(Path path) throws IOException {
        long size = Files.size(path);
        if (size > DraftJsonCodec.MAX_ENCODED_BYTES) {
            throw new DraftFormatException("Encoded draft exceeds bounded storage size");
        }
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(DraftJsonCodec.MAX_ENCODED_BYTES + 1);
            if (bytes.length > DraftJsonCodec.MAX_ENCODED_BYTES) {
                throw new DraftFormatException("Encoded draft exceeds bounded storage size");
            }
            return bytes;
        }
    }

    private ReportSessionId sessionIdFromFilename(Path path) {
        Matcher matcher = DRAFT_FILE.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a canonical draft filename");
        }
        return ReportSessionId.parse(matcher.group(1));
    }

    private Path draftPath(ReportSessionId sessionId) {
        Path path = root.resolve(sessionId + DRAFT_SUFFIX).normalize();
        if (!root.equals(path.getParent())) {
            throw new DraftStoreException(
                    DraftStoreCode.ROOT_INVALID, "Draft path escaped its storage directory");
        }
        return path;
    }

    private void ensureWritableRoot() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new DraftStoreException(
                    DraftStoreCode.IO_FAILURE, "Could not create report draft directory", exception);
        }
        requireSafeRoot();
    }

    private void requireSafeRoot() {
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new DraftStoreException(
                    DraftStoreCode.ROOT_INVALID,
                    "Report draft storage root must be a real directory");
        }
    }

    private static void deleteTemporaryQuietly(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The next bounded scan recognizes and retries cleanup of only store-owned temp files.
        }
    }

    private static boolean deleteRecognizedTemporary(Path temporary) {
        if (!Files.isRegularFile(temporary, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(temporary)) {
            return false;
        }
        try {
            return Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            return false;
        }
    }
}
