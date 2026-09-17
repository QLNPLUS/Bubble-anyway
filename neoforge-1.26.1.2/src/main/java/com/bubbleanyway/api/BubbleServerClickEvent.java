package com.bubbleanyway.api;

import com.bubbleanyway.data.BubbleControl;
import com.bubbleanyway.data.BubbleSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired after the server validates a bubble control click. */
public final class BubbleServerClickEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final BubbleSpec bubble;
    private final BubbleControl control;

    public BubbleServerClickEvent(ServerPlayer player, BubbleSpec bubble, BubbleControl control) {
        this.player = player;
        this.bubble = bubble;
        this.control = control;
    }

    public ServerPlayer player() { return player; }
    public BubbleSpec bubble() { return bubble; }
    public BubbleControl control() { return control; }
    public String bubbleId() { return bubble.id(); }
    public String controlId() { return control.id(); }
    public ServerPlayer getPlayer() { return player; }
    public BubbleSpec getBubble() { return bubble; }
    public BubbleControl getControl() { return control; }
    public String getBubbleId() { return bubbleId(); }
    public String getControlId() { return controlId(); }
    public com.google.gson.JsonElement getData() { return control.data(); }

    public void tell(String message) {
        if (player != null) {
            player.sendSystemMessage(Component.literal(message == null ? "" : message));
        }
    }
}
