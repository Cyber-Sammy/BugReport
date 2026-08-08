package com.cybersammy.bugreport.core.configuration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.api.version.SchemaVersion;
import com.cybersammy.bugreport.core.sanitization.SanitizationProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReportConfigurationJsonCodecTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void encodesAndDecodesCanonicalCurrentConfiguration() {
        ReportConfiguration configuration = configuration();

        byte[] encoded = ReportConfigurationJsonCodec.encode(configuration);
        DecodedReportConfiguration decoded = ReportConfigurationJsonCodec.decode(encoded);

        assertEquals(configuration, decoded.configuration());
        assertEquals(ReportConfigurationJsonCodec.CURRENT_SCHEMA_VERSION, decoded.sourceVersion());
        assertFalse(decoded.migrated());
        assertArrayEquals(encoded, ReportConfigurationJsonCodec.encode(decoded.configuration()));
    }

    @Test
    void migratesLegacyConfigurationWithoutWeakeningProductCeilings() {
        String legacy = """
                {"schemaId":"bugreport:configuration","schemaVersion":"0.1",
                "maximumBytes":1048576,"privacy":"STRICT_PRIVACY",
                "workspaceDirectory":"reports/workspaces","retentionDays":14}
                """;

        DecodedReportConfiguration decoded = ReportConfigurationJsonCodec.decode(
                legacy.getBytes(StandardCharsets.UTF_8));

        assertTrue(decoded.migrated());
        assertEquals(new SchemaVersion(0, 1), decoded.sourceVersion());
        assertEquals(1_048_576L, decoded.configuration().sizeLimits().maximumReportBytes());
        assertEquals(1_048_576L, decoded.configuration().sizeLimits().maximumBytesPerFile());
        assertEquals(SanitizationProfile.STRICT_PRIVACY, decoded.configuration().privacyProfile());
        assertEquals(14, decoded.configuration().cleanupPolicy().retentionDays());
    }

    @Test
    void rejectsAmbiguousOrUnsafePersistedConfiguration() {
        String duplicate = """
                {"schemaId":"bugreport:configuration","schemaVersion":"1.0",
                "privacyProfile":"STANDARD","privacyProfile":"STRICT_PRIVACY"}
                """;
        String legacyKeyInCurrent = """
                {"schemaId":"bugreport:configuration","schemaVersion":"1.0",
                "sizeLimits":{"maximumMatchedFiles":1,"maximumBytesPerFile":1024,"maximumReportBytes":1024},
                "privacyProfile":"STANDARD","privacy":"STANDARD","workspaceDirectory":"bugreport/workspaces",
                "cleanup":{"retentionDays":30,"maximumRetainedReports":10}}
                """;

        assertThrows(
                ConfigurationFormatException.class,
                () -> ReportConfigurationJsonCodec.decode(duplicate.getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                ConfigurationFormatException.class,
                () -> ReportConfigurationJsonCodec.decode(
                        legacyKeyInCurrent.getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkspaceLocation(RelativePath.of("../outside")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportSizeLimits(1, 1_024, 129L * 1024L * 1024L));
    }

    @Test
    void atomicallyPersistsCanonicalConfigurationAndReportsMalformedFiles() throws Exception {
        Path path = temporaryDirectory.resolve("config").resolve("bugreport.json");
        FileReportConfigurationStore store = new FileReportConfigurationStore(path);

        assertTrue(store.load().isEmpty());
        store.save(configuration());

        assertEquals(configuration(), store.load().orElseThrow().configuration());
        assertArrayEquals(
                ReportConfigurationJsonCodec.encode(configuration()),
                Files.readAllBytes(path));

        Files.writeString(path, "not json", StandardCharsets.UTF_8);
        ConfigurationStoreException failure = assertThrows(
                ConfigurationStoreException.class, store::load);
        assertEquals(ConfigurationStoreCode.FORMAT_INVALID, failure.code());
    }

    private static ReportConfiguration configuration() {
        return new ReportConfiguration(
                new ReportSizeLimits(4, 2L * 1024L * 1024L, 8L * 1024L * 1024L),
                SanitizationProfile.CUSTOM_REVIEW,
                new WorkspaceLocation(RelativePath.of("reports/workspaces")),
                new CleanupPolicy(45, 250));
    }
}
