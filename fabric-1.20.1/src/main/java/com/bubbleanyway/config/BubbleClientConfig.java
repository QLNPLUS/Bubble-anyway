package com.bubbleanyway.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Lightweight TOML-compatible client settings for Fabric toast integration. */
public final class BubbleClientConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("bubble_anyway/client.toml");

    private BubbleClientConfig() {
    }

    public static boolean enabled() {
        return bool("toast.enabled", true);
    }

    public static boolean advancementEnabled() {
        return enabled() && bool("toast.advancement.enabled", true);
    }

    public static boolean recipeEnabled() {
        return enabled() && bool("toast.recipe.enabled", true);
    }

    public static boolean ftbQuestsEnabled() {
        return enabled() && bool("toast.ftbQuests.enabled", true);
    }

    public static String fallback() {
        return "IGNORE".equalsIgnoreCase(value("toast.fallback", "ORIGINAL")) ? "IGNORE" : "ORIGINAL";
    }

    public static String advancementTheme(String frame) {
        return switch (frame.toUpperCase()) {
            case "GOAL" -> value("toast.advancement.goalTheme", "bubble_anyway:toast_advancement_goal");
            case "CHALLENGE" -> value("toast.advancement.challengeTheme", "bubble_anyway:toast_advancement_challenge");
            default -> value("toast.advancement.taskTheme", "bubble_anyway:toast_advancement_task");
        };
    }

    public static String recipeTheme() {
        return value("toast.recipe.theme", "bubble_anyway:toast_recipe");
    }

    public static String ftbCompletionTheme() {
        return value("toast.ftbQuests.completionTheme", "bubble_anyway:toast_ftb_completion");
    }

    public static String ftbRewardTheme() {
        return value("toast.ftbQuests.rewardTheme", "bubble_anyway:toast_ftb_reward");
    }

    public static boolean diagnosticsEnabled() {
        return bool("debug.diagnosticsEnabled", false);
    }

    public static boolean showDiagnosticBubble() {
        return diagnosticsEnabled() && bool("debug.showTestBubble", false);
    }

    private static boolean bool(String key, boolean fallback) {
        return Boolean.parseBoolean(value(key, Boolean.toString(fallback)));
    }

    private static String value(String key, String fallback) {
        Map<String, String> values = read();
        return values.getOrDefault(key, fallback);
    }

    private static Map<String, String> read() {
        ensure();
        Map<String, String> values = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException ignored) {
            // Defaults remain active when the optional file cannot be read.
        }
        return values;
    }

    private static void ensure() {
        if (Files.isRegularFile(FILE)) {
            return;
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "# Bubble Anyway Fabric client settings\n"
                    + "toast.enabled = true\n"
                    + "toast.fallback = \"ORIGINAL\"\n"
                    + "toast.advancement.enabled = true\n"
                    + "toast.advancement.taskTheme = \"bubble_anyway:toast_advancement_task\"\n"
                    + "toast.advancement.goalTheme = \"bubble_anyway:toast_advancement_goal\"\n"
                    + "toast.advancement.challengeTheme = \"bubble_anyway:toast_advancement_challenge\"\n"
                    + "toast.recipe.enabled = true\n"
                    + "toast.recipe.theme = \"bubble_anyway:toast_recipe\"\n"
                    + "toast.ftbQuests.enabled = true\n"
                    + "toast.ftbQuests.completionTheme = \"bubble_anyway:toast_ftb_completion\"\n"
                    + "toast.ftbQuests.rewardTheme = \"bubble_anyway:toast_ftb_reward\"\n"
                    + "debug.diagnosticsEnabled = false\n"
                    + "debug.showTestBubble = false\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Defaults remain active when the file cannot be created.
        }
    }
}
