package com.cybersammy.bugreport.core.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.BugReportProvider;
import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.identifier.CategoryId;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.identifier.ProviderId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.CategorySpecification;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.ProviderSpecification;
import com.cybersammy.bugreport.api.specification.RelativePath;
import com.cybersammy.bugreport.api.version.ProviderVersion;
import com.cybersammy.bugreport.core.registry.DiscoveredProvider;
import com.cybersammy.bugreport.core.registry.ProviderRegistry;
import com.cybersammy.bugreport.core.registry.ProviderRegistrySnapshot;
import com.cybersammy.bugreport.core.session.ReportSessionId;
import com.cybersammy.bugreport.core.source.ApprovedSourceRoots;
import com.cybersammy.bugreport.core.source.CategorySourcePlanner;
import com.cybersammy.bugreport.core.source.PlannedSourceFile;
import com.cybersammy.bugreport.core.source.ResolvedSourceFile;
import com.cybersammy.bugreport.core.source.SourcePathResolver;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceSourceCollectorTest {
    private static final ProviderId PROVIDER_ID = ProviderId.parse("example");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.parse("1.0.0");
    private static final CategoryId CATEGORY_ID = CategoryId.of("general");
    private static final ReportSessionId SESSION_ID =
            ReportSessionId.parse("11111111-1111-1111-1111-111111111111");

    @TempDir Path temporaryDirectory;

    @Test
    void streamsExactBytesAndPublishesCanonicalSha256Metadata() throws IOException {
        Fixture fixture = fixture("hello", 1024, NioWorkspaceFileOperations.INSTANCE);

        CollectedSourceFile collected = WorkspaceSourceCollector.collect(
                fixture.planned(), fixture.roots(), fixture.workspace());

        assertEquals(5, collected.byteCount());
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                collected.checksum().value());
        assertTrue(collected.artifactName().matches("source-[0-9a-f]{64}\\.data"));
        assertEquals(
                "hello",
                Files.readString(fixture.workspace().directory().resolve(collected.artifactName())));
        assertEquals(1, collected.provenances().size());
        assertEquals(DiagnosticContentType.TEXT, collected.contentType());
        assertWorkspaceEntries(fixture.workspace(), collected.artifactName());
    }

    @Test
    void enforcesEffectiveLimitAgainstFileGrowthAndRemovesPartialOutput() throws IOException {
        Fixture fixture = fixture("a", 2, NioWorkspaceFileOperations.INSTANCE);
        AtomicBoolean expanded = new AtomicBoolean();

        SourceCopyException failure = assertThrows(
                SourceCopyException.class,
                () -> WorkspaceSourceCollector.collect(
                        fixture.planned(),
                        fixture.roots(),
                        fixture.workspace(),
                        copied -> {
                            if (expanded.compareAndSet(false, true)) {
                                Files.writeString(
                                        fixture.source(),
                                        "overflow",
                                        StandardOpenOption.APPEND);
                            }
                        }));

        assertEquals(SourceCopyCode.BYTE_LIMIT_EXCEEDED, failure.code());
        assertEquals(2, fixture.planned().maximumBytes());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void rejectsSourceChangedBeforeOrDuringStreaming() throws IOException {
        Fixture changedBefore = fixture("before", 1024, NioWorkspaceFileOperations.INSTANCE);
        Files.writeString(changedBefore.source(), "changed-before");

        SourceCopyException beforeFailure = assertThrows(
                SourceCopyException.class,
                () -> WorkspaceSourceCollector.collect(
                        changedBefore.planned(), changedBefore.roots(), changedBefore.workspace()));
        assertEquals(SourceCopyCode.SOURCE_CHANGED, beforeFailure.code());
        assertOnlyMarker(changedBefore.workspace());

        Fixture changedDuring = fixture(
                "x".repeat(WorkspaceSourceCollector.COPY_BUFFER_BYTES * 2),
                WorkspaceSourceCollector.COPY_BUFFER_BYTES * 3L,
                NioWorkspaceFileOperations.INSTANCE);
        AtomicBoolean changed = new AtomicBoolean();
        SourceCopyException duringFailure = assertThrows(
                SourceCopyException.class,
                () -> WorkspaceSourceCollector.collect(
                        changedDuring.planned(),
                        changedDuring.roots(),
                        changedDuring.workspace(),
                        copied -> {
                            if (changed.compareAndSet(false, true)) {
                                Files.writeString(
                                        changedDuring.source(),
                                        "replacement",
                                        StandardOpenOption.TRUNCATE_EXISTING);
                            }
                        }));
        assertEquals(SourceCopyCode.SOURCE_CHANGED, duringFailure.code());
        assertOnlyMarker(changedDuring.workspace());
    }

    @Test
    void rejectsOpenedHandleWhoseIdentityDiffersFromTheRevalidatedPath()
            throws IOException {
        Fixture fixture = fixture("file-a", 1024, NioWorkspaceFileOperations.INSTANCE);
        Path alternate = fixture.source().resolveSibling("alternate.log");
        Files.writeString(alternate, "file-b");
        ResolvedSourceFile alternateObservation = SourcePathResolver.resolveRegularFile(
                fixture.roots(),
                LogicalRoot.GAME_LOGS,
                RelativePath.of("alternate.log"));
        SourceReadOperations mismatchedHandle = (roots, planned) -> new SourceReadHandle(
                FileChannel.open(
                        alternate,
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS),
                alternateObservation,
                SourceReadIdentityAssurance.HANDLE_STABILIZED);

        SourceCopyException failure = assertThrows(
                SourceCopyException.class,
                () -> WorkspaceSourceCollector.collect(
                        fixture.planned(),
                        fixture.roots(),
                        fixture.workspace(),
                        mismatchedHandle,
                        copied -> {}));

        assertEquals(SourceCopyCode.SOURCE_CHANGED, failure.code());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsSourceHandlePreventsPathReplacementUntilClosed() throws IOException {
        Fixture fixture = fixture("locked", 1024, NioWorkspaceFileOperations.INSTANCE);
        ResolvedSourceFile planned = fixture.planned().file();

        try (SourceReadHandle handle =
                NioSourceReadOperations.INSTANCE.open(fixture.roots(), planned)) {
            assertEquals(
                    SourceReadIdentityAssurance.HANDLE_STABILIZED,
                    handle.identityAssurance());
            assertThrows(
                    IOException.class,
                    () -> Files.move(
                            fixture.source(),
                            fixture.source().resolveSibling("replacement-window.log")));
        }
    }

    @Test
    void rejectsChangedWorkspaceAndExistingDestinationWithoutOverwriting() throws IOException {
        Fixture changedWorkspace = fixture("workspace", 1024, NioWorkspaceFileOperations.INSTANCE);
        Path marker = changedWorkspace.workspace().directory().resolve(
                FileReportWorkspaceStore.MARKER_FILENAME);
        byte[] markerBytes = Files.readAllBytes(marker);
        markerBytes[0] = markerBytes[0] == 'x' ? (byte) 'y' : (byte) 'x';
        Files.write(
                marker,
                markerBytes,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);

        SourceCopyException workspaceFailure = assertThrows(
                SourceCopyException.class,
                () -> WorkspaceSourceCollector.collect(
                        changedWorkspace.planned(),
                        changedWorkspace.roots(),
                        changedWorkspace.workspace()));
        assertEquals(SourceCopyCode.WORKSPACE_CHANGED, workspaceFailure.code());

        Fixture collision = fixture("stable", 1024, NioWorkspaceFileOperations.INSTANCE);
        CollectedSourceFile first = WorkspaceSourceCollector.collect(
                collision.planned(), collision.roots(), collision.workspace());
        byte[] original = Files.readAllBytes(
                collision.workspace().directory().resolve(first.artifactName()));

        SourceCopyException collisionFailure = assertThrows(
                SourceCopyException.class,
                () -> WorkspaceSourceCollector.collect(
                        collision.planned(), collision.roots(), collision.workspace()));
        assertEquals(SourceCopyCode.DESTINATION_ALREADY_EXISTS, collisionFailure.code());
        assertArrayEquals(
                original,
                Files.readAllBytes(
                        collision.workspace().directory().resolve(first.artifactName())));
        assertWorkspaceEntries(collision.workspace(), first.artifactName());
    }

    @Test
    void failsWhenAtomicPublicationIsUnavailableAndCleansTemporaryFile() throws IOException {
        WorkspaceFileOperations noAtomicMove = new DelegatingWorkspaceFileOperations() {
            @Override
            public void replaceAtomically(Path source, Path target) throws IOException {
                throw new AtomicMoveNotSupportedException(
                        source.toString(), target.toString(), "injected");
            }
        };
        Fixture fixture = fixture("atomic", 1024, noAtomicMove);

        SourceCopyException failure = assertThrows(
                SourceCopyException.class,
                () -> WorkspaceSourceCollector.collect(
                        fixture.planned(), fixture.roots(), fixture.workspace()));

        assertEquals(SourceCopyCode.ATOMIC_MOVE_UNSUPPORTED, failure.code());
        assertOnlyMarker(fixture.workspace());
    }

    @Test
    void createsOwnerOnlyCollectedFileOnPosix() throws IOException {
        Assumptions.assumeTrue(Files.getFileStore(temporaryDirectory)
                .supportsFileAttributeView(PosixFileAttributeView.class));
        Fixture fixture = fixture("private", 1024, NioWorkspaceFileOperations.INSTANCE);

        CollectedSourceFile collected = WorkspaceSourceCollector.collect(
                fixture.planned(), fixture.roots(), fixture.workspace());

        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(
                        fixture.workspace().directory().resolve(collected.artifactName()),
                        LinkOption.NOFOLLOW_LINKS));
    }

    private Fixture fixture(
            String contents, long maximumBytes, WorkspaceFileOperations workspaceFiles)
            throws IOException {
        Path game = Files.createDirectory(temporaryDirectory.resolve("game-" + UUIDSuffix.next()));
        Files.createDirectory(game.resolve("logs"));
        Files.createDirectory(game.resolve("crash-reports"));
        Files.createDirectory(game.resolve("config"));
        Path source = game.resolve("logs/latest.log");
        Files.writeString(source, contents);
        ApprovedSourceRoots roots = ApprovedSourceRoots.forGameDirectory(game.toAbsolutePath());
        DiagnosticSourceSpecification declaration = DiagnosticSourceSpecification.exactFile(
                        DiagnosticSourceId.of("latest"),
                        LogicalRoot.GAME_LOGS,
                        RelativePath.of("latest.log"))
                .labelKey(LocalizationKey.of("example.source.latest"))
                .privacy(PrivacyClassification.PERSONAL)
                .contentType(DiagnosticContentType.TEXT)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .constraints(CollectionConstraints.builder()
                        .maxBytesPerFile(maximumBytes)
                        .maxTotalBytes(maximumBytes)
                        .build())
                .build();
        ProviderSpecification specification = ProviderSpecification.builder(
                        PROVIDER_ID,
                        PROVIDER_VERSION,
                        LocalizationKey.of("example.provider"))
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .addSource(declaration)
                .addCategory(CategorySpecification.builder(
                                CATEGORY_ID,
                                LocalizationKey.of("example.category.general"))
                        .useSource(declaration.id())
                        .build())
                .build();
        PlannedSourceFile planned = new CategorySourcePlanner(
                        registry(specification), roots, SupportedSide.PHYSICAL_CLIENT)
                .plan(PROVIDER_ID, CATEGORY_ID)
                .files()
                .getFirst();
        Path workspaceRoot = temporaryDirectory
                .resolve("workspaces-" + UUIDSuffix.next())
                .toAbsolutePath();
        ReportWorkspace workspace =
                new FileReportWorkspaceStore(workspaceRoot, workspaceFiles).create(SESSION_ID);
        return new Fixture(source, roots, planned, workspace);
    }

    private static ProviderRegistrySnapshot registry(ProviderSpecification specification) {
        BugReportProvider provider = new BugReportProvider() {
            @Override
            public String providerId() {
                return specification.id().value();
            }

            @Override
            public String providerVersion() {
                return specification.version().value();
            }

            @Override
            public Optional<ProviderSpecification> specification() {
                return Optional.of(specification);
            }
        };
        return ProviderRegistry.createSnapshot(List.of(new DiscoveredProvider(
                specification.id().namespace(), "WorkspaceSourceCollectorFixture", provider)));
    }

    private static void assertOnlyMarker(ReportWorkspace workspace) throws IOException {
        assertEquals(
                List.of(FileReportWorkspaceStore.MARKER_FILENAME),
                entryNames(workspace));
    }

    private static void assertWorkspaceEntries(
            ReportWorkspace workspace, String artifactName) throws IOException {
        assertEquals(
                List.of(FileReportWorkspaceStore.MARKER_FILENAME, artifactName).stream()
                        .sorted()
                        .toList(),
                entryNames(workspace));
        assertFalse(Files.exists(workspace.directory().resolve(".part")));
    }

    private static List<String> entryNames(ReportWorkspace workspace) throws IOException {
        try (var entries = Files.list(workspace.directory())) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private record Fixture(
            Path source,
            ApprovedSourceRoots roots,
            PlannedSourceFile planned,
            ReportWorkspace workspace) {}

    private static final class UUIDSuffix {
        private static int next;

        private UUIDSuffix() {}

        private static synchronized int next() {
            return next++;
        }
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
}
