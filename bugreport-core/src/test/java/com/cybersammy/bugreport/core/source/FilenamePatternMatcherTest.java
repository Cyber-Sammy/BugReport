package com.cybersammy.bugreport.core.source;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.specification.FilenamePattern;
import org.junit.jupiter.api.Test;

final class FilenamePatternMatcherTest {
    @Test
    void matchesPortableWildcardsCaseSensitively() {
        FilenamePattern pattern = FilenamePattern.of("debug-?.*.log");

        assertTrue(FilenamePatternMatcher.matches(pattern, "debug-1.client.log"));
        assertFalse(FilenamePatternMatcher.matches(pattern, "debug-12.client.log"));
        assertFalse(FilenamePatternMatcher.matches(pattern, "DEBUG-1.client.log"));
    }

    @Test
    void supportsStarsAtPatternBoundaries() {
        assertTrue(FilenamePatternMatcher.matches(FilenamePattern.of("latest.*"), "latest.log"));
        assertTrue(FilenamePatternMatcher.matches(FilenamePattern.of("*.log"), "latest.log"));
        assertFalse(FilenamePatternMatcher.matches(FilenamePattern.of("*.log"), "latest.txt"));
    }

    @Test
    void questionMarkMatchesOneUnicodeCodePoint() {
        assertTrue(
                FilenamePatternMatcher.matches(
                        FilenamePattern.of("?.log"), "\uD83D\uDC1B.log"));
    }
}
