package com.cybersammy.bugreport.core.form;

import com.cybersammy.bugreport.api.localization.LocalizationKey;

/** Closed product-defined contexts in which a reported problem occurs. */
public enum ReportSideContext {
    /** Client startup, menus, rendering, or another non-world client context. */
    CLIENT_GENERAL("client_general"),
    /** An integrated singleplayer world. */
    SINGLEPLAYER("singleplayer"),
    /** A client connected to a multiplayer server. */
    MULTIPLAYER_CLIENT("multiplayer_client"),
    /** A dedicated server process or server-side behavior. */
    DEDICATED_SERVER("dedicated_server"),
    /** The reporter cannot determine the applicable context. */
    UNKNOWN("unknown");

    private final String value;
    private final LocalizationKey labelKey;

    ReportSideContext(String value) {
        this.value = value;
        labelKey = LocalizationKey.of("bugreport.field.side_context.option." + value);
    }

    /**
     * Returns the stable persisted value.
     *
     * @return canonical value
     */
    public String value() {
        return value;
    }

    /**
     * Returns the Bug Report-owned localized label key.
     *
     * @return label localization key
     */
    public LocalizationKey labelKey() {
        return labelKey;
    }
}
