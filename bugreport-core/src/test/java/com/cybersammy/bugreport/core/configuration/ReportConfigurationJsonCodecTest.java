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
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
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
    void acceptsBoundedUnknownMembersButRejectsUnsupportedSchema() {
        String currentWithUnknown = """
                {"schemaId":"bugreport:configuration","schemaVersion":"1.0",
                "sizeLimits":{"maximumMatchedFiles":1,"maximumBytesPerFile":1024,"maximumReportBytes":1024},
                "privacyProfile":"STANDARD","workspaceDirectory":"bugreport/workspaces",
                "cleanup":{"retentionDays":30,"maximumRetainedReports":10},"future":{"value":true}}
                """;
        String futureSchema = currentWithUnknown.replace("\"1.0\"", "\"1.1\"");

        assertEquals(
                SanitizationProfile.STANDARD,
                ReportConfigurationJsonCodec.decode(
                        currentWithUnknown.getBytes(StandardCharsets.UTF_8))
                        .configuration()
                        .privacyProfile());
        assertThrows(
                ConfigurationFormatException.class,
                () -> ReportConfigurationJsonCodec.decode(
                        futureSchema.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void atomicallyPersistsCanonicalConfigurationAndReportsMalformedFiles() throws Exception {
        Path directory = temporaryDirectory.resolve("config");
        Files.createDirectory(directory);
        Path path = directory.resolve(FileReportConfigurationStore.CONFIGURATION_FILENAME);
        FileReportConfigurationStore store = new FileReportConfigurationStore(directory);

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

    @Test
    void rejectsRedirectedDirectoriesAndUnsafeConfigurationFiles() throws Exception {
        Path target = temporaryDirectory.resolve("target");
        Path link = temporaryDirectory.resolve("link");
        Files.createDirectory(target);
        Path nested = target.resolve("nested");
        Files.createDirectory(nested);
        assumeSymbolicLink(link, target);

        assertThrows(IllegalArgumentException.class, () -> new FileReportConfigurationStore(link));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FileReportConfigurationStore(link.resolve("nested")));

        Path safeDirectory = temporaryDirectory.resolve("safe");
        Files.createDirectory(safeDirectory);
        Path outside = temporaryDirectory.resolve("outside.json");
        Files.writeString(outside, "{}", StandardCharsets.UTF_8);
        Path configuration = safeDirectory.resolve(FileReportConfigurationStore.CONFIGURATION_FILENAME);
        Files.createSymbolicLink(configuration, outside);

        ConfigurationStoreException failure = assertThrows(
                ConfigurationStoreException.class,
                () -> new FileReportConfigurationStore(safeDirectory).load());
        assertEquals(ConfigurationStoreCode.UNSAFE_FILE, failure.code());
    }

    @Test
    void rejectsOversizedConfigurationWithoutReadingItsDeclaredLength() throws Exception {
        Path directory = temporaryDirectory.resolve("config");
        Files.createDirectory(directory);
        Path configuration = directory.resolve(FileReportConfigurationStore.CONFIGURATION_FILENAME);
        try (var channel = Files.newByteChannel(
                configuration,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            channel.position(ReportConfigurationJsonCodec.MAX_ENCODED_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0, 0}));
        }

        ConfigurationStoreException failure = assertThrows(
                ConfigurationStoreException.class,
                () -> new FileReportConfigurationStore(directory).load());

        assertEquals(ConfigurationStoreCode.FORMAT_INVALID, failure.code());
    }

    @Test
    void acceptsExactProductLimitAndCleanupBoundaries() {
        ReportSizeLimits defaults = ReportSizeLimits.productDefaults();

        assertEquals(
                defaults,
                new ReportSizeLimits(
                        defaults.maximumMatchedFiles(),
                        defaults.maximumBytesPerFile(),
                        defaults.maximumReportBytes()));
        CleanupPolicy minimum = new CleanupPolicy(
                CleanupPolicy.MIN_RETENTION_DAYS, CleanupPolicy.MIN_RETAINED_REPORTS);
        CleanupPolicy maximum = new CleanupPolicy(
                CleanupPolicy.MAX_RETENTION_DAYS, CleanupPolicy.MAX_RETAINED_REPORTS);
        assertEquals(CleanupPolicy.MIN_RETENTION_DAYS, minimum.retentionDays());
        assertEquals(CleanupPolicy.MAX_RETAINED_REPORTS, maximum.maximumRetainedReports());
    }

    private static void assumeSymbolicLink(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException exception) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }
    }

    private static ReportConfiguration configuration() {
        return new ReportConfiguration(
                new ReportSizeLimits(4, 2L * 1024L * 1024L, 8L * 1024L * 1024L),
                SanitizationProfile.CUSTOM_REVIEW,
                new WorkspaceLocation(RelativePath.of("reports/workspaces")),
                new CleanupPolicy(45, 250));
    }
}
