package com.cybersammy.bugreport.core.sanitization;

import com.cybersammy.bugreport.api.classification.PrivacyClassification;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared bounded mechanics for detectors backed by one trusted regular expression. */
abstract class RegexSanitizationStage implements TextSanitizationStage {
    private final SanitizationStageId id;
    private final int order;
    private final Pattern pattern;
    private final int matchedGroup;
    private final PrivacyClassification classification;
    private final SanitizationAction action;
    private final String replacement;

    RegexSanitizationStage(
            SanitizationStageId id,
            int order,
            Pattern pattern,
            int matchedGroup,
            PrivacyClassification classification,
            SanitizationAction action,
            String replacement) {
        this.id = Objects.requireNonNull(id, "id");
        this.order = order;
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        if (matchedGroup < 0 || matchedGroup > pattern.matcher("").groupCount()) {
            throw new IllegalArgumentException("Matched regular-expression group is invalid");
        }
        this.matchedGroup = matchedGroup;
        this.classification = Objects.requireNonNull(classification, "classification");
        this.action = Objects.requireNonNull(action, "action");
        this.replacement = Objects.requireNonNull(replacement, "replacement");
    }

    @Override
    public final SanitizationStageId id() {
        return id;
    }

    @Override
    public final int order() {
        return order;
    }

    @Override
    public final List<SanitizationMatch> findMatches(String line) {
        Matcher matcher = pattern.matcher(Objects.requireNonNull(line, "line"));
        List<SanitizationMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            int start = matcher.start(matchedGroup);
            int end = matchEnd(matcher, matcher.end(matchedGroup));
            if (start >= 0 && end > start && accept(line, matcher, start, end)) {
                matches.add(SanitizationStageSupport.match(
                        start, end, classification, action, replacement));
            }
        }
        return List.copyOf(matches);
    }

    boolean accept(String line, Matcher matcher, int start, int end) {
        return true;
    }

    int matchEnd(Matcher matcher, int defaultEnd) {
        return defaultEnd;
    }
}
