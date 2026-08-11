package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileReportWorkspaceStoreTest {
    private static final ReportSessionId FIRST =
            ReportSessionId.parse("11111111-1111-1111-1111-111111111111");
    private static final ReportSessionId SECOND =
            ReportSessionId.parse("22222222-2222-2222-2222-222222222222");

    @TempDir Path temporaryDirectory;

    @Test
    void createsDeterministicIsolatedWorkspacesWithOwnershipMarkers() throws IOException {
        Path root = temporaryDirectory.resolve("workspaces");
        FileReportWorkspaceStore store = new FileReportWorkspaceStore(root.toAbsolutePath());

        ReportWorkspace first = store.create(FIRST);
        ReportWorkspace second = store.create(SECOND);

        assertEquals(root.resolve(FIRST.toString()).toRealPath(), first.directory());
        assertEquals(root.resolve(SECOND.toString()).toRealPath(), second.directory());
        assertEquals(FIRST, first.sessionId());
        assertEquals(SECOND, second.sessionId());
        assertNotEquals(first.directory(), second.directory());
        assertArrayEquals(
                FileReportWorkspaceStore.markerContents(FIRST),
                Files.readAllBytes(first.directory().resolve(
                        FileReportWorkspaceStore.MARKER_FILENAME)));
        assertArrayEquals(
                FileReportWorkspaceStore.markerContents(SECOND),
                Files.readAllBytes(second.directory().resolve(
                        FileReportWorkspaceStore.MARKER_FILENAME)));
    }

    @Test
    void createsOwnerOnlyPosixDirectoriesAndMarkerWhenSupported() throws IOException {
        Assumptions.assumeTrue(Files.getFileStore(temporaryDirectory)
                .supportsFileAttributeView(PosixFileAttributeView.class));
        Path firstRootSegment = temporaryDirectory.resolve("private");
        Path root = firstRootSegment.resolve("workspaces");

        ReportWorkspace workspace =
                new FileReportWorkspaceStore(root.toAbsolutePath()).create(FIRST);

        Set<PosixFilePermission> privateDirectory =
                PosixFilePermissions.fromString("rwx------");
        assertEquals(
                privateDirectory,
                Files.getPosixFilePermissions(
                        firstRootSegment, LinkOption.NOFOLLOW_LINKS));
        assertEquals(
                privateDirectory,
                Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS));
        assertEquals(
                privateDirectory,
                Files.getPosixFilePermissions(
                        workspace.directory(), LinkOption.NOFOLLOW_LINKS));
        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(
                        workspace.directory().resolve(
                                FileReportWorkspaceStore.MARKER_FILENAME),
                        LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void neverReusesExistingSessionPathOrTrustsItsMarker() throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("workspaces"));
        Path existing = Files.createDirectory(root.resolve(FIRST.toString()));
        Path marker = existing.resolve(FileReportWorkspaceStore.MARKER_FILENAME);
        byte[] foreignMarker = "foreign-or-corrupt\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(marker, foreignMarker);

        WorkspaceCreationException failure = assertThrows(
                WorkspaceCreationException.class,
                () -> new FileReportWorkspaceStore(root.toAbsolutePath()).create(FIRST));

        assertEquals(WorkspaceCreationCode.WORKSPACE_ALREADY_EXISTS, failure.code());
        assertEquals(FIRST, failure.sessionId());
        assertArrayEquals(foreignMarker, Files.readAllBytes(marker));
    }

    @Test
    void rejectsRelativeAndRedirectedRootsBeforeCreatingWorkspace() throws IOException {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FileReportWorkspaceStore(Path.of("relative/workspaces")));

        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Path redirected = temporaryDirectory.resolve("redirected");
        try {
            Files.createSymbolicLink(redirected, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }

        WorkspaceCreationException failure = assertThrows(
                WorkspaceCreationException.class,
                () -> new FileReportWorkspaceStore(
                                redirected.resolve("workspaces").toAbsolutePath())
                        .create(FIRST));

        assertEquals(WorkspaceCreationCode.ROOT_INVALID, failure.code());
        assertFalse(Files.exists(outside.resolve("workspaces")));
    }

    @Test
    void removesOnlyItsNewDirectoryWhenMarkerCreationFails() {
        Path root = temporaryDirectory.resolve("workspaces").toAbsolutePath();
        WorkspaceFileOperations failing = new DelegatingWorkspaceFileOperations() {
            @Override
            public void writeNewPrivateMarker(Path path, byte[] contents) throws IOException {
                throw new IOException("injected marker failure");
            }
        };

        WorkspaceCreationException failure = assertThrows(
                WorkspaceCreationException.class,
                () -> new FileReportWorkspaceStore(root, failing).create(FIRST));

        assertEquals(WorkspaceCreationCode.IO_FAILURE, failure.code());
        assertFalse(Files.exists(root.resolve(FIRST.toString())));
        assertTrue(Files.isDirectory(root));
    }

    @Test
    void failsWhenPrivateWorkspaceCreationCannotBeEnforced() throws IOException {
        Path root = Files.createDirectory(
                temporaryDirectory.resolve("workspaces")).toAbsolutePath();
        WorkspaceFileOperations failing = new DelegatingWorkspaceFileOperations() {
            @Override
            public void createPrivateDirectory(Path path) throws IOException {
                if (path.getFileName().toString().equals(FIRST.toString())) {
                    throw new IOException("injected restrictive-permission failure");
                }
                super.createPrivateDirectory(path);
            }
        };

        WorkspaceCreationException failure = assertThrows(
                WorkspaceCreationException.class,
                () -> new FileReportWorkspaceStore(root, failing).create(FIRST));

        assertEquals(WorkspaceCreationCode.IO_FAILURE, failure.code());
        assertFalse(Files.exists(root.resolve(FIRST.toString())));
    }

    @Test
    void reportsRollbackFailureWithoutDeletingUnknownContents() {
        Path root = temporaryDirectory.resolve("workspaces").toAbsolutePath();
        Path workspace = root.resolve(FIRST.toString());
        WorkspaceFileOperations failing = new DelegatingWorkspaceFileOperations() {
            @Override
            public void writeNewPrivateMarker(Path path, byte[] contents) throws IOException {
                Files.writeString(workspace.resolve("unexpected"), "external");
                throw new IOException("injected marker failure");
            }
        };

        WorkspaceCreationException failure = assertThrows(
                WorkspaceCreationException.class,
                () -> new FileReportWorkspaceStore(root, failing).create(FIRST));

        assertEquals(WorkspaceCreationCode.ROLLBACK_FAILED, failure.code());
        assertTrue(Files.exists(workspace.resolve("unexpected")));
    }

    @Test
    void removesOnlyExplicitlyConfirmedAbandonedWorkspaces() throws IOException {
        Path root = temporaryDirectory.resolve("workspaces").toAbsolutePath();
        FileReportWorkspaceStore creator = new FileReportWorkspaceStore(root);
        ReportWorkspace abandoned = creator.create(FIRST);
        ReportWorkspace retained = creator.create(SECOND);
        Path artifact = abandoned.directory().resolve("source-" + "a".repeat(64) + ".data");
        try (FileChannel output = abandoned.files().openNewPrivateFile(artifact)) {
            output.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        }
        Path reviewCopy = abandoned.directory().resolve(
                ".review-original-00000000-0000-4000-8000-000000000099-"
                        + "source-" + "a".repeat(64) + ".data");
        try (FileChannel output = abandoned.files().openNewPrivateFile(reviewCopy)) {
            output.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        }

        AbandonedWorkspaceCleanupResult result =
                new FileReportWorkspaceStore(root).cleanupAbandoned(Set.of(FIRST));

        assertEquals(1, result.removedCount());
        assertEquals(
                AbandonedWorkspaceCleanupCode.REMOVED,
                result.outcomes().getFirst().code());
        assertFalse(Files.exists(abandoned.directory()));
        assertTrue(Files.isDirectory(retained.directory()));
    }

    @Test
    void refusesToRemoveWorkspaceActiveInThisStoreInstance() {
        Path root = temporaryDirectory.resolve("workspaces").toAbsolutePath();
        FileReportWorkspaceStore store = new FileReportWorkspaceStore(root);
        ReportWorkspace workspace = store.create(FIRST);

        AbandonedWorkspaceCleanupResult result = store.cleanupAbandoned(Set.of(FIRST));

        assertEquals(
                AbandonedWorkspaceCleanupCode.ACTIVE_SESSION,
                result.outcomes().getFirst().code());
        assertTrue(Files.isDirectory(workspace.directory()));
    }

    @Test
    void quarantinesInvalidMarkerAndUnknownEntryWithoutDeletingEither() throws IOException {
        Path root = temporaryDirectory.resolve("workspaces").toAbsolutePath();
        FileReportWorkspaceStore creator = new FileReportWorkspaceStore(root);
        ReportWorkspace invalidMarker = creator.create(FIRST);
        ReportWorkspace unknownEntry = creator.create(SECOND);
        Files.writeString(
                invalidMarker.directory().resolve(FileReportWorkspaceStore.MARKER_FILENAME),
                "invalid\n");
        Path unknown = unknownEntry.directory().resolve("foreign.txt");
        Files.writeString(unknown, "do not delete");

        LinkedHashSet<ReportSessionId> reversed = new LinkedHashSet<>();
        reversed.add(SECOND);
        reversed.add(FIRST);
        AbandonedWorkspaceCleanupResult result =
                new FileReportWorkspaceStore(root).cleanupAbandoned(reversed);

        assertEquals(List.of(FIRST, SECOND), result.outcomes().stream()
                .map(AbandonedWorkspaceCleanupOutcome::sessionId)
                .toList());
        assertEquals(AbandonedWorkspaceCleanupCode.MARKER_INVALID, result.outcomes().get(0).code());
        assertEquals(
                AbandonedWorkspaceCleanupCode.UNEXPECTED_ENTRY,
                result.outcomes().get(1).code());
        assertTrue(Files.exists(invalidMarker.directory()));
        assertEquals("do not delete", Files.readString(unknown));
    }

    @Test
    void reportsMissingWorkspaceIdempotently() throws IOException {
        Path root = temporaryDirectory.resolve("workspaces").toAbsolutePath();
        FileReportWorkspaceStore store = new FileReportWorkspaceStore(root);

        AbandonedWorkspaceCleanupResult first = store.cleanupAbandoned(Set.of(FIRST));
        store.create(SECOND);
        AbandonedWorkspaceCleanupResult second = store.cleanupAbandoned(Set.of(FIRST));

        assertEquals(AbandonedWorkspaceCleanupCode.NOT_FOUND, first.outcomes().getFirst().code());
        assertEquals(AbandonedWorkspaceCleanupCode.NOT_FOUND, second.outcomes().getFirst().code());
    }

    @Test
    void rejectsRedirectedArtifactWithoutFollowingOrDeletingIt() throws IOException {
        Path root = temporaryDirectory.resolve("workspaces").toAbsolutePath();
        ReportWorkspace workspace = new FileReportWorkspaceStore(root).create(FIRST);
        Path outside = temporaryDirectory.resolve("outside.txt");
        Files.writeString(outside, "outside");
        Path redirected = workspace.directory().resolve("source-" + "b".repeat(64) + ".data");
        try {
            Files.createSymbolicLink(redirected, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }

        AbandonedWorkspaceCleanupResult result =
                new FileReportWorkspaceStore(root).cleanupAbandoned(Set.of(FIRST));

        assertEquals(
                AbandonedWorkspaceCleanupCode.WORKSPACE_UNSAFE,
                result.outcomes().getFirst().code());
        assertEquals("outside", Files.readString(outside));
        assertTrue(Files.isSymbolicLink(redirected));
    }

    @Test
    void quarantinesMarkerSubstitutionDuringRevalidation() throws IOException {
        Path root = temporaryDirectory.resolve("workspaces").toAbsolutePath();
        ReportWorkspace workspace = new FileReportWorkspaceStore(root).create(FIRST);
        Path marker = workspace.directory().resolve(FileReportWorkspaceStore.MARKER_FILENAME);
        AtomicInteger markerReads = new AtomicInteger();
        WorkspaceFileOperations replacing = new DelegatingWorkspaceFileOperations() {
            private boolean replaced;

            @Override
            public BasicFileAttributes readAttributes(Path path, boolean followLinks)
                    throws IOException {
                BasicFileAttributes attributes = super.readAttributes(path, followLinks);
                return replaced && path.equals(marker) && !followLinks
                        ? new ReplacedFileAttributes(attributes)
                        : attributes;
            }

            @Override
            public byte[] readBounded(Path path, int maximumBytes) throws IOException {
                byte[] contents = super.readBounded(path, maximumBytes);
                if (path.equals(marker) && markerReads.incrementAndGet() == 2) {
                    Files.delete(marker);
                    NioWorkspaceFileOperations.INSTANCE.writeNewPrivateMarker(marker, contents);
                    replaced = true;
                }
                return contents;
            }
        };

        AbandonedWorkspaceCleanupResult result =
                new FileReportWorkspaceStore(root, replacing).cleanupAbandoned(Set.of(FIRST));

        assertEquals(
                AbandonedWorkspaceCleanupCode.ENTRY_CHANGED,
                result.outcomes().getFirst().code());
        assertTrue(Files.isDirectory(workspace.directory()));
        assertTrue(Files.isRegularFile(marker));
    }

    private static class DelegatingWorkspaceFileOperations
            implements WorkspaceFileOperations {
        @Override
        public BasicFileAttributes readAttributes(Path path, boolean followLinks)
                throws IOException {
            return NioWorkspaceFileOperations.INSTANCE.readAttributes(path, followLinks);
        }

        @Override
        public Path realPath(Path path, boolean followLinks) throws IOException {
            return NioWorkspaceFileOperations.INSTANCE.realPath(path, followLinks);
        }

        @Override
        public FileStore fileStore(Path path) throws IOException {
            return NioWorkspaceFileOperations.INSTANCE.fileStore(path);
        }

        @Override
        public void createPrivateDirectory(Path path) throws IOException {
            NioWorkspaceFileOperations.INSTANCE.createPrivateDirectory(path);
        }

        @Override
        public void writeNewPrivateMarker(Path path, byte[] contents) throws IOException {
            NioWorkspaceFileOperations.INSTANCE.writeNewPrivateMarker(path, contents);
        }

        @Override
        public FileChannel openNewPrivateFile(Path path) throws IOException {
            return NioWorkspaceFileOperations.INSTANCE.openNewPrivateFile(path);
        }

        @Override
        public FileChannel openExistingPrivateFile(Path path) throws IOException {
            return NioWorkspaceFileOperations.INSTANCE.openExistingPrivateFile(path);
        }

        @Override
        public java.util.List<Path> listDirectChildren(Path directory, int maximumEntries)
                throws IOException {
            return NioWorkspaceFileOperations.INSTANCE.listDirectChildren(
                    directory, maximumEntries);
        }

        @Override
        public void verifyPrivateDirectory(Path path) throws IOException {
            NioWorkspaceFileOperations.INSTANCE.verifyPrivateDirectory(path);
        }

        @Override
        public void verifyPrivateFile(Path path) throws IOException {
            NioWorkspaceFileOperations.INSTANCE.verifyPrivateFile(path);
        }

        @Override
        public void replaceAtomically(Path source, Path target) throws IOException {
            NioWorkspaceFileOperations.INSTANCE.replaceAtomically(source, target);
        }

        @Override
        public byte[] readBounded(Path path, int maximumBytes) throws IOException {
            return NioWorkspaceFileOperations.INSTANCE.readBounded(path, maximumBytes);
        }

        @Override
        public boolean deleteIfExists(Path path) throws IOException {
            return NioWorkspaceFileOperations.INSTANCE.deleteIfExists(path);
        }
    }

    private record ReplacedFileAttributes(BasicFileAttributes delegate)
            implements BasicFileAttributes {
        @Override
        public FileTime lastModifiedTime() {
            return delegate.lastModifiedTime();
        }

        @Override
        public FileTime lastAccessTime() {
            return delegate.lastAccessTime();
        }

        @Override
        public FileTime creationTime() {
            return delegate.creationTime();
        }

        @Override
        public boolean isRegularFile() {
            return delegate.isRegularFile();
        }

        @Override
        public boolean isDirectory() {
            return delegate.isDirectory();
        }

        @Override
        public boolean isSymbolicLink() {
            return delegate.isSymbolicLink();
        }

        @Override
        public boolean isOther() {
            return delegate.isOther();
        }

        @Override
        public long size() {
            return delegate.size();
        }

        @Override
        public Object fileKey() {
            return "replacement-file-key";
        }
    }
}
