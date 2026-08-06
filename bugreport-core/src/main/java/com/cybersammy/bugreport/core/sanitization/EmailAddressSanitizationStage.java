package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.Objects;
import java.util.regex.Pattern;

/** Detects bounded email addresses and handles the complete address before username masking. */
public final class EmailAddressSanitizationStage extends RegexSanitizationStage {
    public static final SanitizationStageId ID = new SanitizationStageId("email_address");
    public static final int ORDER = 60;
    public static final String REPLACEMENT = "<email>";

    private static final Pattern PATTERN = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}._%+-])"
                    + "[\\p{L}\\p{N}][\\p{L}\\p{N}._%+-]{0,63}"
                    + "@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+"
                    + "(?![\\p{L}\\p{N}._%+-])",
            Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    public EmailAddressSanitizationStage(SanitizationAction action) {
        super(
                ID,
                ORDER,
                PATTERN,
                0,
                PrivacyClassification.PERSONAL,
                Objects.requireNonNull(action, "action"),
                REPLACEMENT);
    }
}
