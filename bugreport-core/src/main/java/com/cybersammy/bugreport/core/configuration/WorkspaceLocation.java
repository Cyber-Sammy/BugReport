package com.cybersammy.bugreport.core.configuration;

import com.cybersammy.bugreport.api.specification.RelativePath;
import java.util.Objects;

/** Workspace directory relative to a platform-owned Bug Report data root. */
public record WorkspaceLocation(RelativePath relativeDirectory) {
    public WorkspaceLocation {
        Objects.requireNonNull(relativeDirectory, "relativeDirectory");
    }

    /** Returns the default product-owned location below the platform data root. */
    public static WorkspaceLocation productDefault() {
        return new WorkspaceLocation(RelativePath.of("bugreport/workspaces"));
    }
}
