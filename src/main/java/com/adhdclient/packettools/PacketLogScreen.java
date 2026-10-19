package com.adhdclient.packettools;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Großes Paket-Log: alle gesendeten (→) und empfangenen (←) Pakete der eigenen Verbindung, mit Filter, Suche,
 * Pause, Leeren, Kopieren und einer Zählung nach Paket-Typ. Neue Pakete laufen unten ein, solange man ganz unten ist.
 */
public final class PacketLogScreen extends ThemedScreen {

    private static final int ROW = 18;
    private static final int LINE_H = 10;
    private static final int OUT_COLOR = 0xFF4ADE80;
    private static final int IN_COLOR = 0xFF60A5FA;

    private static boolean showOut = true;
    private static boolean showIn = true;
    private static boolean counts;
    private static String query = "";

    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
    private TextFieldWidget search;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private double scroll;
    private boolean follow = true;
    private List<String> visibleText = List.of();

    /** Eine Zeile der Zählung. */
    private record Count(boolean outgoing, String name, int count, long last) {
    }

    public PacketLogScreen(Screen parent) {
        super(Text.literal(L.t("Paket-Log", "Packet log")), parent);
    }

    @Override
    protected void build() {
        panel(4, 4, width - 4, height - 4);
        int x = 10;
        int y = 4 + Theme.TITLE_H + 6;
        int inner = width - 20;

        // Werkzeugleiste: Richtung, Ansicht, Suche, Pause, Leeren, Kopieren
        // erste Zeile: sechs gleich breite Knöpfe, zweite Zeile: Suche über die ganze Breite
        int bw = (inner - 5 * 4) / 6;
        int[] bx = new int[6];
        for (int i = 0; i < 6; i++) bx[i] = x + i * (bw + 4);
        int lastW = x + inner - bx[5];
        toggle(L.t("→ Gesendet", "→ Sent"), () -> showOut, v -> showOut = v, bx[0], y, bw, ROW - 2,
                L.t("Pakete, die du an den Server schickst", "Packets you send to the server"), null);
        toggle(L.t("← Empfangen", "← Received"), () -> showIn, v -> showIn = v, bx[1], y, bw, ROW - 2,
                L.t("Pakete, die der Server dir schickt", "Packets the server sends you"), null);
        ButtonWidget mode = ButtonWidget.builder(Text.literal(modeText()), b -> {
            counts = !counts;
            b.setMessage(Text.literal(modeText()));
            scroll = 0;
            follow = !counts;
        }).dimensions(bx[2], y, bw, ROW - 2).build();
        mode.setTooltip(Tooltip.of(Text.literal(L.t("Liste = jedes Paket einzeln, Zählung = wie oft jeder Typ kam",
                "List = every packet, Count = how often each type came"))));
        addDrawableChild(mode);
        toggle(L.t("Pause", "Pause"), () -> PacketHistory.paused, v -> PacketHistory.paused = v, bx[3], y, bw, ROW - 2,
                L.t("Neue Pakete nicht mehr aufnehmen", "Stop recording new packets"), null);
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Leeren", "Clear")), b -> {
            PacketHistory.clear();
            scroll = 0;
            follow = true;
        }).dimensions(bx[4], y, bw, ROW - 2).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Kopieren", "Copy")), b -> {
            if (client != null) client.keyboard.setClipboard(String.join("\n", visibleText));
            status(visibleText.size() + L.t(" Zeilen kopiert", " lines copied"));
        }).dimensions(bx[5], y, lastW, ROW - 2)
                .tooltip(Tooltip.of(Text.literal(L.t("Die angezeigten Zeilen in die Zwischenablage", "Copy the shown lines to the clipboard")))).build());
        y += ROW;

        search = new TextFieldWidget(textRenderer, x, y, inner, ROW - 2, Text.literal("Suche"));
        search.setPlaceholder(Text.literal(L.t("Suchen nach Name oder Inhalt (z. B. move, chat, container) …",
                "Search by name or content (e.g. move, chat, container) …")).withColor(0xFF808080));
        search.setMaxLength(64);
        search.setText(query);
        search.setChangedListener(s -> {
            query = s;
            scroll = 0;
        });
        addDrawableChild(search);

        listLeft = x;
        listRight = x + inner;
        listTop = y + ROW + 2;
        listBottom = height - 4 - 16;
        if (!PacketToolsClient.enabled) status(L.t("Packet Tools ist aus – im ADHD-Menü einschalten, dann wird aufgezeichnet",
                "Packet Tools is off – turn it on in the ADHD menu to record"));
    }

    private static String modeText() {
        return counts ? L.t("Zählung", "Count") : L.t("Liste", "List");
    }

    // ------------------------------------------------------------ Daten

    private List<PacketHistory.Entry> filtered() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<PacketHistory.Entry> out = new ArrayList<>();
        for (PacketHistory.Entry e : PacketHistory.snapshot()) {
            if (e.outgoing() ? !showOut : !showIn) continue;
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.detail().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(e);
        }
        return out;
    }

    private static List<Count> countsOf(List<PacketHistory.Entry> entries) {
        Map<String, int[]> n = new LinkedHashMap<>();
        Map<String, Long> last = new LinkedHashMap<>();
        for (PacketHistory.Entry e : entries) {
            String key = (e.outgoing() ? ">" : "<") + e.name();
            n.computeIfAbsent(key, k -> new int[1])[0]++;
            last.put(key, e.time());
        }
        List<Count> out = new ArrayList<>();
        for (Map.Entry<String, int[]> m : n.entrySet()) {
            out.add(new Count(m.getKey().charAt(0) == '>', m.getKey().substring(1), m.getValue()[0], last.get(m.getKey())));
        }
        out.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return out;
    }

    // ------------------------------------------------------------ Zeichnen

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        List<PacketHistory.Entry> entries = filtered();
        int rows;
        List<String> text = new ArrayList<>();
        String hover = null;
        int viewH = listBottom - listTop;
        ctx.fill(listLeft, listTop, listRight, listBottom, Theme.argb(120, 0x000000));

        if (counts) {
            List<Count> list = countsOf(entries);
            rows = list.size();
            clampScroll(rows, viewH);
            ctx.enableScissor(listLeft, listTop, listRight - 6, listBottom);
            int first = (int) (scroll / LINE_H);
            for (int i = 0; i < list.size(); i++) {
                Count c = list.get(i);
                String line = (c.outgoing() ? "→ " : "← ") + c.name() + "  ×" + c.count();
                text.add(line);
                int y = listTop + 2 + i * LINE_H - (int) scroll;
                if (i < first - 1 || y > listBottom) continue;
                int cx = listLeft + 4;
                ctx.drawTextWithShadow(textRenderer, c.outgoing() ? "→" : "←", cx, y, c.outgoing() ? OUT_COLOR : IN_COLOR);
                ctx.drawTextWithShadow(textRenderer, c.name(), cx + 12, y, Theme.text());
                String count = "×" + c.count();
                ctx.drawTextWithShadow(textRenderer, count, listRight - 12 - textRenderer.getWidth(count), y, Theme.accent());
            }
            ctx.disableScissor();
        } else {
            rows = entries.size();
            if (follow) scroll = Math.max(0, rows * LINE_H + 4 - viewH);
            clampScroll(rows, viewH);
            ctx.enableScissor(listLeft, listTop, listRight - 6, listBottom);
            int first = Math.max(0, (int) (scroll / LINE_H) - 1);
            for (int i = 0; i < entries.size(); i++) {
                PacketHistory.Entry e = entries.get(i);
                String time = clock.format(new Date(e.time()));
                text.add(time + "  " + (e.outgoing() ? "→ " : "← ") + e.name() + (e.detail().isEmpty() ? "" : "  " + e.detail()));
                if (i < first) continue;
                int y = listTop + 2 + i * LINE_H - (int) scroll;
                if (y > listBottom) continue;
                int cx = listLeft + 4;
                ctx.drawTextWithShadow(textRenderer, time, cx, y, Theme.textDim());
                cx += textRenderer.getWidth("00:00:00.000") + 6;
                ctx.drawTextWithShadow(textRenderer, e.outgoing() ? "→" : "←", cx, y, e.outgoing() ? OUT_COLOR : IN_COLOR);
                cx += 12;
                ctx.drawTextWithShadow(textRenderer, e.name(), cx, y, Theme.text());
                cx += textRenderer.getWidth(e.name()) + 8;
                if (!e.detail().isEmpty() && cx < listRight - 20) {
                    ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(e.detail(), listRight - 12 - cx), cx, y, Theme.textDim());
                }
                boolean hovered = mouseY >= y - 1 && mouseY < y + LINE_H - 1 && mouseX >= listLeft && mouseX < listRight - 6
                        && mouseY >= listTop && mouseY < listBottom;
                if (hovered && !e.detail().isEmpty()) hover = e.name() + " " + e.detail();
            }
            ctx.disableScissor();
        }
        visibleText = text;

        // Scrollbalken
        int content = rows * LINE_H + 4;
        if (content > viewH) {
            int barH = Math.max(14, viewH * viewH / content);
            int barY = listTop + (int) ((viewH - barH) * (scroll / Math.max(1, content - viewH)));
            ctx.fill(listRight - 4, listTop, listRight - 1, listBottom, Theme.argb(80, 0xFFFFFF));
            ctx.fill(listRight - 4, barY, listRight - 1, barY + barH, Theme.accent());
        }
        if (rows == 0) {
            String empty = PacketToolsClient.enabled ? L.t("Noch keine Pakete – spiel ein bisschen, dann tauchen sie hier auf", "No packets yet – play a bit and they show up here")
                    : L.t("Packet Tools ist aus", "Packet Tools is off");
            ctx.drawCenteredTextWithShadow(textRenderer, empty, (listLeft + listRight) / 2, listTop + viewH / 2 - 4, Theme.textDim());
        }
        String info = L.t("Gespeichert: ", "Stored: ") + PacketHistory.snapshot().size() + "/" + PacketHistory.MAX
                + L.t(" · gesamt ", " · total ") + PacketHistory.total() + L.t(" · angezeigt ", " · shown ") + rows
                + (PacketHistory.paused ? L.t(" · PAUSIERT", " · PAUSED") : "");
        ctx.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(info, listRight - listLeft), listLeft, listBottom + 3, Theme.textDim());
        if (hover != null) {
            ctx.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(Text.literal(hover), Math.max(200, width / 2)), mouseX, mouseY);
        }
    }

    private void clampScroll(int rows, int viewH) {
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, rows * LINE_H + 4 - viewH));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= listTop && mouseY <= listBottom) {
            scroll -= vertical * LINE_H * 3;
            if (scroll < 0) scroll = 0;
            int rows = counts ? countsOf(filtered()).size() : filtered().size();
            int max = Math.max(0, rows * LINE_H + 4 - (listBottom - listTop));
            follow = !counts && scroll >= max - 1;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
}
