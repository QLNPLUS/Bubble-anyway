package com.bubbleanyway.client;

import com.bubbleanyway.config.BubbleClientConfig;
import com.bubbleanyway.client.mixin.AdvancementToastAccessor;
import com.bubbleanyway.client.mixin.RecipeToastAccessor;
import com.bubbleanyway.data.BubbleSpec;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.client.toast.AdvancementToast;
import net.minecraft.client.toast.RecipeToast;
import net.minecraft.client.toast.Toast;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

/** Converts vanilla and optional FTB Quests toasts into Bubble Anyway bubbles on Fabric. */
public final class BubbleToastIntegration {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean hookSeen;

    private BubbleToastIntegration() {
    }

    public static boolean hookSeen() {
        return hookSeen;
    }

    public static boolean handle(Toast toast) {
        hookSeen = true;
        BubbleDiagnostics.toastHookReceived(toast.getClass().getName());
        diagnostic("toast hook: {}", toast.getClass().getName());
        if (!BubbleClientConfig.enabled()) {
            diagnostic("toast ignored: integration disabled");
            return false;
        }
        try {
            LOGGER.debug("Bubble Anyway received Fabric toast {}", toast.getClass().getName());
            Optional<ToastData> converted = convert(toast);
            if (converted.isEmpty()) {
                diagnostic("toast not supported or conversion empty: {}", toast.getClass().getName());
                return false;
            }
            ToastData data = converted.get();
            BubbleThemeClientCache.refreshLocalThemes();
            boolean accepted = BubbleThemeClientCache.enqueue(data.themeId(), data.overridesJson());
            LOGGER.debug("{} Fabric toast with Bubble Anyway theme {}",
                    accepted ? "Replaced or queued" : "Could not accept", data.themeId());
            diagnostic("toast converted: type={}, theme={}, accepted={}, active={}, pending={}, hasIcon={}",
                    toast.getClass().getName(), data.themeId(), accepted,
                    BubbleOverlay.activeCount(), BubbleOverlay.pendingCount(), data.hasIcon());
            return accepted || "IGNORE".equalsIgnoreCase(BubbleClientConfig.fallback());
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not convert a Fabric toast to Bubble Anyway", exception);
            diagnostic("toast conversion failed: {} -> {}", toast.getClass().getName(), exception.toString());
            return "IGNORE".equalsIgnoreCase(BubbleClientConfig.fallback());
        }
    }

    private static Optional<ToastData> convert(Toast toast) {
        if (toast instanceof AdvancementToast advancementToast) {
            return BubbleClientConfig.advancementEnabled() ? convertAdvancement(advancementToast) : Optional.empty();
        }
        if (toast instanceof RecipeToast recipeToast) {
            return BubbleClientConfig.recipeEnabled() ? convertRecipe(recipeToast) : Optional.empty();
        }
        if (BubbleClientConfig.ftbQuestsEnabled() && isFtbToast(toast)) {
            return convertFtb(toast);
        }
        return Optional.empty();
    }

    private static Optional<ToastData> convertAdvancement(Toast toast) {
        if (!(toast instanceof AdvancementToast advancementToast)) {
            return Optional.empty();
        }
        AdvancementEntry entry = ((AdvancementToastAccessor) advancementToast).bubbleAnyway$getAdvancement();
        AdvancementDisplay display = entry == null ? null : entry.value().display().orElse(null);
        if (entry == null || display == null) {
            LOGGER.warn("Could not read advancement display from Fabric toast {}", toast.getClass().getName());
            return Optional.empty();
        }
        String frameName = display.getFrame().name().toUpperCase(Locale.ROOT);
        JsonObject overrides = baseOverrides("toast_advancement_" + frameName.toLowerCase(Locale.ROOT),
                "advancement_" + entry.id().toString().replace(':', '_'));
        addTextParts(overrides, display.getTitle(), display.getDescription());
        addGenericIcon(overrides, display.getIcon());
        return Optional.of(new ToastData(BubbleClientConfig.advancementTheme(frameName), GSON.toJson(overrides), overrides.has("icon")));
    }

    private static Optional<ToastData> convertRecipe(Toast toast) {
        if (!(toast instanceof RecipeToast recipeToast)) {
            return Optional.empty();
        }
        List<?> recipes = ((RecipeToastAccessor) recipeToast).bubbleAnyway$getRecipes();
        if (recipes == null || recipes.isEmpty()) {
            return Optional.empty();
        }
        Object first = recipes.get(0);
        if (!(first instanceof RecipeEntry<?>)) {
            return Optional.empty();
        }
        RecipeEntry<?> recipe = (RecipeEntry<?>) first;
        JsonObject overrides = baseOverrides("toast_recipe", "recipe_" + recipe.id().toString().replace(':', '_'));
        addTextParts(overrides, Text.translatable("recipe.toast.title"),
                Text.translatable("recipe.toast.description"));
        addGenericIcon(overrides, recipe.value().createIcon());
        return Optional.of(new ToastData(BubbleClientConfig.recipeTheme(), GSON.toJson(overrides), overrides.has("icon")));
    }

    private static Optional<ToastData> convertFtb(Toast toast) {
        Object title = invokeAny(toast, "getTitle", "title");
        Object subtitle = invokeAny(toast, "getSubtitle", "subtitle");
        Object icon = invokeAny(toast, "getIcon", "icon");
        if (title == null && subtitle == null && icon == null) {
            return Optional.empty();
        }
        boolean completion = toast.getClass().getName().endsWith("ToastQuestObject");
        JsonObject overrides = baseOverrides(completion ? "toast_ftb_completion" : "toast_ftb_reward",
                "ftb_" + Integer.toHexString(System.identityHashCode(toast)));
        addTextParts(overrides, textValue(title), textValue(subtitle));
        addGenericIcon(overrides, icon);
        return Optional.of(new ToastData(
                completion ? BubbleClientConfig.ftbCompletionTheme() : BubbleClientConfig.ftbRewardTheme(),
                GSON.toJson(overrides), overrides.has("icon")));
    }

    private static JsonObject baseOverrides(String kind, String id) {
        JsonObject overrides = new JsonObject();
        overrides.addProperty("id", "bubble_anyway:" + kind + "_" + id);
        overrides.addProperty("layer", BubbleSpec.RenderLayer.ABOVE_PAUSE.name());
        return overrides;
    }

    private static void addTextParts(JsonObject overrides, Object title, Object subtitle) {
        JsonArray parts = new JsonArray();
        String titleText = textValue(title);
        String subtitleText = textValue(subtitle);
        if (!titleText.isBlank()) {
            parts.add(textPart("title", titleText));
        }
        if (!subtitleText.isBlank()) {
            parts.add(textPart("subtitle", "\n" + subtitleText));
        }
        overrides.add("textParts", parts);
    }

    private static JsonObject textPart(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("role", role);
        part.addProperty("text", text);
        return part;
    }

    private static void addGenericIcon(JsonObject overrides, Object icon) {
        if (icon instanceof ItemStack stack && !stack.isEmpty()) {
            overrides.addProperty("icon", Registries.ITEM.getId(stack.getItem()).toString());
            overrides.addProperty("iconType", BubbleSpec.IconType.ITEM.name());
            diagnostic("icon converted as item: {}", Registries.ITEM.getId(stack.getItem()));
            return;
        }
        Object stack = invokeAny(icon, "getStack", "getIngredient");
        if (stack instanceof ItemStack item && !item.isEmpty()) {
            addGenericIcon(overrides, item);
            return;
        }
        Object texture = invokeAny(icon, "getResourceLocation", "texture");
        if (texture instanceof Identifier id) {
            overrides.addProperty("icon", id.toString());
            overrides.addProperty("iconType", BubbleSpec.IconType.TEXTURE.name());
            diagnostic("icon converted as texture: {}", id);
        }
    }

    private static void diagnostic(String message, Object... arguments) {
        if (BubbleClientConfig.diagnosticsEnabled()) {
            LOGGER.info("Bubble Anyway diagnostics: " + message, arguments);
        }
    }

    private static boolean isFtbToast(Toast toast) {
        String name = toast.getClass().getName();
        return name.startsWith("dev.ftb.mods.ftbquests.client.gui.")
                && hasMethod(toast, "getTitle") && hasMethod(toast, "getSubtitle") && hasMethod(toast, "getIcon");
    }

    private static String textValue(Object value) {
        if (value instanceof Text text) {
            return text.getString();
        }
        Object result = invokeAny(value, "getString", "asString");
        return result == null ? (value == null ? "" : String.valueOf(value)) : String.valueOf(result);
    }

    private static Object invokeText(Object target, String... names) {
        return invokeAny(target, names);
    }

    private static Object invokeAny(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Object unwrapOptional(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static boolean hasMethod(Object target, String name) {
        try {
            target.getClass().getMethod(name);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static List<?> listField(Object target, String... names) {
        Object value = namedField(target, names);
        return value instanceof List<?> list ? list : null;
    }

    private static Object namedField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (value instanceof List<?> || value.getClass().getName().contains("Advancement")) {
                        return value;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private record ToastData(String themeId, String overridesJson, boolean hasIcon) {
    }
}
