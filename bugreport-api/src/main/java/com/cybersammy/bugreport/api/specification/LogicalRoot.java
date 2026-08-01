package com.cybersammy.bugreport.api.specification;

/** Approved local root that a declarative filesystem selector may address. */
public enum LogicalRoot {
    /** Local Minecraft client log directory. */
    GAME_LOGS,
    /** Local Minecraft client crash-report directory. */
    CRASH_REPORTS,
    /** Local Minecraft client configuration directory. */
    MOD_CONFIGURATION
}
