package com.cybersammy.bugreport.example;

import com.cybersammy.bugreport.api.BugReportProvider;

public final class ExampleBugReportProvider implements BugReportProvider {
    public ExampleBugReportProvider() {}

    @Override
    public String providerId() {
        return ExampleMod.MOD_ID;
    }
}
