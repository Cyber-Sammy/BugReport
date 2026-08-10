package com.cybersammy.bugreport.core.draft;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileDraftStoreTest {
    private static final ReportSessionId SESSION_ID =
            ReportSessionId.parse("00000000-0000-4000-8000-000000000051");
    private static final ReportSessionId OTHER_SESSION_ID =
            ReportSessionId.parse("00000000-0000-4000-8000-000000000052");

    @TempDir Path temporaryDirectory;

    @Test
    void atomicallySavesAndLoadsCanonicalDraft() throws IOException {
        Path root = temporaryDirectory.resolve("drafts");
        FileDraftStore store = new FileDraftStore(root);
        ReportDraft draft = draft(1, SESSION_ID);

        store.save(draft);
        DraftLoadBatch batch = store.loadAll();

        assertEquals(1, batch.outcomes().size());
        DraftLoadOutcome.Loaded loaded =
                assertInstanceOf(DraftLoadOutcome.Loaded.class, batch.outcomes().getFirst());
        assertEquals(draft, loaded.decoded().draft());
        assertFalse(batch.scanLimitReached());
        try (Stream<Path> files = Files.list(root)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void refusesStaleRevisionAndPreservesOriginalBytes() throws IOException {
        Path root = temporaryDirectory.resolve("drafts");
        FileDraftStore store = new FileDraftStore(root);
        store.save(draft(2, SESSION_ID));
        Path persisted = root.resolve(SESSION_ID + ".json");
        byte[] original = Files.readAllBytes(persisted);

        DraftStoreException exception =
                assertThrows(DraftStoreException.class, () -> store.save(draft(2, SESSION_ID)));

        assertEquals(DraftStoreCode.STALE_REVISION, exception.code());
        assertArrayEquals(original, Files.readAllBytes(persisted));
    }

    @Test
    void refusesToOverwriteCorruptOnlyCopy() throws IOException {
        Path root = temporaryDirectory.resolve("drafts");
        Files.createDirectories(root);
        Path persisted = root.resolve(SESSION_ID + ".json");
        byte[] corrupt = "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(persisted, corrupt);

        DraftStoreException exception =
                assertThrows(
                        DraftStoreException.class,
                        () -> new FileDraftStore(root).save(draft(3, SESSION_ID)));

        assertEquals(DraftStoreCode.EXISTING_DRAFT_INVALID, exception.code());
        assertArrayEquals(corrupt, Files.readAllBytes(persisted));
    }

    @Test
    void isolatesCorruptAndMismatchedDraftsWhileLoadingValidOne() throws IOException {
        Path root = temporaryDirectory.resolve("drafts");
        Files.createDirectories(root);
        Files.write(root.resolve(SESSION_ID + ".json"), DraftJsonCodec.encode(draft(1, SESSION_ID)));
        Files.write(root.resolve(OTHER_SESSION_ID + ".json"), DraftJsonCodec.encode(draft(1, SESSION_ID)));
        ReportSessionId corruptId =
                ReportSessionId.parse("00000000-0000-4000-8000-000000000053");
        Files.writeString(root.resolve(corruptId + ".json"), "{");

        DraftLoadBatch batch = new FileDraftStore(root).loadAll();

        assertEquals(3, batch.outcomes().size());
        assertTrue(batch.outcomes().stream().anyMatch(DraftLoadOutcome.Loaded.class::isInstance));
        assertTrue(
                batch.outcomes().stream()
                        .filter(DraftLoadOutcome.Rejected.class::isInstance)
                        .map(DraftLoadOutcome.Rejected.class::cast)
                        .anyMatch(value -> value.code() == DraftLoadFailureCode.SESSION_ID_MISMATCH));
        assertTrue(
                batch.outcomes().stream()
                        .filter(DraftLoadOutcome.Rejected.class::isInstance)
                        .map(DraftLoadOutcome.Rejected.class::cast)
                        .anyMatch(value -> value.code() == DraftLoadFailureCode.FORMAT_INVALID));
    }

    @Test
    void deletesOnlyRecognizedTemporaryFiles() throws IOException {
        Path root = temporaryDirectory.resolve("drafts");
        Files.createDirectories(root);
        Path ownedTemp = root.resolve("." + SESSION_ID + "-interrupted.tmp");
        Path unrelatedTemp = root.resolve("keep.tmp");
        Files.writeString(ownedTemp, "partial");
        Files.writeString(unrelatedTemp, "unrelated");

        DraftLoadBatch batch = new FileDraftStore(root).loadAll();

        assertEquals(1, batch.temporaryFilesDeleted());
        assertFalse(Files.exists(ownedTemp));
        assertTrue(Files.exists(unrelatedTemp));
    }

    @Test
    void isolatesOversizedDraftWithoutAllocatingItsDeclaredSize() throws IOException {
        Path root = temporaryDirectory.resolve("drafts");
        Files.createDirectories(root);
        Path oversized = root.resolve(SESSION_ID + ".json");
        try (var channel =
                Files.newByteChannel(
                        oversized,
                        java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(DraftJsonCodec.MAX_ENCODED_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0, 0}));
        }

        DraftLoadOutcome.Rejected rejected =
                assertInstanceOf(
                        DraftLoadOutcome.Rejected.class,
                        new FileDraftStore(root).loadAll().outcomes().getFirst());

        assertEquals(DraftLoadFailureCode.FORMAT_INVALID, rejected.code());
    }

    @Test
    void deletesOnlyTheExactCanonicalSessionDraft() {
        Path root = temporaryDirectory.resolve("drafts");
        FileDraftStore store = new FileDraftStore(root);
        store.save(draft(1, SESSION_ID));
        store.save(draft(1, OTHER_SESSION_ID));

        assertTrue(store.delete(SESSION_ID));
        assertFalse(store.delete(SESSION_ID));
        assertFalse(Files.exists(root.resolve(SESSION_ID + ".json")));
        assertTrue(Files.exists(root.resolve(OTHER_SESSION_ID + ".json")));
    }

    private static ReportDraft draft(long revision, ReportSessionId sessionId) {
        return new ReportDraft(
                sessionId,
                revision,
                ProviderId.parse("example_mod"),
                ProviderVersion.parse("1.0.0"),
                Optional.of(CategoryId.of("general")),
                ReportSessionState.FORM_IN_PROGRESS,
                FormSubmission.empty());
    }
}
