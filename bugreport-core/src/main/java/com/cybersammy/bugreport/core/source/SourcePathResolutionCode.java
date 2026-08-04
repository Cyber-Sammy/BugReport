package com.cybersammy.bugreport.core.source;

/** Stable reason why one declared source path could not be safely resolved. */
public enum SourcePathResolutionCode {
    ROOT_MISSING,
    ROOT_UNSAFE,
    COMPONENT_MISSING,
    COMPONENT_NOT_DIRECTORY,
    PATH_REDIRECTION,
    PATH_CHANGED_DURING_RESOLUTION,
    TARGET_NOT_REGULAR_FILE,
    TARGET_OUTSIDE_ROOT,
    ACCESS_DENIED,
    IO_FAILURE
}
