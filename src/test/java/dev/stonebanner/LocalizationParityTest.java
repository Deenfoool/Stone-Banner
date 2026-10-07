package dev.stonebanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalizationParityTest {
    @Test
    void russianAndEnglishLocalizationContainTheSameKeys() {
        JsonObject english = load("assets/stonebanner/lang/en_us.json");
        JsonObject russian = load("assets/stonebanner/lang/ru_ru.json");

        assertEquals(english.keySet(), russian.keySet());
        for (String key : english.keySet()) {
            assertEquals(placeholders(english.get(key).getAsString()),
                    placeholders(russian.get(key).getAsString()), "Placeholder mismatch: " + key);
        }
    }

    private static long placeholders(String text) {
        return java.util.regex.Pattern.compile("%(?:\\d+\\$)?[sd]").matcher(text).results().count();
    }

    private static JsonObject load(String path) {
        InputStream stream = LocalizationParityTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "Missing classpath resource: " + path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("Unable to read classpath resource: " + path, exception);
        }
    }
}
