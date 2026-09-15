package com.bubbleanyway.client;

import com.bubbleanyway.config.BubbleClientConfig;
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
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.client.event.ToastAddEvent;
import org.slf4j.Logger;

/** Converts supported vanilla and optional FTB Quests toasts into Bubble Anyway specs. */
public final class BubbleToastIntegration {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean hookSeen;

    private BubbleToastIntegration() {
    }

    public static boolean hookSeen() {
        return hookSeen;
    }

    public static void handle(ToastAddEvent event) {
        hookSeen = true;
        BubbleDiagnostics.toastHookReceived(event.getToast().getClass().getName());
        diagnostic("toast hook: {}", event.getToast().getClass().getName());
        if (!BubbleClientConfig.enabled()) {
            diagnostic("toast ignored: integration disabled");
            return;
        }

        try {
            LOGGER.debug("Bubble Anyway received toast {}", event.getToast().getClass().getName());
            Optional<ToastData> converted = convert(event.getToast());
            if (converted.isEmpty()) {
                diagnostic("toast not supported or conversion empty: {}", event.getToast().getClass().getName());
                return;
            }

            ToastData data = converted.get();
            BubbleThemeClientCache.refreshLocalThemes();
            boolean accepted = BubbleThemeClientCache.enqueue(data.themeId(), data.overridesJson());
            diagnostic("toast converted: type={}, theme={}, accepted={}, active={}, pending={}, hasIcon={}",
                    event.getToast().getClass().getName(), data.themeId(), accepted,
                    BubbleOverlay.activeCount(), BubbleOverlay.pendingCount(), data.overridesJson().contains("\"icon\""));
            if (accepted) {
                event.setCanceled(true);
                LOGGER.debug("Replaced or queued toast {} with Bubble Anyway theme {}",
                        event.getToast().getClass().getName(), data.themeId());
            } else {
                LOGGER.debug("Bubble Anyway could not accept toast {}; keeping the original toast",
                        event.getToast().getClass().getName());
                if ("IGNORE".equalsIgnoreCase(BubbleClientConfig.fallback())) {
                    event.setCanceled(true);
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not convert a toast to a Bubble Anyway bubble", exception);
            diagnostic("toast conversion failed: {} -> {}", event.getToast().getClass().getName(), exception.toString());
            if ("IGNORE".equalsIgnoreCase(BubbleClientConfig.fallback())) {
                event.setCanceled(true);
            }
        }
    }

    private static Optional<ToastData> convert(Toast toast) {
        if (toast instanceof AdvancementToast advancementToast) {
            if (!BubbleClientConfig.advancementEnabled()) {
                return Optional.empty();
            }
            return convertAdvancement(advancementToast);
        }
        if (toast instanceof RecipeToast recipeToast) {
            if (!BubbleClientConfig.recipeEnabled()) {
                return Optional.empty();
            }
            return convertRecipe(recipeToast);
        }
        if (BubbleClientConfig.ftbQuestsEnabled() && isFtbToast(toast)) {
            return convertFtb(toast);
        }
        return Optional.empty();
    }

    private static Optional<ToastData> convertAdvancement(AdvancementToast toast) {
        Advancement advancement = readField(toast, Advancement.class, "advancement", "e");
        if (advancement == null || advancement.getDisplay() == null) {
            LOGGER.warn("Could not read advancement data from {}. The runtime may use a mapped field name that is not known to Bubble Anyway.",
                    toast.getClass().getName());
            return Optional.empty();
        }

        DisplayInfo display = advancement.getDisplay();
        String frame = display.getFrame().getName().toUpperCase(Locale.ROOT);
        JsonObject overrides = baseOverrides(
                "bubble_anyway:toast_advancement_" + frame.toLowerCase(Locale.ROOT),
                "advancement_" + advancement.getId().toString().replace(':', '_'));
        addTextParts(overrides, display.getTitle(), display.getDescription());
        addItemIcon(overrides, display.getIcon());
        return Optional.of(new ToastData(BubbleClientConfig.advancementTheme(frame), GSON.toJson(overrides)));
    }

    private static Optional<ToastData> convertRecipe(RecipeToast toast) {
        List<?> recipes = readField(toast, List.class, "recipes", "g");
        if (recipes == null || recipes.isEmpty() || !(recipes.get(0) instanceof Recipe<?> recipe)) {
            LOGGER.warn("Could not read recipe data from {}. The runtime may use a mapped field name that is not known to Bubble Anyway.",
                    toast.getClass().getName());
            return Optional.empty();
        }

        JsonObject overrides = baseOverrides("bubble_anyway:toast_recipe", "recipe_" + recipe.getId());
        addTextParts(overrides, Component.translatable("recipe.toast.title"),
                Component.translatable("recipe.toast.description"));
        addItemIcon(overrides, recipe.getToastSymbol());
        return Optional.of(new ToastData(BubbleClientConfig.recipeTheme(), GSON.toJson(overrides)));
    }

    private static Optional<ToastData> convertFtb(Toast toast) {
        Component title = invoke(toast, "getTitle", Component.class);
        Component subtitle = invoke(toast, "getSubtitle", Component.class);
        Object icon = invoke(toast, "getIcon", Object.class);
        if (title == null && subtitle == null && icon == null) {
            return Optional.empty();
        }

        boolean completion = toast.getClass().getName().endsWith("ToastQuestObject");
        JsonObject overrides = baseOverrides(
                completion ? "bubble_anyway:toast_ftb_completion" : "bubble_anyway:toast_ftb_reward",
                "ftb_" + Integer.toHexString(System.identityHashCode(toast)));
        addTextParts(overrides, title, subtitle);
        addGenericIcon(overrides, icon);
        return Optional.of(new ToastData(
                completion ? BubbleClientConfig.ftbCompletionTheme() : BubbleClientConfig.ftbRewardTheme(),
                GSON.toJson(overrides)));
    }

    private static JsonObject baseOverrides(String id, String fallbackId) {
        JsonObject overrides = new JsonObject();
        overrides.addProperty("id", id + "_" + fallbackId);
        // Keep converted Toasts above the pause screen even when an older local theme file is used.
        overrides.addProperty("layer", BubbleSpec.RenderLayer.ABOVE_PAUSE.name());
        return overrides;
    }

    private static void addTextParts(JsonObject overrides, Component title, Component subtitle) {
        JsonArray parts = new JsonArray();
        if (title != null && !title.getString().isBlank()) {
            parts.add(textPart("title", title.getString()));
        }
        if (subtitle != null && !subtitle.getString().isBlank()) {
            parts.add(textPart("subtitle", "\n" + subtitle.getString()));
        }
        overrides.add("textParts", parts);
    }

    private static JsonObject textPart(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("role", role);
        part.addProperty("text", text);
        return part;
    }

    private static void addItemIcon(JsonObject overrides, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            diagnostic("item icon missing or empty");
            return;
        }
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        overrides.addProperty("icon", itemId.toString());
        overrides.addProperty("iconType", BubbleSpec.IconType.ITEM.name());
        diagnostic("icon converted as item: {}", itemId);
    }

    private static void addGenericIcon(JsonObject overrides, Object icon) {
        if (icon == null) {
            return;
        }
        if (icon instanceof ItemStack stack) {
            addItemIcon(overrides, stack);
            return;
        }

        ItemStack stack = invoke(icon, "getStack", ItemStack.class);
        if (stack == null) {
            stack = invoke(icon, "getIngredient", ItemStack.class);
        }
        if (stack != null && !stack.isEmpty()) {
            addItemIcon(overrides, stack);
            return;
        }

        ResourceLocation texture = invoke(icon, "getResourceLocation", ResourceLocation.class);
        if (texture == null) {
            texture = readField(icon, ResourceLocation.class, "texture");
        }
        // FTB Library's ImageIcon already exposes the final texture location. Do not require a
        // literal "textures/" path: resource-pack-backed icons may use a custom path.
        if (texture != null) {
            overrides.addProperty("icon", texture.toString());
            overrides.addProperty("iconType", BubbleSpec.IconType.TEXTURE.name());
            diagnostic("icon converted as texture: {}", texture);
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
                && hasMethod(toast, "getTitle")
                && hasMethod(toast, "getSubtitle")
                && hasMethod(toast, "getIcon");
    }

    private static boolean hasMethod(Object target, String name) {
        try {
            target.getClass().getMethod(name);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static <T> T invoke(Object target, String name, Class<T> type) {
        try {
            Method method = target.getClass().getMethod(name);
            Object value = method.invoke(target);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static <T> T readField(Object target, Class<T> type, String... names) {
        Class<?> current = target.getClass();
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (type.isInstance(value)) {
                        return type.cast(value);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next mapped or obfuscated field name.
                }
            }
            current = current.getSuperclass();
        }

        // Production Minecraft uses obfuscated field names. If the mapped aliases above are
        // unavailable, locate the first instance field whose runtime value matches the type.
        current = target.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (type.isInstance(value)) {
                        return type.cast(value);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next field. Mapped and obfuscated runtimes can expose different names.
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private record ToastData(String themeId, String overridesJson) {
    }
}
