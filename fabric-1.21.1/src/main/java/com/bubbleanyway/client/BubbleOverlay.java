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
    private static final int SCREEN_MARGIN = 6;
    private static final float MIN_RENDER_ALPHA = 0.02F;
    private static final float LAYER_Z = 1000.0F;
    private static final List<ActiveBubble> ACTIVE = new ArrayList<>();
    private static final List<QueuedBubble> PENDING = new ArrayList<>();
    private static final Map<Identifier, TextureSize> TEXTURE_SIZES = new HashMap<>();
    private static final Map<Identifier, ItemStack> ICON_STACKS = new HashMap<>();
    private static long sequence;
    private static long logicalNow;
    private static long wallClockNow;
    private static boolean clockInitialized;
    private static boolean renderCallbackSeen;

    private BubbleOverlay() {
    }

    public static synchronized boolean enqueue(BubbleSpec spec) {
        if (MinecraftClient.getInstance().world == null) {
            return false;
        }
        if (spec.replace()) {
            ACTIVE.removeIf(active -> active.spec().id().equals(spec.id()));
            PENDING.removeIf(queued -> queued.spec().id().equals(spec.id()));
        }
        PENDING.add(new QueuedBubble(spec, sequence++));
        PENDING.sort(Comparator.comparingInt((QueuedBubble queued) -> queued.spec().priority()).reversed()
                .thenComparingLong(QueuedBubble::sequence));
        return true;
    }

    public static synchronized void clear() {
        ACTIVE.clear();
        PENDING.clear();
        logicalNow = 0L;
        wallClockNow = 0L;
        clockInitialized = false;
    }

    public static synchronized void clientTick(MinecraftClient client) {
        if (client.world == null) {
            clear();
            return;
        }
        animationNow(client.isPaused());
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
        renderCallbackSeen = true;
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft.world == null) {
            clear();
            return;
        }

        int screenWidth = minecraft.getWindow().getScaledWidth();
        int screenHeight = minecraft.getWindow().getScaledHeight();
        TextRenderer textRenderer = minecraft.textRenderer;
        boolean paused = minecraft.isPaused();
        long now = animationNow(paused);
        List<ActiveBubble> visible = snapshot(now, textRenderer, screenWidth, screenHeight);
        if (visible.isEmpty()) {
            return;
        }

        EnumMap<BubbleSpec.Anchor, Integer> stackOffsets = new EnumMap<>(BubbleSpec.Anchor.class);
        Map<ActiveBubble, Integer> bubbleOffsets = new HashMap<>();
        Map<ActiveBubble, BubbleLayout> layouts = new HashMap<>();
        Map<ActiveBubble, ResolvedIcon> icons = new HashMap<>();
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, LAYER_Z);
        for (ActiveBubble active : visible) {
            ResolvedIcon icon = resolveIcon(active.spec());
            BubbleLayout layout = BubbleLayout.create(textRenderer, active.spec(), screenWidth, !icon.isEmpty());
            int stackOffset = stackOffsets.getOrDefault(active.spec().anchor(), 0);
            bubbleOffsets.put(active, stackOffset);
            layouts.put(active, layout);
            icons.put(active, icon);
            stackOffsets.put(active.spec().anchor(), stackOffset + layout.scaledHeight() + SCREEN_MARGIN);
        }
        for (int i = visible.size() - 1; i >= 0; i--) {
            ActiveBubble active = visible.get(i);
            renderBubble(context, textRenderer, active, layouts.get(active), icons.get(active), bubbleOffsets.get(active),
                    screenWidth, screenHeight, now);
        }
        context.getMatrices().pop();
    }

    public static synchronized int activeCount() {
        return ACTIVE.size();
    }

    public static synchronized int pendingCount() {
        return PENDING.size();
    }

    public static boolean renderCallbackSeen() {
        return renderCallbackSeen;
    }

    private static synchronized List<ActiveBubble> snapshot(
            long now,
            TextRenderer textRenderer,
            int screenWidth,
            int screenHeight) {
        ACTIVE.removeIf(active -> active.ageTicks(now) >= active.spec().duration());
        if (!MinecraftClient.getInstance().isPaused()) {
            promotePending(now, textRenderer, screenWidth, screenHeight);
        }
        return List.copyOf(ACTIVE);
    }

    private static synchronized long animationNow(boolean paused) {
        long wallNow = System.nanoTime();
        if (!clockInitialized) {
            clockInitialized = true;
            logicalNow = wallNow;
        } else if (!paused) {
            logicalNow += Math.max(0L, wallNow - wallClockNow);
        }
        wallClockNow = wallNow;
        return logicalNow;
    }

    private static void promotePending(long now, TextRenderer textRenderer, int screenWidth, int screenHeight) {
        boolean promoted;
        do {
            promoted = false;
            for (QueuedBubble queued : List.copyOf(PENDING)) {
                ActiveBubble candidate = new ActiveBubble(queued.spec(), now, queued.sequence());
                List<ActiveBubble> trial = new ArrayList<>(ACTIVE);
                trial.add(candidate);
                trial.sort(Comparator.comparingInt((ActiveBubble active) -> active.spec().priority()).reversed()
                        .thenComparingLong(ActiveBubble::sequence));
                if (!fitsCandidateOnScreen(trial, candidate, textRenderer, screenWidth, screenHeight)) {
                    continue;
                }
                PENDING.remove(queued);
                ACTIVE.add(candidate);
                ACTIVE.sort(Comparator.comparingInt((ActiveBubble active) -> active.spec().priority()).reversed()
                        .thenComparingLong(ActiveBubble::sequence));
                playSound(candidate.spec());
                promoted = true;
                break;
            }
        } while (promoted && !PENDING.isEmpty());
    }

    private static boolean fitsCandidateOnScreen(
            List<ActiveBubble> bubbles,
            ActiveBubble candidate,
            TextRenderer textRenderer,
            int screenWidth,
            int screenHeight) {
        EnumMap<BubbleSpec.Anchor, Integer> stackOffsets = new EnumMap<>(BubbleSpec.Anchor.class);
        for (ActiveBubble active : bubbles) {
            ResolvedIcon icon = resolveIcon(active.spec());
            BubbleLayout layout = BubbleLayout.create(textRenderer, active.spec(), screenWidth, !icon.isEmpty());
            int stackOffset = stackOffsets.getOrDefault(active.spec().anchor(), 0);
            float[] target = targetPosition(active.spec(), layout, stackOffset, screenWidth, screenHeight);
            if (active == candidate) {
                return target[0] >= SCREEN_MARGIN && target[1] >= SCREEN_MARGIN
                        && target[0] + layout.scaledWidth() <= screenWidth - SCREEN_MARGIN
                        && target[1] + layout.scaledHeight() <= screenHeight - SCREEN_MARGIN;
            }
            stackOffsets.put(active.spec().anchor(), stackOffset + layout.scaledHeight() + SCREEN_MARGIN);
        }
        return false;
    }

    private static float[] targetPosition(
            BubbleSpec spec,
            BubbleLayout layout,
            int stackOffset,
            int screenWidth,
            int screenHeight) {
        float targetX = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> spec.x();
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenWidth - layout.scaledWidth() + spec.x();
            case CENTER_TOP, CENTER, CENTER_BOTTOM -> (screenWidth - layout.scaledWidth()) / 2.0F + spec.x();
        };
        float baseY = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_TOP, TOP_RIGHT -> spec.y();
            case BOTTOM_LEFT, CENTER_BOTTOM, BOTTOM_RIGHT -> screenHeight - layout.scaledHeight() - spec.y();
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (screenHeight - layout.scaledHeight()) / 2.0F + spec.y();
        };
        float minX = SCREEN_MARGIN;
        float maxX = Math.max(minX, screenWidth - layout.scaledWidth() - SCREEN_MARGIN);
        float minY = SCREEN_MARGIN;
        float maxY = Math.max(minY, screenHeight - layout.scaledHeight() - SCREEN_MARGIN);
        baseY = Math.max(minY, Math.min(maxY, baseY));
        float targetY = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_TOP, TOP_RIGHT -> baseY + stackOffset;
            case BOTTOM_LEFT, CENTER_BOTTOM, BOTTOM_RIGHT -> baseY - stackOffset;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> baseY + stackOffset;
        };
        return new float[] {Math.max(minX, Math.min(maxX, targetX)), targetY};
    }

    private static void renderBubble(
            DrawContext context,
            TextRenderer textRenderer,
            ActiveBubble active,
            BubbleLayout layout,
            ResolvedIcon icon,
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
        float slideEntrance = eased(Math.min(1.0F, spec.slideIn() <= 0 ? 1.0F : (float) (age / spec.slideIn())));

        float[] target = targetPosition(spec, layout, stackOffset, screenWidth, screenHeight);
        float targetX = target[0];
        float targetY = target[1];

        float slideDistance = spec.animation() == BubbleSpec.Animation.FADE ? 0.0F
                : (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_LEFT || spec.animation() == BubbleSpec.Animation.SLIDE_FROM_RIGHT
                ? layout.scaledWidth() : layout.scaledHeight()) + SCREEN_MARGIN + 8.0F;
        float slideIn = (1.0F - slideEntrance) * slideDistance;
        float slideOut = exitSlideProgress(spec, age) * slideDistance;
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

        context.draw();

        if (!icon.isEmpty()) {
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
            if (icon.type() == BubbleSpec.IconType.ITEM) {
                context.drawItem(icon.itemStack(), 0, 0);
            } else if (icon.type() == BubbleSpec.IconType.TEXTURE) {
                int iconWidth = icon.textureSize().width();
                int iconHeight = icon.textureSize().height();
                float ratio = iconWidth / (float) Math.max(1, iconHeight);
                int drawWidth = ratio >= 1.0F ? layout.iconSize() : Math.max(1, Math.round(layout.iconSize() * ratio));
                int drawHeight = ratio <= 1.0F ? layout.iconSize() : Math.max(1, Math.round(layout.iconSize() / ratio));
                int drawX = (layout.iconSize() - drawWidth) / 2;
                int drawY = (layout.iconSize() - drawHeight) / 2;
                context.drawTexture(icon.texture(), drawX, drawY, 0, 0, drawWidth, drawHeight, iconWidth, iconHeight);
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.getMatrices().pop();
            context.draw();
        }

        int textY = spec.padding() + spec.textOffsetY();
        for (StyledLine line : layout.lines()) {
            int lineWidth = line.width(textRenderer);
            int textX = switch (spec.textAlignment()) {
                case LEFT -> layout.textStartX();
                case CENTER -> layout.textStartX() + (layout.textAreaWidth() - lineWidth) / 2;
                case RIGHT -> layout.textStartX() + layout.textAreaWidth() - lineWidth;
            };
            int textAreaEnd = layout.textStartX() + layout.textAreaWidth();
            textX = Math.max(layout.textStartX(), Math.min(textAreaEnd - lineWidth, textX));
            int runX = textX + spec.textOffsetX();
            for (StyledRun run : line.runs()) {
                context.getMatrices().push();
                context.getMatrices().translate(runX, textY, 0.0F);
                context.getMatrices().scale(run.part().scale(), run.part().scale(), 1.0F);
                context.drawText(textRenderer, run.text(), 0, 0,
                        withAlpha(run.part().color(), alpha), run.part().shadow());
                context.getMatrices().pop();
                runX += Math.round(textRenderer.getWidth(run.text()) * run.part().scale());
            }
            textY += line.height(textRenderer);
        }
        context.draw();
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
        // Fabric's 10-argument overload is ordered as destination x/y/width/height followed
        // by source u/v/width/height and the full texture size. Passing source coordinates in
        // the destination-width slots makes short bubbles render as broken texture fragments.
        context.drawTexture(texture, destinationX, destinationY, destinationWidth, destinationHeight,
                sourceX, sourceY, sourceWidth, sourceHeight,
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

    private static ResolvedIcon resolveIcon(BubbleSpec spec) {
        if (spec.iconId().isBlank() || spec.iconType() == BubbleSpec.IconType.NONE) {
            return ResolvedIcon.EMPTY;
        }
        Identifier id = parseResource(spec.iconId());
        if (id == null) {
            return ResolvedIcon.EMPTY;
        }
        if (spec.iconType() != BubbleSpec.IconType.TEXTURE) {
            ItemStack stack = ICON_STACKS.computeIfAbsent(id,
                    key -> Registries.ITEM.getOrEmpty(key).map(ItemStack::new).orElse(ItemStack.EMPTY));
            if (!stack.isEmpty()) {
                return new ResolvedIcon(BubbleSpec.IconType.ITEM, stack, null, null);
            }
        }
        if (spec.iconType() != BubbleSpec.IconType.ITEM) {
            TextureSize size = textureSize(id);
            if (size != null) {
                return new ResolvedIcon(BubbleSpec.IconType.TEXTURE, ItemStack.EMPTY, id, size);
            }
        }
        return ResolvedIcon.EMPTY;
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

    private static float exitSlideProgress(BubbleSpec spec, double age) {
        if (spec.slideOut() <= 0) {
            return 0.0F;
        }

        double slideOutStart = Math.max(0, spec.duration() - spec.slideOut());
        if (age <= slideOutStart) {
            return 0.0F;
        }
        return eased(Math.min(1.0F, (float) ((age - slideOutStart) / spec.slideOut())));
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

    private record QueuedBubble(BubbleSpec spec, long sequence) {
    }

    private record TextureSize(int width, int height) {
    }

    private record ResolvedIcon(
            BubbleSpec.IconType type,
            ItemStack itemStack,
            Identifier texture,
            TextureSize textureSize) {
        private static final ResolvedIcon EMPTY =
                new ResolvedIcon(BubbleSpec.IconType.NONE, ItemStack.EMPTY, null, null);

        private boolean isEmpty() {
            return type == BubbleSpec.IconType.NONE;
        }
    }

    private static final class BubbleLayout {
        private final List<StyledLine> lines;
        private final int width;
        private final int height;
        private final int scaledWidth;
        private final int scaledHeight;
        private final int iconSize;
        private final int textStartX;
        private final int textAreaWidth;

        private BubbleLayout(
                List<StyledLine> lines,
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

        private List<StyledLine> lines() { return lines; }
        private int width() { return width; }
        private int height() { return height; }
        private int scaledWidth() { return scaledWidth; }
        private int scaledHeight() { return scaledHeight; }
        private int iconSize() { return iconSize; }
        private int textStartX() { return textStartX; }
        private int textAreaWidth() { return textAreaWidth; }

        private static BubbleLayout create(TextRenderer textRenderer, BubbleSpec spec, int screenWidth, boolean hasIcon) {
            int maxWidth = Math.min(spec.maxWidth(), Math.max(40, screenWidth - SCREEN_MARGIN * 2));
            int iconWidth = hasIcon ? spec.iconSize() + spec.iconGap() : 0;
            int contentWidth = spec.width() > 0
                    ? Math.max(1, spec.width() - spec.padding() * 2 - iconWidth)
                    : Math.max(1, maxWidth - spec.padding() * 2 - iconWidth);
            List<StyledLine> lines = layoutText(textRenderer, spec, contentWidth);
            int measuredWidth = lines.stream().mapToInt(line -> line.width(textRenderer)).max().orElse(0);
            int width = spec.width() > 0
                    ? spec.width()
                    : autoWidth(measuredWidth, spec.padding(), iconWidth, maxWidth, spec.scale());
            int minimumHeight = Math.max(Math.max(9, lines.stream().mapToInt(line -> line.height(textRenderer)).sum()), hasIcon ? spec.iconSize() : 0)
                    + spec.padding() * 2;
            int height = spec.height() > 0 ? Math.max(spec.height(), minimumHeight) : minimumHeight;
            int textStartX = spec.padding() + iconWidth;
            int textAreaWidth = Math.max(1, width - spec.padding() - textStartX);
            return new BubbleLayout(lines, width, height, spec.scale(), hasIcon ? spec.iconSize() : 0,
                    textStartX, textAreaWidth);
        }

        private static List<StyledLine> layoutText(TextRenderer renderer, BubbleSpec spec, int contentWidth) {
            List<StyledLine> lines = new ArrayList<>();
            StyledLine current = new StyledLine();
            for (BubbleSpec.TextPart part : spec.textParts()) {
                String[] explicitLines = part.text().split("\\R", -1);
                for (int index = 0; index < explicitLines.length; index++) {
                    current = appendWrapped(renderer, current, part, explicitLines[index], contentWidth, lines);
                    if (index < explicitLines.length - 1) {
                        lines.add(current);
                        current = new StyledLine();
                    }
                }
            }
            if (!current.runs().isEmpty() || lines.isEmpty()) {
                lines.add(current);
            }
            return lines;
        }

        private static StyledLine appendWrapped(
                TextRenderer renderer,
                StyledLine current,
                BubbleSpec.TextPart part,
                String value,
                int contentWidth,
                List<StyledLine> lines) {
            int offset = 0;
            while (offset < value.length()) {
                int available = contentWidth - current.width(renderer);
                if (available <= 0 && !current.runs().isEmpty()) {
                    lines.add(current);
                    current = new StyledLine();
                    available = contentWidth;
                }
                int fit = fittingLength(renderer, value, offset, available, part);
                if (fit <= 0) {
                    if (!current.runs().isEmpty()) {
                        lines.add(current);
                        current = new StyledLine();
                        continue;
                    }
                    fit = 1;
                }
                int end = Math.min(value.length(), offset + fit);
                if (end < value.length()) {
                    int space = value.lastIndexOf(' ', end - 1);
                    if (space > offset) {
                        end = space;
                    }
                }
                String chunk = value.substring(offset, end);
                if (!chunk.isEmpty()) {
                    current.runs().add(new StyledRun(part, styledText(part, chunk)));
                }
                offset = end;
                while (offset < value.length() && value.charAt(offset) == ' ') {
                    offset++;
                }
            }
            return current;
        }

        private static int fittingLength(TextRenderer renderer, String value, int start, int available, BubbleSpec.TextPart part) {
            int low = 0;
            int high = value.length() - start;
            while (low < high) {
                int middle = (low + high + 1) / 2;
                Text candidate = styledText(part, value.substring(start, start + middle));
                if (Math.round(renderer.getWidth(candidate) * part.scale()) <= available) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return low;
        }

        private static Text styledText(BubbleSpec.TextPart part, String text) {
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(part.color() & 0x00FFFFFF))
                    .withBold(part.bold())
                    .withItalic(part.italic())
                    .withUnderline(part.underlined())
                    .withStrikethrough(part.strikethrough())
                    .withObfuscated(part.obfuscated());
            return Text.literal(text).setStyle(style);
        }

        private static int autoWidth(int measuredWidth, int padding, int iconWidth, int maxWidth, float scale) {
            int contentWidth = measuredWidth + padding * 2 + iconWidth;
            int logicalWidth = Math.max(80, contentWidth);
            int requiredDisplayWidth = (int) Math.ceil(contentWidth * scale);
            while (logicalWidth < maxWidth && Math.round(logicalWidth * scale) < requiredDisplayWidth) {
                logicalWidth++;
            }
            return Math.min(maxWidth, logicalWidth);
        }
    }

    private static final class StyledLine {
        private final List<StyledRun> runs = new ArrayList<>();

        private List<StyledRun> runs() {
            return runs;
        }

        private int width(TextRenderer renderer) {
            return runs.stream().mapToInt(run -> Math.round(renderer.getWidth(run.text()) * run.part().scale())).sum();
        }

        private int height(TextRenderer renderer) {
            return Math.max(9, runs.stream().mapToInt(run -> Math.round(9.0F * run.part().scale())).max().orElse(9));
        }
    }

    private record StyledRun(BubbleSpec.TextPart part, Text text) {
    }
}
