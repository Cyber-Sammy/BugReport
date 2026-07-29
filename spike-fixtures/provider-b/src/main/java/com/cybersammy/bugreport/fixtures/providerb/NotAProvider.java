package com.cybersammy.bugreport.fixtures.providerb;

public final class NotAProvider {
    static {
        failIfInitialized();
    }

    private static void failIfInitialized() {
        throw new IllegalStateException("Invalid provider class was initialized");
    }
}
