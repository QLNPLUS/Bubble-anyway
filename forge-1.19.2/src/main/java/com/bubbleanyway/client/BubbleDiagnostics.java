package com.bubbleanyway.client;

import com.bubbleanyway.config.BubbleClientConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/** Optional, low-noise client diagnostics for validating a packaged loader branch. */
public final class BubbleDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOADER = "Forge 1.19.2";
    private static final String SESSION = Long.toHexString(System.nanoTime());
    private static final String[] REQUIRED_THEMES = {
            "bubble_anyway:default",
            "bubble_anyway:modern",
            "bubble_anyway:toast_advancement_task",
            "bubble_anyway:toast_advancement_goal",
            "bubble_anyway:toast_advancement_challenge",
            "bubble_anyway:toast_recipe",
            "bubble_anyway:toast_ftb_completion",
            "bubble_anyway:toast_ftb_reward"
    };
    private static boolean reported;
    private static boolean testBubbleSent;
    private static boolean worldLoaded;
    private static int ticksSinceWorldLoad;

    private BubbleDiagnostics() {
    }

    public static void clientTick(Minecraft client) {
        if (!BubbleClientConfig.diagnosticsEnabled()) {
            reported = false;
            testBubbleSent = false;
            worldLoaded = false;
            ticksSinceWorldLoad = 0;
            return;
        }
        if (client.level == null) {
            if (worldLoaded) {
                LOGGER.info("Bubble Anyway diagnostics: world ended; active={}, pending={} (queue cleared)",
                        BubbleOverlay.activeCount(), BubbleOverlay.pendingCount());
            }
            worldLoaded = false;
            reported = false;
            testBubbleSent = false;
            ticksSinceWorldLoad = 0;
            return;
        }

        if (!worldLoaded) {
            worldLoaded = true;
            ticksSinceWorldLoad = 0;
        }
        if (reported || ++ticksSinceWorldLoad < 20) {
            return;
        }
        reported = true;
        runReport();
    }

    private static void runReport() {
        try {
            BubbleThemeClientCache.refreshLocalThemes();
            LOGGER.info("Bubble Anyway diagnostics [{}] session={}", LOADER, SESSION);
            LOGGER.info("  config: diagnosticsEnabled={}, showTestBubble={}",
                    BubbleClientConfig.diagnosticsEnabled(), BubbleClientConfig.showDiagnosticBubble());
            LOGGER.info("  render: callbackSeen={}, active={}, pending={}",
                    BubbleOverlay.renderCallbackSeen(), BubbleOverlay.activeCount(), BubbleOverlay.pendingCount());
            LOGGER.info("  toast: integrationHookSeen={} (NOT_SEEN means no supported toast has arrived yet)",
                    BubbleToastIntegration.hookSeen());
            for (String themeId : REQUIRED_THEMES) {
                LOGGER.info("  theme: {}={}", themeId, themeStatus(themeId));
            }
            LOGGER.info("  pause: promotion is blocked while paused; world exit clears active and pending bubbles");

            if (BubbleClientConfig.showDiagnosticBubble() && !testBubbleSent) {
                String themeId = BubbleThemeClientCache.hasTheme("bubble_anyway:default")
                        ? "bubble_anyway:default" : "bubble_anyway:toast_recipe";
                testBubbleSent = true;
                BubbleThemeClientCache.enqueue(themeId,
                        "{\"id\":\"bubble_anyway:diagnostic\",\"text\":\"Bubble Anyway diagnostics OK\",\"icon\":\"minecraft:diamond\",\"duration\":100,\"priority\":1000,\"replace\":true}");
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Bubble Anyway diagnostics failed on {}", LOADER, exception);
        }
    }

    public static void toastHookReceived(String toastClass) {
        if (BubbleClientConfig.diagnosticsEnabled()) {
            LOGGER.info("Bubble Anyway diagnostics: Toast hook received {}", toastClass);
        }
    }

    private static String themeStatus(String themeId) {
        if (!BubbleThemeClientCache.hasTheme(themeId)) {
            return "MISSING";
        }
        try {
            BubbleThemeClientCache.resolve(themeId, "{\"text\":\"diagnostic\"}");
            return "OK";
        } catch (RuntimeException exception) {
            return "INVALID (" + exception.getClass().getSimpleName() + ")";
        }
    }
}
