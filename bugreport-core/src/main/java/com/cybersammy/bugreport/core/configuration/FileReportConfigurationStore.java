package com.cybersammy.bugreport.core.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;

/** Atomic bounded storage for one platform-selected Bug Report configuration file. */
public final class FileReportConfigurationStore {
    private final Path configurationFile;

    /** Binds storage to one absolute JSON file without touching it. */
    public FileReportConfigurationStore(Path configurationFile) {
        Path supplied = Objects.requireNonNull(configurationFile, "configurationFile");
        if (!supplied.isAbsolute() || supplied.getFileName() == null) {
            throw new IllegalArgumentException("Configuration file must be an absolute file path");
        }
        this.configurationFile = supplied.normalize();
    }

    /** Loads the current configuration when present; a missing file is not an error. */
    public synchronized Optional<DecodedReportConfiguration> load() {
        if (!Files.exists(configurationFile, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        requireSafeFile();
        try {
            return Optional.of(ReportConfigurationJsonCodec.decode(readBounded()));
        } catch (ConfigurationFormatException exception) {
            throw new ConfigurationStoreException(
                    ConfigurationStoreCode.FORMAT_INVALID,
                    "Persisted Bug Report configuration is invalid",
                    exception);
        } catch (IOException exception) {
            throw new ConfigurationStoreException(
                    ConfigurationStoreCode.IO_FAILURE,
                    "Could not read persisted Bug Report configuration",
                    exception);
        }
    }

    /** Atomically replaces the configuration file with canonical current-schema JSON. */
    public synchronized void save(ReportConfiguration configuration) {
        byte[] encoded = ReportConfigurationJsonCodec.encode(
                Objects.requireNonNull(configuration, "configuration"));
        Path parent = configurationFile.getParent();
        try {
            Files.createDirectories(parent);
            requireSafeParent(parent);
            if (Files.exists(configurationFile, LinkOption.NOFOLLOW_LINKS)) {
                requireSafeFile();
            }
            Path temporary = Files.createTempFile(parent, ".bugreport-config-", ".tmp");
            try {
                writeAndForce(temporary, encoded);
                Files.move(
                        temporary,
                        configurationFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new ConfigurationStoreException(
                        ConfigurationStoreCode.ATOMIC_MOVE_UNSUPPORTED,
                        "Configuration storage does not support atomic replacement",
                        exception);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (ConfigurationStoreException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ConfigurationStoreException(
                    ConfigurationStoreCode.IO_FAILURE,
                    "Could not persist Bug Report configuration",
                    exception);
        }
    }

    private byte[] readBounded() throws IOException {
        long size = Files.size(configurationFile);
        if (size > ReportConfigurationJsonCodec.MAX_ENCODED_BYTES) {
            throw new ConfigurationFormatException("Configuration file exceeds storage bound");
        }
        try (InputStream input = Files.newInputStream(configurationFile, LinkOption.NOFOLLOW_LINKS)) {
            byte[] encoded = input.readNBytes(ReportConfigurationJsonCodec.MAX_ENCODED_BYTES + 1);
            if (encoded.length > ReportConfigurationJsonCodec.MAX_ENCODED_BYTES) {
                throw new ConfigurationFormatException("Configuration file exceeds storage bound");
            }
            return encoded;
        }
    }

    private void requireSafeFile() {
        if (Files.isSymbolicLink(configurationFile)
                || !Files.isRegularFile(configurationFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new ConfigurationStoreException(
                    ConfigurationStoreCode.UNSAFE_FILE,
                    "Configuration path is not a regular file");
        }
    }

    private static void requireSafeParent(Path parent) {
        if (Files.isSymbolicLink(parent)
                || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new ConfigurationStoreException(
                    ConfigurationStoreCode.PATH_INVALID,
                    "Configuration parent must be a real directory");
        }
    }

    private static void writeAndForce(Path temporary, byte[] encoded) throws IOException {
        try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
                LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(encoded);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }
}
