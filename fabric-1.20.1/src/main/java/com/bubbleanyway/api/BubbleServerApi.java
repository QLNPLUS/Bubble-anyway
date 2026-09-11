package com.bubbleanyway.api;

import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleThemeManager;
import com.bubbleanyway.network.BubbleNetwork;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-side entry point for mods that want to show bubbles without commands. */
public final class BubbleServerApi {
    private BubbleServerApi() {
    }

    public static void show(ServerPlayerEntity player, BubbleSpec spec) {
        if (player != null) {
            show(List.of(player), spec);
        }
    }

    public static void show(Collection<? extends ServerPlayerEntity> players, BubbleSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Bubble spec cannot be null");
        }

        List<ServerPlayerEntity> targets = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            if (player != null) {
                targets.add(player);
            }
        }
        BubbleNetwork.send(targets, spec);
    }

    public static void showJson(ServerPlayerEntity player, String json) {
        Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        if (theme.isPresent()) {
            showThemeJson(player, theme.get(), json);
        } else {
            show(player, BubbleSpec.fromJson(json));
        }
    }

    public static void showJson(Collection<? extends ServerPlayerEntity> players, String json) {
        Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        if (theme.isPresent()) {
            showThemeJson(players, theme.get(), json);
        } else {
            show(players, BubbleSpec.fromJson(json));
        }
    }

    public static void showAll(MinecraftServer server, BubbleSpec spec) {
        if (server != null) {
            show(server.getPlayerManager().getPlayerList(), spec);
        }
    }

    public static void showAllJson(MinecraftServer server, String json) {
        Optional<String> theme = BubbleThemeManager.themeFromJson(json);
        if (theme.isPresent()) {
            showAllThemeJson(server, theme.get(), json);
        } else {
            showAll(server, BubbleSpec.fromJson(json));
        }
    }

    public static void showTheme(ServerPlayerEntity player, String themeId, String text) {
        showThemeJson(player, themeId, BubbleThemeManager.overridesWithText(text));
    }

    public static void showTheme(Collection<? extends ServerPlayerEntity> players, String themeId, String text) {
        showThemeJson(players, themeId, BubbleThemeManager.overridesWithText(text));
    }

    public static void showThemeJson(ServerPlayerEntity player, String themeId, String overridesJson) {
        if (player != null) {
            showThemeJson(List.of(player), themeId, overridesJson);
        }
    }

    public static void showThemeJson(Collection<? extends ServerPlayerEntity> players, String themeId, String overridesJson) {
        Objects.requireNonNull(players, "players");
        String normalizedOverrides = BubbleThemeManager.withoutThemeField(overridesJson);
        BubbleThemeManager.resolve(themeId, normalizedOverrides);
        List<ServerPlayerEntity> targets = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            if (player != null) {
                targets.add(player);
            }
        }
        BubbleNetwork.sendTheme(targets, themeId, normalizedOverrides);
    }

    public static void showAllTheme(MinecraftServer server, String themeId, String text) {
        showAllThemeJson(server, themeId, BubbleThemeManager.overridesWithText(text));
    }

    public static void showAllThemeJson(MinecraftServer server, String themeId, String overridesJson) {
        if (server != null) {
            showThemeJson(server.getPlayerManager().getPlayerList(), themeId, overridesJson);
        }
    }

    public static void clear(ServerPlayerEntity player) {
        if (player != null) {
            BubbleNetwork.clear(List.of(player));
        }
    }

    public static void clear(Collection<? extends ServerPlayerEntity> players) {
        List<ServerPlayerEntity> targets = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            if (player != null) {
                targets.add(player);
            }
        }
        BubbleNetwork.clear(targets);
    }

    public static void clearAll(MinecraftServer server) {
        if (server != null) {
            clear(server.getPlayerManager().getPlayerList());
        }
    }
}
