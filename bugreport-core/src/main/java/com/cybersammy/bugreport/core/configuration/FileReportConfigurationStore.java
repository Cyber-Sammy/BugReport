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

/** Atomic bounded storage below one pre-existing platform-trusted configuration directory. */
public final class FileReportConfigurationStore {
    /** Fixed filename below the trusted directory; callers cannot supply a relative path. */
    public static final String CONFIGURATION_FILENAME = "bugreport.json";

    private final Path trustedDirectory;
    private final Path configurationFile;

    /**
     * Binds storage to a pre-existing platform-owned directory after checking its complete path
     * chain for filesystem redirection. The store never creates parent directories.
     */
    public FileReportConfigurationStore(Path trustedDirectory) {
        Path supplied = Objects.requireNonNull(trustedDirectory, "trustedDirectory");
        if (!supplied.isAbsolute()) {
            throw new IllegalArgumentException("Configuration directory must be absolute");
        }
        this.trustedDirectory = supplied.normalize();
        requireTrustedDirectoryAtConstruction(this.trustedDirectory);
        configurationFile = this.trustedDirectory.resolve(CONFIGURATION_FILENAME).normalize();
        if (!this.trustedDirectory.equals(configurationFile.getParent())) {
            throw new IllegalArgumentException("Configuration filename escaped its trusted directory");
        }
    }

    /** Loads the current configuration when present; a missing file is not an error. */
    public synchronized Optional<DecodedReportConfiguration> load() {
        requireTrustedDirectory();
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
        requireTrustedDirectory();
        try {
            if (Files.exists(configurationFile, LinkOption.NOFOLLOW_LINKS)) {
                requireSafeFile();
            }
            Path temporary = Files.createTempFile(
                    trustedDirectory, ".bugreport-config-", ".tmp");
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

    private void requireTrustedDirectory() {
        try {
            requireTrustedDirectory(trustedDirectory);
        } catch (IOException exception) {
            throw new ConfigurationStoreException(
                    ConfigurationStoreCode.PATH_INVALID,
                    "Configuration directory is no longer a trusted real directory",
                    exception);
        }
    }

    private static void requireTrustedDirectoryAtConstruction(Path directory) {
        try {
            requireTrustedDirectory(directory);
        } catch (IOException | ConfigurationStoreException exception) {
            throw new IllegalArgumentException(
                    "Configuration directory must be a pre-existing trusted real directory",
                    exception);
        }
    }

    private static void requireTrustedDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new ConfigurationStoreException(
                    ConfigurationStoreCode.PATH_INVALID,
                    "Configuration directory must be a real directory");
        }
        Path current = directory.getRoot();
        if (current == null) {
            throw new ConfigurationStoreException(
                    ConfigurationStoreCode.PATH_INVALID,
                    "Configuration directory must be absolute");
        }
        for (Path segment : directory) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new ConfigurationStoreException(
                        ConfigurationStoreCode.PATH_INVALID,
                        "Configuration directory must not traverse symbolic links");
            }
        }
        Path noFollow = directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path follow = directory.toRealPath();
        if (!noFollow.equals(follow)) {
            throw new ConfigurationStoreException(
                    ConfigurationStoreCode.PATH_INVALID,
                    "Configuration directory must not traverse filesystem redirection");
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
