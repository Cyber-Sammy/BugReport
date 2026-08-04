package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.LogicalRoot;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable binding from the public logical-root vocabulary to platform-owned paths. */
public final class ApprovedSourceRoots {
    private final Map<LogicalRoot, Path> roots;

    private ApprovedSourceRoots(Map<LogicalRoot, Path> roots) {
        this.roots = Map.copyOf(roots);
    }

    /**
     * Binds all declarative roots for one local Minecraft client installation.
     *
     * <p>The platform adapter, never a provider, supplies these locations. Filesystem safety is
     * revalidated whenever a source is resolved because bindings can change after construction.
     */
    public static ApprovedSourceRoots of(
            Path gameLogs, Path crashReports, Path modConfiguration) {
        EnumMap<LogicalRoot, Path> roots = new EnumMap<>(LogicalRoot.class);
        roots.put(LogicalRoot.GAME_LOGS, normalizeAbsolute(gameLogs, LogicalRoot.GAME_LOGS));
        roots.put(
                LogicalRoot.CRASH_REPORTS,
                normalizeAbsolute(crashReports, LogicalRoot.CRASH_REPORTS));
        roots.put(
                LogicalRoot.MOD_CONFIGURATION,
                normalizeAbsolute(modConfiguration, LogicalRoot.MOD_CONFIGURATION));
        requireNonOverlapping(roots);
        return new ApprovedSourceRoots(roots);
    }

    Path path(LogicalRoot root) {
        return roots.get(Objects.requireNonNull(root, "root"));
    }

    private static Path normalizeAbsolute(Path path, LogicalRoot root) {
        Path value = Objects.requireNonNull(path, root + " path");
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
