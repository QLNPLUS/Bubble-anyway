package com.bubbleanyway.api;

import com.bubbleanyway.data.BubbleControl;
import com.bubbleanyway.data.BubbleSpec;
import com.google.gson.JsonElement;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Fired after the server validates a click on a bubble control. */
public final class BubbleServerClickEvent {
    private final ServerPlayerEntity player;
    private final BubbleSpec bubble;
    private final BubbleControl control;
    private boolean canceled;

    public BubbleServerClickEvent(ServerPlayerEntity player, BubbleSpec bubble, BubbleControl control) {
        this.player = player;
        this.bubble = bubble;
        this.control = control;
    }

    public ServerPlayerEntity player() { return player; }
    public BubbleSpec bubble() { return bubble; }
    public BubbleControl control() { return control; }
    public String bubbleId() { return bubble.id(); }
    public String controlId() { return control.id(); }
    public ServerPlayerEntity getPlayer() { return player; }
    public BubbleSpec getBubble() { return bubble; }
    public BubbleControl getControl() { return control; }
    public String getBubbleId() { return bubbleId(); }
    public String getControlId() { return controlId(); }
    public JsonElement getData() { return control.data(); }
    public void tell(String message) { if (player != null) player.sendMessage(Text.literal(message == null ? "" : message), false); }
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean canceled) { this.canceled = canceled; }
    public void cancel() { this.canceled = true; }
}
