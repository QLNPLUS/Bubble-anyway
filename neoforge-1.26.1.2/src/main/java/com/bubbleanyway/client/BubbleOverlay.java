package com.bubbleanyway.client;

import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.data.BubbleControl;
import com.bubbleanyway.data.BubbleControls;
import com.bubbleanyway.data.BubbleControlStyle;
import com.bubbleanyway.network.BubbleNetwork;
import com.bubbleanyway.kubejs.BubbleKubeJSBindings;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.slf4j.Logger;

public final class BubbleOverlay {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCREEN_MARGIN = 6;
    private static final float MIN_RENDER_ALPHA = 0.02F;
    private static final long NOT_CLOSING = Long.MIN_VALUE;
    private static final List<ActiveBubble> ACTIVE = new ArrayList<>();
    private static final List<QueuedBubble> PENDING = new ArrayList<>();
    private static final Map<Identifier, TextureSize> TEXTURE_SIZES = new HashMap<>();
    private static final Map<GeneratedTextureKey, PreparedTexture> GENERATED_TEXTURES = new HashMap<>();
    private static final Map<Identifier, ItemStack> ICON_STACKS = new HashMap<>();
    private static boolean renderCallbackLogged;
    private static long sequence;
    private static long generatedTextureSequence;
    private static long logicalNow;
    private static long wallClockNow;
    private static boolean clockInitialized;
    private static String pressedBubbleId = "";
    private static String pressedControlId = "";
    private static long pressedUntil;

    private BubbleOverlay() {
    }

    public static synchronized boolean enqueue(BubbleSpec spec) {
        return enqueue(spec, false);
    }

    public static synchronized boolean enqueue(BubbleSpec spec, boolean serverSourced) {
        if (Minecraft.getInstance().level == null) {
            return false;
        }
        if (spec.remove()) {
            return requestRemoval(spec.id(), animationNow(Minecraft.getInstance().isPaused()));
        }
        if (spec.replace()) {
            ACTIVE.removeIf(active -> active.spec.id().equals(spec.id()));
            PENDING.removeIf(queued -> queued.spec.id().equals(spec.id()));
        }
        PENDING.add(new QueuedBubble(spec, sequence++, serverSourced));
        PENDING.sort(Comparator.comparingInt((QueuedBubble queued) -> queued.spec.priority()).reversed()
                .thenComparingLong(QueuedBubble::sequence));
        LOGGER.debug("Queued Bubble Anyway bubble {} (pending={}, active={})", spec.id(), PENDING.size(), ACTIVE.size());
        // A queued bubble is accepted immediately. Toast integration can cancel the original
        // Toast because the bubble will be promoted when its screen region has room.
        return true;
    }

    public static synchronized boolean handleMouseClick(
            double mouseX, double mouseY, int button, int screenWidth, int screenHeight,
            BubbleSpec.RenderLayer layer) {
        if (button != 0 || Minecraft.getInstance().level == null || Minecraft.getInstance().screen == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = animationNow(minecraft.isPaused());
        List<ActiveBubble> visible = snapshot(now, minecraft.font, screenWidth, screenHeight);
        List<RenderBubble> placements = buildPlacements(visible, minecraft.font, screenWidth, screenHeight);
        if (layer != null) {
            placements.removeIf(placement -> placement.active.spec.renderLayer() != layer);
        }
        placements.sort(Comparator.comparingInt((RenderBubble bubble) -> bubble.active.spec.priority())
                .thenComparingLong(bubble -> bubble.active.sequence));
        for (int index = placements.size() - 1; index >= 0; index--) {
            RenderBubble placement = placements.get(index);
            double localX = (mouseX - placement.targetX) / placement.active.spec.scale();
            double localY = (mouseY - placement.targetY) / placement.active.spec.scale();
            for (ControlPlacement control : placement.layout.controls) {
                if (!control.contains(localX, localY) || !control.control.enabled()) continue;
                BubbleClientClickEvent event = new BubbleClientClickEvent(placement.active.spec, control.control);
                boolean canceled = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled();
                BubbleKubeJSBindings.dispatchClick(event);
                if (canceled || event.isCanceled()) return true;
                pressedBubbleId = placement.active.spec.id();
                pressedControlId = control.control.id();
                pressedUntil = System.nanoTime() + 150_000_000L;
                if (placement.active.serverSourced && minecraft.getConnection() != null) {
                    BubbleNetwork.sendClickToServer(placement.active.spec.id(), control.control.id());
                } else if (control.control.closeOnPress()) {
                    requestRemoval(placement.active.spec.id(), now);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean requestRemoval(String id, long now) {
        boolean changed = PENDING.removeIf(queued -> queued.spec.id().equals(id));
        for (ActiveBubble active : ACTIVE) {
            if (active.spec.id().equals(id)) {
                active.beginClosing(now);
                changed = true;
            }
        }
        ACTIVE.removeIf(active -> active.isClosing() && active.closeDuration() <= 0);
        return changed;
    }

    public static synchronized void clear() {
        ACTIVE.clear();
        PENDING.clear();
        pressedBubbleId = "";
        pressedControlId = "";
        pressedUntil = 0L;
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

        Identifier soundId = Identifier.tryParse(spec.sound());
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

        // NeoForge 26.1 extracts GUI elements into ordered strata.
        graphics.nextStratum();

        List<RenderBubble> renderBubbles = buildPlacements(visible, font, screenWidth, screenHeight);

        // Draw lower-priority/older bubbles first. The later draw call is the visible top layer
        // whenever two bubble rectangles overlap.
        renderBubbles.sort(Comparator.comparingInt((RenderBubble bubble) -> bubble.active.spec.priority())
                .thenComparingLong(bubble -> bubble.active.sequence));
        for (RenderBubble bubble : renderBubbles) {
            renderBubble(minecraft, graphics, font, bubble, now);
        }
    }

    private static synchronized List<ActiveBubble> snapshot(
            long now,
            Font font,
            int screenWidth,
            int screenHeight) {
        ACTIVE.removeIf(active -> active.isClosing()
                ? active.closeAgeTicks(now) >= active.closeDuration()
                : active.spec.duration() != -1 && active.ageTicks(now) >= active.spec.duration());
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
                ActiveBubble candidate = new ActiveBubble(queued.spec, now, queued.sequence, queued.serverSourced);
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
            GuiGraphicsExtractor graphics,
            Font font,
            RenderBubble placement,
            long now) {
        ActiveBubble active = placement.active;
        BubbleLayout layout = placement.layout;
        ResolvedIcon icon = placement.icon;
        BubbleSpec spec = active.spec;
        double age = active.ageTicks(now);
        float alpha = active.isClosing()
                ? closingAlpha(spec, age, active.closeAgeTicks(now))
                : alpha(spec, age);
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
        float slideOut = (active.isClosing()
                ? closeSlideProgress(spec, active.closeAgeTicks(now))
                : exitSlideProgress(spec, age)) * slideDistance;
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
            Identifier sourceTexture = Identifier.tryParse(spec.backgroundTexture());
            TextureSize sourceSize = sourceTexture == null ? null : textureSize(sourceTexture);
            if (sourceTexture == null || sourceSize == null) {
                graphics.fill(0, 0, layout.width, layout.height, withAlpha(spec.backgroundColor(), alpha));
            } else {
                PreparedTexture prepared = spec.backgroundBorder() > 0
                        ? prepareNineSliceTexture(sourceTexture, sourceSize, spec.backgroundBorder(), spec.backgroundGuide(),
                        layout.width, layout.height)
                        : new PreparedTexture(sourceTexture, sourceSize, spec.backgroundGuide());
                TextureSize textureSize = prepared.size();
                graphics.blit(RenderPipelines.GUI_TEXTURED, prepared.texture(), 0, 0, 0.0F, 0.0F,
                        layout.width, layout.height, layout.width, layout.height,
                        textureSize.width, textureSize.height, alphaColor(alpha));
            }
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
                        0.0F, 0.0F, drawWidth, drawHeight, drawWidth, drawHeight,
                        iconWidth, iconHeight, alphaColor(alpha));
            }
            graphics.pose().popMatrix();
        }

        int textY = spec.padding() + spec.textOffsetY();
        for (int lineIndex = 0; lineIndex < layout.lines.size(); lineIndex++) {
            TextLine line = layout.lines.get(lineIndex);
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
            if (lineIndex + 1 < layout.lines.size()) {
                textY += spec.lineSpacing();
            }
        }
        renderControls(minecraft, graphics, font, spec, layout, alpha, screenX, screenY);
        graphics.pose().popMatrix();
    }

    private static void renderControls(
            Minecraft minecraft,
            GuiGraphicsExtractor graphics,
            Font font,
            BubbleSpec spec,
            BubbleLayout layout,
            float alpha,
            float screenX,
            float screenY) {
        if (layout.controls.isEmpty()) return;
        boolean interactive = minecraft.screen != null;
        double[] mouse = interactive ? mousePosition(minecraft) : null;
        for (ControlPlacement placement : layout.controls) {
            BubbleControl control = placement.control;
            BubbleControlStyle style = spec.controls().style(control.style());
            BubbleControlStyle.InteractionState interaction = !control.enabled()
                    ? BubbleControlStyle.InteractionState.DISABLED
                    : interactive && placement.contains(
                    (mouse[0] - screenX) / spec.scale(), (mouse[1] - screenY) / spec.scale())
                    ? (isPressed(control, spec) ? BubbleControlStyle.InteractionState.PRESSED
                    : BubbleControlStyle.InteractionState.HOVER)
                    : BubbleControlStyle.InteractionState.NORMAL;
            BubbleControlStyle.State state = style.state(interaction);
            if (!state.backgroundTexture().isBlank()) {
                Identifier texture = Identifier.tryParse(state.backgroundTexture());
                TextureSize size = texture == null ? null : textureSize(texture);
                if (texture != null && size != null) {
                    PreparedTexture prepared = state.backgroundBorder() > 0
                            ? prepareNineSliceTexture(texture, size, state.backgroundBorder(), state.backgroundGuide(),
                            placement.width, placement.height)
                            : new PreparedTexture(texture, size, 0);
                    graphics.blit(RenderPipelines.GUI_TEXTURED, prepared.texture(), placement.x, placement.y,
                            0.0F, 0.0F, placement.width, placement.height,
                            placement.width, placement.height,
                            prepared.size().width, prepared.size().height, alphaColor(alpha));
                } else {
                    graphics.fill(placement.x, placement.y, placement.x + placement.width,
                            placement.y + placement.height, withAlpha(state.backgroundColor(), alpha));
                }
            } else {
                graphics.fill(placement.x, placement.y, placement.x + placement.width,
                        placement.y + placement.height, withAlpha(state.backgroundColor(), alpha));
            }
            Component label = controlComponent(control.text(), state);
            int labelWidth = Math.round(font.width(label) * state.scale());
            int labelHeight = Math.max(9, Math.round(9.0F * state.scale()));
            int labelX = placement.x + Math.max(0, (placement.width - labelWidth) / 2);
            int labelY = placement.y + Math.max(0, (placement.height - labelHeight) / 2);
            graphics.pose().pushMatrix();
            graphics.pose().translate(labelX, labelY);
            graphics.pose().scale(state.scale(), state.scale());
            graphics.text(font, label, 0, 0, withAlpha(state.textColor(), alpha), state.shadow());
            graphics.pose().popMatrix();
        }
    }

    private static boolean isPressed(BubbleControl control, BubbleSpec spec) {
        return System.nanoTime() < pressedUntil
                && pressedBubbleId.equals(spec.id()) && pressedControlId.equals(control.id());
    }

    private static double[] mousePosition(Minecraft minecraft) {
        var window = minecraft.getWindow();
        double scaleX = window.getWidth() <= 0 ? 1.0D : window.getGuiScaledWidth() / (double) window.getWidth();
        double scaleY = window.getHeight() <= 0 ? 1.0D : window.getGuiScaledHeight() / (double) window.getHeight();
        return new double[]{minecraft.mouseHandler.xpos() * scaleX, minecraft.mouseHandler.ypos() * scaleY};
    }

    private static Component controlComponent(String text, BubbleControlStyle.State state) {
        Style style = Style.EMPTY.withColor(TextColor.fromRgb(state.textColor() & 0x00FFFFFF))
                .withBold(state.bold()).withItalic(state.italic()).withUnderlined(state.underlined())
                .withStrikethrough(state.strikethrough()).withObfuscated(state.obfuscated());
        return Component.literal(text == null ? "" : text).withStyle(style);
    }

    private static PreparedTexture prepareNineSliceTexture(
            Identifier sourceTexture,
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
                        preparedImage.setPixel(x, y, sourceImage.getPixel(sourceX, sourceY));
                    }
                }
            }

            Identifier generatedId = Identifier.fromNamespaceAndPath(
                    "bubble_anyway", "generated/9slice/" + generatedTextureSequence++);
            DynamicTexture dynamicTexture = new DynamicTexture(() -> generatedId.toString(), preparedImage);
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

        Identifier id = Identifier.tryParse(spec.iconId());
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
        float fadeIn = fadeInProgress(spec, age);
        if (spec.duration() == -1) {
            return fadeIn;
        }
        double fadeOutStart = Math.max(0, spec.duration() - spec.fadeOut());
        float fadeOut = spec.fadeOut() <= 0 || age < fadeOutStart ? 1.0F : Math.min(1.0F, (float) ((spec.duration() - age) / spec.fadeOut()));
        return Math.max(0.0F, Math.min(1.0F, Math.min(fadeIn, fadeOut)));
    }

    private static float closingAlpha(BubbleSpec spec, double age, double closeAge) {
        float fadeIn = fadeInProgress(spec, age);
        float fadeOut = spec.fadeOut() <= 0
                ? 1.0F
                : Math.max(0.0F, 1.0F - Math.min(1.0F, (float) (closeAge / spec.fadeOut())));
        return Math.max(0.0F, Math.min(1.0F, fadeIn * fadeOut));
    }

    private static float fadeInProgress(BubbleSpec spec, double age) {
        return spec.fadeIn() <= 0 ? 1.0F : Math.min(1.0F, (float) (age / spec.fadeIn()));
    }

    private static float exitSlideProgress(BubbleSpec spec, double age) {
        if (spec.duration() == -1 || spec.slideOut() <= 0) {
            return 0.0F;
        }

        double slideOutStart = Math.max(0, spec.duration() - spec.slideOut());
        if (age <= slideOutStart) {
            return 0.0F;
        }
        return eased(Math.min(1.0F, (float) ((age - slideOutStart) / spec.slideOut())));
    }

    private static float closeSlideProgress(BubbleSpec spec, double closeAge) {
        if (spec.slideOut() <= 0) {
            return 0.0F;
        }
        return eased(Math.min(1.0F, (float) (closeAge / spec.slideOut())));
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

    private static final class ActiveBubble {
        private final BubbleSpec spec;
        private final long createdAt;
        private final long sequence;
        private final boolean serverSourced;
        private long closingAt = NOT_CLOSING;

        private ActiveBubble(BubbleSpec spec, long createdAt, long sequence, boolean serverSourced) {
            this.spec = spec;
            this.createdAt = createdAt;
            this.sequence = sequence;
            this.serverSourced = serverSourced;
        }

        private long sequence() {
            return sequence;
        }

        private double ageTicks(long now) {
            return (now - createdAt) / 50_000_000.0D;
        }

        private boolean isClosing() {
            return closingAt != NOT_CLOSING;
        }

        private void beginClosing(long now) {
            if (!isClosing()) {
                closingAt = now;
            }
        }

        private double closeAgeTicks(long now) {
            return isClosing() ? (now - closingAt) / 50_000_000.0D : 0.0D;
        }

        private int closeDuration() {
            return spec.animation() == BubbleSpec.Animation.FADE
                    ? spec.fadeOut()
                    : Math.max(spec.fadeOut(), spec.slideOut());
        }
    }

    private record QueuedBubble(BubbleSpec spec, long sequence, boolean serverSourced) {
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
            Identifier texture,
            TextureSize textureSize) {
        private static final ResolvedIcon EMPTY =
                new ResolvedIcon(BubbleSpec.IconType.NONE, ItemStack.EMPTY, null, null);

        private boolean isEmpty() {
            return type == BubbleSpec.IconType.NONE;
        }
    }

    private record GeneratedTextureKey(
            Identifier sourceTexture,
            TextureSize sourceSize,
            int border,
            int guide,
            int targetWidth,
            int targetHeight) {
    }

    private record PreparedTexture(Identifier texture, TextureSize size, int guide) {
    }

    private static final class BubbleLayout {
        private final List<TextLine> lines;
        private final int width;
        private final int height;
        private final int scaledWidth;
        private final int scaledHeight;
        private final int iconSize;
        private final int bodyHeight;
        private final int textStartX;
        private final int textAreaWidth;
        private final List<ControlPlacement> controls;

        private BubbleLayout(
                List<TextLine> lines,
                int width,
                int height,
                float scale,
                int iconSize,
                int bodyHeight,
                int textStartX,
                int textAreaWidth,
                List<ControlPlacement> controls) {
            this.lines = lines;
            this.width = width;
            this.height = height;
            this.scaledWidth = Math.round(width * scale);
            this.scaledHeight = Math.round(height * scale);
            this.iconSize = iconSize;
            this.bodyHeight = bodyHeight;
            this.textStartX = textStartX;
            this.textAreaWidth = textAreaWidth;
            this.controls = List.copyOf(controls);
        }

        private static BubbleLayout create(Font font, BubbleSpec spec, int screenWidth, boolean hasIcon) {
            int maxWidth = Math.min(spec.maxWidth(), Math.max(40, screenWidth - SCREEN_MARGIN * 2));
            int iconWidth = hasIcon ? spec.iconSize() + spec.iconGap() : 0;
            int contentWidth = spec.width() > 0
                    ? Math.max(1, spec.width() - spec.padding() * 2 - iconWidth)
                    : Math.max(1, maxWidth - spec.padding() * 2 - iconWidth);
            List<TextLine> lines = layoutText(font, spec, contentWidth);
            int measuredWidth = lines.stream().mapToInt(line -> line.width(font)).max().orElse(0);
            int controlsWidth = controlsWidth(font, spec, maxWidth);
            int width = spec.width() > 0
                    ? spec.width()
                    : Math.max(autoWidth(measuredWidth, spec.padding(), iconWidth, maxWidth, spec.scale()),
                    Math.min(maxWidth, controlsWidth + spec.padding() * 2));
            int lineSpacingHeight = Math.max(0, lines.size() - 1) * spec.lineSpacing();
            int bodyHeight = Math.max(Math.max(9, lines.stream().mapToInt(line -> line.height(font)).sum()
                    + lineSpacingHeight), hasIcon ? spec.iconSize() : 0);
            List<ControlPlacement> controls = layoutControls(font, spec, width, bodyHeight);
            int controlsHeight = controls.isEmpty() ? 0
                    : controls.stream().mapToInt(control -> control.y + control.height).max().orElse(0)
                    - (spec.padding() + bodyHeight);
            int minimumHeight = spec.padding() * 2 + bodyHeight + controlsHeight;
            int height = spec.height() > 0 ? Math.max(spec.height(), minimumHeight) : minimumHeight;
            int textStartX = spec.padding() + iconWidth;
            int textAreaWidth = Math.max(1, width - spec.padding() - textStartX);
            return new BubbleLayout(lines, width, height, spec.scale(), hasIcon ? spec.iconSize() : 0,
                    bodyHeight, textStartX, textAreaWidth, controls);
        }

        private static int controlsWidth(Font font, BubbleSpec spec, int maxWidth) {
            if (spec.controls().isEmpty()) return 0;
            int width = 0;
            if (spec.controls().layout() == BubbleControls.Layout.VERTICAL) {
                for (BubbleControl control : spec.controls().items()) {
                    width = Math.max(width, controlWidth(font, spec.controls(), control));
                }
                return width;
            }
            for (int index = 0; index < spec.controls().items().size(); index++) {
                if (index > 0) width += spec.controls().gap();
                width += controlWidth(font, spec.controls(), spec.controls().items().get(index));
            }
            return Math.min(maxWidth, width);
        }

        private static List<ControlPlacement> layoutControls(Font font, BubbleSpec spec, int width, int bodyHeight) {
            if (spec.controls().isEmpty()) return List.of();
            int available = Math.max(1, width - spec.padding() * 2);
            List<List<ControlSize>> rows = new ArrayList<>();
            List<ControlSize> row = new ArrayList<>();
            if (spec.controls().layout() == BubbleControls.Layout.VERTICAL) {
                for (BubbleControl control : spec.controls().items()) {
                    rows.add(List.of(new ControlSize(control, Math.min(available,
                            controlWidth(font, spec.controls(), control)), controlHeight(spec.controls(), control))));
                }
            } else {
                int rowWidth = 0;
                for (BubbleControl control : spec.controls().items()) {
                    int controlWidth = Math.min(available, controlWidth(font, spec.controls(), control));
                    int required = row.isEmpty() ? controlWidth : rowWidth + spec.controls().gap() + controlWidth;
                    if (!row.isEmpty() && required > available) {
                        rows.add(row);
                        row = new ArrayList<>();
                        rowWidth = 0;
                    }
                    row.add(new ControlSize(control, controlWidth, controlHeight(spec.controls(), control)));
                    rowWidth = row.size() == 1 ? controlWidth : rowWidth + spec.controls().gap() + controlWidth;
                }
                if (!row.isEmpty()) rows.add(row);
            }
            List<ControlPlacement> result = new ArrayList<>();
            int y = spec.padding() + bodyHeight + (rows.isEmpty() ? 0 : spec.controls().gap());
            for (List<ControlSize> controls : rows) {
                int rowWidth = controls.stream().mapToInt(ControlSize::width).sum()
                        + Math.max(0, controls.size() - 1) * spec.controls().gap();
                int x = switch (spec.controls().alignment()) {
                    case LEFT -> spec.padding();
                    case CENTER -> spec.padding() + Math.max(0, (available - rowWidth) / 2);
                    case RIGHT -> spec.padding() + Math.max(0, available - rowWidth);
                };
                int rowHeight = controls.stream().mapToInt(ControlSize::height).max().orElse(0);
                for (ControlSize control : controls) {
                    result.add(new ControlPlacement(control.control(), x, y, control.width(), control.height()));
                    x += control.width() + spec.controls().gap();
                }
                y += rowHeight + spec.controls().gap();
            }
            return result;
        }

        private static int controlWidth(Font font, BubbleControls controls, BubbleControl control) {
            BubbleControlStyle.State state = controls.style(control.style()).normal();
            int textWidth = Math.round(font.width(controlComponent(control.text(), state)) * state.scale());
            int automatic = Math.max(40, textWidth + state.padding() * 2);
            return control.width() > 0 ? control.width() : automatic;
        }

        private static int controlHeight(BubbleControls controls, BubbleControl control) {
            BubbleControlStyle.State state = controls.style(control.style()).normal();
            int automatic = Math.max(BubbleControl.DEFAULT_HEIGHT,
                    Math.round(9.0F * state.scale()) + state.padding() * 2);
            return control.height() > 0 ? control.height() : automatic;
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

    private record ControlSize(BubbleControl control, int width, int height) {
    }

    private record ControlPlacement(BubbleControl control, int x, int y, int width, int height) {
        private boolean contains(double localX, double localY) {
            return localX >= x && localX < x + width && localY >= y && localY < y + height;
        }
    }
}
