package com.bubbleanyway.client;

import com.bubbleanyway.BubbleAnyway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
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
    public static void renderScreenPre(ScreenEvent.Render.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(event.getScreen() instanceof PauseScreen)) {
            return;
        }

        // Draw ordinary bubbles before PauseScreen.render() starts. The pause screen then
        // covers the complete bubble unit, including its icon and text.
        BubbleOverlay.renderTop(
                event.getGuiGraphics(),
                event.getPartialTick(),
                event.getScreen().width,
                event.getScreen().height,
                com.bubbleanyway.data.BubbleSpec.RenderLayer.BELOW_PAUSE);
    }

    @SubscribeEvent
    public static void renderScreen(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        if (event.getScreen() instanceof PauseScreen) {
            BubbleOverlay.renderTop(
                    event.getGuiGraphics(),
                    event.getPartialTick(),
                    event.getScreen().width,
                    event.getScreen().height,
                    com.bubbleanyway.data.BubbleSpec.RenderLayer.ABOVE_PAUSE);
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
