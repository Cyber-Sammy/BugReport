package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.regex.Pattern;

/** Removes values assigned to explicit API-key configuration or header names. */
public final class ApiKeySanitizationStage extends RegexSanitizationStage {
    public static final SanitizationStageId ID = new SanitizationStageId("api_key");
    public static final int ORDER = 40;
    public static final String REPLACEMENT = "<api-key>";

    private static final Pattern PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_-])"
                    + "(?:[\\\"']?(?:api[_-]?key|x-api-key)[\\\"']?\\s*[:=]\\s*"
                    + "[\\\"']?)([A-Za-z0-9._~+/-]{8,}={0,2})");

    public ApiKeySanitizationStage() {
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
