package com.bubbleanyway.client;

import com.bubbleanyway.data.BubbleSpec;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.sound.SoundEvent;

public final class BubbleOverlay {
    private static final int MAX_ACTIVE_BUBBLES = 16;
    private static final int SCREEN_MARGIN = 6;
    private static final float MIN_RENDER_ALPHA = 0.02F;
    private static final float LAYER_Z = 1000.0F;
    private static final List<ActiveBubble> ACTIVE = new ArrayList<>();
    private static final Map<Identifier, TextureSize> TEXTURE_SIZES = new HashMap<>();
    private static final Map<Identifier, ItemStack> ICON_STACKS = new HashMap<>();
    private static long sequence;

    private BubbleOverlay() {
    }

    public static synchronized void enqueue(BubbleSpec spec) {
        if (spec.replace()) {
            ACTIVE.removeIf(active -> active.spec().id().equals(spec.id()));
        }
        ACTIVE.add(new ActiveBubble(spec, System.nanoTime(), sequence++));
        ACTIVE.sort(Comparator.comparingInt((ActiveBubble active) -> active.spec().priority()).reversed()
                .thenComparingLong(ActiveBubble::sequence));
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

        Identifier soundId = parseResource(spec.sound());
        if (soundId == null) {
            return;
        }

        SoundEvent sound = Registries.SOUND_EVENT.getOrEmpty(soundId).orElse(null);
        if (sound != null) {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(sound, spec.soundVolume(), spec.soundPitch()));
        }
    }

    public static void render(DrawContext context, float partialTick) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft.world == null) {
            return;
        }

        long now = System.nanoTime();
        List<ActiveBubble> visible = snapshot(now);
        if (visible.isEmpty()) {
            return;
        }

        int screenWidth = minecraft.getWindow().getScaledWidth();
        int screenHeight = minecraft.getWindow().getScaledHeight();
        TextRenderer textRenderer = minecraft.textRenderer;
        EnumMap<BubbleSpec.Anchor, Integer> stackOffsets = new EnumMap<>(BubbleSpec.Anchor.class);
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, LAYER_Z);
        for (ActiveBubble active : visible) {
            ItemStack iconStack = resolveIcon(active.spec().iconId());
            BubbleLayout layout = BubbleLayout.create(textRenderer, active.spec(), screenWidth, !iconStack.isEmpty());
            int stackOffset = stackOffsets.getOrDefault(active.spec().anchor(), 0);
            renderBubble(context, textRenderer, active, layout, iconStack, stackOffset, screenWidth, screenHeight, now);
            stackOffsets.put(active.spec().anchor(), stackOffset + layout.scaledHeight() + SCREEN_MARGIN);
        }
        context.getMatrices().pop();
    }

    private static synchronized List<ActiveBubble> snapshot(long now) {
        ACTIVE.removeIf(active -> active.ageTicks(now) >= active.spec().duration());
        return List.copyOf(ACTIVE);
    }

    private static void renderBubble(
            DrawContext context,
            TextRenderer textRenderer,
            ActiveBubble active,
            BubbleLayout layout,
            ItemStack iconStack,
            int stackOffset,
            int screenWidth,
            int screenHeight,
            long now) {
        BubbleSpec spec = active.spec();
        double age = active.ageTicks(now);
        float alpha = alpha(spec, age);
        if (alpha <= MIN_RENDER_ALPHA) {
            return;
        }
        float entrance = eased(Math.min(1.0F, spec.fadeIn() <= 0 ? 1.0F : (float) (age / spec.fadeIn())));

        float targetX = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> spec.x();
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenWidth - layout.scaledWidth() + spec.x();
            case CENTER_TOP, CENTER, CENTER_BOTTOM -> (screenWidth - layout.scaledWidth()) / 2.0F + spec.x();
        };
        float targetY = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_TOP, TOP_RIGHT -> spec.y() + stackOffset;
            case BOTTOM_LEFT, CENTER_BOTTOM, BOTTOM_RIGHT -> screenHeight - layout.scaledHeight() - spec.y() - stackOffset;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (screenHeight - layout.scaledHeight()) / 2.0F + spec.y() + stackOffset;
        };

        float minX = SCREEN_MARGIN;
        float maxX = Math.max(minX, screenWidth - layout.scaledWidth() - SCREEN_MARGIN);
        float minY = SCREEN_MARGIN;
        float maxY = Math.max(minY, screenHeight - layout.scaledHeight() - SCREEN_MARGIN);
        targetX = Math.max(minX, Math.min(maxX, targetX));
        targetY = Math.max(minY, Math.min(maxY, targetY));

        float slideDistance = spec.animation() == BubbleSpec.Animation.FADE ? 0.0F
                : (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_LEFT || spec.animation() == BubbleSpec.Animation.SLIDE_FROM_RIGHT
                ? layout.scaledWidth() : layout.scaledHeight()) + SCREEN_MARGIN + 8.0F;
        float slideIn = (1.0F - entrance) * slideDistance;
        float slideOut = exitProgress(spec, age) * slideDistance;
        float screenX = targetX;
        float screenY = targetY;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_LEFT) screenX -= slideIn + slideOut;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_RIGHT) screenX += slideIn + slideOut;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_TOP) screenY -= slideIn + slideOut;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_BOTTOM) screenY += slideIn + slideOut;

        context.getMatrices().push();
        context.getMatrices().translate(screenX, screenY, 0.0F);
        context.getMatrices().scale(spec.scale(), spec.scale(), 1.0F);
        if (!spec.backgroundTexture().isBlank()) {
            Identifier texture = parseResource(spec.backgroundTexture());
            TextureSize textureSize = texture == null ? null : textureSize(texture);
            if (texture == null || textureSize == null) {
                context.fill(0, 0, layout.width(), layout.height(), withAlpha(spec.backgroundColor(), alpha));
                context.getMatrices().pop();
                return;
            }
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            if (spec.backgroundBorder() > 0) {
                renderNineSlice(context, texture, textureSize, layout.width(), layout.height(),
                        spec.backgroundBorder(), spec.backgroundGuide());
            } else {
                context.drawTexture(texture, 0, 0, 0, 0, layout.width(), layout.height(), textureSize.width(), textureSize.height());
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            context.fill(0, 0, layout.width(), layout.height(), withAlpha(spec.backgroundColor(), alpha));
        }

        if (!iconStack.isEmpty()) {
            int iconY = spec.padding() + Math.max(0,
                    (layout.height() - spec.padding() * 2 - layout.iconSize()) / 2);
            context.getMatrices().push();
            context.getMatrices().translate(spec.padding() + spec.iconOffsetX(),
                    iconY + spec.iconOffsetY(), 0.0F);
            float iconScale = layout.iconSize() / 16.0F;
            context.getMatrices().scale(iconScale, iconScale, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            context.drawItem(iconStack, 0, 0);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.getMatrices().pop();
        }

        int textY = spec.padding() + spec.textOffsetY();
        for (OrderedText line : layout.lines()) {
            int lineWidth = textRenderer.getWidth(line);
            int textX = switch (spec.textAlignment()) {
                case LEFT -> layout.textStartX();
                case CENTER -> layout.textStartX() + (layout.textAreaWidth() - lineWidth) / 2;
                case RIGHT -> layout.textStartX() + layout.textAreaWidth() - lineWidth;
            };
            int textAreaEnd = layout.textStartX() + layout.textAreaWidth();
            textX = Math.max(layout.textStartX(), Math.min(textAreaEnd - lineWidth, textX));
            context.drawText(textRenderer, line, textX + spec.textOffsetX(), textY,
                    withAlpha(spec.textColor(), alpha), spec.shadow());
            textY += 9;
        }
        context.getMatrices().pop();
    }

    private static void renderNineSlice(
            DrawContext context,
            Identifier texture,
            TextureSize textureSize,
            int width,
            int height,
            int requestedBorder,
            int requestedGuide) {
        int sourceBorder = Math.min(requestedBorder, Math.min(textureSize.width() / 2, textureSize.height() / 2));
        int sourceGuide = Math.min(requestedGuide, Math.min(
                Math.max(0, (textureSize.width() - sourceBorder * 2) / 2),
                Math.max(0, (textureSize.height() - sourceBorder * 2) / 2)));
        int destinationBorder = Math.min(sourceBorder, Math.min(width / 2, height / 2));
        int sourceCenterWidth = textureSize.width() - sourceBorder * 2 - sourceGuide * 2;
        int sourceCenterHeight = textureSize.height() - sourceBorder * 2 - sourceGuide * 2;
        if (sourceBorder <= 0 || destinationBorder <= 0 || sourceCenterWidth <= 0 || sourceCenterHeight <= 0) {
            context.drawTexture(texture, 0, 0, 0, 0, width, height, textureSize.width(), textureSize.height());
            return;
        }

        int destinationCenterWidth = width - destinationBorder * 2;
        int destinationCenterHeight = height - destinationBorder * 2;
        int sourceCenterStart = sourceBorder + sourceGuide;
        int sourceRightStart = textureSize.width() - sourceBorder;
        int sourceBottomStart = textureSize.height() - sourceBorder;

        drawPart(context, texture, 0, 0, destinationBorder, destinationBorder,
                0, 0, sourceBorder, sourceBorder, textureSize);
        drawPart(context, texture, destinationBorder, 0, destinationCenterWidth, destinationBorder,
                sourceCenterStart, 0, sourceCenterWidth, sourceBorder, textureSize);
        drawPart(context, texture, width - destinationBorder, 0, destinationBorder, destinationBorder,
                sourceRightStart, 0, sourceBorder, sourceBorder, textureSize);

        drawPart(context, texture, 0, destinationBorder, destinationBorder, destinationCenterHeight,
                0, sourceCenterStart, sourceBorder, sourceCenterHeight, textureSize);
        drawPart(context, texture, destinationBorder, destinationBorder, destinationCenterWidth, destinationCenterHeight,
                sourceCenterStart, sourceCenterStart, sourceCenterWidth, sourceCenterHeight, textureSize);
        drawPart(context, texture, width - destinationBorder, destinationBorder, destinationBorder, destinationCenterHeight,
                sourceRightStart, sourceCenterStart, sourceBorder, sourceCenterHeight, textureSize);

        drawPart(context, texture, 0, height - destinationBorder, destinationBorder, destinationBorder,
                0, sourceBottomStart, sourceBorder, sourceBorder, textureSize);
        drawPart(context, texture, destinationBorder, height - destinationBorder, destinationCenterWidth, destinationBorder,
                sourceCenterStart, sourceBottomStart, sourceCenterWidth, sourceBorder, textureSize);
        drawPart(context, texture, width - destinationBorder, height - destinationBorder, destinationBorder, destinationBorder,
                sourceRightStart, sourceBottomStart, sourceBorder, sourceBorder, textureSize);
    }

    private static void drawPart(
            DrawContext context,
            Identifier texture,
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
        context.drawTexture(texture, destinationX, destinationY, sourceX, sourceY,
                destinationWidth, destinationHeight, sourceWidth, sourceHeight,
                textureSize.width(), textureSize.height());
    }

    private static TextureSize textureSize(Identifier texture) {
        if (TEXTURE_SIZES.containsKey(texture)) {
            return TEXTURE_SIZES.get(texture);
        }

        TextureSize size = null;
        try {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) {
                try (InputStream inputStream = resource.get().getInputStream(); NativeImage image = NativeImage.read(inputStream)) {
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
        Identifier id = parseResource(iconId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return ICON_STACKS.computeIfAbsent(id,
                key -> Registries.ITEM.getOrEmpty(key).map(ItemStack::new).orElse(ItemStack.EMPTY));
    }

    private static Identifier parseResource(String value) {
        try {
            return Identifier.tryParse(value);
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

    private record BubbleLayout(
            List<OrderedText> lines,
            int width,
            int height,
            int scaledWidth,
            int scaledHeight,
            int iconSize,
            int textStartX,
            int textAreaWidth) {

        private static BubbleLayout create(TextRenderer textRenderer, BubbleSpec spec, int screenWidth, boolean hasIcon) {
            int maxWidth = Math.min(spec.maxWidth(), Math.max(40, screenWidth - SCREEN_MARGIN * 2));
            int iconWidth = hasIcon ? spec.iconSize() + spec.iconGap() : 0;
            int contentWidth = spec.width() > 0
                    ? Math.max(1, spec.width() - spec.padding() * 2 - iconWidth)
                    : Math.max(1, maxWidth - spec.padding() * 2 - iconWidth);
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(spec.textColor() & 0x00FFFFFF))
                    .withBold(spec.bold())
                    .withItalic(spec.italic())
                    .withUnderline(spec.underlined())
                    .withStrikethrough(spec.strikethrough())
                    .withObfuscated(spec.obfuscated());

            List<OrderedText> lines = new ArrayList<>();
            for (String part : spec.text().split("\\R", -1)) {
                Text component = Text.literal(part).setStyle(style);
                List<OrderedText> wrapped = textRenderer.wrapLines(component, contentWidth);
                if (wrapped.isEmpty()) {
                    lines.add(OrderedText.EMPTY);
                } else {
                    lines.addAll(wrapped);
                }
            }
            int measuredWidth = lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
            int width = spec.width() > 0
                    ? spec.width()
                    : Math.min(maxWidth, Math.max(80, measuredWidth + spec.padding() * 2 + iconWidth));
            int minimumHeight = Math.max(Math.max(9, lines.size() * 9), hasIcon ? spec.iconSize() : 0)
                    + spec.padding() * 2;
            int height = spec.height() > 0 ? Math.max(spec.height(), minimumHeight) : minimumHeight;
            int textStartX = spec.padding() + iconWidth;
            int textAreaWidth = Math.max(1, width - spec.padding() - textStartX);
            return new BubbleLayout(lines, width, height,
                    Math.round(width * spec.scale()), Math.round(height * spec.scale()),
                    hasIcon ? spec.iconSize() : 0, textStartX, textAreaWidth);
        }
    }
}
