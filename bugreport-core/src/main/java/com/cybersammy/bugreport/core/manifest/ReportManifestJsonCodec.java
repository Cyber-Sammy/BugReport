package com.cybersammy.bugreport.core.manifest;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.extension.ExtensionValue;
import com.cybersammy.bugreport.api.identifier.CapabilityId;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ExtensionMetadataKey;
import com.cybersammy.bugreport.api.identifier.FieldId;
import com.cybersammy.bugreport.api.identifier.FieldOptionId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceKind;
import com.cybersammy.bugreport.api.specification.ReportQualityRole;
import com.cybersammy.bugreport.api.version.ApiVersion;
import com.cybersammy.bugreport.api.version.CapabilityVersion;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.api.version.SchemaVersion;
import com.cybersammy.bugreport.core.form.FieldValue;
import com.cybersammy.bugreport.core.form.FormSubmission;
import com.cybersammy.bugreport.core.form.ReportSeverity;
import com.cybersammy.bugreport.core.form.ReportSideContext;
import com.cybersammy.bugreport.core.sanitization.SanitizationAction;
import com.cybersammy.bugreport.core.sanitization.SanitizationFinding;
import com.cybersammy.bugreport.core.sanitization.SanitizationStageId;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic bounded UTF-8 JSON codec for portable report manifest schema 1. */
public final class ReportManifestJsonCodec {
    public static final String SCHEMA_ID = "bugreport:report_manifest";
    public static final SchemaVersion CURRENT_SCHEMA_VERSION = new SchemaVersion(1, 0);
    public static final int MAX_ENCODED_BYTES = 4 * 1024 * 1024;

    private static final int MAX_UNKNOWN_DEPTH = 16;
    private static final int MAX_UNKNOWN_CONTAINER_ENTRIES = 1_024;

    private ReportManifestJsonCodec() {}

    public static byte[] encode(ReportManifest manifest) {
        ReportManifest value = Objects.requireNonNull(manifest, "manifest");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonWriter writer =
                new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.setSerializeNulls(false);
            writeManifest(writer, value);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unexpected in-memory manifest serialization failure", exception);
        }
        byte[] encoded = output.toByteArray();
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Encoded manifest exceeds " + MAX_ENCODED_BYTES + " bytes");
        }
        return encoded;
    }

    public static DecodedReportManifest decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length > MAX_ENCODED_BYTES) {
            throw failure(
                    ManifestFormatCode.LIMIT_EXCEEDED,
                    "Encoded manifest exceeds " + MAX_ENCODED_BYTES + " bytes");
        }
        RawManifest raw = new RawManifest();
        try (JsonReader reader = new JsonReader(new InputStreamReader(
                new ByteArrayInputStream(encoded),
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            reader.setLenient(false);
            readManifest(reader, raw);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw failure(
                        ManifestFormatCode.MALFORMED_JSON,
                        "Manifest contains trailing JSON content");
            }
        } catch (ManifestFormatException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(
                    ManifestFormatCode.MALFORMED_JSON,
                    "Manifest JSON is malformed",
                    exception);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Manifest values violate schema invariants");
        }
        return buildDecoded(raw);
    }

    private static void writeManifest(JsonWriter writer, ReportManifest manifest)
            throws IOException {
        writer.beginObject();
        writer.name("schema");
        writer.beginObject();
        writer.name("id").value(SCHEMA_ID);
        writer.name("major").value(CURRENT_SCHEMA_VERSION.major());
        writer.name("minor").value(CURRENT_SCHEMA_VERSION.minor());
        writer.endObject();
        writer.name("reportId").value(manifest.reportId().toString());
        writer.name("createdAt").value(manifest.createdAt().toString());
        writer.name("producer");
        writeProducer(writer, manifest.producer());
        if (manifest.target().isPresent()) {
            writer.name("target");
            writeTarget(writer, manifest.target().orElseThrow());
        }
        writer.name("environment");
        writeEnvironment(writer, manifest.environment());
        writer.name("incomplete").value(manifest.incomplete());
        writer.name("fields");
        writeFields(writer, manifest.reviewedFields());
        writer.name("capabilities");
        writer.beginObject();
        writer.name("required");
        writeCapabilities(writer, manifest.requiredCapabilities());
        writer.name("optional");
        writeCapabilities(writer, manifest.optionalCapabilities());
        writer.endObject();
        writer.name("entries");
        writer.beginArray();
        for (ManifestEntry entry : manifest.entries()) {
            writeEntry(writer, entry);
        }
        writer.endArray();
        writer.name("errors");
        writeErrors(writer, manifest.errors());
        writer.name("extensions");
        writeExtensions(writer, manifest.extensions());
        writer.endObject();
    }

    private static void writeProducer(JsonWriter writer, ManifestProducer producer)
            throws IOException {
        writer.beginObject();
        writer.name("modVersion").value(producer.modVersion());
        writer.name("apiVersion").value(producer.apiVersion().value());
        writer.endObject();
    }

    private static void writeTarget(JsonWriter writer, ManifestTarget target)
            throws IOException {
        writer.beginObject();
        writer.name("providerId").value(target.providerId().value());
        writer.name("providerVersion").value(target.providerVersion().value());
        writer.name("categoryId").value(target.categoryId().value());
        writer.endObject();
    }

    private static void writeEnvironment(JsonWriter writer, ManifestEnvironment environment)
            throws IOException {
        writer.beginObject();
        writer.name("gameVersion").value(environment.gameVersion());
        writer.name("loaderId").value(environment.loaderId());
        writer.name("loaderVersion").value(environment.loaderVersion());
        writer.name("physicalSide").value(environment.physicalSide().name());
        writer.endObject();
    }

    private static void writeCapabilities(
            JsonWriter writer, List<ManifestCapability> capabilities) throws IOException {
        writer.beginArray();
        for (ManifestCapability capability : capabilities) {
            writer.beginObject();
            writer.name("id").value(capability.id().value());
            writer.name("major").value(capability.version().major());
            writer.name("minor").value(capability.version().minor());
            writer.endObject();
        }
        writer.endArray();
    }

    private static void writeEntry(JsonWriter writer, ManifestEntry entry) throws IOException {
        writer.beginObject();
        writer.name("path").value(entry.archivePath());
        writer.name("uncompressedBytes").value(entry.uncompressedBytes());
        writer.name("sha256").value(entry.checksum().value());
        writer.name("contentType").value(entry.contentType().name());
        if (entry.mediaType().isPresent()) {
            writer.name("mediaType").value(entry.mediaType().orElseThrow());
        }
        writer.name("effectivePrivacy").value(entry.effectivePrivacy().name());
        writer.name("qualityRole").value(entry.qualityRole().name());
        writer.name("collectionStatus").value(entry.collectionStatus().name());
        writer.name("sanitizationStatus").value(entry.sanitizationStatus().name());
        writer.name("provenance");
        writer.beginArray();
        for (ManifestEntryProvenance provenance : entry.provenances()) {
            writeProvenance(writer, provenance);
        }
        writer.endArray();
        writer.name("sanitizationFindings");
        writer.beginArray();
        for (SanitizationFinding finding : entry.sanitizationFindings()) {
            writeFinding(writer, finding);
        }
        writer.endArray();
        writer.name("extensions");
        writeExtensions(writer, entry.extensions());
        writer.endObject();
    }

    private static void writeProvenance(
            JsonWriter writer, ManifestEntryProvenance provenance) throws IOException {
        writer.beginObject();
        writer.name("providerId").value(provenance.providerId().value());
        writer.name("providerVersion").value(provenance.providerVersion().value());
        writer.name("categoryId").value(provenance.categoryId().value());
        writer.name("declarationKind").value(provenance.declarationKind().name());
        writer.name("declarationId").value(provenance.declarationId());
        if (provenance.sourceKind().isPresent()) {
            writer.name("sourceKind").value(provenance.sourceKind().orElseThrow().name());
        }
        writer.name("declaredPrivacy").value(provenance.declaredPrivacy().name());
        writer.endObject();
    }

    private static void writeFinding(JsonWriter writer, SanitizationFinding finding)
            throws IOException {
        writer.beginObject();
        writer.name("stageId").value(finding.stageId().value());
        writer.name("line").value(finding.line());
        writer.name("startColumn").value(finding.startColumn());
        writer.name("endColumn").value(finding.endColumn());
        writer.name("classification").value(finding.classification().name());
        writer.name("action").value(finding.action().name());
        writer.endObject();
    }

    private static void writeErrors(JsonWriter writer, List<ManifestError> errors)
            throws IOException {
        writer.beginArray();
        for (ManifestError error : errors) {
            writer.beginObject();
            writer.name("code").value(error.code());
            writer.name("component").value(error.component());
            error.providerId().ifPresent(value -> writeUnchecked(writer, "providerId", value.value()));
            error.declarationId().ifPresent(value -> writeUnchecked(writer, "declarationId", value));
            writer.endObject();
        }
        writer.endArray();
    }

    private static void writeUnchecked(JsonWriter writer, String name, String value) {
        try {
            writer.name(name).value(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory JSON failure", exception);
        }
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
            case FieldValue.Text text -> writeTyped(writer, "text", text.value());
            case FieldValue.TextList list -> {
                writer.name("type").value("text_list");
                writer.name("value");
                writeStrings(writer, list.values());
            }
            case FieldValue.Checkbox checkbox -> {
                writer.name("type").value("checkbox");
                writer.name("value").value(checkbox.checked());
            }
            case FieldValue.Selection selection ->
                    writeTyped(writer, "selection", selection.optionId().value());
            case FieldValue.MultiSelection selection -> {
                writer.name("type").value("multi_selection");
                writer.name("value");
                writeStrings(
                        writer,
                        selection.optionIds().stream().map(FieldOptionId::value).toList());
            }
            case FieldValue.IntegerNumber number ->
                    writeTyped(writer, "integer", number.value().toString());
            case FieldValue.DecimalNumber number ->
                    writeTyped(writer, "decimal", number.value().toString());
            case FieldValue.Severity severity ->
                    writeTyped(writer, "severity", severity.value().value());
            case FieldValue.SideContext side ->
                    writeTyped(writer, "side_context", side.value().value());
        }
        writer.endObject();
    }

    private static void writeTyped(JsonWriter writer, String type, String value)
            throws IOException {
        writer.name("type").value(type);
        writer.name("value").value(value);
    }

    private static void writeStrings(JsonWriter writer, Iterable<String> values)
            throws IOException {
        writer.beginArray();
        for (String value : values) {
            writer.value(value);
        }
        writer.endArray();
    }

    private static void writeExtensions(JsonWriter writer, ExtensionMetadata metadata)
            throws IOException {
        writer.beginObject();
        for (var entry : metadata.values().entrySet()) {
            writer.name(entry.getKey().value());
            writeExtensionValue(writer, entry.getValue());
        }
        writer.endObject();
    }

    private static void writeExtensionValue(JsonWriter writer, ExtensionValue value)
            throws IOException {
        switch (value) {
            case ExtensionValue.StringValue string -> writer.value(string.value());
            case ExtensionValue.NumberValue number -> writer.value(number.value());
            case ExtensionValue.BooleanValue bool -> writer.value(bool.value());
            case ExtensionValue.NullValue ignored -> writer.nullValue();
            case ExtensionValue.ArrayValue array -> {
                writer.beginArray();
                for (ExtensionValue child : array.values()) {
                    writeExtensionValue(writer, child);
                }
                writer.endArray();
            }
            case ExtensionValue.ObjectValue object -> {
                writer.beginObject();
                for (var entry : object.values().entrySet()) {
                    writer.name(entry.getKey());
                    writeExtensionValue(writer, entry.getValue());
                }
                writer.endObject();
            }
        }
    }

    private static void readManifest(JsonReader reader, RawManifest raw) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest root must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest root");
            switch (name) {
                case "schema" -> raw.schema = readSchema(reader);
                case "reportId" -> raw.reportId = readString(reader, name);
                case "createdAt" -> raw.createdAt = readString(reader, name);
                case "producer" -> raw.producer = readProducer(reader);
                case "target" -> raw.target = readTarget(reader);
                case "environment" -> raw.environment = readEnvironment(reader);
                case "incomplete" -> raw.incomplete = readBoolean(reader, name);
                case "fields" -> raw.fields = readFields(reader);
                case "capabilities" -> raw.capabilities = readCapabilities(reader);
                case "entries" -> raw.entries = readEntries(reader);
                case "errors" -> raw.errors = readErrors(reader);
                case "extensions" -> raw.extensions = readExtensions(reader);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
    }

    private static RawSchema readSchema(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest schema must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String id = null;
        Integer major = null;
        Integer minor = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest schema");
            switch (name) {
                case "id" -> id = readString(reader, name);
                case "major" -> major = readNonNegativeInt(reader, name);
                case "minor" -> minor = readNonNegativeInt(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return new RawSchema(id, major, minor);
    }

    private static ManifestProducer readProducer(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest producer must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String modVersion = null;
        String apiVersion = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest producer");
            switch (name) {
                case "modVersion" -> modVersion = readString(reader, name);
                case "apiVersion" -> apiVersion = readString(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return new ManifestProducer(
                require(modVersion, "producer.modVersion"),
                ApiVersion.parse(require(apiVersion, "producer.apiVersion")));
    }

    private static ManifestTarget readTarget(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest target must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String providerId = null;
        String providerVersion = null;
        String categoryId = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest target");
            switch (name) {
                case "providerId" -> providerId = readString(reader, name);
                case "providerVersion" -> providerVersion = readString(reader, name);
                case "categoryId" -> categoryId = readString(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return new ManifestTarget(
                ProviderId.parse(require(providerId, "target.providerId")),
                ProviderVersion.parse(require(providerVersion, "target.providerVersion")),
                CategoryId.of(require(categoryId, "target.categoryId")));
    }

    private static ManifestEnvironment readEnvironment(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest environment must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String gameVersion = null;
        String loaderId = null;
        String loaderVersion = null;
        String physicalSide = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest environment");
            switch (name) {
                case "gameVersion" -> gameVersion = readString(reader, name);
                case "loaderId" -> loaderId = readString(reader, name);
                case "loaderVersion" -> loaderVersion = readString(reader, name);
                case "physicalSide" -> physicalSide = readString(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return new ManifestEnvironment(
                require(gameVersion, "environment.gameVersion"),
                require(loaderId, "environment.loaderId"),
                require(loaderVersion, "environment.loaderVersion"),
                enumValue(SupportedSide.class, physicalSide, "environment.physicalSide"));
    }

    private static RawCapabilities readCapabilities(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest capabilities must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        List<ManifestCapability> required = null;
        List<ManifestCapability> optional = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest capabilities");
            switch (name) {
                case "required" -> required = readCapabilityArray(reader);
                case "optional" -> optional = readCapabilityArray(reader);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return new RawCapabilities(
                require(required, "capabilities.required"),
                require(optional, "capabilities.optional"));
    }

    private static List<ManifestCapability> readCapabilityArray(JsonReader reader)
            throws IOException {
        requireToken(reader, JsonToken.BEGIN_ARRAY, "Manifest capability list must be an array");
        reader.beginArray();
        List<ManifestCapability> values = new ArrayList<>();
        while (reader.hasNext()) {
            requireSize(values, ReportManifest.MAX_CAPABILITIES, "capabilities");
            values.add(readCapability(reader));
        }
        reader.endArray();
        return List.copyOf(values);
    }

    private static ManifestCapability readCapability(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest capability must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String id = null;
        Integer major = null;
        Integer minor = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest capability");
            switch (name) {
                case "id" -> id = readString(reader, name);
                case "major" -> major = readNonNegativeInt(reader, name);
                case "minor" -> minor = readNonNegativeInt(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return new ManifestCapability(
                CapabilityId.of(require(id, "capability.id")),
                new CapabilityVersion(
                        require(major, "capability.major"),
                        require(minor, "capability.minor")));
    }

    private static List<ManifestEntry> readEntries(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_ARRAY, "Manifest entries must be an array");
        reader.beginArray();
        List<ManifestEntry> entries = new ArrayList<>();
        while (reader.hasNext()) {
            requireSize(entries, ReportManifest.MAX_ENTRIES, "entries");
            entries.add(readEntry(reader));
        }
        reader.endArray();
        return List.copyOf(entries);
    }

    private static ManifestEntry readEntry(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest entry must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        RawEntry raw = new RawEntry();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest entry");
            switch (name) {
                case "path" -> raw.path = readString(reader, name);
                case "uncompressedBytes" -> raw.bytes = readNonNegativeLong(reader, name);
                case "sha256" -> raw.sha256 = readString(reader, name);
                case "contentType" -> raw.contentType = readString(reader, name);
                case "mediaType" -> raw.mediaType = readString(reader, name);
                case "effectivePrivacy" -> raw.privacy = readString(reader, name);
                case "qualityRole" -> raw.qualityRole = readString(reader, name);
                case "collectionStatus" -> raw.collectionStatus = readString(reader, name);
                case "sanitizationStatus" -> raw.sanitizationStatus = readString(reader, name);
                case "provenance" -> raw.provenances = readProvenances(reader);
                case "sanitizationFindings" -> raw.findings = readFindings(reader);
                case "extensions" -> raw.extensions = readExtensions(reader);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        String path = require(raw.path, "entry.path");
        return new ManifestEntry(
                path,
                require(raw.bytes, "entry.uncompressedBytes"),
                new Sha256Checksum(require(raw.sha256, "entry.sha256")),
                enumValue(DiagnosticContentType.class, raw.contentType, "entry.contentType"),
                Optional.ofNullable(raw.mediaType),
                enumValue(PrivacyClassification.class, raw.privacy, "entry.effectivePrivacy"),
                enumValue(ReportQualityRole.class, raw.qualityRole, "entry.qualityRole"),
                enumValue(
                        ManifestCollectionStatus.class,
                        raw.collectionStatus,
                        "entry.collectionStatus"),
                enumValue(
                        ManifestSanitizationStatus.class,
                        raw.sanitizationStatus,
                        "entry.sanitizationStatus"),
                require(raw.provenances, "entry.provenance"),
                toFindings(path, require(raw.findings, "entry.sanitizationFindings")),
                require(raw.extensions, "entry.extensions"));
    }

    private static List<ManifestEntryProvenance> readProvenances(JsonReader reader)
            throws IOException {
        requireToken(reader, JsonToken.BEGIN_ARRAY, "Manifest provenance must be an array");
        reader.beginArray();
        List<ManifestEntryProvenance> values = new ArrayList<>();
        while (reader.hasNext()) {
            requireSize(values, ManifestEntry.MAX_PROVENANCES, "entry provenance");
            values.add(readProvenance(reader));
        }
        reader.endArray();
        return List.copyOf(values);
    }

    private static ManifestEntryProvenance readProvenance(JsonReader reader)
            throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest provenance must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        RawProvenance raw = new RawProvenance();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest provenance");
            switch (name) {
                case "providerId" -> raw.providerId = readString(reader, name);
                case "providerVersion" -> raw.providerVersion = readString(reader, name);
                case "categoryId" -> raw.categoryId = readString(reader, name);
                case "declarationKind" -> raw.declarationKind = readString(reader, name);
                case "declarationId" -> raw.declarationId = readString(reader, name);
                case "sourceKind" -> raw.sourceKind = readString(reader, name);
                case "declaredPrivacy" -> raw.declaredPrivacy = readString(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return ManifestEntryProvenance.decoded(
                ProviderId.parse(require(raw.providerId, "provenance.providerId")),
                ProviderVersion.parse(
                        require(raw.providerVersion, "provenance.providerVersion")),
                CategoryId.of(require(raw.categoryId, "provenance.categoryId")),
                enumValue(
                        ManifestDeclarationKind.class,
                        raw.declarationKind,
                        "provenance.declarationKind"),
                require(raw.declarationId, "provenance.declarationId"),
                Optional.ofNullable(raw.sourceKind)
                        .map(value -> enumValue(
                                DiagnosticSourceKind.class, value, "provenance.sourceKind")),
                enumValue(
                        PrivacyClassification.class,
                        raw.declaredPrivacy,
                        "provenance.declaredPrivacy"));
    }

    private static List<RawFinding> readFindings(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_ARRAY, "Manifest findings must be an array");
        reader.beginArray();
        List<RawFinding> values = new ArrayList<>();
        while (reader.hasNext()) {
            requireSize(values, ManifestEntry.MAX_FINDINGS, "sanitization findings");
            values.add(readFinding(reader));
        }
        reader.endArray();
        return List.copyOf(values);
    }

    private static RawFinding readFinding(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest finding must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        RawFinding raw = new RawFinding();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest finding");
            switch (name) {
                case "stageId" -> raw.stageId = readString(reader, name);
                case "line" -> raw.line = readNonNegativeLong(reader, name);
                case "startColumn" -> raw.startColumn = readNonNegativeInt(reader, name);
                case "endColumn" -> raw.endColumn = readNonNegativeInt(reader, name);
                case "classification" -> raw.classification = readString(reader, name);
                case "action" -> raw.action = readString(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return raw;
    }

    private static List<SanitizationFinding> toFindings(
            String archivePath, List<RawFinding> values) {
        String artifactName = archivePath.substring("content/".length());
        return values.stream()
                .map(raw -> new SanitizationFinding(
                        artifactName,
                        new SanitizationStageId(require(raw.stageId, "finding.stageId")),
                        require(raw.line, "finding.line"),
                        require(raw.startColumn, "finding.startColumn"),
                        require(raw.endColumn, "finding.endColumn"),
                        enumValue(
                                PrivacyClassification.class,
                                raw.classification,
                                "finding.classification"),
                        enumValue(SanitizationAction.class, raw.action, "finding.action")))
                .toList();
    }

    private static List<ManifestError> readErrors(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_ARRAY, "Manifest errors must be an array");
        reader.beginArray();
        List<ManifestError> values = new ArrayList<>();
        while (reader.hasNext()) {
            requireSize(values, ReportManifest.MAX_ERRORS, "errors");
            values.add(readError(reader));
        }
        reader.endArray();
        return List.copyOf(values);
    }

    private static ManifestError readError(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest error must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String code = null;
        String component = null;
        String providerId = null;
        String declarationId = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest error");
            switch (name) {
                case "code" -> code = readString(reader, name);
                case "component" -> component = readString(reader, name);
                case "providerId" -> providerId = readString(reader, name);
                case "declarationId" -> declarationId = readString(reader, name);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return new ManifestError(
                require(code, "error.code"),
                require(component, "error.component"),
                Optional.ofNullable(providerId).map(ProviderId::parse),
                Optional.ofNullable(declarationId));
    }

    private static FormSubmission readFields(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest fields must be an object");
        reader.beginObject();
        FormSubmission.Builder fields = FormSubmission.builder();
        Set<String> names = new HashSet<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest fields");
            fields.put(FieldId.of(name), readFieldValue(reader));
        }
        reader.endObject();
        return fields.build();
    }

    private static FieldValue readFieldValue(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest field value must be an object");
        reader.beginObject();
        Set<String> names = new HashSet<>();
        String type = null;
        RawValue value = null;
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest field value");
            switch (name) {
                case "type" -> type = readString(reader, name);
                case "value" -> value = readRawValue(reader);
                default -> skipBounded(reader, 0);
            }
        }
        reader.endObject();
        return toFieldValue(require(type, "field.type"), require(value, "field.value"));
    }

    private static RawValue readRawValue(JsonReader reader) throws IOException {
        return switch (reader.peek()) {
            case STRING -> new RawValue(reader.nextString(), null, null);
            case BOOLEAN -> new RawValue(null, reader.nextBoolean(), null);
            case BEGIN_ARRAY -> new RawValue(null, null, readStringArray(reader));
            default -> throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Manifest field value has an unsupported representation");
        };
    }

    private static List<String> readStringArray(JsonReader reader) throws IOException {
        reader.beginArray();
        List<String> values = new ArrayList<>();
        while (reader.hasNext()) {
            requireSize(values, FieldValue.MAX_TEXT_ITEMS, "field array");
            values.add(readString(reader, "field array item"));
        }
        reader.endArray();
        return List.copyOf(values);
    }

    private static FieldValue toFieldValue(String type, RawValue value) {
        return switch (type) {
            case "text" -> new FieldValue.Text(value.requireString());
            case "text_list" -> new FieldValue.TextList(value.requireStrings());
            case "checkbox" -> new FieldValue.Checkbox(value.requireBoolean());
            case "selection" ->
                    new FieldValue.Selection(FieldOptionId.of(value.requireString()));
            case "multi_selection" -> {
                TreeSet<FieldOptionId> options = new TreeSet<>();
                for (String option : value.requireStrings()) {
                    if (!options.add(FieldOptionId.of(option))) {
                        throw failure(
                                ManifestFormatCode.INVALID_VALUE,
                                "Manifest multi-selection contains a duplicate option");
                    }
                }
                yield new FieldValue.MultiSelection(options);
            }
            case "integer" ->
                    new FieldValue.IntegerNumber(new BigInteger(value.requireString()));
            case "decimal" ->
                    new FieldValue.DecimalNumber(new BigDecimal(value.requireString()));
            case "severity" -> new FieldValue.Severity(
                    enumBySerializedValue(ReportSeverity.values(), value.requireString()));
            case "side_context" -> new FieldValue.SideContext(
                    enumBySerializedValue(ReportSideContext.values(), value.requireString()));
            default -> throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Unknown manifest field value type");
        };
    }

    private static ReportSeverity enumBySerializedValue(
            ReportSeverity[] values, String serialized) {
        for (ReportSeverity value : values) {
            if (value.value().equals(serialized)) {
                return value;
            }
        }
        throw failure(ManifestFormatCode.INVALID_VALUE, "Unknown report severity");
    }

    private static ReportSideContext enumBySerializedValue(
            ReportSideContext[] values, String serialized) {
        for (ReportSideContext value : values) {
            if (value.value().equals(serialized)) {
                return value;
            }
        }
        throw failure(ManifestFormatCode.INVALID_VALUE, "Unknown report side context");
    }

    private static ExtensionMetadata readExtensions(JsonReader reader) throws IOException {
        requireToken(reader, JsonToken.BEGIN_OBJECT, "Manifest extensions must be an object");
        reader.beginObject();
        ExtensionMetadata.Builder builder = ExtensionMetadata.builder();
        Set<String> names = new HashSet<>();
        ExtensionCounter counter = new ExtensionCounter();
        while (reader.hasNext()) {
            String name = reader.nextName();
            requireUnique(names, name, "manifest extensions");
            if (names.size() > ExtensionMetadata.MAX_TOP_LEVEL_ENTRIES) {
                throw limit("Manifest extensions contain too many entries");
            }
            builder.put(
                    ExtensionMetadataKey.of(name),
                    readExtensionValue(reader, counter, 1));
        }
        reader.endObject();
        return builder.build();
    }

    private static ExtensionValue readExtensionValue(
            JsonReader reader, ExtensionCounter counter, int depth) throws IOException {
        counter.increment();
        return switch (reader.peek()) {
            case STRING -> ExtensionValue.of(reader.nextString());
            case NUMBER -> ExtensionValue.of(new BigDecimal(reader.nextString()));
            case BOOLEAN -> ExtensionValue.of(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield ExtensionValue.nullValue();
            }
            case BEGIN_ARRAY -> readExtensionArray(reader, counter, depth);
            case BEGIN_OBJECT -> readExtensionObject(reader, counter, depth);
            default -> throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Manifest extension value is invalid");
        };
    }

    private static ExtensionValue readExtensionArray(
            JsonReader reader, ExtensionCounter counter, int depth) throws IOException {
        requireExtensionDepth(depth);
        reader.beginArray();
        List<ExtensionValue> values = new ArrayList<>();
        while (reader.hasNext()) {
            requireSize(values, ExtensionMetadata.MAX_CONTAINER_ENTRIES, "extension array");
            values.add(readExtensionValue(reader, counter, depth + 1));
        }
        reader.endArray();
        return ExtensionValue.array(values);
    }

    private static ExtensionValue readExtensionObject(
            JsonReader reader, ExtensionCounter counter, int depth) throws IOException {
        requireExtensionDepth(depth);
        reader.beginObject();
        java.util.TreeMap<String, ExtensionValue> values = new java.util.TreeMap<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (values.containsKey(name)) {
                throw duplicate("manifest extension object", name);
            }
            if (values.size() >= ExtensionMetadata.MAX_CONTAINER_ENTRIES) {
                throw limit("Manifest extension object contains too many entries");
            }
            values.put(name, readExtensionValue(reader, counter, depth + 1));
        }
        reader.endObject();
        return ExtensionValue.object(values);
    }

    private static void requireExtensionDepth(int depth) {
        if (depth > ExtensionMetadata.MAX_DEPTH) {
            throw limit("Manifest extension data is too deeply nested");
        }
    }

    private static DecodedReportManifest buildDecoded(RawManifest raw) {
        try {
            RawSchema schema = require(raw.schema, "schema");
            if (!SCHEMA_ID.equals(require(schema.id(), "schema.id"))) {
                throw failure(
                        ManifestFormatCode.UNSUPPORTED_SCHEMA_ID,
                        "Unsupported manifest schema ID");
            }
            SchemaVersion version = new SchemaVersion(
                    require(schema.major(), "schema.major"),
                    require(schema.minor(), "schema.minor"));
            if (version.major() != CURRENT_SCHEMA_VERSION.major()) {
                throw failure(
                        ManifestFormatCode.UNSUPPORTED_SCHEMA_MAJOR,
                        "Unsupported manifest schema major");
            }
            RawCapabilities capabilities = require(raw.capabilities, "capabilities");
            ReportManifest.Builder builder = ReportManifest.builder(
                            ReportSessionId.parse(require(raw.reportId, "reportId")),
                            Instant.parse(require(raw.createdAt, "createdAt")),
                            require(raw.producer, "producer"),
                            require(raw.environment, "environment"))
                    .incomplete(require(raw.incomplete, "incomplete"))
                    .reviewedFields(require(raw.fields, "fields"))
                    .capabilities(capabilities.required(), capabilities.optional())
                    .entries(require(raw.entries, "entries"))
                    .errors(require(raw.errors, "errors"))
                    .extensions(require(raw.extensions, "extensions"));
            if (raw.target != null) {
                builder.target(raw.target);
            }
            return new DecodedReportManifest(
                    builder.build(),
                    version,
                    version.minor() > CURRENT_SCHEMA_VERSION.minor());
        } catch (ManifestFormatException exception) {
            throw exception;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Manifest values violate schema invariants");
        }
    }

    private static String readString(JsonReader reader, String name) throws IOException {
        requireToken(reader, JsonToken.STRING, "Manifest member " + name + " must be a string");
        return reader.nextString();
    }

    private static boolean readBoolean(JsonReader reader, String name) throws IOException {
        requireToken(reader, JsonToken.BOOLEAN, "Manifest member " + name + " must be boolean");
        return reader.nextBoolean();
    }

    private static int readNonNegativeInt(JsonReader reader, String name) throws IOException {
        long value = readNonNegativeLong(reader, name);
        if (value > Integer.MAX_VALUE) {
            throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Manifest member " + name + " exceeds integer range");
        }
        return (int) value;
    }

    private static long readNonNegativeLong(JsonReader reader, String name) throws IOException {
        requireToken(reader, JsonToken.NUMBER, "Manifest member " + name + " must be an integer");
        String value = reader.nextString();
        if (!value.matches("0|[1-9][0-9]*")) {
            throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Manifest member " + name + " is not a canonical non-negative integer");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Manifest integer exceeds long range");
        }
    }

    private static void requireToken(JsonReader reader, JsonToken expected, String message)
            throws IOException {
        if (reader.peek() != expected) {
            throw failure(ManifestFormatCode.INVALID_VALUE, message);
        }
    }

    private static void requireUnique(Set<String> names, String name, String container) {
        if (!names.add(name)) {
            throw duplicate(container, name);
        }
    }

    private static ManifestFormatException duplicate(String container, String name) {
        return failure(
                ManifestFormatCode.DUPLICATE_MEMBER,
                "Duplicate member in " + container);
    }

    private static <T> void requireSize(List<T> values, int maximum, String description) {
        if (values.size() >= maximum) {
            throw limit("Manifest " + description + " exceeds its item limit");
        }
    }

    private static void skipBounded(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_UNKNOWN_DEPTH) {
            throw limit("Unknown manifest data is too deeply nested");
        }
        switch (reader.peek()) {
            case BEGIN_ARRAY -> {
                reader.beginArray();
                int entries = 0;
                while (reader.hasNext()) {
                    if (++entries > MAX_UNKNOWN_CONTAINER_ENTRIES) {
                        throw limit("Unknown manifest array is too large");
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
                        throw limit("Unknown manifest object is too large");
                    }
                    String name = reader.nextName();
                    requireUnique(names, name, "unknown manifest object");
                    skipBounded(reader, depth + 1);
                }
                reader.endObject();
            }
            case STRING -> reader.nextString();
            case NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw failure(
                    ManifestFormatCode.MALFORMED_JSON,
                    "Unknown manifest value is malformed");
        }
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String value, String description) {
        try {
            return Enum.valueOf(type, require(value, description));
        } catch (IllegalArgumentException exception) {
            throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Unknown " + description);
        }
    }

    private static <T> T require(T value, String description) {
        if (value == null) {
            throw failure(
                    ManifestFormatCode.INVALID_VALUE,
                    "Manifest requires " + description);
        }
        return value;
    }

    private static ManifestFormatException limit(String message) {
        return failure(ManifestFormatCode.LIMIT_EXCEEDED, message);
    }

    private static ManifestFormatException failure(
            ManifestFormatCode code, String message) {
        return new ManifestFormatException(code, message);
    }

    private static ManifestFormatException failure(
            ManifestFormatCode code, String message, Throwable cause) {
        return new ManifestFormatException(code, message, cause);
    }

    private static final class RawManifest {
        private RawSchema schema;
        private String reportId;
        private String createdAt;
        private ManifestProducer producer;
        private ManifestTarget target;
        private ManifestEnvironment environment;
        private Boolean incomplete;
        private FormSubmission fields;
        private RawCapabilities capabilities;
        private List<ManifestEntry> entries;
        private List<ManifestError> errors;
        private ExtensionMetadata extensions;
    }

    private record RawSchema(String id, Integer major, Integer minor) {}

    private record RawCapabilities(
            List<ManifestCapability> required, List<ManifestCapability> optional) {}

    private static final class RawEntry {
        private String path;
        private Long bytes;
        private String sha256;
        private String contentType;
        private String mediaType;
        private String privacy;
        private String qualityRole;
        private String collectionStatus;
        private String sanitizationStatus;
        private List<ManifestEntryProvenance> provenances;
        private List<RawFinding> findings;
        private ExtensionMetadata extensions;
    }

    private static final class RawProvenance {
        private String providerId;
        private String providerVersion;
        private String categoryId;
        private String declarationKind;
        private String declarationId;
        private String sourceKind;
        private String declaredPrivacy;
    }

    private static final class RawFinding {
        private String stageId;
        private Long line;
        private Integer startColumn;
        private Integer endColumn;
        private String classification;
        private String action;
    }

    private record RawValue(String string, Boolean bool, List<String> strings) {
        private String requireString() {
            if (string == null || bool != null || strings != null) {
                throw failure(
                        ManifestFormatCode.INVALID_VALUE,
                        "Manifest field requires a string representation");
            }
            return string;
        }

        private boolean requireBoolean() {
            if (bool == null || string != null || strings != null) {
                throw failure(
                        ManifestFormatCode.INVALID_VALUE,
                        "Manifest field requires a boolean representation");
            }
            return bool;
        }

        private List<String> requireStrings() {
            if (strings == null || string != null || bool != null) {
                throw failure(
                        ManifestFormatCode.INVALID_VALUE,
                        "Manifest field requires a string-array representation");
            }
            return strings;
        }
    }

    private static final class ExtensionCounter {
        private int values;

        private void increment() {
            if (++values > ExtensionMetadata.MAX_VALUE_COUNT) {
                throw limit("Manifest extensions contain too many values");
            }
        }
    }
}
