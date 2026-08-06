package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.Objects;
import java.util.regex.Pattern;

/** Detects hostname endpoints only when an explicit server/address key provides context. */
public final class ServerAddressSanitizationStage extends RegexSanitizationStage {
    public static final SanitizationStageId ID = new SanitizationStageId("server_address");
    public static final int ORDER = 80;
    public static final String REPLACEMENT = "<server-address>";

    private static final Pattern PATTERN = Pattern.compile(
            "(?i)(?:[\\\"']?(?:server(?:[_-]?(?:address|ip))?"
                    + "|address|remote[_-]?host|hostname)"
                    + "[\\\"']?\\s*[:=]\\s*[\\\"']?)"
                    + "((?:localhost|[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?"
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+)"
                    + "(?::[0-9]{1,5})?)",
            Pattern.UNICODE_CASE);

    public ServerAddressSanitizationStage(SanitizationAction action) {
        super(
                ID,
                ORDER,
                PATTERN,
                1,
                PrivacyClassification.PERSONAL,
                Objects.requireNonNull(action, "action"),
                REPLACEMENT);
    }

    @Override
    boolean accept(String line, java.util.regex.Matcher matcher, int start, int end) {
        int separator = matcher.group(1).lastIndexOf(':');
        if (separator < 0) {
            return true;
        }
        try {
            int port = Integer.parseInt(matcher.group(1).substring(separator + 1));
            return port >= 1 && port <= 65_535;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
