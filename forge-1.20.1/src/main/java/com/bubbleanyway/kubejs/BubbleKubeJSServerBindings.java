package com.bubbleanyway.kubejs;

import java.util.function.Consumer;

/** @deprecated Use {@link BubbleKubeJSServerApi}. */
@Deprecated
public final class BubbleKubeJSServerBindings {
    private BubbleKubeJSServerBindings() {
    }

    public static void show(Object player, String text) {
        BubbleKubeJSServerApi.show(player, text);
    }

    public static void showJson(Object player, String json) {
        BubbleKubeJSServerApi.showJson(player, json);
    }

    public static void showAll(Object server, String text) {
        BubbleKubeJSServerApi.showAll(server, text);
    }

    public static void showAllJson(Object server, String json) {
        BubbleKubeJSServerApi.showAllJson(server, json);
    }

    public static void showTheme(Object player, String themeId, String text) {
        BubbleKubeJSServerApi.showTheme(player, themeId, text);
    }

    public static void showThemeJson(Object player, String themeId, String overridesJson) {
        BubbleKubeJSServerApi.showThemeJson(player, themeId, overridesJson);
    }

    public static void showAllTheme(Object server, String themeId, String text) {
        BubbleKubeJSServerApi.showAllTheme(server, themeId, text);
    }

    public static void showAllThemeJson(Object server, String themeId, String overridesJson) {
        BubbleKubeJSServerApi.showAllThemeJson(server, themeId, overridesJson);
    }

    public static void clear(Object player) {
        BubbleKubeJSServerApi.clear(player);
    }

    public static void clearAll(Object server) {
        BubbleKubeJSServerApi.clearAll(server);
    }

    public static void onClick(Consumer<BubbleKubeJSServerClickEvent> listener) {
        BubbleKubeJSServerApi.onClick(listener);
    }
}
