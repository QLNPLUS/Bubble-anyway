package com.bubbleanyway.client;

import com.bubbleanyway.config.BubbleClientConfig;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import org.slf4j.Logger;

/** Optional, low-noise client diagnostics for validating a packaged loader branch. */
public final class BubbleDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOADER = "NeoForge 1.21.1";
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
    private static boolean toastProbeSent;
    private static boolean testBubbleSent;
    private static boolean worldLoaded;
    private static int ticksSinceWorldLoad;

    private BubbleDiagnostics() {
    }

    public static void clientTick(Minecraft client) {
        if (!BubbleClientConfig.diagnosticsEnabled()) {
            reported = false;
            toastProbeSent = false;
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
            toastProbeSent = false;
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
        runReport(client);
    }

    private static void runReport(Minecraft client) {
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
            LOGGER.info("  pause: timers and queue promotion continue while paused; ordinary bubbles stay below the pause screen and Toast bubbles can render above it");
            runToastProbe(client);
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

    private static void runToastProbe(Minecraft client) {
        if (toastProbeSent) {
            return;
        }
        toastProbeSent = true;
        try {
            Object connection = client.getConnection();
            if (connection == null) {
                LOGGER.info("  toastProbe: skipped because the client connection is unavailable");
                return;
            }
            Object advancements = connection.getClass().getMethod("getAdvancements").invoke(connection);
            Object key = parseAdvancementId("minecraft:story/root");
            Object holder = advancements.getClass().getMethod("get", key.getClass()).invoke(advancements, key);
            if (!(holder instanceof AdvancementHolder advancementHolder)) {
                LOGGER.info("  toastProbe: advancement minecraft:story/root is not loaded");
                return;
            }
            Toast toast = new AdvancementToast(advancementHolder);
            Object toastManager = findToastManager(client);
            if (toastManager == null) {
                LOGGER.info("  toastProbe: ToastManager field/method was not found");
                return;
            }
            Method addToast = toastManager.getClass().getMethod("addToast", Toast.class);
            addToast.invoke(toastManager, toast);
            LOGGER.info("  toastProbe: queued a synthetic AdvancementToast");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("  toastProbe: could not queue a synthetic AdvancementToast", exception);
        }
    }

    private static Object findToastManager(Minecraft client) throws ReflectiveOperationException {
        for (String methodName : new String[] {"getToastManager", "getToasts"}) {
            try {
                Method method = client.getClass().getMethod(methodName);
                Object value = method.invoke(client);
                if (value != null && value.getClass().getName().contains("ToastManager")) {
                    return value;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        Class<?> current = client.getClass();
        while (current != null) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (!field.getType().getName().contains("ToastManager")) {
                    continue;
                }
                field.setAccessible(true);
                return field.get(client);
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object parseAdvancementId(String raw) throws ReflectiveOperationException {
        for (String className : new String[] {"net.minecraft.resources.ResourceLocation"}) {
            Class<?> type = Class.forName(className);
            for (String methodName : new String[] {"tryParse", "parse", "withDefaultNamespace"}) {
                try {
                    Method method = type.getMethod(methodName, String.class);
                    Object value = method.invoke(null, raw);
                    if (value != null) {
                        return value;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            try {
                return type.getConstructor(String.class, String.class)
                        .newInstance("minecraft", "story/mine_stone");
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("No ResourceLocation parser is available");
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
