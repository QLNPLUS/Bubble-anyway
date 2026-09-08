package com.bubbleanyway.kubejs;

import com.bubbleanyway.client.BubbleOverlay;
import com.bubbleanyway.client.BubbleThemeClientCache;
import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;

public final class BubbleKubeJSBindings {
    private BubbleKubeJSBindings() {
    }

    public static void show(String text) {
        BubbleOverlay.enqueue(BubbleSpec.simple(text));
    }

    public static void showJson(String json) {
        java.util.Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        if (theme.isPresent()) {
            BubbleThemeClientCache.enqueue(theme.get(), BubbleThemeManager.withoutThemeField(json));
        } else {
            BubbleOverlay.enqueue(BubbleSpec.fromJson(json));
        }
    }

    public static void showTheme(String themeId, String text) {
        showThemeJson(themeId, "{\"text\":" + quote(text) + "}");
    }

    public static void showThemeJson(String themeId, String overridesJson) {
        BubbleThemeClientCache.enqueue(themeId, overridesJson);
    }

    public static void clear() {
        BubbleOverlay.clear();
    }

    private static String quote(String value) {
        String escaped = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}
