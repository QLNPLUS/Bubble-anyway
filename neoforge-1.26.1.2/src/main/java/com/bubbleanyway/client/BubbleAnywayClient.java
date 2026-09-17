package com.bubbleanyway.client;

import com.bubbleanyway.BubbleAnyway;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod(value = BubbleAnyway.MOD_ID, dist = Dist.CLIENT)
public final class BubbleAnywayClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier BUBBLE_LAYER_ID =
            Identifier.fromNamespaceAndPath(BubbleAnyway.MOD_ID, "bubble_overlay");

    public BubbleAnywayClient(IEventBus modEventBus) {
        modEventBus.addListener(BubbleAnywayClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(BubbleAnywayClient::replaceToast);
        NeoForge.EVENT_BUS.addListener(BubbleAnywayClient::renderScreenPre);
        NeoForge.EVENT_BUS.addListener(BubbleAnywayClient::renderScreenPost);
        NeoForge.EVENT_BUS.addListener(BubbleAnywayClient::screenMousePressed);
        NeoForge.EVENT_BUS.addListener(BubbleAnywayClient::clientTick);
    }

    public static void renderScreenPre(ScreenEvent.Render.Pre event) {
        if (Minecraft.getInstance().level != null
                && event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            BubbleOverlay.render(event.getGuiGraphics(), event.getPartialTick(),
                    com.bubbleanyway.data.BubbleSpec.RenderLayer.BELOW_PAUSE);
        }
    }

    /** Register after vanilla HUD layers so the extractor keeps the overlay in a dedicated top layer. */
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(BUBBLE_LAYER_ID, (graphics, partialTick) -> {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().screen == null) {
                BubbleOverlay.render(graphics, partialTick);
            }
        });
    }

    public static void replaceToast(ToastAddEvent event) {
        BubbleToastIntegration.handle(event);
    }

    /** Render after the current screen so bubbles stay visible over mod GUIs. */
    public static void renderScreenPost(ScreenEvent.Render.Post event) {
        if (Minecraft.getInstance().screen != null) {
            if (event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
                BubbleOverlay.render(event.getGuiGraphics(), event.getPartialTick(),
                        com.bubbleanyway.data.BubbleSpec.RenderLayer.ABOVE_PAUSE);
            } else {
                BubbleOverlay.render(event.getGuiGraphics(), event.getPartialTick());
            }
        }
    }

    public static void screenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        com.bubbleanyway.data.BubbleSpec.RenderLayer layer =
                event.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen
                        ? com.bubbleanyway.data.BubbleSpec.RenderLayer.ABOVE_PAUSE : null;
        if (BubbleOverlay.handleMouseClick(event.getMouseX(), event.getMouseY(), event.getButton(),
                event.getScreen().width, event.getScreen().height, layer)) {
            event.setCanceled(true);
        }
    }

    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        BubbleOverlay.clientTick(minecraft);
        BubbleDiagnostics.clientTick(minecraft);
    }
}
