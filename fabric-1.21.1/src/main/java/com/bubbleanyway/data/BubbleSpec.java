package com.bubbleanyway.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BubbleSpec {
    public static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_BACKGROUND_COLOR = 0xE6111720;
    public static final int DEFAULT_DURATION = 100;
    public static final int DEFAULT_FADE_IN = 8;
    public static final int DEFAULT_FADE_OUT = 12;
    public static final int DEFAULT_SLIDE_IN = 8;
    public static final int DEFAULT_SLIDE_OUT = 12;
    public static final int DEFAULT_MAX_WIDTH = 320;
    public static final int DEFAULT_PADDING = 10;
    public static final int DEFAULT_BACKGROUND_BORDER = 0;
    public static final int DEFAULT_BACKGROUND_GUIDE = 0;
    public static final int DEFAULT_ICON_SIZE = 16;
    public static final int DEFAULT_ICON_GAP = 6;
    public static final int DEFAULT_ICON_OFFSET_X = 0;
    public static final int DEFAULT_ICON_OFFSET_Y = 0;
    public static final int DEFAULT_TEXT_OFFSET_X = 0;
    public static final int DEFAULT_TEXT_OFFSET_Y = 0;
    public static final String DEFAULT_SOUND = "minecraft:ui.button.click";
    public static final float DEFAULT_SOUND_VOLUME = 1.0F;
    public static final float DEFAULT_SOUND_PITCH = 1.0F;
    private static final Gson GSON = new GsonBuilder().create();

    private final String id;
    private final String text;
    private final String iconId;
    private final IconType iconType;
    private final List<TextPart> textParts;
    private final int iconSize;
    private final int iconGap;
    private final int iconOffsetX;
    private final int iconOffsetY;
    private final int textOffsetX;
    private final int textOffsetY;
    private final int textColor;
    private final int backgroundColor;
    private final String backgroundTexture;
    private final int backgroundBorder;
    private final int backgroundGuide;
    private final String sound;
    private final float soundVolume;
    private final float soundPitch;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int maxWidth;
    private final int padding;
    private final TextAlignment textAlignment;
    private final int duration;
    private final int fadeIn;
    private final int fadeOut;
    private final int slideIn;
    private final int slideOut;
    private final int priority;
    private final float scale;
    private final boolean bold;
    private final boolean italic;
    private final boolean underlined;
    private final boolean strikethrough;
    private final boolean obfuscated;
    private final boolean shadow;
    private final boolean replace;
    private final Anchor anchor;
    private final Animation animation;

    public BubbleSpec(
            String id,
            String text,
            String iconId,
            int iconSize,
            int iconGap,
            int iconOffsetX,
            int iconOffsetY,
            int textOffsetX,
            int textOffsetY,
            int textColor,
            int backgroundColor,
            String backgroundTexture,
            int backgroundBorder,
            int backgroundGuide,
            String sound,
            float soundVolume,
            float soundPitch,
            int x,
            int y,
            int width,
            int height,
            int maxWidth,
            int padding,
            TextAlignment textAlignment,
            int duration,
            int fadeIn,
            int fadeOut,
            int priority,
            float scale,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated,
            boolean shadow,
            boolean replace,
            Anchor anchor,
            Animation animation) {
        this(id, text, iconId, iconSize, iconGap,
                iconOffsetX, iconOffsetY, textOffsetX, textOffsetY,
                textColor, backgroundColor, backgroundTexture, backgroundBorder, backgroundGuide,
                sound, soundVolume, soundPitch, x, y, width, height, maxWidth, padding, textAlignment,
                duration, fadeIn, fadeOut, DEFAULT_SLIDE_IN, DEFAULT_SLIDE_OUT, priority, scale,
                bold, italic, underlined, strikethrough, obfuscated, shadow, replace, anchor, animation);
    }

    public BubbleSpec(
            String id,
            String text,
            String iconId,
            IconType iconType,
            List<TextPart> textParts,
            int iconSize,
            int iconGap,
            int iconOffsetX,
            int iconOffsetY,
            int textOffsetX,
            int textOffsetY,
            int textColor,
            int backgroundColor,
            String backgroundTexture,
            int backgroundBorder,
            int backgroundGuide,
            String sound,
            float soundVolume,
            float soundPitch,
            int x,
            int y,
            int width,
            int height,
            int maxWidth,
            int padding,
            TextAlignment textAlignment,
            int duration,
            int fadeIn,
            int fadeOut,
            int slideIn,
            int slideOut,
            int priority,
            float scale,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated,
            boolean shadow,
            boolean replace,
            Anchor anchor,
            Animation animation) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.text = text == null ? "" : text;
        this.iconId = iconId == null ? "" : iconId;
        this.iconType = iconType == null ? IconType.AUTO : iconType;
        this.iconSize = clamp(iconSize, 8, 64);
        this.iconGap = clamp(iconGap, 0, 64);
        this.iconOffsetX = clamp(iconOffsetX, -4096, 4096);
        this.iconOffsetY = clamp(iconOffsetY, -4096, 4096);
        this.textOffsetX = clamp(textOffsetX, -4096, 4096);
        this.textOffsetY = clamp(textOffsetY, -4096, 4096);
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.backgroundTexture = backgroundTexture == null ? "" : backgroundTexture;
        this.backgroundBorder = clamp(backgroundBorder, 0, 1024);
        this.backgroundGuide = clamp(backgroundGuide, 0, 16);
        this.sound = sound == null ? DEFAULT_SOUND : sound;
        this.soundVolume = clamp(soundVolume, 0.0F, 2.0F);
        this.soundPitch = clamp(soundPitch, 0.5F, 2.0F);
        this.x = x;
        this.y = y;
        this.width = clamp(width, 0, 4096);
        this.height = clamp(height, 0, 4096);
        this.maxWidth = clamp(maxWidth, 40, 4096);
        this.padding = clamp(padding, 0, 128);
        this.textAlignment = textAlignment == null ? TextAlignment.LEFT : textAlignment;
        this.duration = clamp(duration, 1, 20 * 60 * 60);
        this.fadeIn = clamp(fadeIn, 0, this.duration);
        this.fadeOut = clamp(fadeOut, 0, this.duration);
        this.slideIn = clamp(slideIn, 0, this.duration);
        this.slideOut = clamp(slideOut, 0, this.duration);
        this.priority = priority;
        this.scale = clamp(scale, 0.5F, 4.0F);
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.shadow = shadow;
        this.replace = replace;
        this.anchor = anchor == null ? Anchor.CENTER_TOP : anchor;
        this.animation = animation == null ? Animation.FADE : animation;
        this.textParts = textParts == null || textParts.isEmpty()
                ? List.of(new TextPart(this.text, "", this.textColor, 1.0F, this.bold, this.italic,
                this.underlined, this.strikethrough, this.obfuscated, this.shadow))
                : List.copyOf(textParts);
    }

    public BubbleSpec(
            String id,
            String text,
            String iconId,
            int iconSize,
            int iconGap,
            int textColor,
            int backgroundColor,
            String backgroundTexture,
            int backgroundBorder,
            int backgroundGuide,
            int x,
            int y,
            int width,
            int height,
            int maxWidth,
            int padding,
            TextAlignment textAlignment,
            int duration,
            int fadeIn,
            int fadeOut,
            int priority,
            float scale,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated,
            boolean shadow,
            boolean replace,
            Anchor anchor,
            Animation animation) {
        this(id, text, iconId, iconSize, iconGap,
                DEFAULT_ICON_OFFSET_X, DEFAULT_ICON_OFFSET_Y, DEFAULT_TEXT_OFFSET_X, DEFAULT_TEXT_OFFSET_Y,
                textColor, backgroundColor, backgroundTexture,
                backgroundBorder, backgroundGuide, DEFAULT_SOUND, DEFAULT_SOUND_VOLUME, DEFAULT_SOUND_PITCH,
                x, y, width, height, maxWidth, padding,
                textAlignment, duration, fadeIn, fadeOut, DEFAULT_SLIDE_IN, DEFAULT_SLIDE_OUT, priority, scale, bold, italic,
                underlined, strikethrough, obfuscated, shadow, replace, anchor, animation);
    }

    public BubbleSpec(
            String id,
            String text,
            String iconId,
            int iconSize,
            int iconGap,
            int textColor,
            int backgroundColor,
            String backgroundTexture,
            int backgroundBorder,
            int backgroundGuide,
            String sound,
            float soundVolume,
            float soundPitch,
            int x,
            int y,
            int width,
            int height,
            int maxWidth,
            int padding,
            TextAlignment textAlignment,
            int duration,
            int fadeIn,
            int fadeOut,
            int priority,
            float scale,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated,
            boolean shadow,
            boolean replace,
            Anchor anchor,
            Animation animation) {
        this(id, text, iconId, iconSize, iconGap,
                DEFAULT_ICON_OFFSET_X, DEFAULT_ICON_OFFSET_Y, DEFAULT_TEXT_OFFSET_X, DEFAULT_TEXT_OFFSET_Y,
                textColor, backgroundColor, backgroundTexture, backgroundBorder, backgroundGuide,
                sound, soundVolume, soundPitch, x, y, width, height, maxWidth, padding, textAlignment,
                duration, fadeIn, fadeOut, DEFAULT_SLIDE_IN, DEFAULT_SLIDE_OUT, priority, scale, bold, italic, underlined, strikethrough,
                obfuscated, shadow, replace, anchor, animation);
    }

    public BubbleSpec(
            String id,
            String text,
            String iconId,
            int iconSize,
            int iconGap,
            int iconOffsetX,
            int iconOffsetY,
            int textOffsetX,
            int textOffsetY,
            int textColor,
            int backgroundColor,
            String backgroundTexture,
            int backgroundBorder,
            int backgroundGuide,
            String sound,
            float soundVolume,
            float soundPitch,
            int x,
            int y,
            int width,
            int height,
            int maxWidth,
            int padding,
            TextAlignment textAlignment,
            int duration,
            int fadeIn,
            int fadeOut,
            int slideIn,
            int slideOut,
            int priority,
            float scale,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated,
            boolean shadow,
            boolean replace,
            Anchor anchor,
            Animation animation) {
        this(id, text, iconId, IconType.AUTO, List.of(), iconSize, iconGap,
                iconOffsetX, iconOffsetY, textOffsetX, textOffsetY, textColor, backgroundColor,
                backgroundTexture, backgroundBorder, backgroundGuide, sound, soundVolume, soundPitch,
                x, y, width, height, maxWidth, padding, textAlignment, duration, fadeIn, fadeOut,
                slideIn, slideOut, priority, scale, bold, italic, underlined, strikethrough,
                obfuscated, shadow, replace, anchor, animation);
    }

    public static BubbleSpec fromJson(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Bubble config must be a JSON object");
        }

        JsonObject object = element.getAsJsonObject();
        int textColor = textColor(object);
        float scale = decimal(object, "fontSize", decimal(object, "scale", 1.0F));
        boolean bold = bool(object, "bold", false);
        boolean italic = bool(object, "italic", false);
        boolean underlined = bool(object, "underlined", false);
        boolean strikethrough = bool(object, "strikethrough", false);
        boolean obfuscated = bool(object, "obfuscated", false);
        boolean shadow = bool(object, "shadow", true);
        List<TextPart> textParts = parseTextParts(object, textColor, bold, italic, underlined,
                strikethrough, obfuscated, shadow);
        String text = string(object, "text", string(object, "message", ""));
        if (text.isEmpty() && !textParts.isEmpty()) {
            text = textParts.stream().map(TextPart::text).reduce("", String::concat);
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Bubble config requires a non-empty 'text' or 'textParts'");
        }

        return new BubbleSpec(
                string(object, "id", UUID.randomUUID().toString()),
                text,
                string(object, "icon", string(object, "item", "")),
                IconType.parse(string(object, "iconType", "AUTO")),
                textParts,
                integer(object, "iconSize", DEFAULT_ICON_SIZE),
                integer(object, "iconGap", DEFAULT_ICON_GAP),
                integer(object, "iconOffsetX", DEFAULT_ICON_OFFSET_X),
                integer(object, "iconOffsetY", DEFAULT_ICON_OFFSET_Y),
                integer(object, "textOffsetX", DEFAULT_TEXT_OFFSET_X),
                integer(object, "textOffsetY", DEFAULT_TEXT_OFFSET_Y),
                textColor(object),
                color(object, "backgroundColor", DEFAULT_BACKGROUND_COLOR),
                string(object, "background", ""),
                integer(object, "backgroundBorder", DEFAULT_BACKGROUND_BORDER),
                integer(object, "backgroundGuide", DEFAULT_BACKGROUND_GUIDE),
                string(object, "sound", DEFAULT_SOUND),
                decimal(object, "soundVolume", DEFAULT_SOUND_VOLUME),
                decimal(object, "soundPitch", DEFAULT_SOUND_PITCH),
                integer(object, "x", 0),
                integer(object, "y", 0),
                integer(object, "width", 0),
                integer(object, "height", 0),
                integer(object, "maxWidth", DEFAULT_MAX_WIDTH),
                integer(object, "padding", DEFAULT_PADDING),
                TextAlignment.parse(string(object, "textAlign", string(object, "align", "LEFT"))),
                integer(object, "duration", DEFAULT_DURATION),
                integer(object, "fadeIn", DEFAULT_FADE_IN),
                integer(object, "fadeOut", DEFAULT_FADE_OUT),
                integer(object, "slideIn", DEFAULT_SLIDE_IN),
                integer(object, "slideOut", DEFAULT_SLIDE_OUT),
                integer(object, "priority", 0),
                scale,
                bold,
                italic,
                underlined,
                strikethrough,
                obfuscated,
                shadow,
                bool(object, "replace", true),
                Anchor.parse(string(object, "anchor", "CENTER_TOP")),
                Animation.parse(string(object, "animation", "FADE")));
    }

    public static BubbleSpec simple(String text) {
        return new BubbleSpec(
                UUID.randomUUID().toString(),
                text,
                "",
                DEFAULT_ICON_SIZE,
                DEFAULT_ICON_GAP,
                DEFAULT_TEXT_COLOR,
                DEFAULT_BACKGROUND_COLOR,
                "",
                DEFAULT_BACKGROUND_BORDER,
                DEFAULT_BACKGROUND_GUIDE,
                DEFAULT_SOUND,
                DEFAULT_SOUND_VOLUME,
                DEFAULT_SOUND_PITCH,
                0,
                0,
                0,
                0,
                DEFAULT_MAX_WIDTH,
                DEFAULT_PADDING,
                TextAlignment.LEFT,
                DEFAULT_DURATION,
                DEFAULT_FADE_IN,
                DEFAULT_FADE_OUT,
                0,
                1.0F,
                false,
                false,
                false,
                false,
                false,
                true,
                true,
                Anchor.CENTER_TOP,
                Animation.FADE);
    }

    public static List<TextPart> parseTextPartsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Bubble textParts must be a JSON array");
        }
        List<TextPart> result = new ArrayList<>();
        for (JsonElement part : element.getAsJsonArray()) {
            if (part.isJsonObject()) {
                result.add(TextPart.fromJson(part.getAsJsonObject(), new JsonObject(),
                        DEFAULT_TEXT_COLOR, false, false, false, false, false, true));
            }
        }
        return List.copyOf(result);
    }

    private static List<TextPart> parseTextParts(
            JsonObject object,
            int defaultColor,
            boolean defaultBold,
            boolean defaultItalic,
            boolean defaultUnderlined,
            boolean defaultStrikethrough,
            boolean defaultObfuscated,
            boolean defaultShadow) {
        JsonElement value = object.get("textParts");
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }

        JsonObject styles = object.has("textStyles") && object.get("textStyles").isJsonObject()
                ? object.getAsJsonObject("textStyles") : new JsonObject();
        List<TextPart> result = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject part = element.getAsJsonObject();
            String role = string(part, "role", "");
            JsonObject roleStyle = role.isBlank() || !styles.has(role) || !styles.get(role).isJsonObject()
                    ? new JsonObject() : styles.getAsJsonObject(role);
            result.add(TextPart.fromJson(part, roleStyle, defaultColor, defaultBold, defaultItalic,
                    defaultUnderlined, defaultStrikethrough, defaultObfuscated, defaultShadow));
        }
        return List.copyOf(result);
    }

    public String id() { return id; }
    public String text() { return text; }
    public String iconId() { return iconId; }
    public IconType iconType() { return iconType; }
    public List<TextPart> textParts() { return textParts; }
    public String textPartsJson() { return GSON.toJson(textParts); }
    public int iconSize() { return iconSize; }
    public int iconGap() { return iconGap; }
    public int iconOffsetX() { return iconOffsetX; }
    public int iconOffsetY() { return iconOffsetY; }
    public int textOffsetX() { return textOffsetX; }
    public int textOffsetY() { return textOffsetY; }
    public int textColor() { return textColor; }
    public int backgroundColor() { return backgroundColor; }
    public String backgroundTexture() { return backgroundTexture; }
    public int backgroundBorder() { return backgroundBorder; }
    public int backgroundGuide() { return backgroundGuide; }
    public String sound() { return sound; }
    public float soundVolume() { return soundVolume; }
    public float soundPitch() { return soundPitch; }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public int maxWidth() { return maxWidth; }
    public int padding() { return padding; }
    public TextAlignment textAlignment() { return textAlignment; }
    public int duration() { return duration; }
    public int fadeIn() { return fadeIn; }
    public int fadeOut() { return fadeOut; }
    public int slideIn() { return slideIn; }
    public int slideOut() { return slideOut; }
    public int priority() { return priority; }
    public float scale() { return scale; }
    public boolean bold() { return bold; }
    public boolean italic() { return italic; }
    public boolean underlined() { return underlined; }
    public boolean strikethrough() { return strikethrough; }
    public boolean obfuscated() { return obfuscated; }
    public boolean shadow() { return shadow; }
    public boolean replace() { return replace; }
    public Anchor anchor() { return anchor; }
    public Animation animation() { return animation; }

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
        String value = string(object, key, "");
        if (value.isBlank()) {
            return fallback;
        }

        String normalized = value.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
            if (normalized.length() == 6) {
                normalized = "FF" + normalized;
            }
            if (normalized.length() != 8) {
                throw new IllegalArgumentException("Color must be #RRGGBB or #AARRGGBB: " + value);
            }
            return (int) Long.parseLong(normalized, 16);
        }
        return Integer.decode(normalized);
    }

    private static int textColor(JsonObject object) {
        if (object.has("textColor") && !object.get("textColor").isJsonNull()) {
            return color(object, "textColor", DEFAULT_TEXT_COLOR);
        }
        return color(object, "color", DEFAULT_TEXT_COLOR);
    }

    public enum IconType {
        AUTO,
        ITEM,
        TEXTURE,
        NONE;

        public static IconType parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
            } catch (IllegalArgumentException exception) {
                return AUTO;
            }
        }
    }

    public record TextPart(
            String text,
            String role,
            int color,
            float scale,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated,
            boolean shadow) {
        private static TextPart fromJson(
                JsonObject part,
                JsonObject roleStyle,
                int defaultColor,
                boolean defaultBold,
                boolean defaultItalic,
                boolean defaultUnderlined,
                boolean defaultStrikethrough,
                boolean defaultObfuscated,
                boolean defaultShadow) {
            String role = string(part, "role", string(roleStyle, "role", ""));
            int color = textColor(part, roleStyle, defaultColor);
            float scale = decimal(part, "scale", decimal(part, "fontSize",
                    decimal(roleStyle, "scale", decimal(roleStyle, "fontSize", 1.0F))));
            return new TextPart(
                    string(part, "text", ""), role, color, clamp(scale, 0.5F, 4.0F),
                    bool(part, "bold", bool(roleStyle, "bold", defaultBold)),
                    bool(part, "italic", bool(roleStyle, "italic", defaultItalic)),
                    bool(part, "underlined", bool(roleStyle, "underlined", defaultUnderlined)),
                    bool(part, "strikethrough", bool(roleStyle, "strikethrough", defaultStrikethrough)),
                    bool(part, "obfuscated", bool(roleStyle, "obfuscated", defaultObfuscated)),
                    bool(part, "shadow", bool(roleStyle, "shadow", defaultShadow)));
        }

        private static int textColor(JsonObject part, JsonObject roleStyle, int fallback) {
            if (part.has("textColor")) return BubbleSpec.color(part, "textColor", fallback);
            if (part.has("color")) return BubbleSpec.color(part, "color", fallback);
            if (roleStyle.has("textColor")) return BubbleSpec.color(roleStyle, "textColor", fallback);
            return BubbleSpec.color(roleStyle, "color", fallback);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum TextAlignment {
        LEFT,
        CENTER,
        RIGHT;

        public static TextAlignment parse(String value) {
            String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (normalized.equals("MIDDLE") || normalized.equals("CENTERED")) normalized = "CENTER";
            return valueOf(normalized);
        }
    }

    public enum Anchor {
        TOP_LEFT,
        CENTER_TOP,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        CENTER_BOTTOM,
        BOTTOM_RIGHT;

        public static Anchor parse(String value) {
            String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (normalized.equals("TOP_CENTER")) normalized = "CENTER_TOP";
            if (normalized.equals("BOTTOM_CENTER")) normalized = "CENTER_BOTTOM";
            return valueOf(normalized);
        }
    }

    public enum Animation {
        FADE,
        SLIDE_FROM_LEFT,
        SLIDE_FROM_RIGHT,
        SLIDE_FROM_TOP,
        SLIDE_FROM_BOTTOM;

        public static Animation parse(String value) {
            String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            if (normalized.equals("SLIDE_LEFT")) normalized = "SLIDE_FROM_LEFT";
            if (normalized.equals("SLIDE_RIGHT")) normalized = "SLIDE_FROM_RIGHT";
            if (normalized.equals("SLIDE_TOP")) normalized = "SLIDE_FROM_TOP";
            if (normalized.equals("SLIDE_BOTTOM")) normalized = "SLIDE_FROM_BOTTOM";
            return valueOf(normalized);
        }
    }
}
