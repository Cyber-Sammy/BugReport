package com.cybersammy.bugreport.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cybersammy.bugreport.api.localization.LocalizationKey;
import com.cybersammy.bugreport.api.specification.StandardFields;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StandardFieldLocalizationTest {
    private static final String ENGLISH_LANGUAGE_RESOURCE =
            "assets/bugreport/lang/en_us.json";

    @Test
    void englishLanguageFileCoversEveryStandardFieldKey() throws IOException {
        Map<String, String> translations;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(ENGLISH_LANGUAGE_RESOURCE)) {
            assertNotNull(input, "Missing " + ENGLISH_LANGUAGE_RESOURCE);
            translations = parseLanguage(new InputStreamReader(input, StandardCharsets.UTF_8));
        }

        StandardFields.all().forEach(field -> {
            assertContains(translations, field.labelKey());
            assertContains(translations, field.descriptionKey().orElseThrow());
        });
    }

    @Test
    void languageParserRejectsDuplicateKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parseLanguage(new StringReader("{\"key\":\"first\",\"key\":\"second\"}")));
    }

    @Test
    void languageParserRejectsNonStringValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parseLanguage(new StringReader("{\"key\":42}")));
    }

    @Test
    void languageParserRejectsNonObjectRoot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parseLanguage(new StringReader("[]")));
    }

    private static Map<String, String> parseLanguage(Reader source) throws IOException {
        try (JsonReader reader = new JsonReader(source)) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IllegalArgumentException("Language root must be a JSON object");
            }

            Map<String, String> translations = new LinkedHashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (translations.containsKey(key)) {
                    throw new IllegalArgumentException("Duplicate localization key: " + key);
                }
                if (reader.peek() != JsonToken.STRING) {
                    throw new IllegalArgumentException(
                            "Localization value must be a string: " + key);
                }
                translations.put(key, reader.nextString());
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Unexpected data after language object");
            }
            return Map.copyOf(translations);
        }
    }

    private static void assertContains(
            Map<String, String> translations, LocalizationKey key) {
        assertTrue(
                translations.containsKey(key.value()),
                () -> "Missing English localization for " + key);
        assertFalse(
                translations.get(key.value()).isBlank(),
                () -> "Blank English localization for " + key);
    }
}
