package com.cybersammy.bugreport.core.workspace;

import com.cybersammy.bugreport.api.extension.ExtensionMetadata;
import com.cybersammy.bugreport.api.extension.ExtensionValue;
import com.cybersammy.bugreport.api.identifier.ExtensionMetadataKey;
import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serial;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** Streaming, checksum-producing UTF-8 encoders for bounded generated content. */
final class GeneratedContentEncoder {
    private static final int CHARACTER_CHUNK_SIZE = 8_192;

    private GeneratedContentEncoder() {}

    static WorkspaceGeneratedArtifactPublisher.WriteResult writeText(
            FileChannel output,
            CharSequence content,
            OutputLimits limits,
            CancellationSignal cancellation)
            throws IOException {
        BoundedDigestOutputStream bytes =
                new BoundedDigestOutputStream(output, limits, cancellation);
        Writer writer = utf8Writer(bytes);
        try {
            requireWellFormedUnicode(content);
            int offset = 0;
            char[] chunk = new char[CHARACTER_CHUNK_SIZE];
            while (offset < content.length()) {
                requireNotCancelled(cancellation);
                int length = Math.min(chunk.length, content.length() - offset);
                for (int index = 0; index < length; index++) {
                    chunk[index] = content.charAt(offset + index);
                }
                writer.write(chunk, 0, length);
                offset += length;
            }
            writer.flush();
            return bytes.result();
        } catch (CharacterCodingException exception) {
            throw malformedUnicode(exception);
        }
    }

    static WorkspaceGeneratedArtifactPublisher.WriteResult writeJson(
            FileChannel output,
            ExtensionMetadata content,
            OutputLimits limits,
            CancellationSignal cancellation)
            throws IOException {
        BoundedDigestOutputStream bytes =
                new BoundedDigestOutputStream(output, limits, cancellation);
        JsonWriter json = new JsonWriter(utf8Writer(bytes));
        json.setSerializeNulls(true);
        try {
            validateMetadataUnicode(content);
            writeMetadata(json, content);
            json.flush();
            return bytes.result();
        } catch (CharacterCodingException exception) {
            throw malformedUnicode(exception);
        }
    }

    private static Writer utf8Writer(OutputStream output) {
        return new OutputStreamWriter(
                output,
                StandardCharsets.UTF_8
                        .newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT));
    }

    private static void writeMetadata(JsonWriter writer, ExtensionMetadata metadata)
            throws IOException {
        writer.beginObject();
        for (Map.Entry<ExtensionMetadataKey, ExtensionValue> entry :
                metadata.values().entrySet()) {
            writer.name(entry.getKey().value());
            writeValue(writer, entry.getValue());
        }
        writer.endObject();
    }

    private static void writeValue(JsonWriter writer, ExtensionValue value) throws IOException {
        if (value instanceof ExtensionValue.StringValue string) {
            writer.value(string.value());
        } else if (value instanceof ExtensionValue.NumberValue number) {
            writer.value(number.value());
        } else if (value instanceof ExtensionValue.BooleanValue bool) {
            writer.value(bool.value());
        } else if (value instanceof ExtensionValue.NullValue) {
            writer.nullValue();
        } else if (value instanceof ExtensionValue.ArrayValue array) {
            writer.beginArray();
            for (ExtensionValue item : array.values()) {
                writeValue(writer, item);
            }
            writer.endArray();
        } else if (value instanceof ExtensionValue.ObjectValue object) {
            writer.beginObject();
            for (Map.Entry<String, ExtensionValue> entry : object.values().entrySet()) {
                writer.name(entry.getKey());
                writeValue(writer, entry.getValue());
            }
            writer.endObject();
        } else {
            throw new IllegalStateException("Unsupported extension value implementation");
        }
    }

    private static void validateMetadataUnicode(ExtensionMetadata metadata)
            throws EncodingException {
        for (Map.Entry<ExtensionMetadataKey, ExtensionValue> entry :
                metadata.values().entrySet()) {
            requireWellFormedUnicode(entry.getKey().value());
            validateValueUnicode(entry.getValue());
        }
    }

    private static void validateValueUnicode(ExtensionValue value)
            throws EncodingException {
        if (value instanceof ExtensionValue.StringValue string) {
            requireWellFormedUnicode(string.value());
        } else if (value instanceof ExtensionValue.ArrayValue array) {
            for (ExtensionValue item : array.values()) {
                validateValueUnicode(item);
            }
        } else if (value instanceof ExtensionValue.ObjectValue object) {
            for (Map.Entry<String, ExtensionValue> entry : object.values().entrySet()) {
                requireWellFormedUnicode(entry.getKey());
                validateValueUnicode(entry.getValue());
            }
        }
    }

    private static void requireWellFormedUnicode(CharSequence value)
            throws EncodingException {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw malformedUnicode(null);
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                throw malformedUnicode(null);
            }
        }
    }

    private static EncodingException malformedUnicode(Throwable cause) {
        return new EncodingException(
                GeneratedDiagnosticCode.INVALID_TEXT,
                "Generated content contains malformed Unicode",
                cause);
    }

    private static void requireNotCancelled(CancellationSignal cancellation)
            throws EncodingException {
        if (cancellation.isCancellationRequested()) {
            throw new EncodingException(
                    GeneratedDiagnosticCode.CANCELLED,
                    "Generated diagnostic collection was cancelled");
        }
    }

    record OutputLimits(
            long perArtifactBytes, long generatorRemainingBytes, long collectionRemainingBytes) {
        OutputLimits {
            if (perArtifactBytes < 0
                    || generatorRemainingBytes < 0
                    || collectionRemainingBytes < 0) {
                throw new IllegalArgumentException("Generated output limits must be non-negative");
            }
        }

        long effectiveBytes() {
            return Math.min(
                    perArtifactBytes,
                    Math.min(generatorRemainingBytes, collectionRemainingBytes));
        }

        GeneratedDiagnosticCode exceededCode() {
            if (collectionRemainingBytes <= perArtifactBytes
                    && collectionRemainingBytes <= generatorRemainingBytes) {
                return GeneratedDiagnosticCode.COLLECTION_BYTE_LIMIT_EXCEEDED;
            }
            if (generatorRemainingBytes <= perArtifactBytes) {
                return GeneratedDiagnosticCode.TOTAL_BYTE_LIMIT_EXCEEDED;
            }
            return GeneratedDiagnosticCode.ARTIFACT_BYTE_LIMIT_EXCEEDED;
        }
    }

    static final class EncodingException extends IOException {
        @Serial private static final long serialVersionUID = 1L;

        private final GeneratedDiagnosticCode code;

        private EncodingException(GeneratedDiagnosticCode code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "code");
        }

        private EncodingException(
                GeneratedDiagnosticCode code, String message, Throwable cause) {
            super(message, cause);
            this.code = Objects.requireNonNull(code, "code");
        }

        GeneratedDiagnosticCode code() {
            return code;
        }
    }

    private static final class BoundedDigestOutputStream extends OutputStream {
        private final FileChannel output;
        private final OutputLimits limits;
        private final CancellationSignal cancellation;
        private final MessageDigest digest = sha256();
        private long byteCount;

        private BoundedDigestOutputStream(
                FileChannel output,
                OutputLimits limits,
                CancellationSignal cancellation) {
            this.output = Objects.requireNonNull(output, "output");
            this.limits = Objects.requireNonNull(limits, "limits");
            this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, values.length);
            requireNotCancelled(cancellation);
            if (length > limits.effectiveBytes() - byteCount) {
                throw new EncodingException(
                        limits.exceededCode(),
                        "Generated artifact exceeded its byte ceiling");
            }
            ByteBuffer buffer = ByteBuffer.wrap(values, offset, length);
            while (buffer.hasRemaining()) {
                output.write(buffer);
            }
            digest.update(values, offset, length);
            byteCount += length;
        }

        private WorkspaceGeneratedArtifactPublisher.WriteResult result() {
            return new WorkspaceGeneratedArtifactPublisher.WriteResult(
                    byteCount,
                    new Sha256Checksum(HexFormat.of().formatHex(digest.digest())));
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Required SHA-256 implementation is unavailable", exception);
        }
    }
}
