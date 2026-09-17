package com.bubbleanyway.kubejs;

import com.bubbleanyway.api.BubbleServerApi;
import com.bubbleanyway.api.BubbleServerClickEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** KubeJS-facing server API for showing bubbles from server scripts. */
public final class BubbleKubeJSServerApi {
    private static volatile Consumer<BubbleKubeJSServerClickEvent> clickListener;
    private BubbleKubeJSServerApi() {
    }

    public static void show(Object player, String text) {
        showJson(player, BubbleSpecJson.simple(text));
    }

    public static void showJson(Object player, String json) {
        ServerPlayer serverPlayer = findServerPlayer(player, 4);
        if (serverPlayer == null) {
            throw new IllegalArgumentException("Expected a server player or a KubeJS player wrapper");
        }
        BubbleServerApi.showJson(serverPlayer, json);
    }

    public static void showAll(Object server, String text) {
        showAllJson(server, BubbleSpecJson.simple(text));
    }

    public static void showAllJson(Object server, String json) {
        MinecraftServer minecraftServer = findServer(server, 4);
        if (minecraftServer == null) {
            throw new IllegalArgumentException("Expected a Minecraft server or a KubeJS server wrapper");
        }
        BubbleServerApi.showAllJson(minecraftServer, json);
    }

    public static void showTheme(Object player, String themeId, String text) {
        showThemeJson(player, themeId, BubbleSpecJson.withText(text));
    }

    public static void showThemeJson(Object player, String themeId, String overridesJson) {
        ServerPlayer serverPlayer = findServerPlayer(player, 4);
        if (serverPlayer == null) {
            throw new IllegalArgumentException("Expected a server player or a KubeJS player wrapper");
        }
        BubbleServerApi.showThemeJson(serverPlayer, themeId, overridesJson);
    }

    public static void showAllTheme(Object server, String themeId, String text) {
        showAllThemeJson(server, themeId, BubbleSpecJson.withText(text));
    }

    public static void showAllThemeJson(Object server, String themeId, String overridesJson) {
        MinecraftServer minecraftServer = findServer(server, 4);
        if (minecraftServer == null) {
            throw new IllegalArgumentException("Expected a Minecraft server or a KubeJS server wrapper");
        }
        BubbleServerApi.showAllThemeJson(minecraftServer, themeId, overridesJson);
    }

    public static void clear(Object player) {
        ServerPlayer serverPlayer = findServerPlayer(player, 4);
        if (serverPlayer != null) {
            BubbleServerApi.clear(serverPlayer);
        }
    }

    public static void clearAll(Object server) {
        MinecraftServer minecraftServer = findServer(server, 4);
        if (minecraftServer != null) {
            BubbleServerApi.clearAll(minecraftServer);
        }
    }

    public static void onClick(Consumer<BubbleKubeJSServerClickEvent> listener) {
        clickListener = listener;
    }

    public static void dispatchClick(BubbleServerClickEvent event) {
        Consumer<BubbleKubeJSServerClickEvent> listener = clickListener;
        if (listener == null) return;
        try {
            listener.accept(new BubbleKubeJSServerClickEvent(event));
        } catch (RuntimeException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("Bubble Anyway KubeJS click listener failed", exception);
        }
    }

    private static ServerPlayer findServerPlayer(Object value, int depth) {
        if (value == null || depth <= 0) {
            return null;
        }
        if (value instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }

        for (String methodName : new String[]{"getMinecraftPlayer", "getServerPlayer", "getPlayer", "getEntity"}) {
            Object nested = invokeNoArg(value, methodName);
            ServerPlayer result = findServerPlayer(nested, depth - 1);
            if (result != null) {
                return result;
            }
        }
        return findServerPlayer(readField(value, "minecraftPlayer"), depth - 1);
    }

    private static MinecraftServer findServer(Object value, int depth) {
        if (value == null || depth <= 0) {
            return null;
        }
        if (value instanceof MinecraftServer minecraftServer) {
            return minecraftServer;
        }

        for (String methodName : new String[]{"getMinecraftServer", "getServer"}) {
            Object nested = invokeNoArg(value, methodName);
            MinecraftServer result = findServer(nested, depth - 1);
            if (result != null) {
                return result;
            }
        }
        return findServer(readField(value, "minecraftServer"), depth - 1);
    }

    private static Object invokeNoArg(Object value, String name) {
        try {
            Method method = value.getClass().getMethod(name);
            return method.invoke(value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object readField(Object value, String name) {
        Class<?> type = value.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(value);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static final class BubbleSpecJson {
        private BubbleSpecJson() {
        }

        private static String simple(String text) {
            return withText(text);
        }

        private static String withText(String text) {
            String escaped = text == null ? "" : text
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
            return "{\"text\":\"" + escaped + "\"}";
        }
    }
}
