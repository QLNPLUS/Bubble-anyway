package com.bubbleanyway.kubejs;

import com.bubbleanyway.api.BubbleServerClickEvent;
import com.bubbleanyway.data.BubbleControl;
import com.bubbleanyway.data.BubbleSpec;
import com.google.gson.JsonElement;
import java.util.Locale;
import net.minecraft.server.network.ServerPlayerEntity;

/** KubeJS-friendly view of a validated server-side bubble click. */
public final class BubbleKubeJSServerClickEvent {
    private final BubbleServerClickEvent source;
    private final BubbleView bubble;
    private final ControlView control;

    public BubbleKubeJSServerClickEvent(BubbleServerClickEvent source) {
        this.source = source;
        this.bubble = new BubbleView(source.bubble());
        this.control = new ControlView(source.control());
    }

    public ServerPlayerEntity getPlayer() { return source.player(); }
    public BubbleView getBubble() { return bubble; }
    public ControlView getControl() { return control; }
    public String getBubbleId() { return bubble.getId(); }
    public String getControlId() { return control.getId(); }
    public JsonElement getData() { return control.getData(); }
    public void tell(String message) { source.tell(message); }
    public boolean isCanceled() { return source.isCanceled(); }
    public void setCanceled(boolean value) { source.setCanceled(value); }
    public void cancel() { source.cancel(); }

    public static final class BubbleView {
        private final BubbleSpec spec;
        private BubbleView(BubbleSpec spec) { this.spec = spec; }
        public String getId() { return spec.id(); }
        public String getText() { return spec.text(); }
        public BubbleSpec getSpec() { return spec; }
    }

    public static final class ControlView {
        private final BubbleControl control;
        private ControlView(BubbleControl control) { this.control = control; }
        public String getId() { return control.id(); }
        public String getType() { return control.type().name().toLowerCase(Locale.ROOT); }
        public String getText() { return control.text(); }
        public boolean getEnabled() { return control.enabled(); }
        public String getStyle() { return control.style(); }
        public boolean getCloseOnPress() { return control.closeOnPress(); }
        public JsonElement getData() { return control.data(); }
        public BubbleControl getControl() { return control; }
    }
}
