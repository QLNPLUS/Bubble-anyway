package com.bubbleanyway.client;

import com.bubbleanyway.data.BubbleControl;
import com.bubbleanyway.data.BubbleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Fired on the client when a visible bubble control is clicked. */
@Cancelable
public final class BubbleClientClickEvent extends Event {
    private final LocalPlayer player;
    private final BubbleSpec bubble;
    private final BubbleControl control;

    public BubbleClientClickEvent(BubbleSpec bubble, BubbleControl control) {
        this.player = Minecraft.getInstance().player;
        this.bubble = bubble;
        this.control = control;
    }

    public LocalPlayer player() { return player; }
    public BubbleSpec bubble() { return bubble; }
    public BubbleControl control() { return control; }
    public String bubbleId() { return bubble.id(); }
    public String controlId() { return control.id(); }
    public LocalPlayer getPlayer() { return player; }
    public BubbleSpec getBubble() { return bubble; }
    public BubbleControl getControl() { return control; }
    public String getBubbleId() { return bubbleId(); }
    public String getControlId() { return controlId(); }
    public com.google.gson.JsonElement getData() { return control.data(); }
}
