package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.regex.Pattern;

/** Removes values assigned to known Minecraft authentication token keys. */
public final class MinecraftAuthenticationSanitizationStage extends RegexSanitizationStage {
    public static final SanitizationStageId ID =
            new SanitizationStageId("minecraft_authentication");
    public static final int ORDER = 10;
    public static final String REPLACEMENT = "<minecraft-auth>";

    private static final Pattern PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_-])"
                    + "[\\\"']?(?:accessToken|clientToken|minecraft[_-]?access[_-]?token)"
                    + "[\\\"']?\\s*[:=]\\s*[\\\"']?"
                    + "([A-Za-z0-9._~+/-]{8,}={0,2})");

    public MinecraftAuthenticationSanitizationStage() {
        super(
                ID,
                ORDER,
                PATTERN,
                1,
                PrivacyClassification.PROHIBITED,
                SanitizationAction.AUTOMATIC_REDACTION,
                REPLACEMENT);
    }
}
