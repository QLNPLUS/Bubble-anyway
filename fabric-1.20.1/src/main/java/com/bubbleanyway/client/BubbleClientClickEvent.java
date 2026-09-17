package com.bubbleanyway.client;

import com.bubbleanyway.data.BubbleControl;
import com.bubbleanyway.data.BubbleSpec;
import com.google.gson.JsonElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** Fired on the client when a visible bubble control is clicked. */
public final class BubbleClientClickEvent {
    private final ClientPlayerEntity player;
    private final BubbleSpec bubble;
    private final BubbleControl control;
    private boolean canceled;

    public BubbleClientClickEvent(BubbleSpec bubble, BubbleControl control) {
        this.player = MinecraftClient.getInstance().player;
        this.bubble = bubble;
        this.control = control;
    }

    public ClientPlayerEntity player() { return player; }
    public BubbleSpec bubble() { return bubble; }
    public BubbleControl control() { return control; }
    public String bubbleId() { return bubble.id(); }
    public String controlId() { return control.id(); }
    public ClientPlayerEntity getPlayer() { return player; }
    public BubbleSpec getBubble() { return bubble; }
    public BubbleControl getControl() { return control; }
    public String getBubbleId() { return bubbleId(); }
    public String getControlId() { return controlId(); }
    public JsonElement getData() { return control.data(); }
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }
    public void cancel() { this.canceled = true; }
}
