package com.cybersammy.bugreport.core.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Production NIO operations for report-workspace creation. */
final class NioWorkspaceFileOperations implements WorkspaceFileOperations {
    static final NioWorkspaceFileOperations INSTANCE = new NioWorkspaceFileOperations();

    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");
    private static final Set<AclEntryPermission> OWNER_PERMISSIONS =
            Set.copyOf(EnumSet.allOf(AclEntryPermission.class));

    private NioWorkspaceFileOperations() {}

    @Override
    public BasicFileAttributes readAttributes(Path path, boolean followLinks) throws IOException {
        return Files.readAttributes(
                path,
                BasicFileAttributes.class,
                followLinks ? new LinkOption[0] : new LinkOption[] {LinkOption.NOFOLLOW_LINKS});
    }

    @Override
    public Path realPath(Path path, boolean followLinks) throws IOException {
        return followLinks
                ? path.toRealPath()
                : path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public FileStore fileStore(Path path) throws IOException {
        return Files.getFileStore(path);
    }

    @Override
    public void createPrivateDirectory(Path path) throws IOException {
        PermissionModel model = permissionModel(path.getParent());
        CreatedEntryIdentity created = null;
        try {
            if (model == PermissionModel.POSIX) {
                Files.createDirectory(
                        path,
                        PosixFilePermissions.asFileAttribute(
                                PRIVATE_DIRECTORY_PERMISSIONS));
            } else {
                Files.createDirectory(path);
            }
            created = observeCreatedEntry(path, true);
            enforceAndVerifyPrivateAccess(path, model, true);
        } catch (IOException | RuntimeException exception) {
            if (created != null) {
                rollbackNewEntry(path, created, exception);
            }
            throw exception;
        }
    }

    @Override
    public void writeNewPrivateMarker(Path path, byte[] contents) throws IOException {
        PermissionModel model = permissionModel(path.getParent());
        CreatedEntryIdentity created = null;
        try (FileChannel channel = openPrivateFile(path, model)) {
            created = observeCreatedEntry(path, false);
            enforceAndVerifyPrivateAccess(path, model, false);
            ByteBuffer buffer = ByteBuffer.wrap(contents);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        } catch (IOException | RuntimeException exception) {
            if (created != null) {
                rollbackNewEntry(path, created, exception);
            }
            throw exception;
        }
    }

    @Override
    public FileChannel openNewPrivateFile(Path path) throws IOException {
        PermissionModel model = permissionModel(path.getParent());
        CreatedEntryIdentity created = null;
        try {
            FileChannel channel = openPrivateFile(path, model);
            try {
                created = observeCreatedEntry(path, false);
                enforceAndVerifyPrivateAccess(path, model, false);
                return channel;
            } catch (IOException | RuntimeException exception) {
                channel.close();
                throw exception;
            }
        } catch (IOException | RuntimeException exception) {
            if (created != null) {
                rollbackNewEntry(path, created, exception);
            }
            throw exception;
        }
    }

    @Override
    public FileChannel openExistingPrivateFile(Path path) throws IOException {
        return FileChannel.open(
                path,
                Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
    }

    @Override
    public List<Path> listDirectChildren(Path directory, int maximumEntries)
            throws IOException {
        if (maximumEntries < 0) {
            throw new IllegalArgumentException("Maximum directory entries must be non-negative");
        }
        List<Path> entries = new ArrayList<>(Math.min(maximumEntries, 64));
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                if (entries.size() == maximumEntries) {
                    throw new IOException("Workspace contains more entries than expected");
                }
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    @Override
    public void verifyPrivateDirectory(Path path) throws IOException {
        verifyPrivateAccess(path, permissionModel(path.getParent()), true);
    }

    @Override
    public void verifyPrivateFile(Path path) throws IOException {
        verifyPrivateAccess(path, permissionModel(path.getParent()), false);
    }

    @Override
    public void replaceAtomically(Path source, Path target) throws IOException {
        Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static FileChannel openPrivateFile(Path path, PermissionModel model)
            throws IOException {
        Set<StandardOpenOption> options =
                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        if (model == PermissionModel.POSIX) {
            return FileChannel.open(
                    path,
                    options,
                    PosixFilePermissions.asFileAttribute(PRIVATE_FILE_PERMISSIONS));
        }
        return FileChannel.open(path, options);
    }

    private static PermissionModel permissionModel(Path parent) throws IOException {
        FileStore store = Files.getFileStore(parent);
        if (store.supportsFileAttributeView(PosixFileAttributeView.class)) {
            return PermissionModel.POSIX;
        }
        if (store.supportsFileAttributeView(AclFileAttributeView.class)) {
            return PermissionModel.ACL;
        }
        return PermissionModel.BEST_EFFORT;
    }

    private static void enforceAndVerifyPrivateAccess(
            Path path, PermissionModel model, boolean directory) throws IOException {
        switch (model) {
            case POSIX -> verifyPosixPermissions(path, directory);
            case ACL -> {
                applyOwnerOnlyAcl(path, directory);
                verifyOwnerOnlyAcl(path, directory);
            }
            case BEST_EFFORT -> {
                // No portable permission model exists for this filesystem. Containment,
                // identity, and no-redirection checks still apply at the store boundary.
            }
        }
    }

    private static void verifyPrivateAccess(
            Path path, PermissionModel model, boolean directory) throws IOException {
        switch (model) {
            case POSIX -> verifyPosixPermissions(path, directory);
            case ACL -> verifyOwnerOnlyAcl(path, directory);
            case BEST_EFFORT -> {
                // No portable permission model exists for this filesystem.
            }
        }
    }

    private static void verifyPosixPermissions(Path path, boolean directory)
            throws IOException {
        Set<PosixFilePermission> expected = directory
                ? PRIVATE_DIRECTORY_PERMISSIONS
                : PRIVATE_FILE_PERMISSIONS;
        Set<PosixFilePermission> actual = Files.getPosixFilePermissions(
                path, LinkOption.NOFOLLOW_LINKS);
        if (!actual.equals(expected)) {
            throw new IOException("Owner-only POSIX permissions were not applied");
        }
    }

    private static void applyOwnerOnlyAcl(Path path, boolean directory) throws IOException {
        AclFileAttributeView view = aclView(path);
        UserPrincipal owner = view.getOwner();
        AclEntry.Builder builder = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(OWNER_PERMISSIONS);
        if (directory) {
            builder.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
        }
        view.setAcl(List.of(builder.build()));
    }

    private static void verifyOwnerOnlyAcl(Path path, boolean directory) throws IOException {
        AclFileAttributeView view = aclView(path);
        UserPrincipal owner = view.getOwner();
        List<AclEntry> entries = view.getAcl();
        if (entries.isEmpty()
                || entries.stream().anyMatch(entry -> entry.type() != AclEntryType.ALLOW
                        || !entry.principal().equals(owner)
                        || !entry.permissions().containsAll(OWNER_PERMISSIONS))
                || (directory
                        && entries.stream().noneMatch(entry ->
                                entry.flags().contains(AclEntryFlag.DIRECTORY_INHERIT)
                                        && entry.flags().contains(AclEntryFlag.FILE_INHERIT)))) {
            throw new IOException("Owner-only filesystem ACL was not applied");
        }
    }

    private static AclFileAttributeView aclView(Path path) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(
                path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("Filesystem advertised ACL support but exposed no ACL view");
        }
        return view;
    }

    private static CreatedEntryIdentity observeCreatedEntry(Path path, boolean directory)
            throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink()
                || attributes.isOther()
                || attributes.isDirectory() != directory
                || attributes.isRegularFile() == directory) {
            throw new IOException("New private workspace entry has an unexpected type");
        }
        return new CreatedEntryIdentity(
                path.toRealPath(LinkOption.NOFOLLOW_LINKS),
                attributes.fileKey(),
                attributes.creationTime(),
                directory);
    }

    private static void rollbackNewEntry(
            Path path, CreatedEntryIdentity original, Throwable originalFailure) {
        try {
            CreatedEntryIdentity current = observeCreatedEntry(path, original.directory());
            if (!original.sameEntry(current)) {
                throw new IOException(
                        "New private workspace entry changed before permission rollback");
            }
            Files.delete(path);
        } catch (IOException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public byte[] readBounded(Path path, int maximumBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return input.readNBytes(maximumBytes + 1);
        }
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
        return Files.deleteIfExists(path);
    }

    private enum PermissionModel {
        POSIX,
        ACL,
        BEST_EFFORT
    }

    private record CreatedEntryIdentity(
            Path realPath, Object fileKey, FileTime creationTime, boolean directory) {
        private boolean sameEntry(CreatedEntryIdentity other) {
            if (fileKey != null && other.fileKey != null) {
                return fileKey.equals(other.fileKey) && realPath.equals(other.realPath);
            }
            if ((fileKey == null) != (other.fileKey == null)) {
                return false;
            }
            return realPath.equals(other.realPath)
                    && creationTime.equals(other.creationTime)
                    && directory == other.directory;
        }
    }
}
