package com.cybersammy.bugreport.core.configuration;

/** Stable failure reason for the local configuration persistence boundary. */
public enum ConfigurationStoreCode {
    PATH_INVALID,
    UNSAFE_FILE,
    ATOMIC_MOVE_UNSUPPORTED,
    FORMAT_INVALID,
    IO_FAILURE
}
