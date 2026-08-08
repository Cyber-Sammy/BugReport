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

    private static ReportHistoryEntry entry(String id, long revision, Instant updatedAt) {
        return new ReportHistoryEntry(ReportSessionId.parse(id), ProviderId.parse("example_mod"), ProviderVersion.parse("1.0.0"), Optional.of(CategoryId.of("general")), ReportHistoryStatus.DRAFT, revision, updatedAt, Optional.empty());
    }
}
