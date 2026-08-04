package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable binding from the public logical-root vocabulary to platform-owned paths. */
public final class ApprovedSourceRoots {
    private static final String LOGS_DIRECTORY = "logs";
    private static final String CRASH_REPORTS_DIRECTORY = "crash-reports";
    private static final String CONFIGURATION_DIRECTORY = "config";

    private final Map<LogicalRoot, Path> roots;

    private ApprovedSourceRoots(Map<LogicalRoot, Path> roots) {
        this.roots = Map.copyOf(roots);
    }

    /**
     * Derives all normal declarative roots from one local Minecraft game directory.
     *
     * <p>The game directory itself is deliberately never exposed as a root. This prevents an
     * accidental platform binding from granting declarative access to saves, identity/history
     * files, mods, or other sibling directories. Filesystem safety is revalidated whenever a
     * source is resolved because bindings can change after construction.
     *
     * @param gameDirectory platform-owned local Minecraft game directory
     * @return narrow approved roots for logs, crash reports, and mod configuration
     */
    public static ApprovedSourceRoots forGameDirectory(Path gameDirectory) {
        Path game = normalizeAbsolute(gameDirectory, "game directory");
        return of(
                game.resolve(LOGS_DIRECTORY),
                game.resolve(CRASH_REPORTS_DIRECTORY),
                game.resolve(CONFIGURATION_DIRECTORY));
    }

    static ApprovedSourceRoots of(
            Path gameLogs, Path crashReports, Path modConfiguration) {
        EnumMap<LogicalRoot, Path> roots = new EnumMap<>(LogicalRoot.class);
        roots.put(LogicalRoot.GAME_LOGS, normalizeAbsolute(gameLogs, "game logs"));
        roots.put(
                LogicalRoot.CRASH_REPORTS,
                normalizeAbsolute(crashReports, "crash reports"));
        roots.put(
                LogicalRoot.MOD_CONFIGURATION,
                normalizeAbsolute(modConfiguration, "mod configuration"));
        requireNonOverlapping(roots);
        return new ApprovedSourceRoots(roots);
    }

    Path path(LogicalRoot root) {
        return roots.get(Objects.requireNonNull(root, "root"));
    }

    private static Path normalizeAbsolute(Path path, String description) {
        Path value = Objects.requireNonNull(path, description);
        if (!value.isAbsolute()) {
            throw new IllegalArgumentException("Approved logical roots must be absolute paths");
        }
        return value.normalize();
    }

    private static void requireNonOverlapping(Map<LogicalRoot, Path> roots) {
        List<Path> paths = List.copyOf(roots.values());
        for (int left = 0; left < paths.size(); left++) {
            for (int right = left + 1; right < paths.size(); right++) {
                Path first = paths.get(left);
                Path second = paths.get(right);
                if (first.startsWith(second) || second.startsWith(first)) {
                    throw new IllegalArgumentException(
                            "Approved logical roots must not overlap");
                }
            }
        }
    }
}
