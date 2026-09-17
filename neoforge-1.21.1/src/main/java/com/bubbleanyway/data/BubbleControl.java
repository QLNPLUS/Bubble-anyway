package com.bubbleanyway.data;

import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/** A clickable control rendered inside a bubble. */
public final class BubbleControl {
    private static final Gson GSON = new GsonBuilder().create();
    public static final int DEFAULT_WIDTH = 0;
    public static final int DEFAULT_HEIGHT = 20;

    private final String id;
    private final String text;
    private final Type type;
    private final boolean enabled;
    private final int width;
    private final int height;
    private final String style;
    private final boolean closeOnPress;
    private final JsonElement data;

    private BubbleControl(
            String id,
            String text,
            Type type,
            boolean enabled,
            int width,
            int height,
            String style,
            boolean closeOnPress,
            JsonElement data) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Bubble control requires a non-empty id");
        }
        this.id = id;
        this.text = text == null ? "" : text;
        this.type = type == null ? Type.BUTTON : type;
        this.enabled = enabled;
        this.width = clamp(width, 0, 4096);
        this.height = clamp(height, 0, 4096);
        this.style = style == null ? "" : style;
        this.closeOnPress = closeOnPress;
        this.data = data == null ? null : data.deepCopy();
    }

    public static BubbleControl button(String id, String text) {
        return builder(id).text(text).build();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static BubbleControl fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Bubble control must be a JSON object");
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement data = object.get("data");
        return new BubbleControl(
                string(object, "id", ""),
                string(object, "text", string(object, "label", "")),
                Type.parse(string(object, "type", "BUTTON")),
                bool(object, "enabled", true),
                integer(object, "width", DEFAULT_WIDTH),
                integer(object, "height", DEFAULT_HEIGHT),
                string(object, "style", ""),
                bool(object, "closeOnPress", true),
                data);
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("text", text);
        object.addProperty("type", type.name());
        if (!enabled) object.addProperty("enabled", false);
        if (width > 0) object.addProperty("width", width);
        if (height != DEFAULT_HEIGHT) object.addProperty("height", height);
        if (!style.isBlank()) object.addProperty("style", style);
        if (!closeOnPress) object.addProperty("closeOnPress", false);
        if (data != null) object.add("data", data.deepCopy());
        return object;
    }

    public String id() {
        return id;
    }

    public String text() {
        return text;
    }

    public Type type() {
        return type;
    }

    public boolean enabled() {
        return enabled;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String style() {
        return style;
    }

    public boolean closeOnPress() {
        return closeOnPress;
    }

    public JsonElement data() {
        return data == null ? null : data.deepCopy();
    }

    public String dataJson() {
        return data == null ? "null" : data.toString();
    }

    public enum Type {
        BUTTON;

        public static Type parse(String value) {
            try {
                return valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return BUTTON;
            }
        }
    }

    public static final class Builder {
        private final String id;
        private String text = "";
        private Type type = Type.BUTTON;
        private boolean enabled = true;
        private int width = DEFAULT_WIDTH;
        private int height = DEFAULT_HEIGHT;
        private String style = "";
        private boolean closeOnPress = true;
        private JsonElement data;

        private Builder(String id) {
            this.id = id;
        }

        public Builder text(String value) { text = value; return this; }
        public Builder label(String value) { return text(value); }
        public Builder type(Type value) { type = value; return this; }
        public Builder enabled(boolean value) { enabled = value; return this; }
        public Builder width(int value) { width = value; return this; }
        public Builder height(int value) { height = value; return this; }
        public Builder size(int value, int other) { width = value; height = other; return this; }
        public Builder style(String value) { style = value; return this; }
        public Builder closeOnPress(boolean value) { closeOnPress = value; return this; }
        public Builder data(JsonElement value) { data = value == null ? null : value.deepCopy(); return this; }
        public Builder data(String json) { data = json == null ? null : JsonParser.parseString(json); return this; }
        public Builder data(Object value) {
            if (value == null) data = null;
            else if (value instanceof JsonElement element) data = element.deepCopy();
            else data = GSON.toJsonTree(value);
            return this;
        }

        public BubbleControl build() {
            return new BubbleControl(id, text, type, enabled, width, height, style, closeOnPress, data);
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsInt();
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
