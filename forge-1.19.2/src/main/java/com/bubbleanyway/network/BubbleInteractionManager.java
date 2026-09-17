package com.bubbleanyway.network;

import com.bubbleanyway.api.BubbleServerClickEvent;
import com.bubbleanyway.data.BubbleControl;
import com.bubbleanyway.data.BubbleSpec;
import com.bubbleanyway.kubejs.BubbleKubeJSServerApi;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/** Tracks server-side bubbles so client clicks can be validated. */
public final class BubbleInteractionManager {
    private static final Map<UUID, Map<String, Entry>> ACTIVE = new ConcurrentHashMap<>();

    private BubbleInteractionManager() {
    }

    public static void register(ServerPlayer player, BubbleSpec spec) {
        if (player == null || spec == null) return;
        Map<String, Entry> bubbles = ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());
        if (spec.remove()) bubbles.remove(spec.id());
        else bubbles.put(spec.id(), new Entry(spec, System.nanoTime()));
    }

    public static void clear(ServerPlayer player) {
        if (player != null) ACTIVE.remove(player.getUUID());
    }

    public static void handleClick(ServerPlayer player, String bubbleId, String controlId) {
        if (player == null || bubbleId == null || controlId == null) return;
        Map<String, Entry> bubbles = ACTIVE.get(player.getUUID());
        Entry entry = bubbles == null ? null : bubbles.get(bubbleId);
        if (entry == null || entry.expired()) {
            if (bubbles != null) bubbles.remove(bubbleId);
            return;
        }
        BubbleControl control = entry.spec.controls().find(controlId);
        if (control == null || !control.enabled()) return;
        BubbleServerClickEvent event = new BubbleServerClickEvent(player, entry.spec, control);
        boolean canceled = MinecraftForge.EVENT_BUS.post(event);
        BubbleKubeJSServerApi.dispatchClick(event);
        if (canceled || event.isCanceled()) return;
        if (control.closeOnPress()) {
            bubbles.remove(bubbleId);
            BubbleNetwork.send(java.util.List.of(player), BubbleSpec.remove(bubbleId));
        }
    }

    private record Entry(BubbleSpec spec, long createdAt) {
        private boolean expired() {
            return spec.duration() != -1
                    && (System.nanoTime() - createdAt) / 50_000_000L >= spec.duration();
        }
    }
}
