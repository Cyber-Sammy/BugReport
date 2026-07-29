package com.cybersammy.bugreport.neoforge;

import com.cybersammy.bugreport.api.BugReportProvider;
import java.lang.reflect.Constructor;
import java.util.Objects;

record ProviderCandidate(
        String ownerModId,
        String className,
        Constructor<? extends BugReportProvider> constructor) {
    ProviderCandidate {
        Objects.requireNonNull(ownerModId);
        Objects.requireNonNull(className);
        Objects.requireNonNull(constructor);
    }
}
