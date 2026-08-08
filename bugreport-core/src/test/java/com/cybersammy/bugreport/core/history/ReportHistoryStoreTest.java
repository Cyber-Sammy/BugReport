package com.cybersammy.bugreport.core.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReportHistoryStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void canonicallyPersistsAndUpdatesSafeHistoryEntries() throws Exception {
        Path directory = temporaryDirectory.resolve("history");
        Files.createDirectory(directory);
        FileReportHistoryStore store = new FileReportHistoryStore(directory);
        ReportHistoryEntry first = entry("00000000-0000-4000-8000-000000000101", 1, Instant.parse("2026-01-01T00:00:00Z"));
        ReportHistoryEntry newer = ReportHistoryEntry.failed(first, 2, Instant.parse("2026-01-02T00:00:00Z"));

        ReportHistoryIndex index = ReportHistoryIndex.empty().upsert(first).upsert(newer);
        store.save(index);

        HistoryIndexLoad loaded = store.load();
        assertFalse(loaded.recoveredFromCorruption());
        assertEquals(List.of(newer), loaded.index().entries());
        assertThrows(IllegalArgumentException.class, () -> index.upsert(first));
    }

    @Test
    void isolatesMalformedAndOversizedHistoryAsRecoverableEmptyIndex() throws Exception {
        Path directory = temporaryDirectory.resolve("history");
        Files.createDirectory(directory);
        Path file = directory.resolve(FileReportHistoryStore.HISTORY_FILENAME);
        Files.writeString(file, "{", StandardCharsets.UTF_8);

        HistoryIndexLoad malformed = new FileReportHistoryStore(directory).load();
        assertTrue(malformed.recoveredFromCorruption());
        assertTrue(malformed.index().entries().isEmpty());

        try (var channel = Files.newByteChannel(file, java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(ReportHistoryJsonCodec.MAX_ENCODED_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0, 0}));
        }
        assertTrue(new FileReportHistoryStore(directory).load().recoveredFromCorruption());
    }

    @Test
    void rejectsDuplicateMembersAndUnsupportedSchema() {
        String duplicate = """
                {"schemaId":"bugreport:history_index","schemaVersion":"1.0","entries":[],"entries":[]}
                """;
        String future = """
                {"schemaId":"bugreport:history_index","schemaVersion":"2.0","entries":[]}
                """;
        assertThrows(HistoryFormatException.class, () -> ReportHistoryJsonCodec.decode(duplicate.getBytes(StandardCharsets.UTF_8)));
        assertThrows(HistoryFormatException.class, () -> ReportHistoryJsonCodec.decode(future.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void isolatesOneInvalidEntryWithoutDiscardingValidHistory() {
        String json = """
                {"schemaId":"bugreport:history_index","schemaVersion":"1.0","entries":[
                {"sessionId":"00000000-0000-4000-8000-000000000111","providerId":"example_mod","providerVersion":"1.0.0","status":"DRAFT","revision":1,"updatedAt":"2026-01-01T00:00:00Z"},
                {"sessionId":"00000000-0000-4000-8000-000000000112","providerId":"example_mod","providerVersion":"1.0.0","status":"DRAFT","revision":"oops","updatedAt":"2026-01-01T00:00:00Z"},
                {"sessionId":"00000000-0000-4000-8000-000000000113","providerId":"example_mod","providerVersion":"1.0.0","status":"DRAFT","revision":1,"updatedAt":"2026-01-02T00:00:00Z"}]}
                """;
        DecodedHistoryIndex decoded = ReportHistoryJsonCodec.decodeRecovering(json.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, decoded.skippedEntries());
        assertEquals(2, decoded.index().entries().size());
    }

    @Test
    void enforcesMonotonicTerminalLifecycleAndIdentity() {
        ReportHistoryEntry draft = entry("00000000-0000-4000-8000-000000000121", 1, Instant.parse("2026-01-01T00:00:00Z"));
        ReportHistoryIndex index = ReportHistoryIndex.empty().upsert(draft);
        ReportHistoryEntry completed = new ReportHistoryEntry(draft.sessionId(), draft.providerId(), draft.providerVersion(), draft.categoryId(), ReportHistoryStatus.COMPLETED, 2, Instant.parse("2026-01-02T00:00:00Z"), Optional.of(new ReportArchiveSummary(1, new com.cybersammy.bugreport.core.workspace.Sha256Checksum("0".repeat(64)), 1)));
        assertEquals(completed, index.upsert(completed).entries().getFirst());
        assertThrows(IllegalStateException.class, () -> index.upsert(completed).upsert(ReportHistoryEntry.failed(completed, 3, Instant.now())));
        ReportHistoryEntry foreign = new ReportHistoryEntry(draft.sessionId(), ProviderId.parse("other_mod"), draft.providerVersion(), draft.categoryId(), ReportHistoryStatus.FAILED, 2, Instant.now(), Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> index.upsert(foreign));
    }

    private static ReportHistoryEntry entry(String id, long revision, Instant updatedAt) {
        return new ReportHistoryEntry(ReportSessionId.parse(id), ProviderId.parse("example_mod"), ProviderVersion.parse("1.0.0"), Optional.of(CategoryId.of("general")), ReportHistoryStatus.DRAFT, revision, updatedAt, Optional.empty());
    }
}
