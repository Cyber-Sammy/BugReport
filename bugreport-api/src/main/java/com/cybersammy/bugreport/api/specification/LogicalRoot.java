package com.cybersammy.bugreport.api.specification;

/**
 * Approved narrow local root that a normal declarative filesystem selector may address.
 *
 * <p>This closed vocabulary deliberately excludes the game directory, saves, world/player data,
 * identity and connection history, and arbitrary filesystem locations. Access to a future bounded
 * world-state export requires a separate capability and does not add a declarative root here.
 */
public enum LogicalRoot {
    /** Local Minecraft client log directory. */
    GAME_LOGS,
    /** Local Minecraft client crash-report directory. */
    CRASH_REPORTS,
    /** Local Minecraft client configuration directory. */
    MOD_CONFIGURATION
}
