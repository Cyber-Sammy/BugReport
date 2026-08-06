package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.regex.Pattern;

/** Removes bearer authorization credentials while retaining no token material. */
public final class BearerTokenSanitizationStage extends RegexSanitizationStage {
    public static final SanitizationStageId ID = new SanitizationStageId("bearer_token");
    public static final int ORDER = 30;
    public static final String REPLACEMENT = "<bearer-token>";

    private static final Pattern PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_-])Bearer\\s+[A-Za-z0-9._~+/-]{8,}={0,2}"
                    + "(?![A-Za-z0-9_-])");

    public BearerTokenSanitizationStage() {
        super(
                ID,
                ORDER,
                PATTERN,
                0,
                PrivacyClassification.PROHIBITED,
                SanitizationAction.AUTOMATIC_REDACTION,
                REPLACEMENT);
    }
}
