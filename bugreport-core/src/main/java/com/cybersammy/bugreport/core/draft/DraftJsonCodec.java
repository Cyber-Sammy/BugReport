package com.cybersammy.bugreport.core.draft;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.api.version.SchemaVersion;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.form.ReportSeverity;
import com.cybersammy.bugreport.core.form.ReportSideContext;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.session.ReportSessionState;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic bounded JSON codec for the persisted report-draft schema. */
public final class DraftJsonCodec {
    /** Canonical persisted schema identity. */
    public static final String SCHEMA_ID = "bugreport:report_draft";
    /** Schema written by this codec. */
    public static final SchemaVersion CURRENT_SCHEMA_VERSION = new SchemaVersion(1, 0);
    /** Maximum accepted encoded draft size. */
    public static final int MAX_ENCODED_BYTES = 1_048_576;

    private static final SchemaVersion LEGACY_SCHEMA_VERSION = new SchemaVersion(0, 1);
    private static final int MAX_UNKNOWN_DEPTH = 16;
    private static final int MAX_UNKNOWN_CONTAINER_ENTRIES = 1_024;

    private DraftJsonCodec() {}

    /**
     * Serializes a draft as canonical UTF-8 JSON.
     *
     * @param draft bounded draft model
     * @return deterministic encoded bytes
     */
    public static byte[] encode(ReportDraft draft) {
        ReportDraft value = Objects.requireNonNull(draft, "draft");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonWriter writer =
                new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.setSerializeNulls(false);
            writer.beginObject();
            writer.name("schemaId").value(SCHEMA_ID);
            writer.name("schemaVersion").value(CURRENT_SCHEMA_VERSION.toString());
            writer.name("sessionId").value(value.sessionId().toString());
            writer.name("revision").value(value.revision());
            writer.name("providerId").value(value.providerId().value());
            writer.name("providerVersion").value(value.providerVersion().value());
            if (value.categoryId().isPresent()) {
                writer.name("categoryId").value(value.categoryId().orElseThrow().value());
            }
            writer.name("state").value(value.recordedState().name());
            writer.name("fields");
            writeFields(writer, value.formSubmission());
            writer.endObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory draft serialization failure", exception);
        }
        byte[] encoded = output.toByteArray();
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Encoded draft exceeds " + MAX_ENCODED_BYTES + " bytes");
        }
        return encoded;
    }

    /**
     * Parses and migrates bounded UTF-8 JSON without trusting persisted declarations.
     *
     * @param encoded encoded draft bytes
     * @return decoded current draft plus source-version provenance
     */
    public static DecodedReportDraft decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw new DraftFormatException(
                    "Encoded draft exceeds " + MAX_ENCODED_BYTES + " bytes");
        }
        RawDraft raw = new RawDraft();
        try (JsonReader reader =
                new JsonReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(encoded),
                                StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(CodingErrorAction.REPORT)
                                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            reader.setLenient(false);
            readRoot(reader, raw);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new DraftFormatException("Draft contains trailing JSON content");
            }
        } catch (DraftFormatException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new DraftFormatException("Draft JSON is malformed or invalid", exception);
        }
        return buildDecoded(raw);
    }

    private static void writeFields(JsonWriter writer, FormSubmission submission)
            throws IOException {
        writer.beginObject();
        for (var entry : submission.values().entrySet()) {
            writer.name(entry.getKey().value());
            writeFieldValue(writer, entry.getValue());
        }
        writer.endObject();
    }

    private static void writeFieldValue(JsonWriter writer, FieldValue value) throws IOException {
        writer.beginObject();
        switch (value) {
            case FieldValue.Text text -> {
                writer.name("type").value("text");
                writer.name("value").value(text.value());
            }
            case FieldValue.TextList list -> {
                writer.name("type").value("text_list");
                writer.name("value");
                writeStrings(writer, list.values());
            }
            case FieldValue.Checkbox checkbox -> {
                writer.name("type").value("checkbox");
                writer.name("value").value(checkbox.checked());
            }
            case FieldValue.Selection selection -> {
                writer.name("type").value("selection");
                writer.name("value").value(selection.optionId().value());
            }
            case FieldValue.MultiSelection selection -> {
                writer.name("type").value("multi_selection");
                writer.name("value");
                writeStrings(
                        writer,
                        selection.optionIds().stream().map(FieldOptionId::value).toList());
            }
            case FieldValue.IntegerNumber number -> {
                writer.name("type").value("integer");
                writer.name("value").value(number.value().toString());
            }
            case FieldValue.DecimalNumber number -> {
                writer.name("type").value("decimal");
                writer.name("value").value(number.value().toString());
            }
            case FieldValue.Severity severity -> {
                writer.name("type").value("severity");
                writer.name("value").value(severity.value().value());
            }
            case FieldValue.SideContext side -> {
                writer.name("type").value("side_context");
                writer.name("value").value(side.value().value());
            }
        }
        writer.endObject();
    }

    private static void writeStrings(JsonWriter writer, Iterable<String> values)
            throws IOException {
        writer.beginArray();
        for (String value : values) {
            writer.value(value);
        }
        writer.endArray();
    }

    private static void readRoot(JsonReader reader, RawDraft raw) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Draft root must be an object");
        reader.beginObject();
        Set<String> members = new HashSet<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(members, name, "draft root");
            switch (name) {
                case "schemaId" -> raw.schemaId = readString(reader, name);
                case "schemaVersion" -> raw.schemaVersion = readString(reader, name);
                case "sessionId" -> raw.sessionId = readString(reader, name);
                case "revision" -> raw.revision = readNonNegativeLong(reader, name);
                case "providerId" -> raw.providerId = readString(reader, name);
                case "providerVersion" -> raw.providerVersion = readString(reader, name);
                case "categoryId" -> raw.categoryId = readString(reader, name);
                case "state" -> raw.state = readString(reader, name);
                case "fields" -> raw.fields = readFields(reader);
                case "values" -> raw.legacyValues = readFields(reader);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
    }

    private static FormSubmission readFields(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Draft fields must be an object");
        reader.beginObject();
        FormSubmission.Builder fields = FormSubmission.builder();
        Set<String> members = new HashSet<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(members, name, "draft fields");
            fields.put(FieldId.of(name), readFieldValue(reader));
        }
        reader.endObject();
        return fields.build();
    }

    private static FieldValue readFieldValue(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Draft field value must be an object");
        reader.beginObject();
        Set<String> members = new HashSet<>();
        String type = null;
        RawValue value = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(members, name, "draft field value");
            switch (name) {
                case "type" -> type = readString(reader, "type");
                case "value" -> value = readRawValue(reader);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        if (type == null || value == null) {
            throw new DraftFormatException("Draft field value requires type and value");
        }
        return toFieldValue(type, value);
    }

    private static RawValue readRawValue(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case STRING -> new RawValue(reader.nextString(), null, null);
            case BOOLEAN -> new RawValue(null, reader.nextBoolean(), null);
            case BEGIN_ARRAY -> new RawValue(null, null, readStringArray(reader));
            default -> throw new DraftFormatException(
                    "Draft field value must be a string, boolean, or string array");
        };
    }

    private static List<String> readStringArray(JsonReader reader) throws IOException {
        reader.beginArray();
        ArrayList<String> values = new ArrayList<>();
        while (reader.hasNext()) {
            if (values.size() >= FieldValue.MAX_TEXT_ITEMS) {
                throw new DraftFormatException(
                        "Draft field array exceeds " + FieldValue.MAX_TEXT_ITEMS + " items");
            }
            values.add(readString(reader, "field array item"));
        }
        reader.endArray();
        return List.copyOf(values);
    }

    private static FieldValue toFieldValue(String type, RawValue raw) {
        return switch (type) {
            case "text" -> new FieldValue.Text(raw.requireString(type));
            case "text_list" -> new FieldValue.TextList(raw.requireStrings(type));
            case "checkbox" -> new FieldValue.Checkbox(raw.requireBoolean(type));
            case "selection" ->
                    new FieldValue.Selection(FieldOptionId.of(raw.requireString(type)));
            case "multi_selection" -> {
                TreeSet<FieldOptionId> options = new TreeSet<>();
                for (String option : raw.requireStrings(type)) {
                    if (!options.add(FieldOptionId.of(option))) {
                        throw new DraftFormatException(
                                "Draft multi-selection contains a duplicate option");
                    }
                }
                yield new FieldValue.MultiSelection(options);
            }
            case "integer" ->
                    new FieldValue.IntegerNumber(new BigInteger(raw.requireString(type)));
            case "decimal" ->
                    new FieldValue.DecimalNumber(new BigDecimal(raw.requireString(type)));
            case "severity" ->
                    new FieldValue.Severity(
                            parseSeverity(raw.requireString(type)));
            case "side_context" ->
                    new FieldValue.SideContext(
                            parseSideContext(raw.requireString(type)));
            default -> throw new DraftFormatException("Unknown draft field value type: " + type);
        };
    }

    private static DecodedReportDraft buildDecoded(RawDraft raw) {
        try {
            if (!SCHEMA_ID.equals(require(raw.schemaId, "schemaId"))) {
                throw new DraftFormatException("Unsupported draft schema ID");
            }
            SchemaVersion sourceVersion =
                    SchemaVersion.parse(require(raw.schemaVersion, "schemaVersion"));
            FormSubmission submission;
            if (CURRENT_SCHEMA_VERSION.equals(sourceVersion)) {
                if (raw.fields == null || raw.legacyValues != null) {
                    throw new DraftFormatException(
                            "Draft schema 1.0 requires fields and forbids legacy values");
                }
                submission = raw.fields;
            } else if (LEGACY_SCHEMA_VERSION.equals(sourceVersion)) {
                submission = migrateLegacyValues(raw);
            } else {
                throw new DraftFormatException(
                        "Unsupported draft schema version: " + sourceVersion);
            }
            ReportDraft draft =
                    new ReportDraft(
                            ReportSessionId.parse(require(raw.sessionId, "sessionId")),
                            require(raw.revision, "revision"),
                            ProviderId.parse(require(raw.providerId, "providerId")),
                            ProviderVersion.parse(
                                    require(raw.providerVersion, "providerVersion")),
                            java.util.Optional.ofNullable(raw.categoryId).map(CategoryId::of),
                            ReportSessionState.valueOf(require(raw.state, "state")),
                            submission);
            return new DecodedReportDraft(
                    draft,
                    sourceVersion,
                    !CURRENT_SCHEMA_VERSION.equals(sourceVersion));
        } catch (DraftFormatException exception) {
            throw exception;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new DraftFormatException("Draft values violate the persisted schema", exception);
        }
    }

    private static FormSubmission migrateLegacyValues(RawDraft raw) {
        if (raw.legacyValues == null || raw.fields != null) {
            throw new DraftFormatException(
                    "Draft schema 0.1 requires values and forbids current fields");
        }
        return raw.legacyValues;
    }

    private static ReportSeverity parseSeverity(String value) {
        for (ReportSeverity severity : ReportSeverity.values()) {
            if (severity.value().equals(value)) {
                return severity;
            }
        }
        throw new DraftFormatException("Unknown report severity: " + value);
    }

    private static ReportSideContext parseSideContext(String value) {
        for (ReportSideContext side : ReportSideContext.values()) {
            if (side.value().equals(value)) {
                return side;
            }
        }
        throw new DraftFormatException("Unknown report side context: " + value);
    }

    private static String readString(JsonReader reader, String name) throws IOException {
        requireToken(reader, JsonToken.STRING, "Draft member " + name + " must be a string");
        return reader.nextString();
    }

    private static long readNonNegativeLong(JsonReader reader, String name) throws IOException {
        requireToken(reader, JsonToken.NUMBER, "Draft member " + name + " must be an integer");
        String value = reader.nextString();
        if (!value.matches("0|[1-9][0-9]*")) {
            throw new DraftFormatException(
                    "Draft member " + name + " must be a canonical non-negative integer");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new DraftFormatException("Draft member " + name + " exceeds long range", exception);
        }
    }

    private static void skipBounded(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_UNKNOWN_DEPTH) {
            throw new DraftFormatException("Unknown draft data exceeds maximum nesting depth");
        }
        switch (reader.peek()) {
            case BEGIN_ARRAY -> {
                reader.beginArray();
                int entries = 0;
                while (reader.hasNext()) {
                    if (++entries > MAX_UNKNOWN_CONTAINER_ENTRIES) {
                        throw new DraftFormatException("Unknown draft array is too large");
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
                        throw new DraftFormatException("Unknown draft object is too large");
                    }
                    String name = reader.nextName();
                    requireUnique(names, name, "unknown draft object");
                    skipBounded(reader, depth + 1);
                }
                reader.endObject();
            }
            case STRING -> reader.nextString();
            case NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new DraftFormatException("Invalid unknown draft value");
        }
    }

    private static void requireToken(JsonReader reader, JsonToken token, String message)
            throws IOException {
        if (reader.peek() != token) {
            throw new DraftFormatException(message);
        }
    }

    private static void requireUnique(Set<String> members, String name, String owner) {
        if (!members.add(name)) {
            throw new DraftFormatException("Duplicate member " + name + " in " + owner);
        }
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new DraftFormatException("Draft is missing required member: " + name);
        }
        return value;
    }

    private record RawValue(String string, Boolean bool, List<String> strings) {
        private String requireString(String type) {
            if (string == null || bool != null || strings != null) {
                throw new DraftFormatException("Draft field type " + type + " requires a string");
            }
            return string;
        }

        private boolean requireBoolean(String type) {
            if (bool == null || string != null || strings != null) {
                throw new DraftFormatException("Draft field type " + type + " requires a boolean");
            }
            return bool;
        }

        private List<String> requireStrings(String type) {
            if (strings == null || string != null || bool != null) {
                throw new DraftFormatException(
                        "Draft field type " + type + " requires a string array");
            }
            return strings;
        }
    }

    private static final class RawDraft {
        private String schemaId;
        private String schemaVersion;
        private String sessionId;
        private Long revision;
        private String providerId;
        private String providerVersion;
        private String categoryId;
        private String state;
        private FormSubmission fields;
        private FormSubmission legacyValues;
    }
}
