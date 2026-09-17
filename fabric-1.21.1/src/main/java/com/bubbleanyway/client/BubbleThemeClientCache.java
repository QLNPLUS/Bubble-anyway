package com.bubbleanyway.client;

import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

/** Client cache for themes received from a server or loaded from the local config. */
public final class BubbleThemeClientCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicReference<Map<Identifier, JsonObject>> SERVER_THEMES =
            new AtomicReference<>(Map.of());
    private static final AtomicReference<Map<Identifier, JsonObject>> LOCAL_THEMES =
            new AtomicReference<>(Map.of());
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Map<String, List<PendingTheme>> PENDING = new LinkedHashMap<>();
    private static volatile Consumer<String> THEME_REQUESTER = ignored -> {
    };
    private static volatile boolean localThemesLoaded;

    private BubbleThemeClientCache() {
    }

    public static void setThemeRequester(Consumer<String> requester) {
        THEME_REQUESTER = requester == null ? ignored -> {
        } : requester;
    }

    public static void mergeServerThemes(Map<String, String> encodedThemes) {
        refreshLocalThemes();
        Map<Identifier, JsonObject> merged = new LinkedHashMap<>(SERVER_THEMES.get());
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
        Map<Identifier, JsonObject> themes = new LinkedHashMap<>(SERVER_THEMES.get());
        themes.putAll(LOCAL_THEMES.get());
        return BubbleSpec.fromJson(BubbleThemeManager.mergeJson(themes, themeId, overridesJson));
    }

    public static boolean hasTheme(String themeId) {
        Identifier id = BubbleThemeManager.parseId(themeId);
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
            THEME_REQUESTER.accept(canonicalId);
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
        LOCAL_THEMES.set(Map.copyOf(new LinkedHashMap<>(BubbleThemeManager.readConfigThemes())));
        localThemesLoaded = true;
    }

    private static String canonicalId(String themeId) {
        Identifier parsed = BubbleThemeManager.parseId(themeId);
        return parsed == null ? themeId : parsed.toString();
    }

    private record PendingTheme(String overridesJson, boolean serverSourced) {
    }
}
