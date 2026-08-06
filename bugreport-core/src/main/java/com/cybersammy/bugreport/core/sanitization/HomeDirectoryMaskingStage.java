package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Masks one explicitly supplied absolute home directory without reading process properties. */
public final class HomeDirectoryMaskingStage implements TextSanitizationStage {
    public static final SanitizationStageId ID = new SanitizationStageId("home_directory");
    public static final int ORDER = 100;
    public static final String REPLACEMENT = "<home>";

    private static final int MAXIMUM_HOME_CHARACTERS = 1_024;
    private static final String SEPARATORS = "[\\\\/]+";
    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:[\\\\/]");

    private final Pattern homePattern;
    private final SanitizationAction action;

    public HomeDirectoryMaskingStage(
            String homeDirectory, SanitizationCaseSensitivity caseSensitivity) {
        this(
                homeDirectory,
                caseSensitivity,
                SanitizationAction.AUTOMATIC_REDACTION);
    }

    public HomeDirectoryMaskingStage(
            String homeDirectory,
            SanitizationCaseSensitivity caseSensitivity,
            SanitizationAction action) {
        String home = requireSafeHome(homeDirectory);
        SanitizationCaseSensitivity sensitivity =
                Objects.requireNonNull(caseSensitivity, "caseSensitivity");
        this.action = Objects.requireNonNull(action, "action");
        int flags = sensitivity == SanitizationCaseSensitivity.INSENSITIVE
                ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
                : 0;
        this.homePattern = Pattern.compile(pathPattern(home), flags);
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
        List<SanitizationMatch> matches = new ArrayList<>();
        Matcher matcher = homePattern.matcher(value);
        while (matcher.find()) {
            if (isStartBoundary(value, matcher.start())
                    && isEndBoundary(value, matcher.end())) {
                matches.add(SanitizationStageSupport.match(
                        matcher.start(),
                        matcher.end(),
                        PrivacyClassification.PERSONAL,
                        action,
                        REPLACEMENT));
            }
        }
        return List.copyOf(matches);
    }

    private static String requireSafeHome(String homeDirectory) {
        String value = Objects.requireNonNull(homeDirectory, "homeDirectory");
        if (value.isBlank()
                || !value.equals(value.strip())
                || value.length() > MAXIMUM_HOME_CHARACTERS
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Home directory is not bounded text");
        }
        String withoutTrailing = stripTrailingSeparators(value);
        boolean driveAbsolute = DRIVE_PREFIX.matcher(value).find();
        boolean slashAbsolute = value.startsWith("/");
        boolean uncAbsolute = value.startsWith("\\\\") || value.startsWith("//");
        if ((!driveAbsolute && !slashAbsolute && !uncAbsolute)
                || isFilesystemRoot(withoutTrailing)
                || containsTraversalSegment(withoutTrailing)) {
            throw new IllegalArgumentException(
                    "Home directory must be a non-root absolute path without traversal");
        }
        return withoutTrailing;
    }

    private static String stripTrailingSeparators(String value) {
        int end = value.length();
        while (end > 0 && isSeparator(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static boolean isFilesystemRoot(String value) {
        return value.isEmpty()
                || value.matches("[A-Za-z]:")
                || value.chars().allMatch(character -> isSeparator((char) character));
    }

    private static boolean containsTraversalSegment(String value) {
        for (String segment : value.split("[\\\\/]+")) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static String pathPattern(String home) {
        boolean drive = home.length() >= 2 && home.charAt(1) == ':';
        boolean unc = home.startsWith("\\\\") || home.startsWith("//");
        String[] segments = home.split("[\\\\/]+");
        StringBuilder pattern = new StringBuilder();
        int firstSegment = 0;
        if (drive) {
            pattern.append(Pattern.quote(segments[0])).append(SEPARATORS);
            firstSegment = 1;
        } else if (unc) {
            pattern.append("[\\\\/]{2,}");
            while (firstSegment < segments.length && segments[firstSegment].isEmpty()) {
                firstSegment++;
            }
        } else {
            pattern.append(SEPARATORS);
            while (firstSegment < segments.length && segments[firstSegment].isEmpty()) {
                firstSegment++;
            }
        }
        for (int index = firstSegment; index < segments.length; index++) {
            if (index > firstSegment) {
                pattern.append(SEPARATORS);
            }
            pattern.append(Pattern.quote(segments[index]));
        }
        return pattern.toString();
    }

    private static boolean isStartBoundary(String line, int start) {
        if (start == 0) {
            return true;
        }
        char previous = line.charAt(start - 1);
        return !Character.isLetterOrDigit(previous)
                && previous != '_'
                && previous != '.';
    }

    private static boolean isEndBoundary(String line, int end) {
        if (end == line.length()) {
            return true;
        }
        char next = line.charAt(end);
        return isSeparator(next)
                || (!Character.isLetterOrDigit(next)
                        && next != '_'
                        && next != '-'
                        && next != '.');
    }

    private static boolean isSeparator(char character) {
        return character == '/' || character == '\\';
    }
}
