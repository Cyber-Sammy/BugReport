package com.cybersammy.bugreport.core.configuration;

import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.api.version.SchemaVersion;
import com.cybersammy.bugreport.core.sanitization.SanitizationProfile;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Deterministic bounded JSON codec for the persisted product configuration. */
public final class ReportConfigurationJsonCodec {
    /** Canonical persisted schema identity. */
    public static final String SCHEMA_ID = "bugreport:configuration";
    /** Schema written by this codec. */
    public static final SchemaVersion CURRENT_SCHEMA_VERSION = new SchemaVersion(1, 0);
    /** Maximum accepted encoded configuration size. */
    public static final int MAX_ENCODED_BYTES = 64 * 1024;

    private static final SchemaVersion LEGACY_SCHEMA_VERSION = new SchemaVersion(0, 1);
    private static final int MAX_UNKNOWN_DEPTH = 8;
    private static final int MAX_UNKNOWN_CONTAINER_ENTRIES = 128;

    private ReportConfigurationJsonCodec() {}

    /** Serializes configuration as canonical UTF-8 JSON. */
    public static byte[] encode(ReportConfiguration configuration) {
        ReportConfiguration value = Objects.requireNonNull(configuration, "configuration");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.setSerializeNulls(false);
            writer.beginObject();
            writer.name("schemaId").value(SCHEMA_ID);
            writer.name("schemaVersion").value(CURRENT_SCHEMA_VERSION.toString());
            writer.name("sizeLimits");
            writer.beginObject();
            writer.name("maximumMatchedFiles").value(value.sizeLimits().maximumMatchedFiles());
            writer.name("maximumBytesPerFile").value(value.sizeLimits().maximumBytesPerFile());
            writer.name("maximumReportBytes").value(value.sizeLimits().maximumReportBytes());
            writer.endObject();
            writer.name("privacyProfile").value(value.privacyProfile().name());
            writer.name("workspaceDirectory").value(value.workspaceLocation().relativeDirectory().value());
            writer.name("cleanup");
            writer.beginObject();
            writer.name("retentionDays").value(value.cleanupPolicy().retentionDays());
            writer.name("maximumRetainedReports").value(value.cleanupPolicy().maximumRetainedReports());
            writer.endObject();
            writer.endObject();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unexpected in-memory configuration serialization failure", exception);
        }
        byte[] encoded = output.toByteArray();
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Encoded configuration exceeds " + MAX_ENCODED_BYTES + " bytes");
        }
        return encoded;
    }

    /** Parses and migrates bounded UTF-8 JSON without trusting persisted values. */
    public static DecodedReportConfiguration decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw new ConfigurationFormatException(
                    "Encoded configuration exceeds " + MAX_ENCODED_BYTES + " bytes");
        }
        RawConfiguration raw = new RawConfiguration();
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new ByteArrayInputStream(encoded),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            reader.setLenient(false);
            readRoot(reader, raw);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new ConfigurationFormatException("Configuration contains trailing JSON content");
            }
        } catch (ConfigurationFormatException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ConfigurationFormatException(
                    "Configuration JSON is malformed or invalid", exception);
        }
        return buildDecoded(raw);
    }

    private static void readRoot(JsonReader reader, RawConfiguration raw) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Configuration root must be an object");
        reader.beginObject();
        Set<String> members = new HashSet<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(members, name, "configuration root");
            switch (name) {
                case "schemaId" -> raw.schemaId = readString(reader, name);
                case "schemaVersion" -> raw.schemaVersion = readString(reader, name);
                case "sizeLimits" -> raw.sizeLimits = readSizeLimits(reader);
                case "privacyProfile" -> raw.privacyProfile = readString(reader, name);
                case "privacy" -> raw.legacyPrivacy = readString(reader, name);
                case "workspaceDirectory" -> raw.workspaceDirectory = readString(reader, name);
                case "cleanup" -> raw.cleanup = readCleanup(reader);
                case "maximumBytes" -> raw.legacyMaximumBytes = readPositiveLong(reader, name);
                case "retentionDays" -> raw.legacyRetentionDays = readPositiveInt(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
    }

    private static RawSizeLimits readSizeLimits(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Configuration sizeLimits must be an object");
        reader.beginObject();
        RawSizeLimits limits = new RawSizeLimits();
        Set<String> members = new HashSet<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(members, name, "configuration sizeLimits");
            switch (name) {
                case "maximumMatchedFiles" -> limits.maximumMatchedFiles = readPositiveInt(reader, name);
                case "maximumBytesPerFile" -> limits.maximumBytesPerFile = readPositiveLong(reader, name);
                case "maximumReportBytes" -> limits.maximumReportBytes = readPositiveLong(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return limits;
    }

    private static RawCleanup readCleanup(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Configuration cleanup must be an object");
        reader.beginObject();
        RawCleanup cleanup = new RawCleanup();
        Set<String> members = new HashSet<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(members, name, "configuration cleanup");
            switch (name) {
                case "retentionDays" -> cleanup.retentionDays = readPositiveInt(reader, name);
                case "maximumRetainedReports" ->
                        cleanup.maximumRetainedReports = readPositiveInt(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return cleanup;
    }

    private static DecodedReportConfiguration buildDecoded(RawConfiguration raw) {
        try {
            if (!SCHEMA_ID.equals(require(raw.schemaId, "schemaId"))) {
                throw new ConfigurationFormatException("Unsupported configuration schema ID");
            }
            SchemaVersion sourceVersion = SchemaVersion.parse(require(raw.schemaVersion, "schemaVersion"));
            ReportConfiguration configuration;
            if (CURRENT_SCHEMA_VERSION.equals(sourceVersion)) {
                configuration = currentConfiguration(raw);
            } else if (LEGACY_SCHEMA_VERSION.equals(sourceVersion)) {
                configuration = migrateLegacyConfiguration(raw);
            } else {
                throw new ConfigurationFormatException(
                        "Unsupported configuration schema version: " + sourceVersion);
            }
            return new DecodedReportConfiguration(
                    configuration,
                    sourceVersion,
                    !CURRENT_SCHEMA_VERSION.equals(sourceVersion));
        } catch (ConfigurationFormatException exception) {
            throw exception;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ConfigurationFormatException(
                    "Configuration values violate the persisted schema", exception);
        }
    }

    private static ReportConfiguration currentConfiguration(RawConfiguration raw) {
        if (raw.sizeLimits == null
                || raw.cleanup == null
                || raw.legacyMaximumBytes != null
                || raw.legacyRetentionDays != null
                || raw.legacyPrivacy != null) {
            throw new ConfigurationFormatException(
                    "Configuration schema 1.0 requires current members only");
        }
        return new ReportConfiguration(
                new ReportSizeLimits(
                        require(raw.sizeLimits.maximumMatchedFiles, "maximumMatchedFiles"),
                        require(raw.sizeLimits.maximumBytesPerFile, "maximumBytesPerFile"),
                        require(raw.sizeLimits.maximumReportBytes, "maximumReportBytes")),
                parseProfile(require(raw.privacyProfile, "privacyProfile")),
                new WorkspaceLocation(RelativePath.of(require(raw.workspaceDirectory, "workspaceDirectory"))),
                new CleanupPolicy(
                        require(raw.cleanup.retentionDays, "retentionDays"),
                        require(raw.cleanup.maximumRetainedReports, "maximumRetainedReports")));
    }

    private static ReportConfiguration migrateLegacyConfiguration(RawConfiguration raw) {
        if (raw.sizeLimits != null || raw.cleanup != null || raw.privacyProfile != null) {
            throw new ConfigurationFormatException(
                    "Configuration schema 0.1 forbids current nested members");
        }
        ReportConfiguration defaults = ReportConfiguration.productDefaults();
        long maximumReportBytes = require(raw.legacyMaximumBytes, "maximumBytes");
        return new ReportConfiguration(
                new ReportSizeLimits(
                        defaults.sizeLimits().maximumMatchedFiles(),
                        Math.min(defaults.sizeLimits().maximumBytesPerFile(), maximumReportBytes),
                        maximumReportBytes),
                parseProfile(require(raw.legacyPrivacy, "privacy")),
                new WorkspaceLocation(RelativePath.of(require(raw.workspaceDirectory, "workspaceDirectory"))),
                new CleanupPolicy(
                        require(raw.legacyRetentionDays, "retentionDays"),
                        defaults.cleanupPolicy().maximumRetainedReports()));
    }

    private static SanitizationProfile parseProfile(String value) {
        try {
            return SanitizationProfile.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationFormatException("Unknown privacy profile", exception);
        }
    }

    private static int readPositiveInt(JsonReader reader, String name) throws IOException {
        long value = readPositiveLong(reader, name);
        if (value > Integer.MAX_VALUE) {
            throw new ConfigurationFormatException("Configuration member " + name + " exceeds int range");
        }
        return (int) value;
    }

    private static long readPositiveLong(JsonReader reader, String name) throws IOException {
        requireToken(reader, JsonToken.NUMBER, "Configuration member " + name + " must be an integer");
        String value = reader.nextString();
        if (!value.matches("[1-9][0-9]*")) {
            throw new ConfigurationFormatException(
                    "Configuration member " + name + " must be a canonical positive integer");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ConfigurationFormatException(
                    "Configuration member " + name + " exceeds long range", exception);
        }
    }

    private static String readString(JsonReader reader, String name) throws IOException {
        requireToken(reader, JsonToken.STRING, "Configuration member " + name + " must be a string");
        return reader.nextString();
    }

    private static void skipBounded(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_UNKNOWN_DEPTH) {
            throw new ConfigurationFormatException("Unknown configuration data exceeds maximum nesting depth");
        }
        switch (reader.peek()) {
            case BEGIN_ARRAY -> {
                reader.beginArray();
                int entries = 0;
                while (reader.hasNext()) {
                    if (++entries > MAX_UNKNOWN_CONTAINER_ENTRIES) {
                        throw new ConfigurationFormatException("Unknown configuration array is too large");
                    }
                    skipBounded(reader, depth + 1);
                }
                reader.endArray();
            }
            case BEGIN_OBJECT -> {
                reader.beginObject();
                int entries = 0;
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    if (++entries > MAX_UNKNOWN_CONTAINER_ENTRIES) {
                        throw new ConfigurationFormatException("Unknown configuration object is too large");
                    }
                    requireUnique(names, reader.nextName(), "unknown configuration object");
                    skipBounded(reader, depth + 1);
                }
                reader.endObject();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new ConfigurationFormatException("Invalid unknown configuration value");
        }
    }

    private static void requireToken(JsonReader reader, JsonToken token, String message)
            throws IOException {
        if (reader.peek() != token) {
            throw new ConfigurationFormatException(message);
        }
    }

    private static void requireUnique(Set<String> members, String name, String owner) {
        if (!members.add(name)) {
            throw new ConfigurationFormatException("Duplicate member " + name + " in " + owner);
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new ConfigurationFormatException("Configuration is missing required member: " + name);
        }
        return value;
    }

    private static final class RawConfiguration {
        private String schemaId;
        private String schemaVersion;
        private RawSizeLimits sizeLimits;
        private String privacyProfile;
        private String legacyPrivacy;
        private String workspaceDirectory;
        private RawCleanup cleanup;
        private Long legacyMaximumBytes;
        private Integer legacyRetentionDays;
    }

    private static final class RawSizeLimits {
        private Integer maximumMatchedFiles;
        private Long maximumBytesPerFile;
        private Long maximumReportBytes;
    }

    private static final class RawCleanup {
        private Integer retentionDays;
        private Integer maximumRetainedReports;
    }
}
