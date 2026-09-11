package com.bubbleanyway.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;

/** Creates the bundled theme catalog without overwriting an existing user file. */
public final class BubbleThemeDefaults {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE = "config/bubble_anyway/themes.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BubbleThemeDefaults() {
    }

    public static void ensure(Path configDirectory) {
        Path target = configDirectory.resolve("bubble_anyway/themes.json");
        try (InputStream input = BubbleThemeDefaults.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                LOGGER.warn("Bundled Bubble Anyway theme catalog is missing: {}", RESOURCE);
                return;
            }
            Files.createDirectories(target.getParent());
            JsonObject bundled = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!Files.isRegularFile(target)) {
                Files.writeString(target, GSON.toJson(bundled));
                LOGGER.info("Created default Bubble Anyway theme catalog at {}", target);
                return;
            }

            JsonElement existingElement = JsonParser.parseString(Files.readString(target));
            if (!existingElement.isJsonObject()) {
                LOGGER.warn("Keeping existing Bubble Anyway themes because {} is not a JSON object", target);
                return;
            }
            JsonObject existing = existingElement.getAsJsonObject();
            JsonObject bundledThemes = themesObject(bundled);
            JsonObject existingThemes = themesObject(existing);
            boolean changed = false;
            for (var entry : bundledThemes.entrySet()) {
                if (!existingThemes.has(entry.getKey())) {
                    existingThemes.add(entry.getKey(), entry.getValue().deepCopy());
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(target, GSON.toJson(existing));
                LOGGER.info("Added missing default Bubble Anyway themes to {}", target);
            }
        } catch (IOException exception) {
            LOGGER.warn("Could not create default Bubble Anyway theme catalog at {}", target, exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not merge default Bubble Anyway theme catalog at {}", target, exception);
        }
    }

    private static JsonObject themesObject(JsonObject root) {
        JsonElement themes = root.get("themes");
        return themes != null && themes.isJsonObject() ? themes.getAsJsonObject() : root;
    }
}
