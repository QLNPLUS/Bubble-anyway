package com.bubbleanyway.client;

import com.bubbleanyway.data.BubbleSpec;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.slf4j.Logger;

public final class BubbleOverlay {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCREEN_MARGIN = 6;
    private static final float MIN_RENDER_ALPHA = 0.02F;
    private static final float BELOW_PAUSE_Z = -1000.0F;
    private static final float ABOVE_PAUSE_Z = 1000.0F;
    // Item icons are explicitly rendered back into their bubble layer below.
    private static final float BUBBLE_LAYER_STEP = 1.0F;
    private static final List<ActiveBubble> ACTIVE = new ArrayList<>();
    private static final List<QueuedBubble> PENDING = new ArrayList<>();
    private static final Map<ResourceLocation, TextureSize> TEXTURE_SIZES = new HashMap<>();
    private static final Map<GeneratedTextureKey, PreparedTexture> GENERATED_TEXTURES = new HashMap<>();
    private static final Map<ResourceLocation, ItemStack> ICON_STACKS = new HashMap<>();
    private static boolean renderCallbackLogged;
    private static long sequence;
    private static long generatedTextureSequence;
    private static long logicalNow;
    private static long wallClockNow;
    private static boolean clockInitialized;

    private BubbleOverlay() {
    }

    public static synchronized boolean enqueue(BubbleSpec spec) {
        if (Minecraft.getInstance().level == null) {
            return false;
        }
        if (spec.replace()) {
            ACTIVE.removeIf(active -> active.spec.id().equals(spec.id()));
            PENDING.removeIf(queued -> queued.spec.id().equals(spec.id()));
        }
        PENDING.add(new QueuedBubble(spec, sequence++));
        PENDING.sort(Comparator.comparingInt((QueuedBubble queued) -> queued.spec.priority()).reversed()
                .thenComparingLong(QueuedBubble::sequence));
        LOGGER.debug("Queued Bubble Anyway bubble {} (pending={}, active={})", spec.id(), PENDING.size(), ACTIVE.size());
        // A queued bubble is accepted immediately. Toast integration can cancel the original
        // Toast because the bubble will be promoted when its screen region has room.
        return true;
    }

    public static synchronized void clear() {
        ACTIVE.clear();
        PENDING.clear();
        logicalNow = 0L;
        wallClockNow = 0L;
        clockInitialized = false;
    }

    public static synchronized void clientTick(Minecraft minecraft) {
        if (minecraft.level == null) {
            clear();
            return;
        }
        animationNow(minecraft.isPaused());
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

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        renderInternal(graphics, null);
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        renderInternal(graphics, null);
    }

    public static void render(GuiGraphics graphics, float partialTick, BubbleSpec.RenderLayer layer) {
        renderInternal(graphics, layer);
    }

    public static synchronized int activeCount() {
        return ACTIVE.size();
    }

    public static synchronized int pendingCount() {
        return PENDING.size();
    }

    public static boolean renderCallbackSeen() {
        return renderCallbackLogged;
    }

    private static void renderInternal(GuiGraphics graphics, BubbleSpec.RenderLayer layer) {
        if (!renderCallbackLogged) {
            LOGGER.info("Bubble Anyway GUI layer render callback is active");
            renderCallbackLogged = true;
        }
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            clear();
            return;
        }
        long now = animationNow(minecraft.isPaused());
        Font font = minecraft.font;
        List<ActiveBubble> visible = snapshot(now, font, screenWidth, screenHeight);
        if (layer != null) {
            visible = visible.stream().filter(active -> active.spec.renderLayer() == layer).toList();
        }
        if (visible.isEmpty()) {
            return;
        }

        // Finish lower GUI batches first so this layer cannot be reordered below them.
        graphics.flush();
        minecraft.renderBuffers().bufferSource().endBatch();
        RenderSystem.disableDepthTest();

        List<RenderBubble> renderBubbles = buildPlacements(visible, font, screenWidth, screenHeight);

        // Draw lower-priority/older bubbles first. The later draw call is the visible top layer
        // whenever two bubble rectangles overlap.
        renderBubbles.sort(Comparator.comparingInt((RenderBubble bubble) -> bubble.active.spec.priority())
                .thenComparingLong(bubble -> bubble.active.sequence));
        int layerIndex = 0;
        for (RenderBubble bubble : renderBubbles) {
            renderBubble(minecraft, graphics, font, bubble,
                    now,
                    (layer == BubbleSpec.RenderLayer.BELOW_PAUSE ? BELOW_PAUSE_Z : ABOVE_PAUSE_Z)
                            + layerIndex++ * BUBBLE_LAYER_STEP);
        }
        flushBubble(graphics);
        RenderSystem.enableDepthTest();
    }

    private static synchronized List<ActiveBubble> snapshot(
            long now,
            Font font,
            int screenWidth,
            int screenHeight) {
        ACTIVE.removeIf(active -> active.ageTicks(now) >= active.spec.duration());
        promotePending(now, font, screenWidth, screenHeight);
        return List.copyOf(ACTIVE);
    }

    private static synchronized long animationNow(boolean paused) {
        long wallNow = System.nanoTime();
        if (!clockInitialized) {
            clockInitialized = true;
            logicalNow = wallNow;
        } else {
            logicalNow += Math.max(0L, wallNow - wallClockNow);
        }
        wallClockNow = wallNow;
        return logicalNow;
    }

    private static void promotePending(long now, Font font, int screenWidth, int screenHeight) {
        boolean promoted;
        do {
            promoted = false;
            List<QueuedBubble> waiting = new ArrayList<>(PENDING);
            for (QueuedBubble queued : waiting) {
                ActiveBubble candidate = new ActiveBubble(queued.spec, now, queued.sequence);
                List<ActiveBubble> trial = new ArrayList<>(ACTIVE);
                trial.add(candidate);
                trial.sort(ACTIVE_ORDER);
                List<RenderBubble> placements = buildPlacements(trial, font, screenWidth, screenHeight);
                RenderBubble candidatePlacement = placements.stream()
                        .filter(placement -> placement.active == candidate)
                        .findFirst()
                        .orElse(null);
                // Existing bubbles may already be outside the current viewport after a resize or
                // anchor stack change. Only the candidate must fit before it becomes visible.
                if (candidatePlacement == null || !fitsOnScreen(candidatePlacement, screenWidth, screenHeight)) {
                    continue;
                }

                PENDING.remove(queued);
                ACTIVE.add(candidate);
                ACTIVE.sort(ACTIVE_ORDER);
                playSound(candidate.spec);
                promoted = true;
                break;
            }
        } while (promoted && !PENDING.isEmpty());
    }

    private static boolean fitsOnScreen(RenderBubble placement, int screenWidth, int screenHeight) {
        return placement.targetX >= SCREEN_MARGIN
                && placement.targetY >= SCREEN_MARGIN
                && placement.targetX + placement.layout.scaledWidth <= screenWidth - SCREEN_MARGIN
                && placement.targetY + placement.layout.scaledHeight <= screenHeight - SCREEN_MARGIN;
    }

    private static final Comparator<ActiveBubble> ACTIVE_ORDER =
            Comparator.comparingInt((ActiveBubble active) -> active.spec.priority()).reversed()
                    .thenComparingLong(ActiveBubble::sequence);

    private static List<RenderBubble> buildPlacements(
            List<ActiveBubble> bubbles,
            Font font,
            int screenWidth,
            int screenHeight) {
        EnumMap<BubbleSpec.Anchor, Integer> stackOffsets = new EnumMap<>(BubbleSpec.Anchor.class);
        List<RenderBubble> placements = new ArrayList<>(bubbles.size());
        for (ActiveBubble active : bubbles) {
            ResolvedIcon icon = resolveIcon(active.spec);
            BubbleLayout layout = BubbleLayout.create(font, active.spec, screenWidth, !icon.isEmpty());
            int stackOffset = stackOffsets.getOrDefault(active.spec.anchor(), 0);
            float[] target = targetPosition(active.spec, layout, stackOffset, screenWidth, screenHeight);
            placements.add(new RenderBubble(active, layout, icon, stackOffset, target[0], target[1]));
            stackOffsets.put(active.spec.anchor(), stackOffset + layout.scaledHeight + SCREEN_MARGIN);
        }
        return placements;
    }

    private static float[] targetPosition(
            BubbleSpec spec,
            BubbleLayout layout,
            int stackOffset,
            int screenWidth,
            int screenHeight) {
        float targetX = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> spec.x();
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> screenWidth - layout.scaledWidth + spec.x();
            case CENTER_TOP, CENTER, CENTER_BOTTOM -> (screenWidth - layout.scaledWidth) / 2.0F + spec.x();
        };
        float baseY = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_TOP, TOP_RIGHT -> spec.y();
            case BOTTOM_LEFT, CENTER_BOTTOM, BOTTOM_RIGHT -> screenHeight - layout.scaledHeight - spec.y();
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (screenHeight - layout.scaledHeight) / 2.0F + spec.y();
        };

        float minX = SCREEN_MARGIN;
        float maxX = Math.max(minX, screenWidth - layout.scaledWidth - SCREEN_MARGIN);
        float minY = SCREEN_MARGIN;
        float maxY = Math.max(minY, screenHeight - layout.scaledHeight - SCREEN_MARGIN);
        targetX = Math.max(minX, Math.min(maxX, targetX));
        baseY = Math.max(minY, Math.min(maxY, baseY));
        float targetY = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_TOP, TOP_RIGHT -> baseY + stackOffset;
            case BOTTOM_LEFT, CENTER_BOTTOM, BOTTOM_RIGHT -> baseY - stackOffset;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> baseY + stackOffset;
        };
        return new float[] {
                targetX,
                targetY
        };
    }

    private static void renderBubble(
            Minecraft minecraft,
            GuiGraphics graphics,
            Font font,
            RenderBubble placement,
            long now,
            float bubbleZ) {
        ActiveBubble active = placement.active;
        BubbleLayout layout = placement.layout;
        ResolvedIcon icon = placement.icon;
        BubbleSpec spec = active.spec;
        double age = active.ageTicks(now);
        float alpha = alpha(spec, age);
        if (alpha <= MIN_RENDER_ALPHA) {
            return;
        }
        float slideEntrance = eased(Math.min(1.0F, spec.slideIn() <= 0 ? 1.0F : (float) (age / spec.slideIn())));

        // The placement was calculated together with the queue admission test. Reuse it here
        // so the rendered rectangle and the reserved rectangle are identical.
        float targetX = placement.targetX;
        float targetY = placement.targetY;

        float slideDistance = spec.animation() == BubbleSpec.Animation.FADE ? 0.0F
                : (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_LEFT || spec.animation() == BubbleSpec.Animation.SLIDE_FROM_RIGHT
                ? layout.scaledWidth : layout.scaledHeight) + SCREEN_MARGIN + 8.0F;
        float slideIn = (1.0F - slideEntrance) * slideDistance;
        float slideOut = exitSlideProgress(spec, age) * slideDistance;
        float screenX = targetX;
        float screenY = targetY;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_LEFT) screenX -= slideIn + slideOut;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_RIGHT) screenX += slideIn + slideOut;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_TOP) screenY -= slideIn + slideOut;
        if (spec.animation() == BubbleSpec.Animation.SLIDE_FROM_BOTTOM) screenY += slideIn + slideOut;

        graphics.pose().pushPose();
        graphics.pose().translate(screenX, screenY, bubbleZ);
        graphics.pose().scale(spec.scale(), spec.scale(), 1.0F);
        if (!spec.backgroundTexture().isBlank()) {
            ResourceLocation sourceTexture = ResourceLocation.tryParse(spec.backgroundTexture());
            TextureSize sourceSize = sourceTexture == null ? null : textureSize(sourceTexture);
            if (sourceTexture == null || sourceSize == null) {
                graphics.fill(0, 0, layout.width, layout.height, withAlpha(spec.backgroundColor(), alpha));
            } else {
                PreparedTexture prepared = spec.backgroundBorder() > 0
                        ? prepareNineSliceTexture(sourceTexture, sourceSize, spec.backgroundBorder(), spec.backgroundGuide(),
                        layout.width, layout.height)
                        : new PreparedTexture(sourceTexture, sourceSize, spec.backgroundGuide());
                ResourceLocation texture = prepared.texture();
                TextureSize textureSize = prepared.size();
                Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(false, false);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                graphics.blit(texture, 0, 0, 0, 0.0F, 0.0F,
                        layout.width, layout.height, textureSize.width, textureSize.height);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        } else {
            graphics.fill(0, 0, layout.width, layout.height, withAlpha(spec.backgroundColor(), alpha));
        }

        // Commit the background before this bubble's icon/text and before the next bubble starts.
        flushBubble(graphics);

        if (!icon.isEmpty()) {
            int iconY = spec.padding() + Math.max(0,
                    (layout.height - spec.padding() * 2 - layout.iconSize) / 2);
            graphics.pose().pushPose();
            graphics.pose().translate(spec.padding() + spec.iconOffsetX(),
                    iconY + spec.iconOffsetY(), 0.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            if (icon.type() == BubbleSpec.IconType.ITEM) {
                float iconScale = layout.iconSize / 16.0F;
                graphics.pose().scale(iconScale, iconScale, 1.0F);
                // GuiGraphics.renderItem normally adds z=150. Use its GUI offset overload so
                // an icon cannot jump above the next bubble's background.
                graphics.renderItem(icon.itemStack(), 0, 0, 0, -150);
            } else if (icon.type() == BubbleSpec.IconType.TEXTURE) {
                int iconWidth = icon.textureSize().width();
                int iconHeight = icon.textureSize().height();
                float ratio = iconWidth / (float) Math.max(1, iconHeight);
                int drawWidth = ratio >= 1.0F ? layout.iconSize : Math.max(1, Math.round(layout.iconSize * ratio));
                int drawHeight = ratio <= 1.0F ? layout.iconSize : Math.max(1, Math.round(layout.iconSize / ratio));
                int drawX = (layout.iconSize - drawWidth) / 2;
                int drawY = (layout.iconSize - drawHeight) / 2;
                graphics.blit(icon.texture(), drawX, drawY, 0, 0.0F, 0.0F,
                        drawWidth, drawHeight, iconWidth, iconHeight);
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().popPose();
            flushBubble(graphics);
        }

        int textY = spec.padding() + spec.textOffsetY();
        for (TextLine line : layout.lines) {
            int lineWidth = line.width(font);
            int textX = switch (spec.textAlignment()) {
                case LEFT -> layout.textStartX;
                case CENTER -> layout.textStartX + (layout.textAreaWidth - lineWidth) / 2;
                case RIGHT -> layout.textStartX + layout.textAreaWidth - lineWidth;
            };
            int textAreaEnd = layout.textStartX + layout.textAreaWidth;
            textX = Math.max(layout.textStartX, Math.min(textAreaEnd - lineWidth, textX));
            int runX = textX + spec.textOffsetX();
            for (TextRun run : line.runs()) {
                graphics.pose().pushPose();
                graphics.pose().translate(runX, textY, 0.0F);
                graphics.pose().scale(run.part().scale(), run.part().scale(), 1.0F);
                graphics.drawString(font, run.component(), 0, 0,
                        withAlpha(run.part().color(), alpha), run.part().shadow());
                graphics.pose().popPose();
                runX += Math.round(font.width(run.component()) * run.part().scale());
            }
            textY += line.height(font);
        }
        // Keep each bubble as one complete render unit. Without this flush, text from every
        // bubble remains queued until the end of the overlay and can appear above later bubbles'
        // backgrounds when their rectangles overlap.
        flushBubble(graphics);
        graphics.pose().popPose();
    }

    private static void flushBubble(GuiGraphics graphics) {
        graphics.flush();
        // Item rendering uses Minecraft's shared buffer source. Flush it together with the
        // GuiGraphics batch so hotbar/item vertices cannot land after bubble text.
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
        // Reassert the overlay state after Minecraft's item renderer restores depth testing.
        RenderSystem.disableDepthTest();
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

            ResourceLocation generatedId = ResourceLocation.fromNamespaceAndPath(
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

    private static ResolvedIcon resolveIcon(BubbleSpec spec) {
        if (spec.iconId().isBlank() || spec.iconType() == BubbleSpec.IconType.NONE) {
            return ResolvedIcon.EMPTY;
        }

        ResourceLocation id = ResourceLocation.tryParse(spec.iconId());
        if (id == null) {
            return ResolvedIcon.EMPTY;
        }

        if (spec.iconType() != BubbleSpec.IconType.TEXTURE) {
            ItemStack stack = ICON_STACKS.computeIfAbsent(id,
                    key -> BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY));
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

    private record RenderBubble(
            ActiveBubble active,
            BubbleLayout layout,
            ResolvedIcon icon,
            int stackOffset,
            float targetX,
            float targetY) {
    }

    private record TextureSize(int width, int height) {
    }

    private record ResolvedIcon(
            BubbleSpec.IconType type,
            ItemStack itemStack,
            ResourceLocation texture,
            TextureSize textureSize) {
        private static final ResolvedIcon EMPTY =
                new ResolvedIcon(BubbleSpec.IconType.NONE, ItemStack.EMPTY, null, null);

        private boolean isEmpty() {
            return type == BubbleSpec.IconType.NONE;
        }
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
        private final List<TextLine> lines;
        private final int width;
        private final int height;
        private final int scaledWidth;
        private final int scaledHeight;
        private final int iconSize;
        private final int textStartX;
        private final int textAreaWidth;

        private BubbleLayout(
                List<TextLine> lines,
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
            List<TextLine> lines = layoutText(font, spec, contentWidth);
            int measuredWidth = lines.stream().mapToInt(line -> line.width(font)).max().orElse(0);
            int width = spec.width() > 0
                    ? spec.width()
                    : autoWidth(measuredWidth, spec.padding(), iconWidth, maxWidth, spec.scale());
            int minimumHeight = Math.max(Math.max(9, lines.stream().mapToInt(line -> line.height(font)).sum()), hasIcon ? spec.iconSize() : 0)
                    + spec.padding() * 2;
            int height = spec.height() > 0 ? Math.max(spec.height(), minimumHeight) : minimumHeight;
            int textStartX = spec.padding() + iconWidth;
            int textAreaWidth = Math.max(1, width - spec.padding() - textStartX);
            return new BubbleLayout(lines, width, height, spec.scale(), hasIcon ? spec.iconSize() : 0, textStartX, textAreaWidth);
        }

        private static List<TextLine> layoutText(Font font, BubbleSpec spec, int contentWidth) {
            List<TextLine> lines = new ArrayList<>();
            TextLine current = new TextLine();
            for (BubbleSpec.TextPart part : spec.textParts()) {
                String[] explicitLines = part.text().split("\\R", -1);
                for (int index = 0; index < explicitLines.length; index++) {
                    String value = explicitLines[index];
                    current = appendWrapped(font, current, part, value, contentWidth, lines);
                    if (index < explicitLines.length - 1) {
                        lines.add(current);
                        current = new TextLine();
                    }
                }
            }
            if (!current.runs().isEmpty() || lines.isEmpty()) {
                lines.add(current);
            }
            return lines;
        }

        private static TextLine appendWrapped(
                Font font,
                TextLine current,
                BubbleSpec.TextPart part,
                String value,
                int contentWidth,
                List<TextLine> lines) {
            int offset = 0;
            while (offset < value.length()) {
                int available = contentWidth - current.width(font);
                if (available <= 0 && !current.runs().isEmpty()) {
                    lines.add(current);
                    current = new TextLine();
                    available = contentWidth;
                }

                int fit = fittingLength(font, value, offset, available, part);
                if (fit <= 0) {
                    if (!current.runs().isEmpty()) {
                        lines.add(current);
                        current = new TextLine();
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
                    current.runs().add(new TextRun(part, component(part, chunk)));
                }
                offset = end;
                while (offset < value.length() && value.charAt(offset) == ' ') {
                    offset++;
                }
            }
            return current;
        }

        private static int fittingLength(Font font, String value, int start, int available, BubbleSpec.TextPart part) {
            int low = 0;
            int high = value.length() - start;
            while (low < high) {
                int middle = (low + high + 1) / 2;
                String candidate = value.substring(start, start + middle);
                int width = Math.round(font.width(component(part, candidate)) * part.scale());
                if (width <= available) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            return low;
        }

        private static Component component(BubbleSpec.TextPart part, String text) {
            Style style = Style.EMPTY.withColor(TextColor.fromRgb(part.color() & 0x00FFFFFF))
                    .withBold(part.bold())
                    .withItalic(part.italic())
                    .withUnderlined(part.underlined())
                    .withStrikethrough(part.strikethrough())
                    .withObfuscated(part.obfuscated());
            return Component.literal(text).withStyle(style);
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

    private static final class TextLine {
        private final List<TextRun> runs = new ArrayList<>();

        private List<TextRun> runs() {
            return runs;
        }

        private int width(Font font) {
            return runs.stream().mapToInt(run -> Math.round(font.width(run.component()) * run.part().scale())).sum();
        }

        private int height(Font font) {
            return Math.max(9, runs.stream().mapToInt(run -> Math.round(9.0F * run.part().scale())).max().orElse(9));
        }
    }

    private record TextRun(BubbleSpec.TextPart part, Component component) {
    }
}
