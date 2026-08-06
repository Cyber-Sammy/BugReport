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
                    + "(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+))"
                    + "(?::([0-9]{1,5}))?",
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
    int matchEnd(java.util.regex.Matcher matcher, int hostnameEnd) {
        String portText = matcher.group(2);
        if (portText == null) {
            return hostnameEnd;
        }
        try {
            int port = Integer.parseInt(portText);
            return port >= 1 && port <= 65_535 ? matcher.end(2) : hostnameEnd;
        } catch (NumberFormatException exception) {
            return hostnameEnd;
        }
    }
}
