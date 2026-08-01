package com.cybersammy.bugreport.api.specification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class SpecificationValueTest {
    @ParameterizedTest
    @ValueSource(strings = {"latest.log", "nested/debug.log", "config.json"})
    void acceptsPortableRelativePaths(String value) {
        assertEquals(value, RelativePath.of(value).value());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../secrets.txt",
                "/absolute.log",
                "C:/game/latest.log",
                "logs\\latest.log",
                "logs//latest.log",
                "logs/CON.txt",
                "logs/*.log"
            })
    void rejectsPathsThatCouldEscapeOrBehaveDifferentlyAcrossPlatforms(String value) {
        assertThrows(IllegalArgumentException.class, () -> RelativePath.of(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"*.log", "debug-?.txt", "latest.log"})
    void acceptsBoundedNonRecursiveFilenamePatterns(String value) {
        assertEquals(value, FilenamePattern.of(value).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "**/*.log", "logs/*.log", "..", "*"})
    void rejectsUnboundedOrRecursiveFilenamePatterns(String value) {
        assertThrows(IllegalArgumentException.class, () -> FilenamePattern.of(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/support", "https://bugs.example.test:8443/new"})
    void acceptsHttpsTargets(String value) {
        assertEquals(value, HttpsUrl.of(value).value());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://example.com",
                "https://user:secret@example.com",
                "https://example.com/#fragment",
                "https://localhost/support",
                "https://127.0.0.1/support"
            })
    void rejectsUnsafeOrNonPortableWebTargets(String value) {
        assertThrows(IllegalArgumentException.class, () -> HttpsUrl.of(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"support@example.com", "bugs+mod@example.test"})
    void acceptsBoundedEmailTargets(String value) {
        assertEquals(value, EmailAddress.of(value).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"support", "a@@example.com", "support@localhost", " support@example.com"})
    void rejectsInvalidEmailTargets(String value) {
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.of(value));
    }
}
