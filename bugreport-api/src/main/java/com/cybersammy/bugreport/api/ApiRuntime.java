package com.cybersammy.bugreport.api;

/** Runtime identity used only by the M0 API packaging spike. */
public final class ApiRuntime {
    private ApiRuntime() {}

    /** Returns the physical API artifact version selected by NeoForge Jar-in-Jar. */
    public static String version() {
        return "0.2.0";
    }
}
