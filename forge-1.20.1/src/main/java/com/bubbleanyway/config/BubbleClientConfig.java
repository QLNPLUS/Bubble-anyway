package com.bubbleanyway.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Client-side switches for replacing vanilla and supported third-party toasts. */
public final class BubbleClientConfig {
    private static final String CONFIG_FILE = "bubble_anyway/client.toml";
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> FALLBACK;
    private static final ForgeConfigSpec.BooleanValue ADVANCEMENT_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> ADVANCEMENT_TASK_THEME;
    private static final ForgeConfigSpec.ConfigValue<String> ADVANCEMENT_GOAL_THEME;
    private static final ForgeConfigSpec.ConfigValue<String> ADVANCEMENT_CHALLENGE_THEME;
    private static final ForgeConfigSpec.BooleanValue RECIPE_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> RECIPE_THEME;
    private static final ForgeConfigSpec.BooleanValue FTB_QUESTS_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> FTB_COMPLETION_THEME;
    private static final ForgeConfigSpec.ConfigValue<String> FTB_REWARD_THEME;
    private static final ForgeConfigSpec.BooleanValue DIAGNOSTICS_ENABLED;
    private static final ForgeConfigSpec.BooleanValue DIAGNOSTIC_TEST_BUBBLE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Bubble Anyway client toast integration settings.",
                "Themes are defined in config/bubble_anyway/themes.json or resource/data packs.").push("toast");
        ENABLED = builder.comment("Enable toast conversion in general.").define("enabled", true);
        FALLBACK = builder.comment("Behavior for supported toast types whose conversion fails: ORIGINAL or IGNORE.")
                .define("fallback", "ORIGINAL");

        builder.push("advancement");
        ADVANCEMENT_ENABLED = builder.comment("Replace advancement toasts with Bubble Anyway bubbles.")
                .define("enabled", true);
        ADVANCEMENT_TASK_THEME = builder.define("taskTheme", "bubble_anyway:toast_advancement_task");
        ADVANCEMENT_GOAL_THEME = builder.define("goalTheme", "bubble_anyway:toast_advancement_goal");
        ADVANCEMENT_CHALLENGE_THEME = builder.define("challengeTheme", "bubble_anyway:toast_advancement_challenge");
        builder.pop();

        builder.push("recipe");
        RECIPE_ENABLED = builder.comment("Replace recipe unlock toasts with Bubble Anyway bubbles.")
                .define("enabled", true);
        RECIPE_THEME = builder.define("theme", "bubble_anyway:toast_recipe");
        builder.pop();

        builder.push("ftbQuests");
        FTB_QUESTS_ENABLED = builder.comment("Replace supported FTB Quests completion and reward toasts.")
                .define("enabled", true);
        FTB_COMPLETION_THEME = builder.define("completionTheme", "bubble_anyway:toast_ftb_completion");
        FTB_REWARD_THEME = builder.define("rewardTheme", "bubble_anyway:toast_ftb_reward");
        builder.pop();
        builder.pop();

        builder.push("debug");
        DIAGNOSTICS_ENABLED = builder.comment("Log one automatic client diagnostics report after joining a world.")
                .define("diagnosticsEnabled", false);
        DIAGNOSTIC_TEST_BUBBLE = builder.comment("Show one visual diagnostics bubble after the report.")
                .define("showTestBubble", false);
        builder.pop();
        SPEC = builder.build();
    }

    private BubbleClientConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, CONFIG_FILE);
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static boolean advancementEnabled() {
        return enabled() && ADVANCEMENT_ENABLED.get();
    }

    public static boolean recipeEnabled() {
        return enabled() && RECIPE_ENABLED.get();
    }

    public static boolean ftbQuestsEnabled() {
        return enabled() && FTB_QUESTS_ENABLED.get();
    }

    public static String fallback() {
        String value = FALLBACK.get();
        return "IGNORE".equalsIgnoreCase(value) ? "IGNORE" : "ORIGINAL";
    }

    public static String advancementTheme(String frame) {
        return switch (frame.toUpperCase()) {
            case "GOAL" -> ADVANCEMENT_GOAL_THEME.get();
            case "CHALLENGE" -> ADVANCEMENT_CHALLENGE_THEME.get();
            default -> ADVANCEMENT_TASK_THEME.get();
        };
    }

    public static String recipeTheme() {
        return RECIPE_THEME.get();
    }

    public static String ftbCompletionTheme() {
        return FTB_COMPLETION_THEME.get();
    }

    public static String ftbRewardTheme() {
        return FTB_REWARD_THEME.get();
    }

    public static boolean diagnosticsEnabled() {
        return DIAGNOSTICS_ENABLED.get();
    }

    public static boolean showDiagnosticBubble() {
        return diagnosticsEnabled() && DIAGNOSTIC_TEST_BUBBLE.get();
    }
}
