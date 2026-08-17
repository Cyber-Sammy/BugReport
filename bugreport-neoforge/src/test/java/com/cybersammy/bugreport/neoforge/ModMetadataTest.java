package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class ModMetadataTest {
    private static final String METADATA_RESOURCE = "META-INF/neoforge.mods.toml";
    private static final String LOGO_RESOURCE = "bugreport_icon.png";

    @Test
    void presentsReleaseIdentityAndProjectLinks() throws IOException {
        String metadata = readText(METADATA_RESOURCE);

        assertTrue(metadata.contains("displayName=\"Bug Report\""));
        assertTrue(metadata.contains("authors=\"Cyber-Sammy\""));
        assertTrue(metadata.contains(
                "displayURL=\"https://modrinth.com/mod/bugreportmod\""));
        assertTrue(metadata.contains("modUrl=\"https://modrinth.com/mod/bugreportmod\""));
        assertTrue(metadata.contains(
                "issueTrackerURL=\"https://github.com/Cyber-Sammy/BugReport/issues\""));
        assertTrue(metadata.contains("logoFile=\"" + LOGO_RESOURCE + "\""));
        assertTrue(metadata.contains("logoBlur=false"));
        assertTrue(metadata.contains("does not upload or transmit report data automatically"));
    }

    @Test
    void packagesSquareHighResolutionPngLogoAtJarRoot() throws IOException {
        try (InputStream input = resource(LOGO_RESOURCE)) {
            BufferedImage logo = ImageIO.read(input);

            assertNotNull(logo, "The packaged mod logo must be a readable image");
            assertEquals(logo.getWidth(), logo.getHeight());
            assertTrue(logo.getWidth() >= 512, "The packaged mod logo must remain high resolution");
        }
    }

    private static String readText(String path) throws IOException {
        try (InputStream input = resource(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream resource(String path) {
        InputStream input = ModMetadataTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(input, "Missing test resource " + path);
        return input;
    }
}
