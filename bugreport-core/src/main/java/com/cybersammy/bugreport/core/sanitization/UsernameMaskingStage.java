package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masks a platform-supplied username only at Unicode token boundaries. */
public final class UsernameMaskingStage implements TextSanitizationStage {
    public static final SanitizationStageId ID = new SanitizationStageId("username");
    public static final int ORDER = 110;
    public static final String REPLACEMENT = "<user>";

    private static final int MINIMUM_USERNAME_CODE_POINTS = 3;
    private static final int MAXIMUM_USERNAME_CHARACTERS = 256;

    private final Pattern usernamePattern;
    private final SanitizationAction action;

    public UsernameMaskingStage(
            String username, SanitizationCaseSensitivity caseSensitivity) {
        this(username, caseSensitivity, SanitizationAction.AUTOMATIC_REDACTION);
    }

    public UsernameMaskingStage(
            String username,
            SanitizationCaseSensitivity caseSensitivity,
            SanitizationAction action) {
        String value = requireSafeUsername(username);
        SanitizationCaseSensitivity sensitivity =
                Objects.requireNonNull(caseSensitivity, "caseSensitivity");
        this.action = Objects.requireNonNull(action, "action");
        int flags = Pattern.UNICODE_CHARACTER_CLASS;
        if (sensitivity == SanitizationCaseSensitivity.INSENSITIVE) {
            flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        this.usernamePattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}_])"
                        + Pattern.quote(value)
                        + "(?![\\p{L}\\p{N}_])",
                flags);
    }

    @Override
    public SanitizationStageId id() {
        return ID;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public List<SanitizationMatch> findMatches(String line) {
        Matcher matcher = usernamePattern.matcher(Objects.requireNonNull(line, "line"));
        List<SanitizationMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(SanitizationStageSupport.match(
                    matcher.start(),
                    matcher.end(),
                    PrivacyClassification.PERSONAL,
                    action,
                    REPLACEMENT));
        }
        return List.copyOf(matches);
    }

    private static String requireSafeUsername(String username) {
        String value = Objects.requireNonNull(username, "username");
        if (value.isBlank()
                || !value.equals(value.strip())
                || value.length() > MAXIMUM_USERNAME_CHARACTERS
                || value.codePointCount(0, value.length()) < MINIMUM_USERNAME_CODE_POINTS
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "Username is not safe for automatic token masking");
        }
        return value;
    }
}
