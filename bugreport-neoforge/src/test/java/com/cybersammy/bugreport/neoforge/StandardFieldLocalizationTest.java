package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.StandardFields;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class StandardFieldLocalizationTest {
    private static final String ENGLISH_LANGUAGE_RESOURCE =
            "assets/bugreport/lang/en_us.json";

    @Test
    void englishLanguageFileCoversEveryStandardFieldKey() throws IOException {
        String languageFile;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(ENGLISH_LANGUAGE_RESOURCE)) {
            assertNotNull(input, "Missing " + ENGLISH_LANGUAGE_RESOURCE);
            languageFile = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        StandardFields.all().forEach(field -> {
            assertContains(languageFile, field.labelKey());
            assertContains(languageFile, field.descriptionKey().orElseThrow());
        });
    }

    private static void assertContains(String languageFile, LocalizationKey key) {
        assertTrue(
                Pattern.compile("\\\"" + Pattern.quote(key.value()) + "\\\"\\s*:")
                        .matcher(languageFile)
                        .find(),
                () -> "Missing English localization for " + key);
    }
}
