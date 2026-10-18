package com.adhdclient.basefinder.scan;

import com.adhdclient.basefinder.Lang;
import com.adhdclient.basefinder.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Actionbar-Meldungen bei NEUEN Funden, ohne Spam:
 *  - jeder Fund wird nur einmal gemeldet, bis er unter die Schwelle fällt (sein Chunk ist noch
 *    geladen, erfüllt sie aber nicht mehr) und wieder darüber steigt;
 *  - neue Funde werden ~1,5 s gesammelt; mehrere gleichzeitig ergeben eine Sammelmeldung;
 *  - Meldungen werden nacheinander gezeigt (je ~2 s), die Warteschlange ist begrenzt.
 */
public final class Notifier {

    private static final long BATCH_MS = 1500;
    private static final long SHOW_MS = 2000;
    private static final int MAX_QUEUE = 4;

    /** Bereits gemeldete Funde: Schlüssel -> Chunk des Fundes. */
    private final Map<Marker.Mode, Map<String, Long>> announced = new EnumMap<>(Marker.Mode.class);
    private final Map<Marker.Mode, List<Evaluator.Finding>> pending = new EnumMap<>(Marker.Mode.class);
    private final ArrayDeque<Text> queue = new ArrayDeque<>();
    private long batchStart = -1;
    private long nextShow;
    private boolean playSound;

    public Notifier() {
        for (Marker.Mode m : Marker.Mode.values()) {
            announced.put(m, new HashMap<>());
            pending.put(m, new ArrayList<>());
        }
    }

    public void reset() {
        for (Marker.Mode m : Marker.Mode.values()) {
            announced.get(m).clear();
            pending.get(m).clear();
        }
        queue.clear();
        batchStart = -1;
    }

    /** Nach jeder Auswertung: neue Funde erkennen, gefallene vergessen. */
    public void update(Evaluator.Results r, Set<Long> cachedChunks, ModConfig cfg, long now) {
        for (Marker.Mode mode : Marker.Mode.values()) {
            Map<String, Long> ann = announced.get(mode);
            Map<String, Evaluator.Finding> current = new HashMap<>();
            for (Evaluator.Finding f : r.qualifying.get(mode)) current.put(f.key(), f);
            // unter die Schwelle gefallen (Chunk noch im Cache) -> darf später erneut gemeldet werden
            ann.entrySet().removeIf(e -> !current.containsKey(e.getKey()) && cachedChunks.contains(e.getValue()));
            boolean notify = enabled(mode, cfg);
            for (Evaluator.Finding f : current.values()) {
                if (ann.putIfAbsent(f.key(), f.chunkKey()) == null && notify) {
                    pending.get(mode).add(f);
                    if (batchStart < 0) batchStart = now;
                }
            }
        }
    }

    private static boolean enabled(Marker.Mode mode, ModConfig cfg) {
        return switch (mode) {
            case AMETHYST -> cfg.amethyst.notify;
            case BEES -> cfg.bees.notify;
            case SPAWNERS -> cfg.spawners.notify;
            case KELP -> cfg.kelp.notify;
            case ANOMALY -> cfg.anomaly.notify;
        };
    }

    /** Sofortige Meldung (z. B. Zusammenfassung nach Vollscan, Umschalten per Taste). */
    public void showNow(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(text, false);
        nextShow = System.currentTimeMillis() + SHOW_MS;
    }

    public void tick(ModConfig cfg, long now) {
        if (batchStart >= 0 && now - batchStart >= BATCH_MS) {
            batchStart = -1;
            for (Marker.Mode mode : Marker.Mode.values()) {
                List<Evaluator.Finding> list = pending.get(mode);
                if (list.isEmpty()) continue;
                Text msg;
                if (list.size() == 1) {
                    msg = Text.literal(list.get(0).text());
                } else {
                    Evaluator.Finding best = list.get(0);
                    for (Evaluator.Finding f : list) if (f.value() > best.value()) best = f;
                    String detail = best.text().replaceFirst("^[^:]+: (neuer |new )?", "");
                    msg = Text.literal(mode.title() + ": " + list.size() + Lang.t(" neue Funde – bester: ", " new finds – best: ") + detail);
                }
                queue.add(msg.copy().formatted(Formatting.GOLD));
                list.clear();
                playSound = true;
            }
            while (queue.size() > MAX_QUEUE) queue.pollFirst();
        }
        if (!queue.isEmpty() && now >= nextShow) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(queue.pollFirst(), false);
            nextShow = now + SHOW_MS;
            if (playSound && cfg.general.notificationSound) {
                mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.6f));
            }
            playSound = false;
        }
    }
}
