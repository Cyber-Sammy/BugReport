package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects syntactically valid IPv4 and IPv6 endpoints without resolving hostnames. */
public final class NetworkAddressSanitizationStage implements TextSanitizationStage {
    public static final SanitizationStageId ID = new SanitizationStageId("network_address");
    public static final int ORDER = 70;
    public static final String REPLACEMENT = "<network-address>";

    private static final Pattern IPV4 = Pattern.compile(
            "(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(?![0-9.])");
    private static final Pattern BRACKETED_IPV6 = Pattern.compile(
            "(?<![0-9A-Fa-f:.])\\[[0-9A-Fa-f:.]{2,45}](?::[0-9]{1,5})?"
                    + "(?![0-9A-Fa-f:.])");
    private static final Pattern PLAIN_IPV6 = Pattern.compile(
            "(?<![0-9A-Fa-f:.])(?=[0-9A-Fa-f:.]*:)"
                    + "[0-9A-Fa-f:.]{2,45}(?![0-9A-Fa-f:.])");

    private final SanitizationAction action;

    public NetworkAddressSanitizationStage(SanitizationAction action) {
        this.action = Objects.requireNonNull(action, "action");
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
        String value = Objects.requireNonNull(line, "line");
        List<Range> ranges = new ArrayList<>();
        collectIpv4(value, ranges);
        collectIpv6(value, BRACKETED_IPV6, ranges);
        collectIpv6(value, PLAIN_IPV6, ranges);
        ranges.sort(Comparator.comparingInt(Range::start).thenComparingInt(Range::end));
        List<SanitizationMatch> matches = new ArrayList<>();
        int previousEnd = -1;
        for (Range range : ranges) {
            if (range.start() >= previousEnd) {
                matches.add(SanitizationStageSupport.match(
                        range.start(),
                        range.end(),
                        PrivacyClassification.PERSONAL,
                        action,
                        REPLACEMENT));
                previousEnd = range.end();
            }
        }
        return List.copyOf(matches);
    }

    private static void collectIpv4(String line, List<Range> ranges) {
        Matcher matcher = IPV4.matcher(line);
        while (matcher.find()) {
            String candidate = matcher.group();
            int portSeparator = candidate.lastIndexOf(':');
            String address = portSeparator >= 0
                    ? candidate.substring(0, portSeparator)
                    : candidate;
            if (isIpv4(address) && !hasVersionContext(line, matcher.start())) {
                int end = portSeparator < 0 || isPort(candidate.substring(portSeparator + 1))
                        ? matcher.end()
                        : matcher.start() + address.length();
                ranges.add(new Range(matcher.start(), end));
            }
        }
    }

    private static boolean isIpv4(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.length() > 1 && octet.charAt(0) == '0') {
                return false;
            }
            int value = Integer.parseInt(octet);
            if (value > 255) {
                return false;
            }
        }
        return true;
    }

    private static void collectIpv6(String line, Pattern pattern, List<Range> ranges) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String candidate = matcher.group();
            String address = candidate;
            String port = null;
            if (candidate.startsWith("[")) {
                int bracket = candidate.indexOf(']');
                address = candidate.substring(1, bracket);
                if (bracket + 1 < candidate.length()) {
                    port = candidate.substring(bracket + 2);
                }
            }
            if (isIpv6(address)) {
                int end = port == null || isPort(port)
                        ? matcher.end()
                        : matcher.start() + candidate.indexOf(']') + 1;
                ranges.add(new Range(matcher.start(), end));
            }
        }
    }

    private static boolean isIpv6(String address) {
        try {
            InetAddress parsed = InetAddress.getByName(address);
            return address.indexOf(':') >= 0
                    && (parsed instanceof Inet6Address
                            || (address.indexOf('.') >= 0 && parsed instanceof Inet4Address));
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static boolean isPort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65_535;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean hasVersionContext(String line, int start) {
        int contextStart = Math.max(0, start - 24);
        String context = line.substring(contextStart, start);
        return context.matches("(?i).*\\b(?:version|ver)\\s*[:=]\\s*$");
    }

    private record Range(int start, int end) {}
}
