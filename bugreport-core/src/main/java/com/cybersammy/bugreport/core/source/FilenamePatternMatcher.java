package com.cybersammy.bugreport.core.source;

import com.cybersammy.bugreport.api.specification.FilenamePattern;
import java.util.Objects;

/** Deterministic case-sensitive matcher for the portable {@code *}/{@code ?} grammar. */
final class FilenamePatternMatcher {
    private FilenamePatternMatcher() {}

    static boolean matches(FilenamePattern pattern, String filename) {
        int[] wildcard =
                Objects.requireNonNull(pattern, "pattern").value().codePoints().toArray();
        int[] value = Objects.requireNonNull(filename, "filename").codePoints().toArray();
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int starMatchIndex = -1;
        while (valueIndex < value.length) {
            if (patternIndex < wildcard.length
                    && (wildcard[patternIndex] == '?'
                            || wildcard[patternIndex] == value[valueIndex])) {
                patternIndex++;
                valueIndex++;
            } else if (patternIndex < wildcard.length
                    && wildcard[patternIndex] == '*') {
                starIndex = patternIndex++;
                starMatchIndex = valueIndex;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++starMatchIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < wildcard.length && wildcard[patternIndex] == '*') {
            patternIndex++;
        }
        return patternIndex == wildcard.length;
    }
}
