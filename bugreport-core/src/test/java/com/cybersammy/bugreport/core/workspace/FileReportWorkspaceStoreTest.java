package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.core.session.ReportSessionId;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
            public void writeNewMarker(Path path, byte[] contents) throws IOException {
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
    void reportsRollbackFailureWithoutDeletingUnknownContents() {
        Path root = temporaryDirectory.resolve("workspaces").toAbsolutePath();
        Path workspace = root.resolve(FIRST.toString());
        WorkspaceFileOperations failing = new DelegatingWorkspaceFileOperations() {
            @Override
            public void writeNewMarker(Path path, byte[] contents) throws IOException {
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
        public void createDirectory(Path path) throws IOException {
            NioWorkspaceFileOperations.INSTANCE.createDirectory(path);
        }

        @Override
        public void writeNewMarker(Path path, byte[] contents) throws IOException {
            NioWorkspaceFileOperations.INSTANCE.writeNewMarker(path, contents);
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
}
