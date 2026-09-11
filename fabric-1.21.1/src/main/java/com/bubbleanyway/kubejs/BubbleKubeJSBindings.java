package com.bubbleanyway.kubejs;

import com.bubbleanyway.client.BubbleOverlay;
import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.client.BubbleThemeClientCache;

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
        showThemeJson(themeId, BubbleThemeManager.overridesWithText(text));
    }

    public static void showThemeJson(String themeId, String overridesJson) {
        BubbleThemeClientCache.enqueue(themeId, overridesJson);
    }

    public static void clear() {
        BubbleOverlay.clear();
    }
}
