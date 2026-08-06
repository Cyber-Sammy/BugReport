package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.regex.Pattern;

/** Removes known webhook URLs whose path embeds a delivery secret. */
public final class WebhookSanitizationStage extends RegexSanitizationStage {
    public static final SanitizationStageId ID = new SanitizationStageId("webhook");
    public static final int ORDER = 20;
    public static final String REPLACEMENT = "<webhook>";

    private static final Pattern PATTERN = Pattern.compile(
            "(?i)https://(?:"
                    + "(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/"
                    + "[0-9]{5,}/[A-Za-z0-9._-]{8,}"
                    + "|hooks\\.slack\\.com/services/[A-Za-z0-9/_-]{12,}"
                    + ")");

    public WebhookSanitizationStage() {
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
