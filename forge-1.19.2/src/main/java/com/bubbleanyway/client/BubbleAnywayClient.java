package com.bubbleanyway.client;

import com.bubbleanyway.BubbleAnyway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ToastAddEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BubbleAnyway.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BubbleAnywayClient {
    private BubbleAnywayClient() {
    }

    @SubscribeEvent
    public static void renderScreenPre(ScreenEvent.Render.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(event.getScreen() instanceof PauseScreen)) {
            return;
        }

        BubbleOverlay.renderTop(
                event.getPoseStack(),
                event.getPartialTick(),
                event.getScreen().width,
                event.getScreen().height,
                com.bubbleanyway.data.BubbleSpec.RenderLayer.BELOW_PAUSE);
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }

        BubbleOverlay.renderTop(
                event.getPoseStack(),
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

        if (event.getScreen() instanceof PauseScreen) {
            BubbleOverlay.renderTop(
                    event.getPoseStack(),
                    event.getPartialTick(),
                    event.getScreen().width,
                    event.getScreen().height,
                    com.bubbleanyway.data.BubbleSpec.RenderLayer.ABOVE_PAUSE);
            return;
        }

        BubbleOverlay.renderTop(
                event.getPoseStack(),
                event.getPartialTick(),
                event.getScreen().width,
                event.getScreen().height);
    }

    @SubscribeEvent
    public static void replaceToast(ToastAddEvent event) {
        BubbleToastIntegration.handle(event);
    }

    @SubscribeEvent
    public static void screenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        com.bubbleanyway.data.BubbleSpec.RenderLayer layer = event.getScreen() instanceof PauseScreen
                ? com.bubbleanyway.data.BubbleSpec.RenderLayer.ABOVE_PAUSE : null;
        if (BubbleOverlay.handleMouseClick(event.getMouseX(), event.getMouseY(), event.getButton(),
                event.getScreen().width, event.getScreen().height, layer)) {
            event.setCanceled(true);
        }
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
