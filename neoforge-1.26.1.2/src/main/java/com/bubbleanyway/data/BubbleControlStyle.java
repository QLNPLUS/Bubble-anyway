package com.bubbleanyway.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;

/** Visual states shared by bubble controls through a theme. */
public final class BubbleControlStyle {
    public static final int DEFAULT_NORMAL_BACKGROUND = 0xFF3B4350;
    public static final int DEFAULT_HOVER_BACKGROUND = 0xFF566176;
    public static final int DEFAULT_PRESSED_BACKGROUND = 0xFF2A303B;
    public static final int DEFAULT_DISABLED_BACKGROUND = 0xFF30343C;
    public static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_DISABLED_TEXT_COLOR = 0xFF9AA1AB;

    private final State normal;
    private final State hover;
    private final State pressed;
    private final State disabled;

    public BubbleControlStyle(State normal, State hover, State pressed, State disabled) {
        this.normal = normal == null ? State.defaults(DEFAULT_NORMAL_BACKGROUND, DEFAULT_TEXT_COLOR) : normal;
        this.hover = hover == null ? this.normal : hover;
        this.pressed = pressed == null ? this.normal : pressed;
        this.disabled = disabled == null
                ? State.defaults(DEFAULT_DISABLED_BACKGROUND, DEFAULT_DISABLED_TEXT_COLOR)
                : disabled;
    }

    public static BubbleControlStyle defaults() {
        return new BubbleControlStyle(
                State.defaults(DEFAULT_NORMAL_BACKGROUND, DEFAULT_TEXT_COLOR),
                State.defaults(DEFAULT_HOVER_BACKGROUND, DEFAULT_TEXT_COLOR),
                State.defaults(DEFAULT_PRESSED_BACKGROUND, DEFAULT_TEXT_COLOR),
                State.defaults(DEFAULT_DISABLED_BACKGROUND, DEFAULT_DISABLED_TEXT_COLOR));
    }

    public static BubbleControlStyle fromJson(JsonElement element, BubbleControlStyle fallback) {
        BubbleControlStyle base = fallback == null ? defaults() : fallback;
        if (element == null || !element.isJsonObject()) {
            return base;
        }
        JsonObject object = element.getAsJsonObject();
        State flat = State.fromJson(object, base.normal);
        State normal = State.fromJson(object.get("normal"), flat);
        State hover = State.fromJson(object.get("hover"), normal);
        State pressed = State.fromJson(object.get("pressed"), hover);
        State disabled = State.fromJson(object.get("disabled"), base.disabled);
        return new BubbleControlStyle(normal, hover, pressed, disabled);
    }

    public State state(InteractionState state) {
        return switch (state == null ? InteractionState.NORMAL : state) {
            case HOVER -> hover;
            case PRESSED -> pressed;
            case DISABLED -> disabled;
            case NORMAL -> normal;
        };
    }

    public State normal() {
        return normal;
    }

    public State hover() {
        return hover;
    }

    public State pressed() {
        return pressed;
    }

    public State disabled() {
        return disabled;
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.add("normal", normal.toJson());
        object.add("hover", hover.toJson());
        object.add("pressed", pressed.toJson());
        object.add("disabled", disabled.toJson());
        return object;
    }

    public enum InteractionState {
        NORMAL,
        HOVER,
        PRESSED,
        DISABLED
    }

    public record State(
            int backgroundColor,
            String backgroundTexture,
            int backgroundBorder,
            int backgroundGuide,
            int textColor,
            int padding,
            float scale,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated,
            boolean shadow) {
        public State {
            backgroundTexture = backgroundTexture == null ? "" : backgroundTexture;
            backgroundBorder = clamp(backgroundBorder, 0, 1024);
            backgroundGuide = clamp(backgroundGuide, 0, 16);
            padding = clamp(padding, 0, 64);
            scale = clamp(scale, 0.5F, 4.0F);
        }

        public static State defaults(int backgroundColor, int textColor) {
            return new State(backgroundColor, "", 0, 0, textColor, 6, 1.0F,
                    false, false, false, false, false, true);
        }

        public JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("backgroundColor", String.format(Locale.ROOT, "#%08X", backgroundColor));
            if (!backgroundTexture.isBlank()) object.addProperty("background", backgroundTexture);
            if (backgroundBorder > 0) object.addProperty("backgroundBorder", backgroundBorder);
            if (backgroundGuide > 0) object.addProperty("backgroundGuide", backgroundGuide);
            object.addProperty("textColor", String.format(Locale.ROOT, "#%08X", textColor));
            object.addProperty("padding", padding);
            object.addProperty("scale", scale);
            object.addProperty("bold", bold);
            object.addProperty("italic", italic);
            object.addProperty("underlined", underlined);
            object.addProperty("strikethrough", strikethrough);
            object.addProperty("obfuscated", obfuscated);
            object.addProperty("shadow", shadow);
            return object;
        }

        private static State fromJson(JsonElement element, State fallback) {
            if (element == null || !element.isJsonObject()) {
                return fallback;
            }
            JsonObject object = element.getAsJsonObject();
            return fromJson(object, fallback);
        }

        private static State fromJson(JsonObject object, State fallback) {
            if (object == null) {
                return fallback;
            }
            return new State(
                    color(object, "backgroundColor", fallback.backgroundColor),
                    string(object, "background", fallback.backgroundTexture),
                    integer(object, "backgroundBorder", fallback.backgroundBorder),
                    integer(object, "backgroundGuide", fallback.backgroundGuide),
                    color(object, "textColor", color(object, "color", fallback.textColor)),
                    integer(object, "padding", fallback.padding),
                    decimal(object, "scale", decimal(object, "fontSize", fallback.scale)),
                    bool(object, "bold", fallback.bold),
                    bool(object, "italic", fallback.italic),
                    bool(object, "underlined", fallback.underlined),
                    bool(object, "strikethrough", fallback.strikethrough),
                    bool(object, "obfuscated", fallback.obfuscated),
                    bool(object, "shadow", fallback.shadow));
        }

        private static String string(JsonObject object, String key, String fallback) {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsString();
        }

        private static int integer(JsonObject object, String key, int fallback) {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        }

        private static float decimal(JsonObject object, String key, float fallback) {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsFloat();
        }

        private static boolean bool(JsonObject object, String key, boolean fallback) {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
        }

        private static int color(JsonObject object, String key, int fallback) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) {
                return fallback;
            }
            String normalized = value.getAsString().trim();
            if (normalized.startsWith("#")) {
                normalized = normalized.substring(1);
                if (normalized.length() == 6) {
                    normalized = "FF" + normalized;
                }
                if (normalized.length() != 8) {
                    throw new IllegalArgumentException("Color must be #RRGGBB or #AARRGGBB");
                }
                return (int) Long.parseLong(normalized, 16);
            }
            return Integer.decode(normalized);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
