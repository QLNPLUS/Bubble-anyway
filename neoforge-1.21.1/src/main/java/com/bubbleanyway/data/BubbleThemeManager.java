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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/** Loads reusable bubble styles from datapacks and the local config file. */
public final class BubbleThemeManager {
    public static final String DATA_DIRECTORY = "bubble_anyway/themes";
    public static final Path CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("bubble_anyway/themes.json");

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<Map<ResourceLocation, JsonObject>> THEMES =
            new AtomicReference<>(Map.of());
    private static volatile Runnable reloadListener = () -> {
    };

    private BubbleThemeManager() {
    }

    public static void setReloadListener(Runnable listener) {
        reloadListener = listener == null ? () -> {
        } : listener;
    }

    public static void registerReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(GSON, DATA_DIRECTORY) {
            @Override
            protected void apply(
                    Map<ResourceLocation, JsonElement> resources,
                    ResourceManager resourceManager,
                    net.minecraft.util.profiling.ProfilerFiller profiler) {
                Map<ResourceLocation, JsonObject> loaded = new LinkedHashMap<>();
                for (Map.Entry<ResourceLocation, JsonElement> entry : resources.entrySet()) {
                    JsonObject definition = asObject(entry.getValue(), entry.getKey().toString());
                    if (definition != null) {
                        loaded.put(themeIdFromResource(entry.getKey()), definition.deepCopy());
                    }
                }

                loaded.putAll(readConfigThemes());
                THEMES.set(Map.copyOf(loaded));
                LOGGER.info("Loaded {} Bubble Anyway theme(s)", loaded.size());
                try {
                    reloadListener.run();
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not notify clients about the Bubble Anyway theme reload", exception);
                }
            }
        });
    }

    public static Optional<JsonObject> find(String themeId) {
        ResourceLocation id = parseId(themeId);
        return id == null ? Optional.empty() : Optional.ofNullable(effectiveThemes().get(id));
    }

    public static BubbleSpec resolve(String themeId, String overridesJson) {
        return BubbleSpec.fromJson(mergeJson(effectiveThemes(), themeId, overridesJson));
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

    public static String mergeJson(Map<ResourceLocation, JsonObject> themes, String themeId, String overridesJson) {
        ResourceLocation id = parseId(themeId);
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
        for (Map.Entry<ResourceLocation, JsonObject> entry : effectiveThemes().entrySet()) {
            result.put(entry.getKey().toString(), GSON.toJson(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    public static Optional<String> encoded(String themeId) {
        ResourceLocation id = parseId(themeId);
        JsonObject theme = id == null ? null : effectiveThemes().get(id);
        return theme == null ? Optional.empty() : Optional.of(GSON.toJson(theme));
    }

    private static Map<ResourceLocation, JsonObject> effectiveThemes() {
        Map<ResourceLocation, JsonObject> themes = new LinkedHashMap<>(THEMES.get());
        // Commands can run before the first server reload listener has applied. Keep the
        // bundled/config catalog usable in that short window instead of rejecting a valid theme.
        themes.putAll(readConfigThemes());
        return themes;
    }

    public static Map<ResourceLocation, JsonObject> readConfigThemes() {
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
            Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : themeEntries.entrySet()) {
                JsonObject definition = asObject(entry.getValue(), entry.getKey());
                ResourceLocation id = parseId(entry.getKey());
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

    public static Map<ResourceLocation, JsonObject> parseSyncedThemes(Map<String, String> encodedThemes) {
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : encodedThemes.entrySet()) {
            ResourceLocation id = parseId(entry.getKey());
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

    public static ResourceLocation parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.contains(":")) {
            normalized = BubbleAnywayId.DEFAULT_NAMESPACE + ":" + normalized;
        }
        return ResourceLocation.tryParse(normalized);
    }

    private static ResourceLocation themeIdFromResource(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        String prefix = DATA_DIRECTORY + "/";
        if (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return ResourceLocation.fromNamespaceAndPath(resourceId.getNamespace(), path);
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
