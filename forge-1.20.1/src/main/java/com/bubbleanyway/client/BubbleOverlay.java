package com.bubbleanyway.client;

import com.bubbleanyway.data.BubbleSpec;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

public final class BubbleOverlay {
    private static final int MAX_ACTIVE_BUBBLES = 16;
    private static final int SCREEN_MARGIN = 6;
    private static final float MIN_RENDER_ALPHA = 0.02F;
    private static final float LAYER_Z = 1000.0F;
    private static final List<ActiveBubble> ACTIVE = new ArrayList<>();
    private static final Map<ResourceLocation, TextureSize> TEXTURE_SIZES = new HashMap<>();
    private static final Map<GeneratedTextureKey, PreparedTexture> GENERATED_TEXTURES = new HashMap<>();
    private static final Map<ResourceLocation, ItemStack> ICON_STACKS = new HashMap<>();
    private static long sequence;
    private static long generatedTextureSequence;

    private BubbleOverlay() {
    }

    public static synchronized void enqueue(BubbleSpec spec) {
        if (spec.replace()) {
            ACTIVE.removeIf(active -> active.spec.id().equals(spec.id()));
        }
        ACTIVE.add(new ActiveBubble(spec, System.nanoTime(), sequence++));
        ACTIVE.sort(Comparator.comparingInt((ActiveBubble active) -> active.spec.priority()).reversed()
                .thenComparingLong(active -> active.sequence));
        while (ACTIVE.size() > MAX_ACTIVE_BUBBLES) {
            ACTIVE.remove(ACTIVE.size() - 1);
        }
        playSound(spec);
    }

    public static synchronized void clear() {
        ACTIVE.clear();
    }

    private static void playSound(BubbleSpec spec) {
        if (spec.sound().isBlank() || spec.soundVolume() <= 0.0F) {
            return;
        }

        ResourceLocation soundId = ResourceLocation.tryParse(spec.sound());
        if (soundId == null) {
            return;
        }

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(sound, spec.soundVolume(), spec.soundPitch()));
        }
    }

    public static void renderTop(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        long now = System.nanoTime();
        List<ActiveBubble> visible = snapshot(now);
        if (visible.isEmpty()) {
            return;
        }

        // Finish lower GUI batches first so this layer cannot be reordered below them.
        graphics.flush();
        minecraft.renderBuffers().bufferSource().endBatch();
        RenderSystem.disableDepthTest();

        Font font = minecraft.font;
        EnumMap<BubbleSpec.Anchor, Integer> stackOffsets = new EnumMap<>(BubbleSpec.Anchor.class);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, LAYER_Z);
        for (ActiveBubble active : visible) {
            ItemStack iconStack = resolveIcon(active.spec.iconId());
            BubbleLayout layout = BubbleLayout.create(font, active.spec, screenWidth, !iconStack.isEmpty());
            int stackOffset = stackOffsets.getOrDefault(active.spec.anchor(), 0);
            renderBubble(graphics, font, active, layout, iconStack, stackOffset, screenWidth, screenHeight, now);
            stackOffsets.put(active.spec.anchor(), stackOffset + layout.scaledHeight + SCREEN_MARGIN);
        }
        graphics.flush();
        graphics.pose().popPose();
        RenderSystem.enableDepthTest();
    }

    private static synchronized List<ActiveBubble> snapshot(long now) {
        ACTIVE.removeIf(active -> active.ageTicks(now) >= active.spec.duration());
        return List.copyOf(ACTIVE);
    }

    private static void renderBubble(
            GuiGraphics graphics,
            Font font,
            ActiveBubble active,
            BubbleLayout layout,
            ItemStack iconStack,
            int stackOffset,
            int screenWidth,
            int screenHeight,
            long now) {
        BubbleSpec spec = active.spec;
        double age = active.ageTicks(now);
        float alpha = alpha(spec, age);
        if (alpha <= MIN_RENDER_ALPHA) {
            return;
        }
        float entrance = eased(Math.min(1.0F, spec.fadeIn() <= 0 ? 1.0F : (float) (age / spec.fadeIn())));

        float targetX = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> spec.x();
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenWidth - layout.scaledWidth + spec.x();
            case CENTER_TOP, CENTER, CENTER_BOTTOM -> (screenWidth - layout.scaledWidth) / 2.0F + spec.x();
        };
        float targetY = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_TOP, TOP_RIGHT -> spec.y() + stackOffset;
            case BOTTOM_LEFT, CENTER_BOTTOM, BOTTOM_RIGHT -> screenHeight - layout.scaledHeight - spec.y() - stackOffset;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (screenHeight - layout.scaledHeight) / 2.0F + spec.y() + stackOffset;
        };

        // Clamp only the destination. The animated position must be allowed outside the screen.
        float minX = SCREEN_MARGIN;
        float maxX = Math.max(minX, screenWidth - layout.scaledWidth - SCREEN_MARGIN);
        float minY = SCREEN_MARGIN;
        float maxY = Math.max(minY, screenHeight - layout.scaledHeight - SCREEN_MARGIN);
        targetX = Math.max(minX, Math.min(maxX, targetX));
        targetY = Math.max(minY, Math.min(maxY, targetY));

        float slideDistance = spec.animation() == BubbleSpec.Animation.FADE ? 0.0F
                : (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_LEFT || spec.animation() == BubbleSpec.Animation.SLIDE_FROM_RIGHT
                ? layout.scaledWidth : layout.scaledHeight) + SCREEN_MARGIN + 8.0F;
        float slideIn = (1.0F - entrance) * slideDistance;
        float slideOut = exitProgress(spec, age) * slideDistance;
        float screenX = targetX;
        float screenY = targetY;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_LEFT) screenX -= slideIn + slideOut;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_RIGHT) screenX += slideIn + slideOut;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_TOP) screenY -= slideIn + slideOut;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_BOTTOM) screenY += slideIn + slideOut;

        graphics.pose().pushPose();
        graphics.pose().translate(screenX, screenY, 0.0F);
        graphics.pose().scale(spec.scale(), spec.scale(), 1.0F);
        if (!spec.backgroundTexture().isBlank()) {
            ResourceLocation sourceTexture = ResourceLocation.tryParse(spec.backgroundTexture());
            TextureSize sourceSize = sourceTexture == null ? null : textureSize(sourceTexture);
            if (sourceTexture == null || sourceSize == null) {
                graphics.fill(0, 0, layout.width, layout.height, withAlpha(spec.backgroundColor(), alpha));
                graphics.pose().popPose();
                return;
            }
            PreparedTexture prepared = spec.backgroundBorder() > 0
                    ? prepareNineSliceTexture(sourceTexture, sourceSize, spec.backgroundBorder(), spec.backgroundGuide(),
                    layout.width, layout.height)
                    : new PreparedTexture(sourceTexture, sourceSize, spec.backgroundGuide());
            ResourceLocation texture = prepared.texture();
            TextureSize textureSize = prepared.size();
            Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(false, false);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.blit(texture, 0, 0, 0, 0.0F, 0.0F,
                    layout.width, layout.height, textureSize.width, textureSize.height);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            graphics.fill(0, 0, layout.width, layout.height, withAlpha(spec.backgroundColor(), alpha));
        }

        if (!iconStack.isEmpty()) {
            graphics.flush();
            int iconY = spec.padding() + Math.max(0,
                    (layout.height - spec.padding() * 2 - layout.iconSize) / 2);
            graphics.pose().pushPose();
            graphics.pose().translate(spec.padding() + spec.iconOffsetX(),
                    iconY + spec.iconOffsetY(), 0.0F);
            float iconScale = layout.iconSize / 16.0F;
            graphics.pose().scale(iconScale, iconScale, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            graphics.renderItem(iconStack, 0, 0);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().popPose();
            graphics.flush();
        }

        int textY = spec.padding() + spec.textOffsetY();
        for (FormattedCharSequence line : layout.lines) {
            int lineWidth = font.width(line);
            int textX = switch (spec.textAlignment()) {
                case LEFT -> layout.textStartX;
                case CENTER -> layout.textStartX + (layout.textAreaWidth - lineWidth) / 2;
                case RIGHT -> layout.textStartX + layout.textAreaWidth - lineWidth;
            };
            int textAreaEnd = layout.textStartX + layout.textAreaWidth;
            textX = Math.max(layout.textStartX, Math.min(textAreaEnd - lineWidth, textX));
            graphics.drawString(font, line, textX + spec.textOffsetX(), textY,
                    withAlpha(spec.textColor(), alpha), spec.shadow());
            textY += 9;
        }
        graphics.pose().popPose();
    }

    private static PreparedTexture prepareNineSliceTexture(
            ResourceLocation sourceTexture,
            TextureSize sourceSize,
            int requestedBorder,
            int requestedGuide,
            int targetWidth,
            int targetHeight) {
        if (requestedGuide <= 0) {
            return new PreparedTexture(sourceTexture, sourceSize, 0);
        }

        int sourceBorder = Math.min(requestedBorder, Math.min(sourceSize.width / 2, sourceSize.height / 2));
        int sourceGuide = Math.min(requestedGuide, Math.min(
                Math.max(0, (sourceSize.width - sourceBorder * 2) / 2),
                Math.max(0, (sourceSize.height - sourceBorder * 2) / 2)));
        if (sourceBorder <= 0 || sourceGuide <= 0) {
            return new PreparedTexture(sourceTexture, sourceSize, requestedGuide);
        }

        GeneratedTextureKey key = new GeneratedTextureKey(
                sourceTexture, sourceSize, sourceBorder, sourceGuide, targetWidth, targetHeight);
        PreparedTexture cached = GENERATED_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        if (targetWidth <= 0 || targetHeight <= 0) {
            return new PreparedTexture(sourceTexture, sourceSize, requestedGuide);
        }

        NativeImage preparedImage = null;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(sourceTexture);
            if (resource.isEmpty()) {
                return new PreparedTexture(sourceTexture, sourceSize, requestedGuide);
            }

            try (InputStream inputStream = resource.get().open(); NativeImage sourceImage = NativeImage.read(inputStream)) {
                // Rasterize the complete nine-slice at its final size. A single continuous texture
                // removes both guide pixels and UV seams between independently blitted patches.
                preparedImage = new NativeImage(sourceImage.format(), targetWidth, targetHeight, false);
                for (int y = 0; y < targetHeight; y++) {
                    int sourceY = mapNineSliceCoordinate(
                            y, targetHeight, sourceSize.height, sourceBorder, sourceGuide);
                    for (int x = 0; x < targetWidth; x++) {
                        int sourceX = mapNineSliceCoordinate(
                                x, targetWidth, sourceSize.width, sourceBorder, sourceGuide);
                        preparedImage.setPixelRGBA(x, y, sourceImage.getPixelRGBA(sourceX, sourceY));
                    }
                }
            }

            ResourceLocation generatedId = new ResourceLocation(
                    "bubble_anyway", "generated/9slice/" + generatedTextureSequence++);
            DynamicTexture dynamicTexture = new DynamicTexture(preparedImage);
            preparedImage = null;
            Minecraft.getInstance().getTextureManager().register(generatedId, dynamicTexture);
            PreparedTexture prepared = new PreparedTexture(
                    generatedId, new TextureSize(targetWidth, targetHeight), 0);
            GENERATED_TEXTURES.put(key, prepared);
            return prepared;
        } catch (IOException | RuntimeException ignored) {
            if (preparedImage != null) {
                preparedImage.close();
            }
            return new PreparedTexture(sourceTexture, sourceSize, requestedGuide);
        }
    }

    private static int mapNineSliceCoordinate(
            int destinationCoordinate,
            int destinationSize,
            int sourceSize,
            int sourceBorder,
            int sourceGuide) {
        int destinationBorder = Math.min(sourceBorder, destinationSize / 2);
        int destinationCenterSize = destinationSize - destinationBorder * 2;
        int sourceCenterStart = sourceBorder + sourceGuide;
        int sourceCenterSize = sourceSize - sourceBorder * 2 - sourceGuide * 2;
        int sourceRightStart = sourceSize - sourceBorder;

        if (destinationCoordinate < destinationBorder) {
            return destinationCoordinate;
        }
        if (destinationCoordinate >= destinationSize - destinationBorder) {
            return sourceRightStart + destinationCoordinate - (destinationSize - destinationBorder);
        }
        if (destinationCenterSize <= 0 || sourceCenterSize <= 0) {
            return Math.min(sourceSize - 1, sourceCenterStart);
        }

        int centerOffset = destinationCoordinate - destinationBorder;
        return sourceCenterStart + Math.min(
                sourceCenterSize - 1,
                (int) ((long) centerOffset * sourceCenterSize / destinationCenterSize));
    }

    private static TextureSize textureSize(ResourceLocation texture) {
        if (TEXTURE_SIZES.containsKey(texture)) {
            return TEXTURE_SIZES.get(texture);
        }

        TextureSize size = null;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (InputStream inputStream = resource.get().open(); NativeImage image = NativeImage.read(inputStream)) {
                    size = new TextureSize(image.getWidth(), image.getHeight());
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Fall back to the regular background color when a texture cannot be decoded.
        }
        TEXTURE_SIZES.put(texture, size);
        return size;
    }

    private static ItemStack resolveIcon(String iconId) {
        ResourceLocation id = ResourceLocation.tryParse(iconId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return ICON_STACKS.computeIfAbsent(id,
                key -> BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY));
    }

    private static float alpha(BubbleSpec spec, double age) {
        float fadeIn = spec.fadeIn() <= 0 ? 1.0F : Math.min(1.0F, (float) (age / spec.fadeIn()));
        double fadeOutStart = Math.max(0, spec.duration() - spec.fadeOut());
        float fadeOut = spec.fadeOut() <= 0 || age < fadeOutStart ? 1.0F : Math.min(1.0F, (float) ((spec.duration() - age) / spec.fadeOut()));
        return Math.max(0.0F, Math.min(1.0F, Math.min(fadeIn, fadeOut)));
    }

    private static float exitProgress(BubbleSpec spec, double age) {
        if (spec.fadeOut() <= 0) {
            return 0.0F;
        }

        double fadeOutStart = Math.max(0, spec.duration() - spec.fadeOut());
        if (age <= fadeOutStart) {
            return 0.0F;
        }
        return eased(Math.min(1.0F, (float) ((age - fadeOutStart) / spec.fadeOut())));
    }

    private static float eased(float progress) {
        float inverse = 1.0F - progress;
        return 1.0F - inverse * inverse * inverse;
    }

    private static int withAlpha(int argb, float opacity) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private record ActiveBubble(BubbleSpec spec, long createdAt, long sequence) {
        private double ageTicks(long now) {
            return (now - createdAt) / 50_000_000.0D;
        }
    }

    private record TextureSize(int width, int height) {
    }

    private record GeneratedTextureKey(
            ResourceLocation sourceTexture,
            TextureSize sourceSize,
            int border,
            int guide,
            int targetWidth,
            int targetHeight) {
    }

    private record PreparedTexture(ResourceLocation texture, TextureSize size, int guide) {
    }

    private static final class BubbleLayout {
        private final List<FormattedCharSequence> lines;
        private final int width;
        private final int height;
        private final int scaledWidth;
        private final int scaledHeight;
        private final int iconSize;
        private final int textStartX;
        private final int textAreaWidth;

        private BubbleLayout(
                List<FormattedCharSequence> lines,
                int width,
                int height,
                float scale,
                int iconSize,
                int textStartX,
                int textAreaWidth) {
            this.lines = lines;
            this.width = width;
            this.height = height;
            this.scaledWidth = Math.round(width * scale);
            this.scaledHeight = Math.round(height * scale);
            this.iconSize = iconSize;
            this.textStartX = textStartX;
            this.textAreaWidth = textAreaWidth;
        }

        private static BubbleLayout create(Font font, BubbleSpec spec, int screenWidth, boolean hasIcon) {
            int maxWidth = Math.min(spec.maxWidth(), Math.max(40, screenWidth - SCREEN_MARGIN * 2));
            int iconWidth = hasIcon ? spec.iconSize() + spec.iconGap() : 0;
            int contentWidth = spec.width() > 0
                    ? Math.max(1, spec.width() - spec.padding() * 2 - iconWidth)
                    : Math.max(1, maxWidth - spec.padding() * 2 - iconWidth);
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(spec.textColor() & 0x00FFFFFF))
                    .withBold(spec.bold())
                    .withItalic(spec.italic())
                    .withUnderlined(spec.underlined())
                    .withStrikethrough(spec.strikethrough())
                    .withObfuscated(spec.obfuscated());

            List<FormattedCharSequence> lines = new ArrayList<>();
            for (String part : spec.text().split("\\R", -1)) {
                Component component = Component.literal(part).withStyle(style);
                List<FormattedCharSequence> wrapped = font.split(component, contentWidth);
                if (wrapped.isEmpty()) {
                    lines.add(FormattedCharSequence.EMPTY);
                } else {
                    lines.addAll(wrapped);
                }
            }
            int measuredWidth = lines.stream().mapToInt(sequence -> font.width(sequence)).max().orElse(0);
            int width = spec.width() > 0
                    ? spec.width()
                    : autoWidth(measuredWidth, spec.padding(), iconWidth, maxWidth, spec.scale());
            int minimumHeight = Math.max(Math.max(9, lines.size() * 9), hasIcon ? spec.iconSize() : 0)
                    + spec.padding() * 2;
            int height = spec.height() > 0 ? Math.max(spec.height(), minimumHeight) : minimumHeight;
            int textStartX = spec.padding() + iconWidth;
            int textAreaWidth = Math.max(1, width - spec.padding() - textStartX);
            return new BubbleLayout(lines, width, height, spec.scale(), hasIcon ? spec.iconSize() : 0, textStartX, textAreaWidth);
        }

        private static int autoWidth(int measuredWidth, int padding, int iconWidth, int maxWidth, float scale) {
            int contentWidth = measuredWidth + padding * 2 + iconWidth;
            int logicalWidth = Math.max(80, contentWidth);
            int requiredDisplayWidth = (int) Math.ceil(contentWidth * scale);

            // Keep the scaled background at least as wide as the measured content.
            // This avoids losing the last screen pixel when scale is fractional.
            while (logicalWidth < maxWidth && Math.round(logicalWidth * scale) < requiredDisplayWidth) {
                logicalWidth++;
            }
            return Math.min(maxWidth, logicalWidth);
        }
    }
}
