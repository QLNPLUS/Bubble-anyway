package com.bubbleanyway.client;

import com.bubbleanyway.BubbleAnyway;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ToastAddEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BubbleAnyway.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BubbleAnywayClient {
    private BubbleAnywayClient() {
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }

        BubbleOverlay.renderTop(
                event.getGuiGraphics(),
                event.getPartialTick(),
                minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void renderScreen(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        BubbleOverlay.renderTop(
                event.getGuiGraphics(),
                event.getPartialTick(),
                event.getScreen().width,
                event.getScreen().height);
    }

    @SubscribeEvent
    public static void replaceToast(ToastAddEvent event) {
        BubbleToastIntegration.handle(event);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft minecraft = Minecraft.getInstance();
            BubbleOverlay.clientTick(minecraft);
            BubbleDiagnostics.clientTick(minecraft);
        }
    }
}
