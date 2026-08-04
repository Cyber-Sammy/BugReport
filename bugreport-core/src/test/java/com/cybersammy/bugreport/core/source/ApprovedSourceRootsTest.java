package com.cybersammy.bugreport.core.source;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ApprovedSourceRootsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void derivesOnlyNarrowRootsFromGameDirectory() {
        Path gameDirectory = temporaryDirectory.resolve("minecraft").toAbsolutePath();

        ApprovedSourceRoots roots = ApprovedSourceRoots.forGameDirectory(gameDirectory);

        assertEquals(gameDirectory.resolve("logs"), roots.path(LogicalRoot.GAME_LOGS));
        assertEquals(
                gameDirectory.resolve("crash-reports"),
                roots.path(LogicalRoot.CRASH_REPORTS));
        assertEquals(
                gameDirectory.resolve("config"),
                roots.path(LogicalRoot.MOD_CONFIGURATION));

        List<Path> sensitiveLocations = List.of(
                gameDirectory,
                gameDirectory.resolve("saves"),
                gameDirectory.resolve("servers.dat"),
                gameDirectory.resolve("usercache.json"),
                gameDirectory.resolve("command_history.txt"));
        for (Path root : List.of(
                roots.path(LogicalRoot.GAME_LOGS),
                roots.path(LogicalRoot.CRASH_REPORTS),
                roots.path(LogicalRoot.MOD_CONFIGURATION))) {
            for (Path sensitiveLocation : sensitiveLocations) {
                assertFalse(sensitiveLocation.startsWith(root));
            }
        }
    }

    @Test
    void rejectsRelativeGameDirectory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ApprovedSourceRoots.forGameDirectory(Path.of("minecraft")));
    }

    @Test
    void acceptsSiblingAbsolutePlatformBindings() {
        assertDoesNotThrow(
                () ->
                        ApprovedSourceRoots.of(
                                temporaryDirectory.resolve("logs"),
                                temporaryDirectory.resolve("crash-reports"),
                                temporaryDirectory.resolve("config")));
    }

    @Test
    void rejectsRelativeAndDuplicateBindings() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ApprovedSourceRoots.of(
                                Path.of("logs"),
                                temporaryDirectory.resolve("crash-reports"),
                                temporaryDirectory.resolve("config")));
        Path same = temporaryDirectory.resolve("same");
        assertThrows(
                IllegalArgumentException.class,
                () -> ApprovedSourceRoots.of(same, same, temporaryDirectory.resolve("config")));
    }

    @Test
    void rejectsRootThatContainsAnotherLogicalRoot() {
        Path game = temporaryDirectory.resolve("game");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ApprovedSourceRoots.of(
                                game,
                                game.resolve("crash-reports"),
                                temporaryDirectory.resolve("config")));
    }

    @Test
    void rejectsRootNestedBelowAnotherLogicalRootInEitherArgumentOrder() {
        Path game = temporaryDirectory.resolve("game");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ApprovedSourceRoots.of(
                                game.resolve("logs"),
                                temporaryDirectory.resolve("crash-reports"),
                                game));
    }
}
