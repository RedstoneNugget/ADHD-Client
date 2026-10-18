package com.adhdclient.inspect.ui;

import com.adhdclient.inspect.InspectClient;
import com.adhdclient.inspect.config.InspectConfig;
import com.adhdclient.inspect.data.Category;
import com.adhdclient.inspect.data.Entry;
import com.adhdclient.inspect.data.InspectData;
import com.adhdclient.inspect.data.Role;
import com.adhdclient.inspect.data.Section;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

/**
 * Kompaktes Overlay für das gesperrte Ziel: bleibt sichtbar, während man
 * spielt (Taste G). Zeigt die wichtigsten Werte und wird regelmäßig aktualisiert.
 */
public final class LockedHud implements HudElement {

    private static final int MAX_LINES = 12;
    private static final Category[] BLOCK_ORDER = {Category.SUMMARY, Category.BLOCKSTATE, Category.ENVIRONMENT};
    private static final Category[] ITEM_ORDER = {Category.SUMMARY, Category.ITEM_PROPERTIES};
    private static final Category[] ENTITY_ORDER = {Category.ENTITY_STATS, Category.EFFECTS, Category.SUMMARY, Category.GENERAL};
    /** Im kleinen Overlay uninteressant (steht schon im Kopf oder ist statisch). */
    private static final java.util.Set<String> SKIP = java.util.Set.of(
            "Name", "Registry-ID", "Typ", "Type", "Namespace / Mod", "Übersetzungsschlüssel", "Translation key",
            "Datenquelle", "Data source", "Dimension", "UUID", "Netzwerk-ID", "Network ID", "Biom", "Biome",
            "Weltzeit / Tageszeit", "World time / time of day", "Pose", "Team", "Fahrzeug", "Vehicle", "Baby",
            "Lautlos / Keine Schwerkraft", "Silent / no gravity", "Leuchtet / Unsichtbar", "Glowing / invisible",
            "Unverwundbar", "Unverwundbar (Invulnerable)", "Invulnerable");

    private record Line(String key, String value, Role role) {
    }

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        InspectConfig cfg = InspectConfig.get();
        if (!InspectClient.hasLock() || !cfg.hudWhenLocked || client.options.hudHidden
                || client.currentScreen instanceof InspectScreen) {
            return;
        }
        TextRenderer tr = client.textRenderer;
        InspectData data = InspectClient.lockedData();
        int w = Math.min(240, context.getScaledWindowWidth() / 3 + 40);
        int x = cfg.panelPosition == InspectConfig.PanelPosition.RIGHT ? context.getScaledWindowWidth() - w - 6 : 6;
        int y = 6;

        List<Line> lines = new ArrayList<>();
        if (data != null) {
            collect(data, lines);
        }
        int h = 30 + Math.max(1, lines.size()) * 10 + 12;
        int accent = cfg.accent();
        context.fill(x, y, x + w, y + h, cfg.background());
        context.fill(x, y, x + 2, y + h, accent);
        com.adhdclient.gui.Theme.border(context, x, y, x + w, y + h, com.adhdclient.gui.Theme.argb(255, com.adhdclient.gui.Theme.mix(com.adhdclient.gui.Theme.config().accent, com.adhdclient.gui.Theme.config().background, 0.5f)));

        if (data == null) {
            context.drawText(tr, com.adhdclient.gui.L.t("🔒 Gesperrtes Ziel nicht verfügbar", "🔒 Locked target not available"), x + 6, y + 8, cfg.color(Role.WARN), false);
        } else {
            if (!data.icon.isEmpty()) {
                context.drawItem(data.icon, x + 5, y + 5);
            }
            context.drawTextWithShadow(tr, tr.trimToWidth("🔒 " + data.name.getString(), w - 30), x + 25, y + 5, accent);
            context.drawText(tr, tr.trimToWidth(data.registryId + (data.positionText != null ? "  @ " + data.positionText : ""), w - 30),
                    x + 25, y + 16, cfg.color(Role.DIM), false);
            int ly = y + 30;
            for (Line line : lines) {
                int lx = x + 6;
                if (!line.key.isEmpty()) {
                    String k = line.key + (line.value.isEmpty() ? "" : ": ");
                    context.drawText(tr, tr.trimToWidth(k, w / 2), lx, ly, cfg.color(Role.KEY), false);
                    lx += Math.min(tr.getWidth(k), w / 2);
                }
                context.drawText(tr, tr.trimToWidth(line.value, x + w - 6 - lx), lx, ly, cfg.color(line.role), false);
                ly += 10;
            }
        }
        String hint = com.adhdclient.gui.HotKey.name(cfg.lockKey) + com.adhdclient.gui.L.t(": entsperren · ", ": unlock · ")
                + com.adhdclient.gui.HotKey.name(cfg.openKey) + ": Panel";
        context.drawText(tr, tr.trimToWidth(hint, w - 12), x + 6, y + h - 11, cfg.color(Role.DIM), false);
    }

    private static void collect(InspectData data, List<Line> out) {
        InspectConfig cfg = InspectConfig.get();
        Category[] order = switch (data.kind) {
            case BLOCK -> BLOCK_ORDER;
            case ITEM -> ITEM_ORDER;
            case ENTITY -> ENTITY_ORDER;
        };
        for (Category cat : order) {
            if (!cfg.isVisible(cat)) {
                continue;
            }
            for (Section s : data.sections) {
                if (s.category != cat) {
                    continue;
                }
                for (Entry e : s.entries) {
                    if (out.size() >= MAX_LINES) {
                        return;
                    }
                    switch (e) {
                        case Entry.Kv kv -> {
                            if (!kv.value().isEmpty() && !kv.key().startsWith("#") && !SKIP.contains(kv.key())) {
                                out.add(new Line(kv.key(), kv.value(), kv.role()));
                            }
                        }
                        case Entry.Sub sub -> out.add(new Line("", "› " + sub.title(), Role.ACCENT));
                        case Entry.Note note -> {
                            if (note.role() == Role.WARN) {
                                out.add(new Line("", note.text(), Role.WARN));
                            }
                        }
                        case Entry.Snbt ignored -> {
                        }
                    }
                }
            }
        }
    }
}
