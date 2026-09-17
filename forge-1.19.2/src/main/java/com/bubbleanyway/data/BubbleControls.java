package com.bubbleanyway.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** Layout and style catalog for the controls inside one bubble. */
public final class BubbleControls {
    private static final Gson GSON = new GsonBuilder().create();
    private static final BubbleControls EMPTY = new BubbleControls(Layout.HORIZONTAL, Alignment.LEFT, 4, List.of(), Map.of());

    private final Layout layout;
    private final Alignment alignment;
    private final int gap;
    private final List<BubbleControl> items;
    private final Map<String, BubbleControlStyle> styles;

    private BubbleControls(
            Layout layout,
            Alignment alignment,
            int gap,
            List<BubbleControl> items,
            Map<String, BubbleControlStyle> styles) {
        this.layout = layout == null ? Layout.HORIZONTAL : layout;
        this.alignment = alignment == null ? Alignment.LEFT : alignment;
        this.gap = Math.max(0, Math.min(64, gap));
        this.items = List.copyOf(items == null ? List.of() : items);
        this.styles = Map.copyOf(styles == null ? Map.of() : styles);
    }

    public static BubbleControls empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static BubbleControls fromJson(JsonElement element) {
        if (element == null) {
            return empty();
        }

        if (element.isJsonArray()) {
            List<BubbleControl> controls = new ArrayList<>();
            readControlArray(element.getAsJsonArray(), controls);
            return new BubbleControls(Layout.HORIZONTAL, Alignment.LEFT, 4, controls, Map.of());
        }
        if (!element.isJsonObject()) {
            return empty();
        }

        JsonObject object = element.getAsJsonObject();
        List<BubbleControl> controls = new ArrayList<>();
        JsonElement itemsElement = object.get("items");
        if (itemsElement == null) itemsElement = object.get("controls");
        if (itemsElement != null && itemsElement.isJsonArray()) {
            readControlArray(itemsElement.getAsJsonArray(), controls);
        } else if (itemsElement != null && itemsElement.isJsonObject()) {
            readControlMap(itemsElement.getAsJsonObject(), controls);
        }

        // Compact form: {confirm: {text: "确定"}, cancel: {text: "取消"}}. Reserved
        // layout/style keys keep their full-form meaning and are not treated as controls.
        readControlMap(object, controls);

        Map<String, BubbleControlStyle> styles = new LinkedHashMap<>();
        JsonElement stylesElement = object.get("styles");
        if (stylesElement != null && stylesElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stylesElement.getAsJsonObject().entrySet()) {
                styles.put(entry.getKey(), BubbleControlStyle.fromJson(entry.getValue(), BubbleControlStyle.defaults()));
            }
        }
        return new BubbleControls(
                Layout.parse(string(object, "layout", "HORIZONTAL")),
                Alignment.parse(string(object, "align", string(object, "alignment", "LEFT"))),
                integer(object, "gap", 4),
                controls,
                styles);
    }

    private static void readControlArray(JsonArray array, List<BubbleControl> controls) {
        for (JsonElement item : array) {
            try {
                addIfUnique(controls, BubbleControl.fromJson(item));
            } catch (RuntimeException ignored) {
                // Ignore one malformed control while preserving the rest of the bubble.
            }
        }
    }

    private static void readControlMap(JsonObject object, List<BubbleControl> controls) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (isReservedKey(entry.getKey()) || !entry.getValue().isJsonObject()) {
                continue;
            }
            try {
                JsonObject definition = entry.getValue().getAsJsonObject().deepCopy();
                if (!definition.has("id")) {
                    definition.addProperty("id", entry.getKey());
                }
                addIfUnique(controls, BubbleControl.fromJson(definition));
            } catch (RuntimeException ignored) {
                // Ignore one malformed control while preserving the rest of the bubble.
            }
        }
    }

    private static void addIfUnique(List<BubbleControl> controls, BubbleControl control) {
        if (control != null && controls.stream().noneMatch(item -> item.id().equals(control.id()))) {
            controls.add(control);
        }
    }

    private static boolean isReservedKey(String key) {
        return key.equals("layout") || key.equals("align") || key.equals("alignment")
                || key.equals("gap") || key.equals("styles") || key.equals("items")
                || key.equals("controls");
    }

    public String toJson() {
        return GSON.toJson(toJsonObject());
    }

    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        object.addProperty("layout", layout.name());
        object.addProperty("align", alignment.name());
        object.addProperty("gap", gap);
        JsonArray itemsArray = new JsonArray();
        for (BubbleControl item : items) itemsArray.add(item.toJson());
        object.add("items", itemsArray);
        if (!styles.isEmpty()) {
            JsonObject stylesObject = new JsonObject();
            for (Map.Entry<String, BubbleControlStyle> entry : styles.entrySet()) {
                stylesObject.add(entry.getKey(), entry.getValue().toJson());
            }
            object.add("styles", stylesObject);
        }
        return object;
    }

    public Layout layout() { return layout; }
    public Alignment alignment() { return alignment; }
    public int gap() { return gap; }
    public List<BubbleControl> items() { return items; }
    public Map<String, BubbleControlStyle> styles() { return styles; }
    public boolean isEmpty() { return items.isEmpty(); }

    public BubbleControl find(String id) {
        if (id == null) return null;
        for (BubbleControl item : items) {
            if (item.id().equals(id)) return item;
        }
        return null;
    }

    public BubbleControlStyle style(String id) {
        BubbleControlStyle style = id == null ? null : styles.get(id);
        return style == null ? BubbleControlStyle.defaults() : style;
    }

    public enum Layout {
        HORIZONTAL,
        VERTICAL;

        public static Layout parse(String value) {
            try { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException exception) { return HORIZONTAL; }
        }
    }

    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT;

        public static Alignment parse(String value) {
            try { return valueOf(value.toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException exception) { return LEFT; }
        }
    }

    public static final class Builder {
        private Layout layout = Layout.HORIZONTAL;
        private Alignment alignment = Alignment.LEFT;
        private int gap = 4;
        private final List<BubbleControl> items = new ArrayList<>();
        private final Map<String, BubbleControlStyle> styles = new LinkedHashMap<>();

        public Builder layout(Layout value) { layout = value; return this; }
        public Builder alignment(Alignment value) { alignment = value; return this; }
        public Builder align(Alignment value) { return alignment(value); }
        public Builder align(Align value) { return alignment(value == null ? null : value.toAlignment()); }
        public Builder gap(int value) { gap = value; return this; }
        public Builder add(BubbleControl value) { if (value != null) items.add(value); return this; }
        public Builder control(BubbleControl value) { return add(value); }
        public Builder button(String id, String text) { return add(BubbleControl.button(id, text)); }
        public Builder style(String id, BubbleControlStyle value) { if (id != null && value != null) styles.put(id, value); return this; }

        public BubbleControls build() {
            Set<String> ids = new HashSet<>();
            for (BubbleControl item : items) {
                if (!ids.add(item.id())) {
                    throw new IllegalArgumentException("Duplicate bubble control id: " + item.id());
                }
            }
            return new BubbleControls(layout, alignment, gap, items, styles);
        }
    }

    /** Builder-facing alias retained for the original plan/API spelling. */
    public enum Align {
        LEFT,
        CENTER,
        RIGHT;

        private Alignment toAlignment() {
            return Alignment.valueOf(name());
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
}
