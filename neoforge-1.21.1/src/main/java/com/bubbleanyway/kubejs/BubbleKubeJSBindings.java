package com.bubbleanyway.kubejs;

import com.bubbleanyway.client.BubbleOverlay;
import com.bubbleanyway.client.BubbleClientClickEvent;
import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import java.util.function.Consumer;

public final class BubbleKubeJSBindings {
    private static volatile Consumer<BubbleKubeJSClientClickEvent> clickListener;

    private BubbleKubeJSBindings() {
    }

    public static void show(String text) {
        BubbleOverlay.enqueue(BubbleSpec.simple(text));
    }

    public static void showJson(String json) {
        java.util.Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        BubbleOverlay.enqueue(theme.isPresent()
                ? BubbleThemeManager.resolve(theme.get(), BubbleThemeManager.withoutThemeField(json))
                : BubbleSpec.fromJson(json));
    }

    public static void showTheme(String themeId, String text) {
        showThemeJson(themeId, BubbleThemeManager.overridesWithText(text));
    }

    public static void showThemeJson(String themeId, String overridesJson) {
        BubbleOverlay.enqueue(BubbleThemeManager.resolve(themeId, overridesJson));
    }

    public static void clear() {
        BubbleOverlay.clear();
    }

    public static void onClick(Consumer<BubbleKubeJSClientClickEvent> listener) {
        clickListener = listener;
    }

    public static void dispatchClick(BubbleClientClickEvent event) {
        BubbleKubeJSClientClickEvent scriptEvent = new BubbleKubeJSClientClickEvent(event);
        Consumer<BubbleKubeJSClientClickEvent> listener = clickListener;
        if (listener == null) {
            return;
        }
        try {
            listener.accept(scriptEvent);
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("Bubble Anyway KubeJS client click listener failed", exception);
        }
    }
}
