package com.adhdclient.packettools;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.network.packet.Packet;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Paket-Log oben rechts: zeigt, welche Pakete gerade an den Server gehen. Gleiche Pakete direkt hintereinander
 * werden zusammengefasst ("move_player_pos ×12"); Einträge verschwinden nach ein paar Sekunden.
 */
public final class PacketLog implements HudElement {

    private static final int MAX = 10;
    private static final long SHOW_MS = 6000;

    private static final class Line {
        final String name;
        int count = 1;
        long time;

        Line(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }

    private static final Deque<Line> LINES = new ArrayDeque<>();
    private static int total;

    /** Aus dem Mixin: ein Paket wurde wirklich gesendet. */
    public static void record(Packet<?> packet) {
        if (!PacketToolsClient.enabled || !PacketToolsClient.config().showLog) return;
        String name;
        try {
            name = packet.getPacketType().id().getPath();
        } catch (RuntimeException e) {
            name = packet.getClass().getSimpleName();
        }
        long now = System.currentTimeMillis();
        synchronized (LINES) {
            total++;
            Line first = LINES.peekFirst();
            if (first != null && first.name.equals(name)) {
                first.count++;
                first.time = now;
                return;
            }
            LINES.addFirst(new Line(name, now));
            while (LINES.size() > MAX) LINES.removeLast();
        }
    }

    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!PacketToolsClient.enabled || !PacketToolsClient.config().showLog || mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        long now = System.currentTimeMillis();
        int w = 150;
        int x = ctx.getScaledWindowWidth() - w - 4;
        int y = 4;
        String title = L.t("Gesendet", "Sent") + " · " + total + (PacketToolsClient.holdAll ? L.t(" · angehalten ", " · on hold ") + PacketToolsClient.heldCount() : "");
        synchronized (LINES) {
            int shown = 0;
            for (Line l : LINES) if (now - l.time < SHOW_MS) shown++;
            ctx.fill(x, y, x + w, y + 13 + shown * 10 + 2, Theme.argb(Math.round(Theme.config().opacity * 2.55f), Theme.config().background));
            ctx.fill(x, y, x + 2, y + 13 + shown * 10 + 2, Theme.accent());
            ctx.drawTextWithShadow(tr, tr.trimToWidth(title, w - 8), x + 5, y + 3, Theme.accent());
            int ly = y + 14;
            for (Line l : LINES) {
                if (now - l.time >= SHOW_MS) continue;
                String text = "→ " + l.name + (l.count > 1 ? " ×" + l.count : "");
                ctx.drawTextWithShadow(tr, tr.trimToWidth(text, w - 8), x + 5, ly, now - l.time < 300 ? Theme.text() : Theme.textDim());
                ly += 10;
            }
        }
    }
}
