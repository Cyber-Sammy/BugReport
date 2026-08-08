package com.cybersammy.bugreport.core.packaging;

import com.cybersammy.bugreport.api.specification.CancellationSignal;
import com.cybersammy.bugreport.core.workspace.ReportWorkspace;
import com.cybersammy.bugreport.core.workspace.ReviewedWorkspaceSnapshotException;
import com.cybersammy.bugreport.core.workspace.ReviewedWorkspaceSnapshotFactory;
import com.cybersammy.bugreport.core.workspace.Sha256Checksum;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streams one trusted package plan into a private, validated, atomically published ZIP. */
public final class ReportZipWriter {
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<AclEntryPermission> OWNER_PERMISSIONS =
            Set.copyOf(EnumSet.allOf(AclEntryPermission.class));

    private ReportZipWriter() {}

    public static ReportZipArchive write(
            ReportPackagePlan plan, ReportWorkspace workspace, Path destination) {
        return write(
                plan,
                workspace,
                destination,
                CancellationSignal.neverCancelled(),
                ReportZipProgressListener.noOp());
    }

    /**
     * Writes and independently validates a local report archive before atomic publication.
     *
     * <p>The destination must be a new direct child of a real directory and end in
     * {@code .bugreport.zip}. This operation performs blocking I/O and must run off UI and game
     * threads. Cancellation or failure never publishes a partial destination.
     */
    public static ReportZipArchive write(
            ReportPackagePlan plan,
            ReportWorkspace workspace,
            Path destination,
            CancellationSignal cancellation) {
        return write(
                plan, workspace, destination, cancellation, ReportZipProgressListener.noOp());
    }

    public static ReportZipArchive write(
            ReportPackagePlan plan,
            ReportWorkspace workspace,
            Path destination,
            CancellationSignal cancellation,
            ReportZipProgressListener progressListener) {
        ReportPackagePlan trustedPlan = Objects.requireNonNull(plan, "plan");
        ReportWorkspace trustedWorkspace = Objects.requireNonNull(workspace, "workspace");
        CancellationSignal signal = Objects.requireNonNull(cancellation, "cancellation");
        ReportZipProgressListener progress =
                Objects.requireNonNull(progressListener, "progressListener");
        ReportZipValidator.validatePlanLimits(trustedPlan);
        Path target = validateDestination(destination);
        Path temporary = null;
        try {
            requireNotCancelled(signal);
            requireCurrent(trustedPlan, trustedWorkspace);
            temporary = createPrivateTemporary(target.getParent());
            streamPlan(trustedPlan, trustedWorkspace, temporary, signal, progress);
            requireCurrent(trustedPlan, trustedWorkspace);
            requireNotCancelled(signal);
            ReportZipArchive archive = ReportZipValidator.validate(temporary, trustedPlan);
            requireNotCancelled(signal);
            verifyPrivateFile(temporary);
            publish(temporary, target);
            temporary = null;
            return archive;
        } catch (ReportZipException exception) {
            throw rollback(temporary, exception);
        } catch (ReviewedWorkspaceSnapshotException exception) {
            throw rollback(
                    temporary,
                    failure(
                            ReportZipCode.SNAPSHOT_CHANGED,
                            null,
                            "Reviewed workspace changed before archive publication",
                            exception));
        } catch (IOException | SecurityException exception) {
            throw rollback(
                    temporary,
                    failure(
                            ReportZipCode.WRITE_FAILED,
                            null,
                            "Report archive could not be safely written",
                            exception));
        } catch (RuntimeException exception) {
            throw rollback(
                    temporary,
                    failure(
                            ReportZipCode.WRITE_FAILED,
                            null,
                            "Report archive callback failed",
                            exception));
        } catch (Error error) {
            ReportZipException cleanup = rollback(
                    temporary,
                    failure(
                            ReportZipCode.WRITE_FAILED,
                            null,
                            "Report archive callback failed",
                            error));
            if (cleanup.code() == ReportZipCode.ROLLBACK_FAILED) {
                error.addSuppressed(cleanup);
            }
            throw error;
        }
    }

    private static void streamPlan(
            ReportPackagePlan plan,
            ReportWorkspace workspace,
            Path temporary,
            CancellationSignal cancellation,
            ReportZipProgressListener progressListener)
            throws IOException {
        WriteProgress progress = new WriteProgress(plan, progressListener);
        progress.publish();
        try (FileChannel channel = FileChannel.open(
                        temporary,
                        Set.of(StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
                BoundedOutputStream bounded = new BoundedOutputStream(
                        Channels.newOutputStream(channel), ReportZipLimits.MAX_ARCHIVE_BYTES);
                ZipOutputStream zip = new ZipOutputStream(bounded, StandardCharsets.UTF_8)) {
            zip.setLevel(Deflater.DEFAULT_COMPRESSION);
            for (PackagePlanEntry entry : plan.entries()) {
                requireNotCancelled(cancellation);
                ZipEntry zipEntry = new ZipEntry(entry.archivePath());
                zipEntry.setMethod(ZipEntry.DEFLATED);
                zipEntry.setTime(ReportZipLimits.CANONICAL_ENTRY_TIME_MILLIS);
                zipEntry.setComment(null);
                zipEntry.setExtra(new byte[0]);
                zip.putNextEntry(zipEntry);
                writeContents(plan, workspace, entry, zip, cancellation, progress);
                zip.closeEntry();
                progress.completeEntry();
            }
            zip.finish();
            zip.flush();
            channel.force(true);
        }
    }

    private static void writeContents(
            ReportPackagePlan plan,
            ReportWorkspace workspace,
            PackagePlanEntry entry,
            ZipOutputStream output,
            CancellationSignal cancellation,
            WriteProgress progress)
            throws IOException {
        switch (entry.kind()) {
            case MANIFEST -> writeInline(
                    plan.manifestDocument(), entry, output, cancellation, progress);
            case MARKDOWN -> writeInline(
                    plan.markdownDocument().orElseThrow(),
                    entry,
                    output,
                    cancellation,
                    progress);
            case WORKSPACE_ARTIFACT -> writeWorkspaceArtifact(
                    workspace, entry, output, cancellation, progress);
        }
    }

    private static void writeInline(
            byte[] contents,
            PackagePlanEntry entry,
            ZipOutputStream output,
            CancellationSignal cancellation,
            WriteProgress progress)
            throws IOException {
        requireNotCancelled(cancellation);
        if (contents.length != entry.uncompressedBytes()
                || !checksum(contents).equals(entry.checksum())) {
            throw failure(
                    ReportZipCode.ENTRY_CHANGED,
                    entry.archivePath(),
                    "Inline package entry differs from the trusted plan",
                    null);
        }
        output.write(contents);
        progress.addBytes(contents.length);
    }

    private static void writeWorkspaceArtifact(
            ReportWorkspace workspace,
            PackagePlanEntry entry,
            ZipOutputStream output,
            CancellationSignal cancellation,
            WriteProgress progress)
            throws IOException {
        String artifactName = entry.workspaceArtifactName().orElseThrow();
        Path path = workspace.directory().resolve(artifactName).normalize();
        if (!workspace.directory().equals(path.getParent())) {
            throw failure(
                    ReportZipCode.ENTRY_CHANGED,
                    entry.archivePath(),
                    "Workspace artifact escaped its trusted directory",
                    null);
        }
        MessageDigest digest = sha256();
        byte[] buffer = new byte[ReportZipLimits.BUFFER_BYTES];
        long bytes = 0;
        try (FileChannel input = FileChannel.open(
                path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            while (true) {
                requireNotCancelled(cancellation);
                byteBuffer.clear();
                int read = input.read(byteBuffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                if (read > entry.uncompressedBytes() - bytes) {
                    throw changed(entry);
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                bytes += read;
                progress.addBytes(read);
            }
        }
        Sha256Checksum checksum =
                new Sha256Checksum(HexFormat.of().formatHex(digest.digest()));
        if (bytes != entry.uncompressedBytes() || !checksum.equals(entry.checksum())) {
            throw changed(entry);
        }
    }

    private static void requireCurrent(ReportPackagePlan plan, ReportWorkspace workspace) {
        ReviewedWorkspaceSnapshotFactory.requireCurrent(
                plan.preparedSnapshot().reviewedSnapshot(), workspace);
    }

    private static Path validateDestination(Path destination) {
        Path target = Objects.requireNonNull(destination, "destination")
                .toAbsolutePath()
                .normalize();
        Path parent = target.getParent();
        String filename = target.getFileName() == null ? "" : target.getFileName().toString();
        try {
            if (parent == null
                    || !filename.endsWith(".bugreport.zip")
                    || filename.length() <= ".bugreport.zip".length()
                    || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                    || !parent.equals(parent.toRealPath(LinkOption.NOFOLLOW_LINKS))
                    || !parent.equals(parent.toRealPath())) {
                throw failure(
                        ReportZipCode.INVALID_DESTINATION,
                        null,
                        "Report archive destination is not a safe canonical path",
                        null);
            }
            requirePrivateOutputSupport(parent);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw failure(
                        ReportZipCode.OUTPUT_ALREADY_EXISTS,
                        null,
                        "Report archive destination already exists",
                        null);
            }
            return target;
        } catch (ReportZipException exception) {
            throw exception;
        } catch (PrivateOutputUnsupportedException exception) {
            throw failure(
                    ReportZipCode.PRIVATE_OUTPUT_UNSUPPORTED,
                    null,
                    "Report archive destination cannot prove owner-only access",
                    exception);
        } catch (IOException | SecurityException exception) {
            throw failure(
                    ReportZipCode.INVALID_DESTINATION,
                    null,
                    "Report archive destination could not be verified",
                    exception);
        }
    }

    private static Path createPrivateTemporary(Path parent) throws IOException {
        FileStore store = Files.getFileStore(parent);
        PrivateArchivePermissionModel model = PrivateArchivePermissionModel.select(store);
        Path path;
        if (model == PrivateArchivePermissionModel.POSIX) {
            path = Files.createTempFile(
                    parent,
                    ".bugreport-",
                    ".part",
                    PosixFilePermissions.asFileAttribute(PRIVATE_FILE_PERMISSIONS));
        } else {
            path = Files.createTempFile(parent, ".bugreport-", ".part");
        }
        try {
            enforcePrivateFile(path, model);
            return path;
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private static void enforcePrivateFile(Path path, PrivateArchivePermissionModel model)
            throws IOException {
        if (model == PrivateArchivePermissionModel.POSIX) {
            Set<PosixFilePermission> permissions =
                    Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (!permissions.equals(PRIVATE_FILE_PERMISSIONS)) {
                throw new IOException("Owner-only POSIX permissions were not applied");
            }
            return;
        }
        if (model == PrivateArchivePermissionModel.ACL) {
            AclFileAttributeView view = Files.getFileAttributeView(
                    path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
                throw new IOException("Filesystem advertised ACL support without an ACL view");
            }
            UserPrincipal owner = view.getOwner();
            view.setAcl(List.of(AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(OWNER_PERMISSIONS)
                    .build()));
            verifyOwnerOnlyAcl(view, owner);
        }
    }

    private static void verifyPrivateFile(Path path) throws IOException {
        var attributes = Files.readAttributes(
                path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()
                || attributes.isSymbolicLink()
                || attributes.isOther()
                || !path.equals(path.toRealPath(LinkOption.NOFOLLOW_LINKS))
                || !path.equals(path.toRealPath())) {
            throw new IOException("Published report archive is not a safe regular file");
        }
        enforcePrivateFile(path, PrivateArchivePermissionModel.select(Files.getFileStore(path)));
    }

    private static void requirePrivateOutputSupport(Path directory)
            throws IOException, PrivateOutputUnsupportedException {
        try {
            PrivateArchivePermissionModel.select(Files.getFileStore(directory));
        } catch (IOException exception) {
            throw new PrivateOutputUnsupportedException(exception);
        }
    }

    private static void verifyOwnerOnlyAcl(AclFileAttributeView view, UserPrincipal owner)
            throws IOException {
        List<AclEntry> entries = view.getAcl();
        if (entries.isEmpty()
                || entries.stream().anyMatch(entry -> entry.type() != AclEntryType.ALLOW
                        || !entry.principal().equals(owner)
                        || !entry.permissions().containsAll(OWNER_PERMISSIONS)
                        || entry.flags().contains(AclEntryFlag.INHERIT_ONLY))) {
            throw new IOException("Owner-only archive ACL was not applied");
        }
    }

    private static void publish(Path temporary, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw failure(
                    ReportZipCode.OUTPUT_ALREADY_EXISTS,
                    null,
                    "Report archive destination already exists",
                    null);
        }
        try {
            Files.createLink(target, temporary);
        } catch (FileAlreadyExistsException exception) {
            throw failure(
                    ReportZipCode.OUTPUT_ALREADY_EXISTS,
                    null,
                    "Report archive destination already exists",
                    exception);
        } catch (UnsupportedOperationException exception) {
            throw failure(
                    ReportZipCode.PUBLICATION_FAILED,
                    null,
                    "Atomic no-replace report archive publication is not supported",
                    exception);
        } catch (IOException exception) {
            throw failure(
                    ReportZipCode.PUBLICATION_FAILED,
                    null,
                    "Report archive could not be atomically published",
                    exception);
        }
        try {
            Files.delete(temporary);
        } catch (IOException | SecurityException exception) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException | SecurityException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw failure(
                    ReportZipCode.PUBLICATION_FAILED,
                    null,
                    "Published report archive temporary link could not be removed",
                    exception);
        }
    }

    private static void requireNotCancelled(CancellationSignal cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw failure(
                    ReportZipCode.CANCELLED,
                    null,
                    "Report archive creation was cancelled",
                    null);
        }
    }

    private static ReportZipException rollback(Path temporary, ReportZipException failure) {
        if (temporary == null) {
            return failure;
        }
        try {
            Files.deleteIfExists(temporary);
            return failure;
        } catch (IOException | SecurityException cleanupFailure) {
            ReportZipException rollback = failure(
                    ReportZipCode.ROLLBACK_FAILED,
                    null,
                    "Temporary report archive could not be safely removed",
                    cleanupFailure);
            rollback.addSuppressed(failure);
            return rollback;
        }
    }

    private static Sha256Checksum checksum(byte[] contents) {
        return new Sha256Checksum(HexFormat.of().formatHex(sha256().digest(contents)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 implementation is unavailable", exception);
        }
    }

    private static ReportZipException changed(PackagePlanEntry entry) {
        return failure(
                ReportZipCode.ENTRY_CHANGED,
                entry.archivePath(),
                "Workspace artifact bytes differ from the trusted plan",
                null);
    }

    private static ReportZipException failure(
            ReportZipCode code, String entry, String message, Throwable cause) {
        return ReportZipValidator.failure(code, entry, message, cause);
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long maximum;
        private long written;

        private BoundedOutputStream(OutputStream delegate, long maximum) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            delegate.write(value);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            requireCapacity(length);
            delegate.write(bytes, offset, length);
            written += length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void requireCapacity(int length) throws IOException {
            if (length > maximum - written) {
                throw new IOException("Encoded report archive exceeds the product limit");
            }
        }
    }

    private static final class WriteProgress {
        private final int totalEntries;
        private final long totalBytes;
        private final ReportZipProgressListener listener;
        private int completedEntries;
        private long processedBytes;

        private WriteProgress(
                ReportPackagePlan plan, ReportZipProgressListener listener) {
            totalEntries = plan.entries().size();
            totalBytes = plan.totalUncompressedBytes();
            this.listener = listener;
        }

        private void addBytes(long bytes) {
            processedBytes = Math.addExact(processedBytes, bytes);
            publish();
        }

        private void completeEntry() {
            completedEntries++;
            publish();
        }

        private void publish() {
            listener.onProgress(new ReportZipProgress(
                    completedEntries, totalEntries, processedBytes, totalBytes));
        }
    }

    private static final class PrivateOutputUnsupportedException extends IOException {
        private static final long serialVersionUID = 1L;

        private PrivateOutputUnsupportedException(IOException cause) {
            super(cause.getMessage(), cause);
        }
    }
}
