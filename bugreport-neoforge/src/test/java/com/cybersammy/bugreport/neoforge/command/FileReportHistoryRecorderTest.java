package com.cybersammy.bugreport.neoforge.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.history.FileReportHistoryStore;
import com.cybersammy.bugreport.core.history.ReportHistoryEntry;
import com.cybersammy.bugreport.core.history.ReportHistoryStatus;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileReportHistoryRecorderTest {
    @Test
    void terminalSummarySurvivesNewRecorder(@TempDir Path temporaryDirectory) throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("history"));
        ReportHistoryEntry entry = new ReportHistoryEntry(
                ReportSessionId.random(), ProviderId.parse("example_mod"), ProviderVersion.parse("1.0.0"),
                Optional.of(CategoryId.of("general")), ReportHistoryStatus.FAILED, 7,
                Instant.parse("2026-08-10T10:00:00Z"), Optional.empty());
        FileReportHistoryRecorder first = new FileReportHistoryRecorder(
                new FileReportHistoryStore(directory));
        first.record(entry);

        assertEquals(java.util.List.of(entry), new FileReportHistoryRecorder(
                new FileReportHistoryStore(directory)).entries());
    }
}
