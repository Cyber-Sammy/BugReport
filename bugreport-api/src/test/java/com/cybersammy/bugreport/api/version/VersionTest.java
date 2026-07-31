package com.cybersammy.bugreport.api.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

final class VersionTest {
    @Test
    void parsesIndependentVersionDomains() {
        ApiVersion apiVersion = ApiVersion.parse("1.2.3-alpha.1+build.5");

        assertEquals(1, apiVersion.major());
        assertEquals(2, apiVersion.minor());
        assertEquals(3, apiVersion.patch());
        assertEquals("alpha.1", apiVersion.preRelease().orElseThrow());
        assertEquals("build.5", apiVersion.buildMetadata().orElseThrow());
        assertEquals("1.2.3-alpha.1+build.5", apiVersion.toString());
        assertEquals(new SchemaVersion(1, 0), SchemaVersion.parse("1.0"));
        assertEquals(new CapabilityVersion(2, 4), CapabilityVersion.parse("2.4"));
    }

    @Test
    void acceptsMaximumBoundedCoreComponents() {
        ApiVersion version = ApiVersion.parse("2147483647.2147483647.2147483647");

        assertEquals(Integer.MAX_VALUE, version.major());
        assertEquals(Integer.MAX_VALUE, version.minor());
        assertEquals(Integer.MAX_VALUE, version.patch());
    }

    @Test
    void equalityUsesExactTextIncludingBuildMetadata() {
        ApiVersion first = ApiVersion.parse("1.2.3+build1");
        ApiVersion same = ApiVersion.parse("1.2.3+build1");
        ApiVersion differentBuild = ApiVersion.parse("1.2.3+build2");

        assertEquals(first, same);
        assertNotEquals(first, differentBuild);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1", "1.2", "01.2.3", "1.02.3", "1.2.3-01", "1.2.3+"})
    void rejectsNonCanonicalApiVersions(String value) {
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse(value));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2147483648.0.0",
                "0.2147483648.0",
                "0.0.2147483648"
            })
    void rejectsCoreComponentsOutsideDocumentedRange(String value) {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse(value));

        assertTrue(exception.getMessage().contains("0..2147483647"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "1",
                "1.0.0",
                "01.0",
                "1.-1",
                "999999999999999999999.0"
            })
    void rejectsNonCanonicalMajorMinorVersions(String value) {
        assertThrows(IllegalArgumentException.class, () -> SchemaVersion.parse(value));
        assertThrows(IllegalArgumentException.class, () -> CapabilityVersion.parse(value));
    }
}
