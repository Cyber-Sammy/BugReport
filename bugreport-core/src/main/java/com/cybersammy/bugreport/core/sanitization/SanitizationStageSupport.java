package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;

final class SanitizationStageSupport {
    private SanitizationStageSupport() {}

    static SanitizationMatch match(
            int start,
            int end,
            PrivacyClassification classification,
            SanitizationAction action,
            String replacement) {
        return action == SanitizationAction.AUTOMATIC_REDACTION
                ? SanitizationMatch.redact(start, end, classification, replacement)
                : SanitizationMatch.warn(start, end, classification);
    }
}
