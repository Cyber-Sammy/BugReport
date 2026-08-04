package com.cybersammy.bugreport.core.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import com.cybersammy.bugreport.api.classification.SupportedSide;
import com.cybersammy.bugreport.api.constraint.CollectionConstraints;
import com.cybersammy.bugreport.api.identifier.DiagnosticSourceId;
import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.DiagnosticContentType;
import com.cybersammy.bugreport.api.specification.DiagnosticSourceSpecification;
import com.cybersammy.bugreport.api.specification.FilenamePattern;
import com.cybersammy.bugreport.api.specification.LogicalRoot;
import com.cybersammy.bugreport.api.specification.RelativePath;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SourceSelectorPlannerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void plansExactLogAndConfigurationFiles() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/debug.log"), "log");
        Files.writeString(temporaryDirectory.resolve("config/example.toml"), "config");
        DiagnosticSourceSpecification log =
                source(
                        DiagnosticSourceSpecification.exactFile(
                                id("debug_log"),
                                LogicalRoot.GAME_LOGS,
                                RelativePath.of("debug.log")),
                        PrivacyClassification.PERSONAL,
                        DiagnosticContentType.TEXT);
        DiagnosticSourceSpecification config =
                source(
                        DiagnosticSourceSpecification.modConfiguration(
                                id("config"), RelativePath.of("example.toml")),
                        PrivacyClassification.SENSITIVE,
                        DiagnosticContentType.TEXT);

        assertEquals("debug.log", selectedFiles(log, roots).getFirst().relativePath().value());
        assertEquals("example.toml", selectedFiles(config, roots).getFirst().relativePath().value());
    }

    @Test
    void choosesLatestFileDeterministicallyByPathOnTimestampTie() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path first = temporaryDirectory.resolve("logs/a.log");
        Path second = temporaryDirectory.resolve("logs/b.log");
        Files.writeString(first, "a");
        Files.writeString(second, "b");
        FileTime sameTime = FileTime.from(Instant.parse("2026-08-04T12:00:00Z"));
        Files.setLastModifiedTime(first, sameTime);
        Files.setLastModifiedTime(second, sameTime);
        DiagnosticSourceSpecification source =
                source(
                        DiagnosticSourceSpecification.latestFile(
                                id("latest"),
                                LogicalRoot.GAME_LOGS,
                                FilenamePattern.of("*.log")),
                        PrivacyClassification.PERSONAL,
                        DiagnosticContentType.TEXT);

        assertEquals("a.log", selectedFiles(source, roots).getFirst().relativePath().value());
    }

    @Test
    void returnsFilteredMatchesInCanonicalOrderAndHonorsProviderLimit() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path archive = Files.createDirectory(temporaryDirectory.resolve("logs/archive"));
        Files.writeString(archive.resolve("z.log"), "z");
        Files.writeString(archive.resolve("a.log"), "a");
        Files.writeString(archive.resolve("ignored.txt"), "ignored");
        DiagnosticSourceSpecification accepted =
                filteredSource("accepted", 2);

        assertEquals(
                List.of("archive/a.log", "archive/z.log"),
                selectedFiles(accepted, roots).stream()
                        .map(file -> file.relativePath().value())
                        .toList());

        DiagnosticSourceSpecification limited = filteredSource("limited", 1);
        UnavailableSourcePlan unavailable =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        SourceSelectorPlanner.plan(limited, roots));
        assertEquals(SourceSelectionFailureCode.MATCH_LIMIT_EXCEEDED, unavailable.code());
    }

    @Test
    void acceptsNestedDirectoryWithoutFilesystemRedirection() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path current =
                Files.createDirectories(temporaryDirectory.resolve("logs/archive/current"));
        Files.writeString(current.resolve("client.log"), "diagnostic");

        assertEquals(
                "archive/current/client.log",
                selectedFiles(
                                filteredSource(
                                        "nested", RelativePath.of("archive/current"), 2),
                                roots)
                        .getFirst()
                        .relativePath()
                        .value());
    }

    @Test
    void rejectsIntermediateDirectorySymlinkBeforeScanningEvenWhenEmpty()
            throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path realArchive =
                Files.createDirectories(temporaryDirectory.resolve("logs/real-archive/current"));
        Path link = temporaryDirectory.resolve("logs/archive");
        try {
            Files.createSymbolicLink(link, realArchive.getParent());
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }

        UnavailableSourcePlan unavailable =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        SourceSelectorPlanner.plan(
                                filteredSource(
                                        "redirected",
                                        RelativePath.of("archive/current"),
                                        2),
                                roots));

        assertEquals(SourceSelectionFailureCode.PATH_REJECTED, unavailable.code());
        assertEquals(
                SourcePathResolutionCode.PATH_REDIRECTION,
                unavailable.pathCode().orElseThrow());
    }

    @Test
    void deterministicallyRejectsRedirectedIntermediateDirectoryBeforeScanning()
            throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path archive =
                Files.createDirectories(temporaryDirectory.resolve("logs/archive/current"))
                        .getParent();
        Path redirected = Files.createDirectory(temporaryDirectory.resolve("redirected"));
        SourcePathInspection inspection =
                new DelegatingInspection() {
                    @Override
                    public Path realPath(Path path, boolean followLinks) throws IOException {
                        if (path.equals(archive) && followLinks) {
                            return redirected.toRealPath();
                        }
                        return super.realPath(path, followLinks);
                    }
                };

        UnavailableSourcePlan unavailable =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        SourceSelectorPlanner.plan(
                                filteredSource(
                                        "redirected_observation",
                                        RelativePath.of("archive/current"),
                                        2),
                                roots,
                                inspection));

        assertEquals(SourceSelectionFailureCode.PATH_REJECTED, unavailable.code());
        assertEquals(
                SourcePathResolutionCode.PATH_REDIRECTION,
                unavailable.pathCode().orElseThrow());
    }

    @Test
    void rejectsNonDirectoryScanTargetWithExactPathDiagnostic() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/archive"), "not a directory");

        UnavailableSourcePlan unavailable =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        SourceSelectorPlanner.plan(filteredSource("file_target", 2), roots));

        assertEquals(SourceSelectionFailureCode.PATH_REJECTED, unavailable.code());
        assertEquals(
                SourcePathResolutionCode.TARGET_NOT_DIRECTORY,
                unavailable.pathCode().orElseThrow());
    }

    @Test
    void distinguishesNoMatchMissingAndUnsafeMatchedEntry() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        DiagnosticSourceSpecification noMatch = filteredSource("no_match", 2);
        Files.createDirectory(temporaryDirectory.resolve("logs/archive"));
        UnavailableSourcePlan empty =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        SourceSelectorPlanner.plan(noMatch, roots));
        assertEquals(SourceSelectionFailureCode.NO_MATCH, empty.code());

        DiagnosticSourceSpecification missing =
                source(
                        DiagnosticSourceSpecification.exactFile(
                                id("missing"),
                                LogicalRoot.GAME_LOGS,
                                RelativePath.of("missing.log")),
                        PrivacyClassification.PERSONAL,
                        DiagnosticContentType.TEXT);
        UnavailableSourcePlan absent =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        SourceSelectorPlanner.plan(missing, roots));
        assertEquals(SourceSelectionFailureCode.SOURCE_MISSING, absent.code());
        assertEquals(SourcePathResolutionCode.COMPONENT_MISSING, absent.pathCode().orElseThrow());

        Files.createDirectory(temporaryDirectory.resolve("logs/archive/not-a-file.log"));
        UnavailableSourcePlan unsafe =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        SourceSelectorPlanner.plan(noMatch, roots));
        assertEquals(SourceSelectionFailureCode.PATH_REJECTED, unsafe.code());
        assertEquals(
                SourcePathResolutionCode.TARGET_NOT_REGULAR_FILE,
                unsafe.pathCode().orElseThrow());
    }

    @Test
    void failsClosedWhenDirectoryScanExceedsProductCeiling() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        for (int index = 0;
                index <= SourceSelectorPlanner.MAX_SCANNED_DIRECTORY_ENTRIES;
                index++) {
            Files.writeString(
                    temporaryDirectory.resolve("logs/entry-" + index + ".txt"), "entry");
        }
        DiagnosticSourceSpecification source =
                source(
                        DiagnosticSourceSpecification.latestFile(
                                id("bounded"),
                                LogicalRoot.GAME_LOGS,
                                FilenamePattern.of("*.log")),
                        PrivacyClassification.PERSONAL,
                        DiagnosticContentType.TEXT);

        UnavailableSourcePlan unavailable =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        SourceSelectorPlanner.plan(source, roots));

        assertEquals(SourceSelectionFailureCode.SCAN_LIMIT_EXCEEDED, unavailable.code());
    }

    @Test
    void failsClosedWhenScannedDirectoryIdentityChanges() throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Path archive = Files.createDirectory(temporaryDirectory.resolve("logs/archive"));
        Files.writeString(archive.resolve("client.log"), "diagnostic");
        Path replacement = Files.createDirectory(temporaryDirectory.resolve("replacement"));
        SourcePathInspection inspection =
                new DelegatingInspection() {
                    private int archiveNoFollowReads;
                    private boolean replacementObserved;

                    @Override
                    public BasicFileAttributes readAttributes(Path path, boolean followLinks)
                            throws IOException {
                        if (path.equals(archive)
                                && !followLinks
                                && ++archiveNoFollowReads == 4) {
                            replacementObserved = true;
                        }
                        return super.readAttributes(observedPath(path), followLinks);
                    }

                    @Override
                    public Path realPath(Path path, boolean followLinks) throws IOException {
                        return super.realPath(observedPath(path), followLinks);
                    }

                    private Path observedPath(Path path) {
                        return replacementObserved && path.equals(archive) ? replacement : path;
                    }
                };

        UnavailableSourcePlan unavailable =
                assertInstanceOf(
                        UnavailableSourcePlan.class,
                        SourceSelectorPlanner.plan(
                                filteredSource("changed", 2), roots, inspection));

        assertEquals(
                SourceSelectionFailureCode.PATH_CHANGED_DURING_SCAN,
                unavailable.code());
    }

    @Test
    void mapsProductOwnedSelectorsWithoutGrantingFilesystemOrUserAuthority()
            throws IOException {
        ApprovedSourceRoots roots = createRoots();
        Files.writeString(temporaryDirectory.resolve("logs/latest.log"), "latest");
        Path oldCrash =
                temporaryDirectory.resolve("crash-reports/crash-2026-client.txt");
        Files.writeString(oldCrash, "old");
        Path latestCrash =
                temporaryDirectory.resolve("crash-reports/crash-2026-new-client.txt");
        Files.writeString(latestCrash, "new");
        Files.setLastModifiedTime(
                oldCrash, FileTime.from(Instant.parse("2026-08-04T14:00:00Z")));
        Files.setLastModifiedTime(
                latestCrash, FileTime.from(Instant.parse("2026-08-04T15:00:00Z")));

        assertEquals(
                "latest.log",
                selectedFiles(
                                source(
                                        DiagnosticSourceSpecification.latestLog(id("latest_log")),
                                        PrivacyClassification.PERSONAL,
                                        DiagnosticContentType.TEXT),
                                roots)
                        .getFirst()
                        .relativePath()
                        .value());
        assertEquals(
                "crash-2026-new-client.txt",
                selectedFiles(
                                source(
                                        DiagnosticSourceSpecification.latestCrashReport(
                                                id("latest_crash")),
                                        PrivacyClassification.PERSONAL,
                                        DiagnosticContentType.TEXT),
                                roots)
                        .getFirst()
                        .relativePath()
                        .value());

        DiagnosticSourceSpecification screenshot =
                source(
                        DiagnosticSourceSpecification.userSelectedScreenshot(id("screenshot")),
                        PrivacyClassification.SENSITIVE,
                        DiagnosticContentType.BINARY);
        assertInstanceOf(
                UserSelectionSourcePlan.class,
                SourceSelectorPlanner.plan(screenshot, roots));

        DiagnosticSourceSpecification modList =
                source(
                        DiagnosticSourceSpecification.modList(id("mod_list")),
                        PrivacyClassification.LOW,
                        DiagnosticContentType.JSON);
        assertInstanceOf(BuiltInSourcePlan.class, SourceSelectorPlanner.plan(modList, roots));
    }

    private DiagnosticSourceSpecification filteredSource(String sourceId, int maxFiles) {
        return filteredSource(sourceId, RelativePath.of("archive"), maxFiles);
    }

    private DiagnosticSourceSpecification filteredSource(
            String sourceId, RelativePath directory, int maxFiles) {
        return source(
                DiagnosticSourceSpecification.filteredLogDirectory(
                                id(sourceId),
                                directory,
                                FilenamePattern.of("*.log"))
                        .constraints(
                                CollectionConstraints.builder()
                                        .maxMatchedFiles(maxFiles)
                                        .build()),
                PrivacyClassification.PERSONAL,
                DiagnosticContentType.TEXT);
    }

    private static DiagnosticSourceSpecification source(
            DiagnosticSourceSpecification.Builder builder,
            PrivacyClassification privacy,
            DiagnosticContentType contentType) {
        return builder.labelKey(LocalizationKey.of("example.source"))
                .privacy(privacy)
                .contentType(contentType)
                .supportSide(SupportedSide.PHYSICAL_CLIENT)
                .build();
    }

    private static DiagnosticSourceId id(String value) {
        return DiagnosticSourceId.of(value);
    }

    private static List<ResolvedSourceFile> selectedFiles(
            DiagnosticSourceSpecification source, ApprovedSourceRoots roots) {
        return assertInstanceOf(
                        FileSourcePlan.class,
                        SourceSelectorPlanner.plan(source, roots))
                .files();
    }

    private ApprovedSourceRoots createRoots() throws IOException {
        return ApprovedSourceRoots.of(
                Files.createDirectory(temporaryDirectory.resolve("logs")),
                Files.createDirectory(temporaryDirectory.resolve("crash-reports")),
                Files.createDirectory(temporaryDirectory.resolve("config")));
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
