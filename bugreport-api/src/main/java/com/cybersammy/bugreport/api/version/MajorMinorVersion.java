package com.cybersammy.bugreport.api.version;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MajorMinorVersion {
    private static final int MAX_LENGTH = 21;
    private static final Pattern PATTERN = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    private MajorMinorVersion() {}

    static int[] parse(String value, String domain) {
        if (value == null || value.length() > MAX_LENGTH) {
            throw invalid(domain);
        }

        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw invalid(domain);
        }

        try {
            return new int[] {
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))
            };
        } catch (NumberFormatException exception) {
            throw invalid(domain);
        }
    }

    private static IllegalArgumentException invalid(String domain) {
        return new IllegalArgumentException(domain + " version must be canonical major.minor text");
    }
}
