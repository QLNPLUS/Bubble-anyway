package com.bubbleanyway.client;

import com.bubbleanyway.BubbleAnyway;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@Mod(value = BubbleAnyway.MOD_ID, dist = Dist.CLIENT)
public final class BubbleAnywayClient {
    public BubbleAnywayClient(IEventBus modEventBus) {
        modEventBus.addListener(BubbleAnywayClient::registerGuiLayers);
    }

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BubbleAnyway.id("message_bubbles"), BubbleOverlay::render);
    }
}
