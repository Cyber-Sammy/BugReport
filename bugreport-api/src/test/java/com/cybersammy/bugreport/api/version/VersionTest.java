package com.cybersammy.bugreport.api.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1", "1.2", "01.2.3", "1.02.3", "1.2.3-01", "1.2.3+"})
    void rejectsNonCanonicalApiVersions(String value) {
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse(value));
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
