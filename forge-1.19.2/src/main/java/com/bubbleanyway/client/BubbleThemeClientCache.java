package com.bubbleanyway.client;

import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

/** Client cache for themes received from a server or loaded from the local config. */
public final class BubbleThemeClientCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<Map<ResourceLocation, JsonObject>> SERVER_THEMES =
            new AtomicReference<>(Map.of());
    private static final AtomicReference<Map<ResourceLocation, JsonObject>> LOCAL_THEMES =
            new AtomicReference<>(Map.of());
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Map<String, List<PendingTheme>> PENDING = new LinkedHashMap<>();
    private static volatile boolean localThemesLoaded;

    private BubbleThemeClientCache() {
    }

    public static void mergeServerThemes(Map<String, String> encodedThemes) {
        refreshLocalThemes();
        Map<ResourceLocation, JsonObject> merged = new LinkedHashMap<>(SERVER_THEMES.get());
        merged.putAll(BubbleThemeManager.parseSyncedThemes(encodedThemes));
        SERVER_THEMES.set(Map.copyOf(merged));

        finishRequests(encodedThemes);
    }

    public static void replaceServerThemes(Map<String, String> encodedThemes) {
        refreshLocalThemes();
        SERVER_THEMES.set(BubbleThemeManager.parseSyncedThemes(encodedThemes));

        finishRequests(encodedThemes);
    }

    private static void finishRequests(Map<String, String> encodedThemes) {
        for (String themeId : encodedThemes.keySet()) {
            String canonicalId = canonicalId(themeId);
            List<PendingTheme> pending;
            synchronized (PENDING) {
                REQUESTED.remove(canonicalId);
                pending = PENDING.remove(canonicalId);
            }
            if (pending != null) {
                for (PendingTheme request : pending) {
                    enqueue(canonicalId, request.overridesJson(), request.serverSourced());
                }
            }
        }
    }

    public static boolean enqueue(String themeId, String overridesJson) {
        return enqueue(themeId, overridesJson, false);
    }

    public static boolean enqueue(String themeId, String overridesJson, boolean serverSourced) {
        loadLocalThemesIfNeeded();
        if (!hasTheme(themeId)) {
            return requestFromServer(themeId, overridesJson, serverSourced);
        }

        try {
            return BubbleOverlay.enqueue(resolve(themeId, overridesJson), serverSourced);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not show Bubble Anyway theme {}", themeId, exception);
            return false;
        }
    }

    public static BubbleSpec resolve(String themeId, String overridesJson) {
        loadLocalThemesIfNeeded();
        Map<ResourceLocation, JsonObject> themes = new LinkedHashMap<>(SERVER_THEMES.get());
        themes.putAll(LOCAL_THEMES.get());
        return BubbleSpec.fromJson(BubbleThemeManager.mergeJson(themes, themeId, overridesJson));
    }

    public static boolean hasTheme(String themeId) {
        ResourceLocation id = BubbleThemeManager.parseId(themeId);
        if (id == null) {
            return false;
        }
        return LOCAL_THEMES.get().containsKey(id) || SERVER_THEMES.get().containsKey(id);
    }

    public static void resetServerThemes() {
        SERVER_THEMES.set(Map.of());
        synchronized (PENDING) {
            REQUESTED.clear();
            PENDING.clear();
        }
    }

    public static void clear() {
        resetServerThemes();
        LOCAL_THEMES.set(Map.of());
        localThemesLoaded = false;
    }

    private static boolean requestFromServer(String themeId, String overridesJson, boolean serverSourced) {
        String canonicalId = canonicalId(themeId);
        boolean shouldRequest;
        synchronized (PENDING) {
            PENDING.computeIfAbsent(canonicalId, ignored -> new ArrayList<>())
                    .add(new PendingTheme(overridesJson, serverSourced));
            shouldRequest = REQUESTED.add(canonicalId);
        }
        if (!shouldRequest) {
            return false;
        }
        try {
            BubbleNetwork.requestTheme(canonicalId);
            return false;
        } catch (RuntimeException exception) {
            synchronized (PENDING) {
                REQUESTED.remove(canonicalId);
                PENDING.remove(canonicalId);
            }
            LOGGER.warn("Could not request Bubble Anyway theme {} because no server connection is available", canonicalId);
            return false;
        }
    }

    private static synchronized void loadLocalThemesIfNeeded() {
        if (localThemesLoaded) {
            return;
        }

        refreshLocalThemes();
    }

    public static synchronized void refreshLocalThemes() {
        Map<ResourceLocation, JsonObject> local = new LinkedHashMap<>(readResourceThemes());
        local.putAll(BubbleThemeManager.readConfigThemes());
        LOCAL_THEMES.set(Map.copyOf(local));
        localThemesLoaded = true;
    }

    private static Map<ResourceLocation, JsonObject> readResourceThemes() {
        Map<ResourceLocation, JsonObject> result = new LinkedHashMap<>();
        Minecraft minecraft = Minecraft.getInstance();
        for (Map.Entry<ResourceLocation, Resource> entry : minecraft.getResourceManager()
                .listResources(BubbleThemeManager.DATA_DIRECTORY, resourceId -> resourceId.getPath().endsWith(".json"))
                .entrySet()) {
            ResourceLocation resourceId = entry.getKey();
            ResourceLocation themeId = themeIdFromResource(resourceId);
            try {
                Resource resource = entry.getValue();
                try (Reader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonObject()) {
                        result.put(themeId, element.getAsJsonObject().deepCopy());
                    }
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Ignoring invalid local Bubble Anyway theme {}", themeId, exception);
            }
        }
        return result;
    }

    private static ResourceLocation themeIdFromResource(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        String prefix = BubbleThemeManager.DATA_DIRECTORY + "/";
        if (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        return new ResourceLocation(resourceId.getNamespace(), path);
    }

    private static String canonicalId(String themeId) {
        ResourceLocation parsed = BubbleThemeManager.parseId(themeId);
        return parsed == null ? themeId : parsed.toString();
    }

    private record PendingTheme(String overridesJson, boolean serverSourced) {
    }
}
