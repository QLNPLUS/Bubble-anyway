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
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class BubbleOverlay {
    private static final int MAX_ACTIVE_BUBBLES = 16;
    private static final int SCREEN_MARGIN = 6;
    private static final float MIN_RENDER_ALPHA = 0.02F;
    private static final float LAYER_Z = 1000.0F;
    private static final List<ActiveBubble> ACTIVE = new ArrayList<>();
    private static final Map<ResourceLocation, TextureSize> TEXTURE_SIZES = new HashMap<>();
    private static final Map<ResourceLocation, ItemStack> ICON_STACKS = new HashMap<>();
    private static long sequence;

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

        ResourceLocation soundId = parseResource(spec.sound());
        if (soundId == null) {
            return;
        }

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(sound, spec.soundVolume(), spec.soundPitch()));
        }
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

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
            ResourceLocation texture = parseResource(spec.backgroundTexture());
            TextureSize textureSize = texture == null ? null : textureSize(texture);
            if (texture == null || textureSize == null) {
                graphics.fill(0, 0, layout.width, layout.height, withAlpha(spec.backgroundColor(), alpha));
                graphics.pose().popPose();
                return;
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            if (spec.backgroundBorder() > 0) {
                renderNineSlice(graphics, texture, textureSize, layout.width, layout.height,
                        spec.backgroundBorder(), spec.backgroundGuide());
            } else {
                graphics.blit(texture, 0, 0, 0, 0.0F, 0.0F,
                        layout.width, layout.height, textureSize.width, textureSize.height);
            }
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

    private static void renderNineSlice(
            GuiGraphics graphics,
            ResourceLocation texture,
            TextureSize textureSize,
            int width,
            int height,
            int requestedBorder,
            int requestedGuide) {
        int sourceBorder = Math.min(requestedBorder, Math.min(textureSize.width / 2, textureSize.height / 2));
        int sourceGuide = Math.min(requestedGuide, Math.min(
                Math.max(0, (textureSize.width - sourceBorder * 2) / 2),
                Math.max(0, (textureSize.height - sourceBorder * 2) / 2)));
        int destinationBorder = Math.min(sourceBorder, Math.min(width / 2, height / 2));
        int sourceCenterWidth = textureSize.width - sourceBorder * 2 - sourceGuide * 2;
        int sourceCenterHeight = textureSize.height - sourceBorder * 2 - sourceGuide * 2;
        if (sourceBorder <= 0 || destinationBorder <= 0 || sourceCenterWidth <= 0 || sourceCenterHeight <= 0) {
            graphics.blit(texture, 0, 0, 0, 0.0F, 0.0F,
                    width, height, textureSize.width, textureSize.height);
            return;
        }

        int destinationCenterWidth = width - destinationBorder * 2;
        int destinationCenterHeight = height - destinationBorder * 2;
        int sourceCenterStart = sourceBorder + sourceGuide;
        int sourceRightStart = textureSize.width - sourceBorder;
        int sourceBottomStart = textureSize.height - sourceBorder;

        blitPart(graphics, texture, 0, 0, destinationBorder, destinationBorder,
                0, 0, sourceBorder, sourceBorder, textureSize);
        blitPart(graphics, texture, destinationBorder, 0, destinationCenterWidth, destinationBorder,
                sourceCenterStart, 0, sourceCenterWidth, sourceBorder, textureSize);
        blitPart(graphics, texture, width - destinationBorder, 0, destinationBorder, destinationBorder,
                sourceRightStart, 0, sourceBorder, sourceBorder, textureSize);

        blitPart(graphics, texture, 0, destinationBorder, destinationBorder, destinationCenterHeight,
                0, sourceCenterStart, sourceBorder, sourceCenterHeight, textureSize);
        blitPart(graphics, texture, destinationBorder, destinationBorder, destinationCenterWidth, destinationCenterHeight,
                sourceCenterStart, sourceCenterStart, sourceCenterWidth, sourceCenterHeight, textureSize);
        blitPart(graphics, texture, width - destinationBorder, destinationBorder, destinationBorder, destinationCenterHeight,
                sourceRightStart, sourceCenterStart, sourceBorder, sourceCenterHeight, textureSize);

        blitPart(graphics, texture, 0, height - destinationBorder, destinationBorder, destinationBorder,
                0, sourceBottomStart, sourceBorder, sourceBorder, textureSize);
        blitPart(graphics, texture, destinationBorder, height - destinationBorder, destinationCenterWidth, destinationBorder,
                sourceCenterStart, sourceBottomStart, sourceCenterWidth, sourceBorder, textureSize);
        blitPart(graphics, texture, width - destinationBorder, height - destinationBorder, destinationBorder, destinationBorder,
                sourceRightStart, sourceBottomStart, sourceBorder, sourceBorder, textureSize);
    }

    private static void blitPart(
            GuiGraphics graphics,
            ResourceLocation texture,
            int destinationX,
            int destinationY,
            int destinationWidth,
            int destinationHeight,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            TextureSize textureSize) {
        if (destinationWidth <= 0 || destinationHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }

        graphics.blit(texture, destinationX, destinationY, destinationWidth, destinationHeight,
                sourceX, sourceY, sourceWidth, sourceHeight, textureSize.width, textureSize.height);
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
        ResourceLocation id = parseResource(iconId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return ICON_STACKS.computeIfAbsent(id,
                key -> BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY));
    }

    private static ResourceLocation parseResource(String value) {
        try {
            return ResourceLocation.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
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
                    : Math.min(maxWidth, Math.max(80, measuredWidth + spec.padding() * 2 + iconWidth));
            int minimumHeight = Math.max(Math.max(9, lines.size() * 9), hasIcon ? spec.iconSize() : 0)
                    + spec.padding() * 2;
            int height = spec.height() > 0 ? Math.max(spec.height(), minimumHeight) : minimumHeight;
            int textStartX = spec.padding() + iconWidth;
            int textAreaWidth = Math.max(1, width - spec.padding() - textStartX);
            return new BubbleLayout(lines, width, height, spec.scale(), hasIcon ? spec.iconSize() : 0, textStartX, textAreaWidth);
        }
    }
}
