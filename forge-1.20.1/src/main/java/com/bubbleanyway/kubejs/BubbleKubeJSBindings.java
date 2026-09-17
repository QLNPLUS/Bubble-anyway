package com.bubbleanyway.kubejs;

import com.bubbleanyway.client.BubbleOverlay;
import com.bubbleanyway.client.BubbleThemeClientCache;
import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.client.BubbleClientClickEvent;
import java.util.List;
import java.util.function.Consumer;

public final class BubbleKubeJSBindings {
    /** KubeJS replaces script callbacks during reload, so only the current callback is retained. */
    private static volatile Consumer<BubbleKubeJSClientClickEvent> clickListener;
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

    /** Registers a KubeJS-compatible callback for client-side visible control clicks. */
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

    private static String quote(String value) {
        String escaped = value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}
