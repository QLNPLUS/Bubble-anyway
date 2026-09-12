package com.bubbleanyway.client;

import com.bubbleanyway.data.BubbleSpec;
import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

public final class BubbleOverlay {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCREEN_MARGIN = 6;
    private static final float MIN_RENDER_ALPHA = 0.02F;
    private static final List<ActiveBubble> ACTIVE = new ArrayList<>();
    private static final List<QueuedBubble> PENDING = new ArrayList<>();
    private static final Map<Identifier, TextureSize> TEXTURE_SIZES = new HashMap<>();
    private static final Map<Identifier, ItemStack> ICON_STACKS = new HashMap<>();
    private static final Map<GeneratedTextureKey, PreparedTexture> GENERATED_TEXTURES = new HashMap<>();
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
                .thenComparingLong(queued -> queued.sequence));
        LOGGER.debug("Queued Bubble Anyway bubble {} (pending={}, active={})", spec.id(), PENDING.size(), ACTIVE.size());
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

        Identifier soundId = parseResource(spec.sound());
        if (soundId == null) {
            return;
        }

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(sound, spec.soundVolume(), spec.soundPitch()));
        }
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        renderInternal(graphics, null);
    }

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        renderInternal(graphics, null);
    }

    public static void render(GuiGraphicsExtractor graphics, float partialTick, BubbleSpec.RenderLayer layer) {
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

    private static void renderInternal(GuiGraphicsExtractor graphics, BubbleSpec.RenderLayer layer) {
        if (!renderCallbackLogged) {
            LOGGER.info("Bubble Anyway GUI layer render callback is active");
            renderCallbackLogged = true;
        }
        Minecraft minecraft = Minecraft.getInstance();

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

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

        // NeoForge 26.1 extracts GUI elements into ordered strata instead of drawing them
        // immediately. Start a new stratum so bubbles stay above the vanilla HUD and screens.
        graphics.nextStratum();
        EnumMap<BubbleSpec.Anchor, Integer> stackOffsets = new EnumMap<>(BubbleSpec.Anchor.class);
        Map<ActiveBubble, Integer> bubbleOffsets = new HashMap<>();
        Map<ActiveBubble, BubbleLayout> layouts = new HashMap<>();
        Map<ActiveBubble, ResolvedIcon> icons = new HashMap<>();
        graphics.pose().pushMatrix();
        for (ActiveBubble active : visible) {
            ResolvedIcon icon = resolveIcon(active.spec);
            BubbleLayout layout = BubbleLayout.create(font, active.spec, screenWidth, !icon.isEmpty());
            int stackOffset = stackOffsets.getOrDefault(active.spec.anchor(), 0);
            bubbleOffsets.put(active, stackOffset);
            layouts.put(active, layout);
            icons.put(active, icon);
            stackOffsets.put(active.spec.anchor(), stackOffset + layout.scaledHeight + SCREEN_MARGIN);
        }
        for (int i = visible.size() - 1; i >= 0; i--) {
            ActiveBubble active = visible.get(i);
            renderBubble(graphics, font, active, layouts.get(active), icons.get(active), bubbleOffsets.get(active),
                    screenWidth, screenHeight, now);
        }
        graphics.pose().popMatrix();
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
            for (QueuedBubble queued : List.copyOf(PENDING)) {
                ActiveBubble candidate = new ActiveBubble(queued.spec, now, queued.sequence);
                List<ActiveBubble> trial = new ArrayList<>(ACTIVE);
                trial.add(candidate);
                trial.sort(Comparator.comparingInt((ActiveBubble active) -> active.spec.priority()).reversed()
                        .thenComparingLong(active -> active.sequence));
                if (!fitsCandidateOnScreen(trial, candidate, font, screenWidth, screenHeight)) {
                    continue;
                }
                PENDING.remove(queued);
                ACTIVE.add(candidate);
                ACTIVE.sort(Comparator.comparingInt((ActiveBubble active) -> active.spec.priority()).reversed()
                        .thenComparingLong(active -> active.sequence));
                playSound(candidate.spec);
                promoted = true;
                break;
            }
        } while (promoted && !PENDING.isEmpty());
    }

    private static boolean fitsCandidateOnScreen(
            List<ActiveBubble> bubbles,
            ActiveBubble candidate,
            Font font,
            int screenWidth,
            int screenHeight) {
        EnumMap<BubbleSpec.Anchor, Integer> stackOffsets = new EnumMap<>(BubbleSpec.Anchor.class);
        for (ActiveBubble active : bubbles) {
            ResolvedIcon icon = resolveIcon(active.spec);
            BubbleLayout layout = BubbleLayout.create(font, active.spec, screenWidth, !icon.isEmpty());
            int stackOffset = stackOffsets.getOrDefault(active.spec.anchor(), 0);
            float[] target = targetPosition(active.spec, layout, stackOffset, screenWidth, screenHeight);
            if (active == candidate) {
                return target[0] >= SCREEN_MARGIN && target[1] >= SCREEN_MARGIN
                        && target[0] + layout.scaledWidth <= screenWidth - SCREEN_MARGIN
                        && target[1] + layout.scaledHeight <= screenHeight - SCREEN_MARGIN;
            }
            stackOffsets.put(active.spec.anchor(), stackOffset + layout.scaledHeight + SCREEN_MARGIN);
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
        baseY = Math.max(minY, Math.min(maxY, baseY));
        float targetY = switch (spec.anchor()) {
            case TOP_LEFT, CENTER_TOP, TOP_RIGHT -> baseY + stackOffset;
            case BOTTOM_LEFT, CENTER_BOTTOM, BOTTOM_RIGHT -> baseY - stackOffset;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> baseY + stackOffset;
        };
        return new float[] {Math.max(minX, Math.min(maxX, targetX)), targetY};
    }

    private static void renderBubble(
            GuiGraphicsExtractor graphics,
            Font font,
            ActiveBubble active,
            BubbleLayout layout,
            ResolvedIcon icon,
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
        float slideEntrance = eased(Math.min(1.0F, spec.slideIn() <= 0 ? 1.0F : (float) (age / spec.slideIn())));

        float[] target = targetPosition(spec, layout, stackOffset, screenWidth, screenHeight);
        float targetX = target[0];
        float targetY = target[1];

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

        graphics.pose().pushMatrix();
        graphics.pose().translate(screenX, screenY);
        graphics.pose().scale(spec.scale(), spec.scale());
        if (!spec.backgroundTexture().isBlank()) {
            Identifier texture = parseResource(spec.backgroundTexture());
            TextureSize textureSize = texture == null ? null : textureSize(texture);
            if (texture == null || textureSize == null) {
                graphics.fill(0, 0, layout.width, layout.height, withAlpha(spec.backgroundColor(), alpha));
                graphics.pose().popMatrix();
                return;
            }
            PreparedTexture prepared = spec.backgroundBorder() > 0
                    ? prepareNineSliceTexture(texture, textureSize, spec.backgroundBorder(), spec.backgroundGuide(),
                    layout.width, layout.height)
                    : new PreparedTexture(texture, textureSize);
            graphics.blit(RenderPipelines.GUI_TEXTURED, prepared.texture(), 0, 0, 0.0F, 0.0F,
                    layout.width, layout.height, layout.width, layout.height,
                    prepared.size().width, prepared.size().height, alphaColor(alpha));
        } else {
            graphics.fill(0, 0, layout.width, layout.height, withAlpha(spec.backgroundColor(), alpha));
        }

        if (!icon.isEmpty()) {
            int iconY = spec.padding() + Math.max(0,
                    (layout.height - spec.padding() * 2 - layout.iconSize) / 2);
            graphics.pose().pushMatrix();
            graphics.pose().translate(spec.padding() + spec.iconOffsetX(),
                    iconY + spec.iconOffsetY());
            if (icon.type() == BubbleSpec.IconType.ITEM) {
                float iconScale = layout.iconSize / 16.0F;
                graphics.pose().scale(iconScale, iconScale);
                graphics.item(icon.itemStack(), 0, 0);
            } else if (icon.type() == BubbleSpec.IconType.TEXTURE) {
                int iconWidth = icon.textureSize().width();
                int iconHeight = icon.textureSize().height();
                float ratio = iconWidth / (float) Math.max(1, iconHeight);
                int drawWidth = ratio >= 1.0F ? layout.iconSize : Math.max(1, Math.round(layout.iconSize * ratio));
                int drawHeight = ratio <= 1.0F ? layout.iconSize : Math.max(1, Math.round(layout.iconSize / ratio));
                int drawX = (layout.iconSize - drawWidth) / 2;
                int drawY = (layout.iconSize - drawHeight) / 2;
                graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), drawX, drawY,
                        0.0F, 0.0F, drawWidth, drawHeight, iconWidth, iconHeight,
                        iconWidth, iconHeight, alphaColor(alpha));
            }
            graphics.pose().popMatrix();
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
                graphics.pose().pushMatrix();
                graphics.pose().translate(runX, textY);
                graphics.pose().scale(run.part().scale(), run.part().scale());
                graphics.text(font, run.component(), 0, 0,
                        withAlpha(run.part().color(), alpha), run.part().shadow());
                graphics.pose().popMatrix();
                runX += Math.round(font.width(run.component()) * run.part().scale());
            }
            textY += line.height(font);
        }
        graphics.pose().popMatrix();
    }

    private static void renderNineSlice(
            GuiGraphicsExtractor graphics,
            Identifier texture,
            TextureSize textureSize,
            int width,
            int height,
            int requestedBorder,
            int requestedGuide,
            float alpha) {
        int sourceBorder = Math.min(requestedBorder, Math.min(textureSize.width / 2, textureSize.height / 2));
        int sourceGuide = Math.min(requestedGuide, Math.min(
                Math.max(0, (textureSize.width - sourceBorder * 2) / 2),
                Math.max(0, (textureSize.height - sourceBorder * 2) / 2)));
        int destinationBorder = Math.min(sourceBorder, Math.min(width / 2, height / 2));
        int sourceCenterWidth = textureSize.width - sourceBorder * 2 - sourceGuide * 2;
        int sourceCenterHeight = textureSize.height - sourceBorder * 2 - sourceGuide * 2;
        if (sourceBorder <= 0 || destinationBorder <= 0 || sourceCenterWidth <= 0 || sourceCenterHeight <= 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F,
                    width, height, width, height, textureSize.width, textureSize.height, alphaColor(alpha));
            return;
        }

        int destinationCenterWidth = width - destinationBorder * 2;
        int destinationCenterHeight = height - destinationBorder * 2;
        int sourceCenterStart = sourceBorder + sourceGuide;
        int sourceRightStart = textureSize.width - sourceBorder;
        int sourceBottomStart = textureSize.height - sourceBorder;

        blitPart(graphics, texture, 0, 0, destinationBorder, destinationBorder,
                0, 0, sourceBorder, sourceBorder, textureSize, alpha);
        blitPart(graphics, texture, destinationBorder, 0, destinationCenterWidth, destinationBorder,
                sourceCenterStart, 0, sourceCenterWidth, sourceBorder, textureSize, alpha);
        blitPart(graphics, texture, width - destinationBorder, 0, destinationBorder, destinationBorder,
                sourceRightStart, 0, sourceBorder, sourceBorder, textureSize, alpha);

        blitPart(graphics, texture, 0, destinationBorder, destinationBorder, destinationCenterHeight,
                0, sourceCenterStart, sourceBorder, sourceCenterHeight, textureSize, alpha);
        blitPart(graphics, texture, destinationBorder, destinationBorder, destinationCenterWidth, destinationCenterHeight,
                sourceCenterStart, sourceCenterStart, sourceCenterWidth, sourceCenterHeight, textureSize, alpha);
        blitPart(graphics, texture, width - destinationBorder, destinationBorder, destinationBorder, destinationCenterHeight,
                sourceRightStart, sourceCenterStart, sourceBorder, sourceCenterHeight, textureSize, alpha);

        blitPart(graphics, texture, 0, height - destinationBorder, destinationBorder, destinationBorder,
                0, sourceBottomStart, sourceBorder, sourceBorder, textureSize, alpha);
        blitPart(graphics, texture, destinationBorder, height - destinationBorder, destinationCenterWidth, destinationBorder,
                sourceCenterStart, sourceBottomStart, sourceCenterWidth, sourceBorder, textureSize, alpha);
        blitPart(graphics, texture, width - destinationBorder, height - destinationBorder, destinationBorder, destinationBorder,
                sourceRightStart, sourceBottomStart, sourceBorder, sourceBorder, textureSize, alpha);
    }

    private static PreparedTexture prepareNineSliceTexture(
            Identifier sourceTexture,
            TextureSize sourceSize,
            int requestedBorder,
            int requestedGuide,
            int targetWidth,
            int targetHeight) {
        if (requestedGuide <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return new PreparedTexture(sourceTexture, sourceSize);
        }

        int sourceBorder = Math.min(requestedBorder, Math.min(sourceSize.width / 2, sourceSize.height / 2));
        int sourceGuide = Math.min(requestedGuide, Math.min(
                Math.max(0, (sourceSize.width - sourceBorder * 2) / 2),
                Math.max(0, (sourceSize.height - sourceBorder * 2) / 2)));
        if (sourceBorder <= 0 || sourceGuide <= 0) {
            return new PreparedTexture(sourceTexture, sourceSize);
        }

        GeneratedTextureKey key = new GeneratedTextureKey(
                sourceTexture, sourceSize, sourceBorder, sourceGuide, targetWidth, targetHeight);
        PreparedTexture cached = GENERATED_TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }

        NativeImage preparedImage = null;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(sourceTexture);
            if (resource.isEmpty()) {
                return new PreparedTexture(sourceTexture, sourceSize);
            }
            try (InputStream inputStream = resource.get().open(); NativeImage sourceImage = NativeImage.read(inputStream)) {
                preparedImage = new NativeImage(sourceImage.format(), targetWidth, targetHeight, false);
                for (int y = 0; y < targetHeight; y++) {
                    int sourceY = mapNineSliceCoordinate(y, targetHeight, sourceSize.height, sourceBorder, sourceGuide);
                    for (int x = 0; x < targetWidth; x++) {
                        int sourceX = mapNineSliceCoordinate(x, targetWidth, sourceSize.width, sourceBorder, sourceGuide);
                        preparedImage.setPixel(x, y, sourceImage.getPixel(sourceX, sourceY));
                    }
                }
            }

            Identifier generatedId = Identifier.fromNamespaceAndPath(
                    "bubble_anyway", "generated/9slice/" + generatedTextureSequence++);
            DynamicTexture dynamicTexture = new DynamicTexture(() -> generatedId.toString(), preparedImage);
            preparedImage = null;
            Minecraft.getInstance().getTextureManager().register(generatedId, dynamicTexture);
            PreparedTexture prepared = new PreparedTexture(generatedId, new TextureSize(targetWidth, targetHeight));
            GENERATED_TEXTURES.put(key, prepared);
            return prepared;
        } catch (IOException | RuntimeException ignored) {
            if (preparedImage != null) {
                preparedImage.close();
            }
            return new PreparedTexture(sourceTexture, sourceSize);
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
        return sourceCenterStart + Math.min(sourceCenterSize - 1,
                (int) ((long) centerOffset * sourceCenterSize / destinationCenterSize));
    }

    private static void blitPart(
            GuiGraphicsExtractor graphics,
            Identifier texture,
            int destinationX,
            int destinationY,
            int destinationWidth,
            int destinationHeight,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            TextureSize textureSize,
            float alpha) {
        if (destinationWidth <= 0 || destinationHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }

        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, destinationX, destinationY,
                sourceX, sourceY, destinationWidth, destinationHeight, sourceWidth, sourceHeight,
                textureSize.width, textureSize.height, alphaColor(alpha));
    }

    private static TextureSize textureSize(Identifier texture) {
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

        Identifier id = parseResource(spec.iconId());
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

    private static ItemStack resolveIcon(String iconId) {
        Identifier id = parseResource(iconId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        return ICON_STACKS.computeIfAbsent(id,
                key -> BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY));
    }

    private static Identifier parseResource(String value) {
        try {
            return Identifier.parse(value);
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

    private static int alphaColor(float opacity) {
        return withAlpha(0xFFFFFFFF, opacity);
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

    private static final class GeneratedTextureKey {
        private final Identifier sourceTexture;
        private final TextureSize sourceSize;
        private final int border;
        private final int guide;
        private final int targetWidth;
        private final int targetHeight;

        private GeneratedTextureKey(Identifier sourceTexture, TextureSize sourceSize, int border, int guide,
                                    int targetWidth, int targetHeight) {
            this.sourceTexture = sourceTexture;
            this.sourceSize = sourceSize;
            this.border = border;
            this.guide = guide;
            this.targetWidth = targetWidth;
            this.targetHeight = targetHeight;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GeneratedTextureKey key)) {
                return false;
            }
            return sourceTexture.equals(key.sourceTexture) && sourceSize.equals(key.sourceSize)
                    && border == key.border && guide == key.guide
                    && targetWidth == key.targetWidth && targetHeight == key.targetHeight;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(sourceTexture, sourceSize, border, guide, targetWidth, targetHeight);
        }
    }

    private record PreparedTexture(Identifier texture, TextureSize size) {
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
                    : Math.min(maxWidth, Math.max(80, measuredWidth + spec.padding() * 2 + iconWidth));
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
                    current = appendWrapped(font, current, part, explicitLines[index], contentWidth, lines);
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
