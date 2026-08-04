package com.cybersammy.bugreport.core.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SourcePathResolverTest {
    @TempDir Path temporaryDirectory;

    @Test
    void resolvesNestedRegularFileWithPlanningMetadata() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path file = temporaryDirectory.resolve("logs/archive/client.log");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "diagnostic");

        ResolvedSourceFile resolved =
                SourcePathResolver.resolveRegularFile(
                        roots,
                        LogicalRoot.GAME_LOGS,
                        RelativePath.of("archive/client.log"));

        assertEquals(LogicalRoot.GAME_LOGS, resolved.root());
        assertEquals(RelativePath.of("archive/client.log"), resolved.relativePath());
        assertEquals(file.toRealPath(), resolved.localPath());
        assertEquals(Files.size(file), resolved.observedSize());
    }

    @Test
    void distinguishesMissingRootAndMissingComponentWithoutLeakingAbsolutePath()
            throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.delete(temporaryDirectory.resolve("crash-reports"));

        SourcePathResolutionException missingRoot =
                assertFailure(
                        SourcePathResolutionCode.ROOT_MISSING,
                        () ->
                                SourcePathResolver.resolveRegularFile(
                                        roots,
                                        LogicalRoot.CRASH_REPORTS,
                                        RelativePath.of("crash.txt")));
        SourcePathResolutionException missingComponent =
                assertFailure(
                        SourcePathResolutionCode.COMPONENT_MISSING,
                        () ->
                                SourcePathResolver.resolveRegularFile(
                                        roots,
                                        LogicalRoot.GAME_LOGS,
                                        RelativePath.of("missing/client.log")));

        assertFalse(missingRoot.getMessage().contains(temporaryDirectory.toString()));
        assertFalse(missingComponent.getMessage().contains(temporaryDirectory.toString()));
    }

    @Test
    void rejectsRootThatWasReplacedByARegularFile() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path logs = temporaryDirectory.resolve("logs");
        Files.delete(logs);
        Files.writeString(logs, "not a directory");

        assertFailure(
                SourcePathResolutionCode.ROOT_UNSAFE,
                () ->
                        SourcePathResolver.resolveRegularFile(
                                roots,
                                LogicalRoot.GAME_LOGS,
                                RelativePath.of("latest.log")));
    }

    @Test
    void rejectsIntermediateFileAndDirectoryTargetWithExactCodes() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/not-a-directory"), "file");
        Files.createDirectory(temporaryDirectory.resolve("logs/directory-target"));

        assertFailure(
                SourcePathResolutionCode.COMPONENT_NOT_DIRECTORY,
                () ->
                        SourcePathResolver.resolveRegularFile(
                                roots,
                                LogicalRoot.GAME_LOGS,
                                RelativePath.of("not-a-directory/client.log")));
        assertFailure(
                SourcePathResolutionCode.TARGET_NOT_REGULAR_FILE,
                () ->
                        SourcePathResolver.resolveRegularFile(
                                roots,
                                LogicalRoot.GAME_LOGS,
                                RelativePath.of("directory-target")));
    }

    @Test
    void rejectsSymbolicLinkComponentBeforeFollowingIt() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectory(outside);
        Files.writeString(outside.resolve("secret.log"), "secret");
        Path link = temporaryDirectory.resolve("logs/redirect");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }

        SourcePathResolutionException exception =
                assertFailure(
                        SourcePathResolutionCode.PATH_REDIRECTION,
                        () ->
                                SourcePathResolver.resolveRegularFile(
                                        roots,
                                        LogicalRoot.GAME_LOGS,
                                        RelativePath.of("redirect/secret.log")));

        assertFalse(exception.getMessage().contains(outside.toString()));
    }

    @Test
    void revalidatesRootOnEveryResolution() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path file = temporaryDirectory.resolve("config/example.toml");
        Files.writeString(file, "enabled=true");
        assertTrue(Files.isRegularFile(file));
        SourcePathResolver.resolveRegularFile(
                roots,
                LogicalRoot.MOD_CONFIGURATION,
                RelativePath.of("example.toml"));
        Files.delete(file);
        Files.delete(temporaryDirectory.resolve("config"));

        assertFailure(
                SourcePathResolutionCode.ROOT_MISSING,
                () ->
                        SourcePathResolver.resolveRegularFile(
                                roots,
                                LogicalRoot.MOD_CONFIGURATION,
                        RelativePath.of("example.toml")));
    }

    @Test
    void rejectsEntryWhoseFollowedAndNoFollowCanonicalPathsDiffer() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path file = temporaryDirectory.resolve("logs/client.log");
        Path redirected = temporaryDirectory.resolve("outside.log");
        Files.writeString(file, "diagnostic");
        Files.writeString(redirected, "outside");
        SourcePathInspection inspection =
                new DelegatingInspection() {
                    @Override
                    public Path realPath(Path path, boolean followLinks) throws IOException {
                        if (path.equals(file) && followLinks) {
                            return redirected.toRealPath();
                        }
                        return super.realPath(path, followLinks);
                    }
                };

        assertFailure(
                SourcePathResolutionCode.PATH_REDIRECTION,
                () ->
                        SourcePathResolver.resolveRegularFile(
                                roots,
                                LogicalRoot.GAME_LOGS,
                                RelativePath.of("client.log"),
                                inspection));
    }

    @Test
    void rejectsFileChangedBeforeFinalPlanningRevalidation() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path file = temporaryDirectory.resolve("logs/client.log");
        Files.writeString(file, "first");
        SourcePathInspection inspection =
                new DelegatingInspection() {
                    private int noFollowReads;

                    @Override
                    public BasicFileAttributes readAttributes(Path path, boolean followLinks)
                            throws IOException {
                        if (path.equals(file) && !followLinks && ++noFollowReads == 2) {
                            Files.writeString(file, "replacement-with-different-size");
                        }
                        return super.readAttributes(path, followLinks);
                    }
                };

        assertFailure(
                SourcePathResolutionCode.PATH_CHANGED_DURING_RESOLUTION,
                () ->
                        SourcePathResolver.resolveRegularFile(
                                roots,
                                LogicalRoot.GAME_LOGS,
                                RelativePath.of("client.log"),
                                inspection));
    }

    @Test
    void classifiesDisappearanceDuringRevalidationAsConcurrentChange() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path file = temporaryDirectory.resolve("logs/client.log");
        Files.writeString(file, "diagnostic");
        SourcePathInspection inspection =
                new DelegatingInspection() {
                    private int noFollowReads;

                    @Override
                    public BasicFileAttributes readAttributes(Path path, boolean followLinks)
                            throws IOException {
                        if (path.equals(file) && !followLinks && ++noFollowReads == 2) {
                            Files.delete(file);
                        }
                        return super.readAttributes(path, followLinks);
                    }
                };

        assertFailure(
                SourcePathResolutionCode.PATH_CHANGED_DURING_RESOLUTION,
                () ->
                        SourcePathResolver.resolveRegularFile(
                                roots,
                                LogicalRoot.GAME_LOGS,
                                RelativePath.of("client.log"),
                                inspection));
    }

    private ApprovedSourceRoots createRoots() throws IOException {
        Path logs = Files.createDirectory(temporaryDirectory.resolve("logs"));
        Path crashes = Files.createDirectory(temporaryDirectory.resolve("crash-reports"));
        Path config = Files.createDirectory(temporaryDirectory.resolve("config"));
        return ApprovedSourceRoots.of(logs, crashes, config);
    }

    private static SourcePathResolutionException assertFailure(
            SourcePathResolutionCode code, Runnable operation) {
        SourcePathResolutionException exception =
                assertThrows(SourcePathResolutionException.class, operation::run);
        assertEquals(code, exception.code());
        return exception;
    }

    private static class DelegatingInspection implements SourcePathInspection {
        @Override
        public BasicFileAttributes readAttributes(Path path, boolean followLinks)
                throws IOException {
            return NioSourcePathInspection.INSTANCE.readAttributes(path, followLinks);
        }

        @Override
        public Path realPath(Path path, boolean followLinks) throws IOException {
            return NioSourcePathInspection.INSTANCE.realPath(path, followLinks);
        }

        @Override
        public FileStore fileStore(Path path) throws IOException {
            return NioSourcePathInspection.INSTANCE.fileStore(path);
        }
    }
}
