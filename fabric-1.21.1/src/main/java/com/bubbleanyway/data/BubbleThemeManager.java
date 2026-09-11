package com.bubbleanyway.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.util.Identifier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

/** Loads reusable bubble styles from datapacks and the local config file. */
public final class BubbleThemeManager {
    public static final String DATA_DIRECTORY = "bubble_anyway/themes";
    public static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bubble_anyway/themes.json");

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<Map<Identifier, JsonObject>> THEMES =
            new AtomicReference<>(Map.of());
    private static volatile Runnable reloadListener = () -> {
    };

    private BubbleThemeManager() {
    }

    public static void setReloadListener(Runnable listener) {
        reloadListener = listener == null ? () -> {
        } : listener;
    }

    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of("bubble_anyway", "themes");
            }

            @Override
            public void reload(ResourceManager resourceManager) {
                Map<Identifier, JsonObject> loaded = readResourceThemes(resourceManager);
                loaded.putAll(readConfigThemes());
                THEMES.set(Map.copyOf(loaded));
                LOGGER.info("Loaded {} Bubble Anyway theme(s)", loaded.size());
                reloadListener.run();
            }
        });
    }

    private static Map<Identifier, JsonObject> readResourceThemes(ResourceManager resourceManager) {
        Map<Identifier, JsonObject> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> entry : resourceManager
                .findResources(DATA_DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = entry.getValue().getReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element.isJsonObject()) {
                    result.put(themeIdFromResource(entry.getKey()), element.getAsJsonObject().deepCopy());
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Ignoring invalid Bubble Anyway theme {}", entry.getKey(), exception);
            }
        }
        return result;
    }

    public static Optional<JsonObject> find(String themeId) {
        Identifier id = parseId(themeId);
        return id == null ? Optional.empty() : Optional.ofNullable(readConfigThemes().get(id));
    }

    public static BubbleSpec resolve(String themeId, String overridesJson) {
        return BubbleSpec.fromJson(mergeJson(readConfigThemes(), themeId, overridesJson));
    }

    public static String overridesWithText(String text) {
        JsonObject object = new JsonObject();
        object.addProperty("text", text == null ? "" : text);
        return GSON.toJson(object);
    }

    public static Optional<String> themeFromJson(String json) {
        JsonObject object = parseObject(json, "Bubble config");
        JsonElement value = object.get("theme");
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return Optional.empty();
        }
        String theme = value.getAsString();
        return theme.isBlank() ? Optional.empty() : Optional.of(theme);
    }

    public static String withoutThemeField(String json) {
        JsonObject object = parseObject(json, "Bubble config");
        object.remove("theme");
        return GSON.toJson(object);
    }

    public static String mergeJson(Map<Identifier, JsonObject> themes, String themeId, String overridesJson) {
        Identifier id = parseId(themeId);
        if (id == null) {
            throw new IllegalArgumentException("Invalid bubble theme id: " + themeId);
        }

        JsonObject theme = themes.get(id);
        if (theme == null) {
            throw new IllegalArgumentException("Unknown bubble theme: " + id);
        }

        JsonObject merged = theme.deepCopy();
        JsonObject overrides = parseObject(overridesJson, "Theme overrides");
        for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
            merged.add(entry.getKey(), entry.getValue().deepCopy());
        }
        return GSON.toJson(merged);
    }

    public static Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, JsonObject> entry : readConfigThemes().entrySet()) {
            result.put(entry.getKey().toString(), GSON.toJson(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    public static Optional<String> encoded(String themeId) {
        Identifier id = parseId(themeId);
        JsonObject theme = id == null ? null : readConfigThemes().get(id);
        return theme == null ? Optional.empty() : Optional.of(GSON.toJson(theme));
    }

    public static Map<Identifier, JsonObject> readConfigThemes() {
        if (!Files.isRegularFile(CONFIG_FILE)) {
            return Map.of();
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                LOGGER.warn("Ignoring {} because the root value is not an object", CONFIG_FILE);
                return Map.of();
            }

            JsonObject container = root.getAsJsonObject();
            JsonElement themesElement = container.get("themes");
            JsonObject themeEntries = themesElement != null && themesElement.isJsonObject()
                    ? themesElement.getAsJsonObject()
                    : container;
            Map<Identifier, JsonObject> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : themeEntries.entrySet()) {
                JsonObject definition = asObject(entry.getValue(), entry.getKey());
                Identifier id = parseId(entry.getKey());
                if (definition != null && id != null) {
                    result.put(id, definition.deepCopy());
                }
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to load Bubble Anyway themes from {}", CONFIG_FILE, exception);
            return Map.of();
        }
    }

    public static Map<Identifier, JsonObject> parseSyncedThemes(Map<String, String> encodedThemes) {
        Map<Identifier, JsonObject> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : encodedThemes.entrySet()) {
            Identifier id = parseId(entry.getKey());
            if (id == null) {
                LOGGER.warn("Ignoring synced Bubble Anyway theme with invalid id {}", entry.getKey());
                continue;
            }
            try {
                JsonObject definition = parseObject(entry.getValue(), "Synced theme " + id);
                result.put(id, definition);
            } catch (RuntimeException exception) {
                LOGGER.warn("Ignoring invalid synced Bubble Anyway theme {}", id, exception);
            }
        }
        return Map.copyOf(result);
    }

    public static Identifier parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.contains(":")) {
            normalized = BubbleAnywayId.DEFAULT_NAMESPACE + ":" + normalized;
        }
        return Identifier.tryParse(normalized);
    }

    private static Identifier themeIdFromResource(Identifier resourceId) {
        String path = resourceId.getPath();
        String prefix = DATA_DIRECTORY + "/";
        if (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return Identifier.of(resourceId.getNamespace(), path);
    }

    private static JsonObject asObject(JsonElement element, String source) {
        if (element == null || !element.isJsonObject()) {
            LOGGER.warn("Ignoring Bubble Anyway theme {} because it is not a JSON object", source);
            return null;
        }
        return element.getAsJsonObject();
    }

    private static JsonObject parseObject(String json, String description) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(description + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static final class BubbleAnywayId {
        private static final String DEFAULT_NAMESPACE = "bubble_anyway";

        private BubbleAnywayId() {
        }
    }
}
