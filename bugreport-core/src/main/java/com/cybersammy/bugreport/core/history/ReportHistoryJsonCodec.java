package com.cybersammy.bugreport.core.history;

import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.api.version.SchemaVersion;
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
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Canonical bounded JSON codec for the local report-history index. */
public final class ReportHistoryJsonCodec {
    public static final String SCHEMA_ID = "bugreport:history_index";
    public static final SchemaVersion CURRENT_SCHEMA_VERSION = new SchemaVersion(1, 0);
    public static final int MAX_ENCODED_BYTES = 512 * 1024;
    private static final SchemaVersion LEGACY_SCHEMA_VERSION = new SchemaVersion(0, 1);

    private ReportHistoryJsonCodec() {}

    public static byte[] encode(ReportHistoryIndex index) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.beginObject();
            writer.name("schemaId").value(SCHEMA_ID);
            writer.name("schemaVersion").value(CURRENT_SCHEMA_VERSION.toString());
            writer.name("entries").beginArray();
            for (ReportHistoryEntry entry : index.entries()) writeEntry(writer, entry);
            writer.endArray().endObject();
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory history serialization failure", exception);
        }
        byte[] encoded = output.toByteArray();
        if (encoded.length > MAX_ENCODED_BYTES) throw new IllegalArgumentException("History index exceeds encoded bound");
        return encoded;
    }

    public static ReportHistoryIndex decode(byte[] encoded) {
        if (encoded == null || encoded.length > MAX_ENCODED_BYTES) throw new HistoryFormatException("History index exceeds encoded bound");
        RawIndex raw = new RawIndex();
        try (JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(encoded),
                StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            reader.setLenient(false); readRoot(reader, raw);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new HistoryFormatException("History index contains trailing JSON content");
        } catch (HistoryFormatException exception) { throw exception;
        } catch (IOException | IllegalArgumentException exception) { throw new HistoryFormatException("History index JSON is malformed or invalid", exception); }
        if (!SCHEMA_ID.equals(raw.schemaId)) throw new HistoryFormatException("Unsupported history schema ID");
        SchemaVersion version;
        try { version = SchemaVersion.parse(require(raw.schemaVersion, "schemaVersion")); }
        catch (IllegalArgumentException exception) { throw new HistoryFormatException("History schema version is invalid", exception); }
        if (!CURRENT_SCHEMA_VERSION.equals(version) && !LEGACY_SCHEMA_VERSION.equals(version)) throw new HistoryFormatException("Unsupported history schema version: " + version);
        if (raw.entries == null) throw new HistoryFormatException("History index is missing entries");
        return new ReportHistoryIndex(raw.entries);
    }

    private static void writeEntry(JsonWriter writer, ReportHistoryEntry e) throws IOException {
        writer.beginObject(); writer.name("sessionId").value(e.sessionId().toString()); writer.name("providerId").value(e.providerId().value()); writer.name("providerVersion").value(e.providerVersion().value());
        e.categoryId().ifPresent(value -> { try { writer.name("categoryId").value(value.value()); } catch (IOException x) { throw new IllegalStateException(x); } });
        writer.name("status").value(e.status().name()); writer.name("revision").value(e.revision()); writer.name("updatedAt").value(e.updatedAt().toString());
        if (e.archive().isPresent()) { ReportArchiveSummary a = e.archive().orElseThrow(); writer.name("archive").beginObject(); writer.name("bytes").value(a.bytes()); writer.name("checksum").value(a.checksum().value()); writer.name("entryCount").value(a.entryCount()); writer.endObject(); }
        writer.endObject();
    }

    private static void readRoot(JsonReader reader, RawIndex raw) throws IOException {
        object(reader, "History root"); Set<String> names = new HashSet<>();
        while (reader.hasNext()) { String name = reader.nextName(); unique(names,name,"history root"); switch(name) {
            case "schemaId" -> raw.schemaId = string(reader,name); case "schemaVersion" -> raw.schemaVersion = string(reader,name); case "entries" -> raw.entries = readEntries(reader); default -> rejectUnknown(reader,name); } }
        reader.endObject();
    }
    private static List<ReportHistoryEntry> readEntries(JsonReader reader) throws IOException {
        token(reader,JsonToken.BEGIN_ARRAY,"History entries must be an array"); reader.beginArray(); List<ReportHistoryEntry> entries=new ArrayList<>();
        while(reader.hasNext()) { if(entries.size()==ReportHistoryIndex.MAX_ENTRIES) throw new HistoryFormatException("History index exceeds entry bound"); entries.add(readEntry(reader)); }
        reader.endArray(); return List.copyOf(entries);
    }
    private static ReportHistoryEntry readEntry(JsonReader reader) throws IOException {
        object(reader,"History entry"); RawEntry raw=new RawEntry(); Set<String> names=new HashSet<>();
        while(reader.hasNext()) { String name=reader.nextName(); unique(names,name,"history entry"); switch(name) {
            case "sessionId" -> raw.sessionId=string(reader,name); case "providerId" -> raw.providerId=string(reader,name); case "providerVersion" -> raw.providerVersion=string(reader,name); case "categoryId" -> raw.categoryId=string(reader,name); case "status", "outcome" -> raw.status=string(reader,name); case "revision" -> raw.revision=nonnegativeLong(reader,name); case "updatedAt" -> raw.updatedAt=string(reader,name); case "archive" -> raw.archive=readArchive(reader); default -> rejectUnknown(reader,name); } }
        reader.endObject();
        try { return new ReportHistoryEntry(ReportSessionId.parse(require(raw.sessionId,"sessionId")), ProviderId.parse(require(raw.providerId,"providerId")), ProviderVersion.parse(require(raw.providerVersion,"providerVersion")), Optional.ofNullable(raw.categoryId).map(CategoryId::of), ReportHistoryStatus.valueOf(require(raw.status,"status")), require(raw.revision,"revision"), Instant.parse(require(raw.updatedAt,"updatedAt")), Optional.ofNullable(raw.archive)); }
        catch(IllegalArgumentException|NullPointerException exception){ throw new HistoryFormatException("History entry values violate the schema",exception); }
    }
    private static ReportArchiveSummary readArchive(JsonReader reader) throws IOException {
        object(reader,"History archive"); String checksum=null; Long bytes=null; Integer count=null; Set<String> names=new HashSet<>();
        while(reader.hasNext()) { String name=reader.nextName(); unique(names,name,"history archive"); switch(name) { case "bytes" -> bytes=positiveLong(reader,name); case "checksum" -> checksum=string(reader,name); case "entryCount" -> count=positiveInt(reader,name); default -> rejectUnknown(reader,name); } } reader.endObject();
        return new ReportArchiveSummary(require(bytes,"archive bytes"),new Sha256Checksum(require(checksum,"archive checksum")),require(count,"archive entry count"));
    }
    private static void object(JsonReader r,String m)throws IOException{token(r,JsonToken.BEGIN_OBJECT,m+" must be an object");r.beginObject();}
    private static void token(JsonReader r,JsonToken t,String m)throws IOException{if(r.peek()!=t)throw new HistoryFormatException(m);}
    private static String string(JsonReader r,String n)throws IOException{token(r,JsonToken.STRING,"History member "+n+" must be a string");return r.nextString();}
    private static long nonnegativeLong(JsonReader r,String n)throws IOException{String v=number(r,n);if(!v.matches("0|[1-9][0-9]*"))throw new HistoryFormatException("History member "+n+" must be a canonical non-negative integer");try{return Long.parseLong(v);}catch(NumberFormatException x){throw new HistoryFormatException("History member "+n+" exceeds long range",x);}}
    private static long positiveLong(JsonReader r,String n)throws IOException{long v=nonnegativeLong(r,n);if(v==0)throw new HistoryFormatException("History member "+n+" must be positive");return v;}
    private static int positiveInt(JsonReader r,String n)throws IOException{long v=positiveLong(r,n);if(v>Integer.MAX_VALUE)throw new HistoryFormatException("History member "+n+" exceeds int range");return(int)v;}
    private static String number(JsonReader r,String n)throws IOException{token(r,JsonToken.NUMBER,"History member "+n+" must be an integer");return r.nextString();}
    private static void unique(Set<String>s,String n,String o){if(!s.add(n))throw new HistoryFormatException("Duplicate member "+n+" in "+o);}
    private static void rejectUnknown(JsonReader r,String n)throws IOException{r.skipValue();}
    private static <T>T require(T v,String n){if(v==null)throw new HistoryFormatException("History is missing required member: "+n);return v;}
    private static final class RawIndex{String schemaId;String schemaVersion;List<ReportHistoryEntry> entries;}
    private static final class RawEntry{String sessionId;String providerId;String providerVersion;String categoryId;String status;Long revision;String updatedAt;ReportArchiveSummary archive;}
}
