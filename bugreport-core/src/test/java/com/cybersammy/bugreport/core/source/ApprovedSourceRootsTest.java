package com.cybersammy.bugreport.core.source;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ApprovedSourceRootsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void acceptsThreeDistinctAbsolutePlatformBindings() {
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
}
