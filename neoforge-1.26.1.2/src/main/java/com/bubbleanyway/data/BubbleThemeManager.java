package com.bubbleanyway.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.neoforged.fml.loading.FMLPaths;

/** Resolves local theme presets from config/bubble_anyway/themes.json. */
public final class BubbleThemeManager {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("bubble_anyway/themes.json");

    private BubbleThemeManager() {
    }

    public static BubbleSpec resolve(String themeId, String overridesJson) {
        JsonObject theme = readThemes().get(canonicalId(themeId));
        if (theme == null) {
            throw new IllegalArgumentException("Unknown bubble theme: " + themeId);
        }
        JsonObject merged = theme.deepCopy();
        for (Map.Entry<String, JsonElement> entry : parseObject(overridesJson).entrySet()) {
            merged.add(entry.getKey(), entry.getValue().deepCopy());
        }
        return BubbleSpec.fromJson(GSON.toJson(merged));
    }

    public static Optional<String> themeFromJson(String json) {
        JsonElement value = parseObject(json).get("theme");
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive() || value.getAsString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.getAsString());
    }

    public static String withoutThemeField(String json) {
        JsonObject object = parseObject(json);
        object.remove("theme");
        return GSON.toJson(object);
    }

    public static String overridesWithText(String text) {
        JsonObject object = new JsonObject();
        object.addProperty("text", text == null ? "" : text);
        return GSON.toJson(object);
    }

    private static Map<String, JsonObject> readThemes() {
        if (!Files.isRegularFile(CONFIG_FILE)) {
            return Map.of();
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement themes = root.get("themes");
            JsonObject entries = themes != null && themes.isJsonObject() ? themes.getAsJsonObject() : root;
            Map<String, JsonObject> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    result.put(canonicalId(entry.getKey()), entry.getValue().getAsJsonObject().deepCopy());
                }
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            return Map.of();
        }
    }

    private static JsonObject parseObject(String json) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Bubble config must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static String canonicalId(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.contains(":") ? normalized : "bubble_anyway:" + normalized;
    }
}
