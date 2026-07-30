package com.cybersammy.bugreport.api.classification;

/**
 * Physical side on which a provider declaration or callback is supported.
 *
 * <p>Provider discovery remains common-side behavior. This classification
 * describes where later report behavior may execute; it does not grant access
 * to files or state on another machine.
 */
public enum SupportedSide {
    /** Physical Minecraft client, including its integrated server when present. */
    PHYSICAL_CLIENT,
    /** Dedicated server process. Bug Report 1.0 provides discovery compatibility only. */
    DEDICATED_SERVER
}
