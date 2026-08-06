package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.regex.Pattern;

/** Removes values explicitly labelled as session identifiers or session tokens. */
public final class SessionIdentifierSanitizationStage extends RegexSanitizationStage {
    public static final SanitizationStageId ID = new SanitizationStageId("session_identifier");
    public static final int ORDER = 50;
    public static final String REPLACEMENT = "<session-id>";

    private static final Pattern PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_-])"
                    + "(?:[\\\"']?(?:session(?:[_-]?id|[_-]?token)?)[\\\"']?"
                    + "\\s*[:=]\\s*[\\\"']?)"
                    + "([A-Za-z0-9][A-Za-z0-9._~+:/-]{11,511})");

    public SessionIdentifierSanitizationStage() {
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
