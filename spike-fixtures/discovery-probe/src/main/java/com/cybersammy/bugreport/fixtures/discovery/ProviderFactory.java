package com.cybersammy.bugreport.fixtures.discovery;

import com.cybersammy.bugreport.api.BugReportProvider;

@FunctionalInterface
interface ProviderFactory {
    BugReportProvider create() throws ReflectiveOperationException;
}
