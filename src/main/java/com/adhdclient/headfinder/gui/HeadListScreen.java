package com.adhdclient.headfinder.gui;

import com.adhdclient.headfinder.HeadFinderClient;
import com.adhdclient.headfinder.data.HeadEntry;
import com.adhdclient.headfinder.data.HeadStore;
import com.adhdclient.headfinder.scan.HeadScanner;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Liste aller entdeckten Köpfe (Taste K): Koordinaten, Entfernung, Status.
 * Klick auf einen Eintrag setzt ihn als Ziel (Tracer und Pfeil zeigen dann nur dorthin).
 */
public class HeadListScreen extends ScrollScreen {

    private enum Sort {
        NEAR("Entfernung ↑"), FAR("Entfernung ↓"), STATUS("Offene zuerst");

        final String title;

        Sort(String title) {
            this.title = title;
        }
    }

    private enum Filter {
        ALL("Alle"), OPEN("Offen"), FOUND("Gefunden");

        final String title;

        Filter(String title) {
            this.title = title;
        }
    }

    private static final int ROW = 22;
    private static Sort sort = Sort.NEAR;
    private static Filter filter = Filter.ALL;

    private record Row(ButtonWidget main, HeadEntry entry) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int left;
    private int contentW;
    private long resetArmedUntil;
    private ButtonWidget resetButton;
    private int tickCount;

    public HeadListScreen(Screen parent) {
        super(Text.literal("Head Finder – Köpfe"), parent);
    }

    @Override
    protected int viewTop() {
        return 66;
    }

    @Override
    protected int contentRight() {
        return left + contentW;
    }

    @Override
    protected void init() {
        clearPlaced();
        rows.clear();
        contentW = Math.min(480, width - 24);
        left = (width - contentW) / 2;

        // Werkzeugleiste
        int bw = (contentW - 12) / 4;
        int ty = 38;
        addDrawableChild(ButtonWidget.builder(Text.literal("⇅ " + sort.title), b -> {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            scroll = 0;
            clearAndInit();
        }).dimensions(left, ty, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Filter: " + filter.title), b -> {
            filter = Filter.values()[(filter.ordinal() + 1) % Filter.values().length];
            scroll = 0;
            clearAndInit();
        }).dimensions(left + bw + 4, ty, bw, 20).build());
        ButtonWidget clear = ButtonWidget.builder(Text.literal("Ziel aufheben"), b -> {
            store().setTarget(null);
            rebuildKeepScroll();
        }).dimensions(left + 2 * (bw + 4), ty, bw, 20).build();
        clear.active = store().targetKey() != null;
        clear.setTooltip(Tooltip.of(Text.literal("Tracer und Pfeil zeigen wieder automatisch zum nächsten offenen Kopf.")));
        addDrawableChild(clear);
        resetButton = ButtonWidget.builder(Text.literal("Reset gefunden"), b -> {
            if (System.currentTimeMillis() < resetArmedUntil) {
                int n = store().resetFound();
                resetArmedUntil = 0;
                rebuildKeepScroll();
                resetButton.setMessage(Text.literal(n + " zurückgesetzt").formatted(Formatting.GREEN));
            } else {
                resetArmedUntil = System.currentTimeMillis() + 3000;
                b.setMessage(Text.literal("Wirklich? Nochmal!").formatted(Formatting.RED));
            }
        }).dimensions(left + 3 * (bw + 4), ty, contentW - 3 * (bw + 4), 20).build();
        resetButton.setTooltip(Tooltip.of(Text.literal("Alle Köpfe wieder als NICHT gefunden markieren – sie werden wieder angezeigt. Die Positionen bleiben gespeichert.")));
        addDrawableChild(resetButton);

        // Einträge
        List<HeadEntry> list = sortedEntries();
        int copyW = 62;
        int toggleW = 66;
        int mainW = contentW - copyW - toggleW - 8;
        int y = 0;
        for (HeadEntry e : list) {
            ButtonWidget main = ButtonWidget.builder(rowText(e), b -> {
                store().setTarget(e);
                rebuildKeepScroll();
            }).dimensions(left, 0, mainW, 20).build();
            main.active = !e.found && sameDim(e);
            main.setTooltip(Tooltip.of(Text.literal(e.found
                    ? "Gefunden (" + e.foundReason + ")"
                    : sameDim(e) ? "Klicken: als Ziel setzen" : "Andere Dimension: " + e.dimShort())));
            place(main, y);
            rows.add(new Row(main, e));

            ButtonWidget copy = ButtonWidget.builder(Text.literal("Kopieren"), b -> {
                if (client != null) client.keyboard.setClipboard(e.coords());
                b.setMessage(Text.literal("Kopiert!").formatted(Formatting.GREEN));
            }).dimensions(left + mainW + 4, 0, copyW, 20).build();
            copy.setTooltip(Tooltip.of(Text.literal("Koordinaten \"" + e.coords() + "\" in die Zwischenablage kopieren")));
            place(copy, y);

            if (e.kind != HeadEntry.Kind.ITEM) {
                ButtonWidget toggle = ButtonWidget.builder(Text.literal(e.found ? "Offen" : "Gefunden"), b -> {
                    store().setFound(e, !e.found, "manuell");
                    rebuildKeepScroll();
                }).dimensions(left + mainW + copyW + 8, 0, toggleW, 20).build();
                toggle.setTooltip(Tooltip.of(Text.literal(e.found ? "Wieder als offen markieren" : "Von Hand als gefunden markieren")));
                place(toggle, y);
            }
            y += ROW;
        }
        if (list.isEmpty()) {
            String msg = store().countDiscovered() == 0
                    ? "Noch keine Köpfe entdeckt – lauf in die Nähe des Spawns."
                    : "Keine Köpfe für diesen Filter.";
            labels.add(new Label(msg, width / 2, 8, 0xFFAAAAAA, true));
            y = 24;
        }
        contentHeight = y;

        // Fußzeile
        int by = height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Einstellungen"), b -> {
            if (client != null) client.setScreen(new ConfigScreen(this));
        }).dimensions(width / 2 - 154, by, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> close())
                .dimensions(width / 2 + 4, by, 150, 20).build());

        clampScroll();
        relayout();
    }

    private static HeadStore store() {
        return HeadFinderClient.store();
    }

    private String dim() {
        return client != null && client.world != null ? HeadScanner.dimension(client.world) : "";
    }

    private boolean sameDim(HeadEntry e) {
        return dim().equals(e.dim);
    }

    private Vec3d eye() {
        return client != null && client.player != null ? client.player.getEyePos() : Vec3d.ZERO;
    }

    private List<HeadEntry> sortedEntries() {
        List<HeadEntry> list = new ArrayList<>();
        for (HeadEntry e : store().all()) {
            if (filter == Filter.OPEN && e.found) continue;
            if (filter == Filter.FOUND && !e.found) continue;
            list.add(e);
        }
        String dim = dim();
        Vec3d eye = eye();
        Comparator<HeadEntry> byDim = Comparator.comparingInt(e -> dim.equals(e.dim) ? 0 : 1);
        Comparator<HeadEntry> byDist = Comparator.comparingDouble(e -> e.center().squaredDistanceTo(eye));
        Comparator<HeadEntry> cmp = switch (sort) {
            case NEAR -> byDim.thenComparing(byDist);
            case FAR -> byDim.thenComparing(byDist.reversed());
            case STATUS -> byDim.thenComparing(e -> e.found).thenComparing(byDist);
        };
        list.sort(cmp);
        return list;
    }

    private Text rowText(HeadEntry e) {
        boolean target = e.key().equals(store().targetKey());
        MutableText t = Text.empty();
        if (target) t.append(Text.literal("▶ ").formatted(Formatting.GOLD));
        t.append(Text.literal(e.found ? "✔ " : "● ").formatted(e.found ? Formatting.GREEN : Formatting.RED));
        t.append(Text.literal(e.coords()).formatted(Formatting.WHITE));
        String dist = sameDim(e) ? Math.round(e.center().distanceTo(eye())) + " m" : e.dimShort();
        t.append(Text.literal("  " + dist).formatted(Formatting.AQUA));
        t.append(Text.literal(e.found ? "  gefunden" : "  offen").formatted(e.found ? Formatting.GREEN : Formatting.GRAY));
        if (e.kind != HeadEntry.Kind.BLOCK) t.append(Text.literal(" (" + e.kind.title + ")").formatted(Formatting.DARK_GRAY));
        return t;
    }

    @Override
    public void tick() {
        // Entfernungen live aktualisieren (Reihenfolge bleibt bis zum nächsten Neuaufbau stabil)
        if (++tickCount % 10 == 0) {
            for (Row r : rows) r.main.setMessage(rowText(r.entry));
        }
        if (resetArmedUntil > 0 && System.currentTimeMillis() > resetArmedUntil) {
            resetArmedUntil = 0;
            resetButton.setMessage(Text.literal("Reset gefunden"));
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFE066);
        HeadStore s = store();
        ctx.drawCenteredTextWithShadow(textRenderer, "Köpfe: " + s.countFound() + " gefunden / " + s.countDiscovered() + " entdeckt",
                width / 2, 22, 0xFFB0B8C8);
        ctx.drawCenteredTextWithShadow(textRenderer, "Nur Köpfe in geladenen Chunks werden erkannt – gespeicherte bleiben in der Liste.",
                width / 2, height - 40, 0xFF8890A0);
    }

    @Override
    protected int viewBottom() {
        return height - 44;
    }
}
