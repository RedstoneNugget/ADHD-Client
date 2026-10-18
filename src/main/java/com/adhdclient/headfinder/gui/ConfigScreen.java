package com.adhdclient.headfinder.gui;

import com.adhdclient.basefinder.scan.Patterns;
import com.adhdclient.headfinder.HeadFinderClient;
import com.adhdclient.headfinder.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Einfacher Einstellungs-Bildschirm (auch über Mod Menu erreichbar). Jede Änderung wirkt sofort
 * und wird gebündelt in config/head_finder/config.json gespeichert.
 */
public class ConfigScreen extends ScrollScreen {

    private static final int ROW = 24;

    private record Swatch(int x, int baseY, Supplier<String> color) {
    }

    private final List<Swatch> swatches = new ArrayList<>();
    private int left;
    private int contentW;
    private int colW;
    private int cursorY;
    private int col;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Block Finder – Einstellungen"), parent);
    }

    private static ModConfig cfg() {
        return HeadFinderClient.config();
    }

    private static void changed() {
        HeadFinderClient.onConfigEdited();
    }

    @Override
    protected int viewTop() {
        return 26;
    }

    @Override
    protected int contentRight() {
        return left + contentW;
    }

    @Override
    protected void init() {
        clearPlaced();
        swatches.clear();
        contentW = Math.min(440, width - 24);
        left = (width - contentW) / 2;
        colW = (contentW - 8) / 2;
        cursorY = 0;
        col = 0;

        ModConfig c = cfg();
        header("Anzeige");
        toggle("Markierungen", () -> c.espEnabled, v -> c.espEnabled = v, "Farbige Box um jeden Kopf, auch hinter Blöcken sichtbar");
        toggle("Labels mit Entfernung", () -> c.labelsEnabled, v -> c.labelsEnabled = v, null);
        cycleTracer();
        toggle("HUD oben", () -> c.hudEnabled, v -> c.hudEnabled = v, "Zähler, Pfeil und Entfernung zum nächsten Kopf");
        toggle("Gefundene ausblenden", () -> c.hideFound, v -> c.hideFound = v, "An: gefundene Köpfe werden nicht mehr markiert. Aus: sie werden grün markiert.");
        toggle("Meldung bei neuem Kopf", () -> c.notifyNewHeads, v -> c.notifyNewHeads = v, null);
        slider("Reichweite", 16, 1024, 16, () -> c.maxRange, v -> c.maxRange = (int) Math.round(v), v -> (int) Math.round(v) + " m",
                "Maximale Entfernung für Boxen und Labels. Tracer/Pfeil zum Ziel gelten unabhängig davon.");
        slider("Label-Reichweite", 8, 512, 8, () -> c.labelRange, v -> c.labelRange = (int) Math.round(v), v -> (int) Math.round(v) + " m",
                "Labels nur bis zu dieser Entfernung – das Ziel bzw. der nächste Kopf hat immer eins");
        slider("Label-Größe", 0.5, 3.0, 0.1, () -> c.labelScale, v -> c.labelScale = v, v -> String.format(Locale.ROOT, "%.1f", v), null);
        slider("HUD-Abstand oben", 0, 150, 1, () -> c.hudY, v -> c.hudY = (int) Math.round(v), v -> (int) Math.round(v) + " px",
                "Erhöhen, falls Bossleisten das HUD verdecken");
        newline();
        colorField("Offen", () -> c.colorOpen, v -> c.colorOpen = v);
        colorField("Gefunden", () -> c.colorFound, v -> c.colorFound = v);
        colorField("Ziel", () -> c.colorTarget, v -> c.colorTarget = v);

        header("Erkennung");
        toggle("Rechtsklick = gefunden", () -> c.foundOnRightClick, v -> c.foundOnRightClick = v, "Rechtsklick auf einen Kopf markiert ihn als gefunden");
        toggle("Linksklick = gefunden", () -> c.foundOnLeftClick, v -> c.foundOnLeftClick = v, null);
        toggle("Verschwunden = gefunden", () -> c.foundOnDisappear, v -> c.foundOnDisappear = v,
                "Ist ein bekannter Kopf-Block im geladenen Chunk weg, gilt er als eingesammelt");
        toggle("Items & Rahmen erkennen", () -> c.detectItems, v -> c.detectItems = v, "Ziel-Blöcke als Item am Boden und in Item-Frames");
        slider("Scan-Intervall", 5, 100, 1, () -> c.rescanIntervalTicks, v -> c.rescanIntervalTicks = (int) Math.round(v),
                v -> String.format(Locale.ROOT, "%.2f s", v / 20.0), "Wie oft die geladenen Chunks erneut geprüft werden");

        header("Suchprofile (welche Blöcke gesucht werden)");
        for (int i = 0; i < c.profiles.size(); i++) profileBlock(i);
        newline();
        place(ButtonWidget.builder(Text.literal("+ Profil hinzufügen"), b -> {
            cfg().profiles.add(new ModConfig.SearchProfile());
            changed();
            rebuildKeepScroll();
        }).dimensions(left, 0, colW, 20).build(), cursorY);
        place(ButtonWidget.builder(Text.literal("Standard wiederherstellen"), b -> {
            cfg().profiles = ModConfig.defaultProfiles();
            changed();
            rebuildKeepScroll();
        }).dimensions(left + colW + 8, 0, colW, 20).build(), cursorY);
        cursorY += ROW;
        contentHeight = cursorY + 6;

        int by = height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Config neu laden"), b -> {
            HeadFinderClient.reloadConfig();
            clearAndInit();
        }).dimensions(width / 2 - 154, by, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> close())
                .dimensions(width / 2 + 4, by, 150, 20).build());

        clampScroll();
        relayout();
    }

    @Override
    public void close() {
        HeadFinderClient.saveConfigNow();
        super.close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFE066);
        int top = viewTop();
        int bottom = viewBottom();
        for (Swatch s : swatches) {
            int y = top + s.baseY - (int) scroll;
            if (y < top || y + 14 > bottom) continue;
            int color = ModConfig.rgb(s.color.get(), 0x555555);
            ctx.fill(s.x - 1, y - 1, s.x + 15, y + 15, 0xFF000000);
            ctx.fill(s.x, y, s.x + 14, y + 14, 0xFF000000 | color);
        }
    }

    // ---------------------------------------------------------------- Bausteine

    private void newline() {
        if (col != 0) {
            col = 0;
            cursorY += ROW;
        }
    }

    private void advance() {
        if (++col >= 2) {
            col = 0;
            cursorY += ROW;
        }
    }

    private int cellX() {
        return left + col * (colW + 8);
    }

    private void header(String text) {
        newline();
        if (cursorY > 0) cursorY += 4;
        labels.add(new Label("— " + text + " —", left + contentW / 2, cursorY + 2, 0xFFFFE066, true));
        cursorY += 14;
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String tooltip) {
        ButtonWidget b = ButtonWidget.builder(toggleText(label, get.getAsBoolean()), w -> {
            set.accept(!get.getAsBoolean());
            w.setMessage(toggleText(label, get.getAsBoolean()));
            changed();
        }).dimensions(cellX(), 0, colW, 20).build();
        if (tooltip != null) b.setTooltip(Tooltip.of(Text.literal(tooltip)));
        place(b, cursorY);
        advance();
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": ").append(on ? Text.literal("An").formatted(Formatting.GREEN) : Text.literal("Aus").formatted(Formatting.RED));
    }

    private void cycleTracer() {
        ButtonWidget b = ButtonWidget.builder(tracerText(), w -> {
            cfg().tracerMode = cfg().tracerMode.next();
            w.setMessage(tracerText());
            changed();
        }).dimensions(cellX(), 0, colW, 20).build();
        b.setTooltip(Tooltip.of(Text.literal("Linie vom Fadenkreuz: aus, nur zum nächsten (bzw. gewählten) Kopf oder zu allen")));
        place(b, cursorY);
        advance();
    }

    private static Text tracerText() {
        ModConfig.TracerMode m = cfg().tracerMode;
        return Text.literal("Tracer: ").append(Text.literal(m.title).formatted(m == ModConfig.TracerMode.OFF ? Formatting.RED : Formatting.GREEN));
    }

    private void slider(String label, double min, double max, double step, DoubleSupplier get, DoubleConsumer set,
                        java.util.function.DoubleFunction<String> fmt, String tooltip) {
        ValueSlider s = new ValueSlider(cellX(), 0, colW, 20, label, min, max, step, get.getAsDouble(), fmt, v -> {
            set.accept(v);
            changed();
        });
        if (tooltip != null) s.setTooltip(Tooltip.of(Text.literal(tooltip)));
        place(s, cursorY);
        advance();
    }

    private void colorField(String label, Supplier<String> get, Consumer<String> set) {
        int x = cellX();
        labels.add(new Label(label, x, cursorY + 6, 0xFFE0E0E0, false));
        int fieldX = x + 60;
        int fieldW = colW - 60 - 22;
        TextFieldWidget tf = new TextFieldWidget(textRenderer, fieldX, 0, fieldW, 20, Text.literal(label));
        tf.setMaxLength(7);
        tf.setText(get.get());
        tf.setChangedListener(s -> {
            boolean ok = ModConfig.isValidColor(s);
            tf.setEditableColor(ok ? 0xFFE0E0E0 : 0xFFFF5555);
            if (ok) {
                String v = s.trim().toUpperCase(Locale.ROOT);
                set.accept(v.startsWith("#") ? v : "#" + v);
                changed();
            }
        });
        tf.setTooltip(Tooltip.of(Text.literal("Farbe als #RRGGBB")));
        place(tf, cursorY);
        swatches.add(new Swatch(fieldX + fieldW + 6, cursorY + 3, get));
        advance();
    }

    /** Volle Breite: Beschriftung links, Textfeld rechts. */
    private TextFieldWidget field(String label, String value, int maxLen, Consumer<String> onChange, String tooltip) {
        newline();
        int labelW = 70;
        labels.add(new Label(label, left + 8, cursorY + 6, 0xFFE0E0E0, false));
        TextFieldWidget tf = new TextFieldWidget(textRenderer, left + labelW, 0, contentW - labelW, 20, Text.literal(label));
        tf.setMaxLength(maxLen);
        tf.setText(value);
        tf.setCursorToStart(false);
        tf.setChangedListener(onChange);
        if (tooltip != null) tf.setTooltip(Tooltip.of(Text.literal(tooltip)));
        place(tf, cursorY);
        cursorY += ROW;
        return tf;
    }

    private void profileBlock(int index) {
        ModConfig.SearchProfile p = cfg().profiles.get(index);
        newline();
        labels.add(new Label("Profil " + (index + 1), left, cursorY + 6, 0xFFFFFFFF, false));
        int bw = 90;
        ButtonWidget onOff = ButtonWidget.builder(toggleText("Aktiv", p.enabled), w -> {
            p.enabled = !p.enabled;
            w.setMessage(toggleText("Aktiv", p.enabled));
            changed();
        }).dimensions(left + contentW - 2 * bw - 4, 0, bw, 20).build();
        place(onOff, cursorY);
        ButtonWidget remove = ButtonWidget.builder(Text.literal("Entfernen").formatted(Formatting.RED), w -> {
            cfg().profiles.remove(p);
            changed();
            rebuildKeepScroll();
        }).dimensions(left + contentW - bw, 0, bw, 20).build();
        place(remove, cursorY);
        cursorY += ROW;

        field("Name", p.name, 64, s -> {
            p.name = s;
            changed();
        }, "Anzeigename (leer = Muster selbst wird angezeigt)");
        TextFieldWidget pattern = field("Block", p.pattern, 256, null,
                "Block-ID (minecraft:diamond_ore), Platzhalter (*diamond_ore) oder Tag (#minecraft:logs)");
        pattern.setChangedListener(s -> {
            p.pattern = s;
            pattern.setEditableColor(s.isBlank() || Patterns.matchesSomething(s) ? 0xFFE0E0E0 : 0xFFFF5555);
            changed();
        });
        cursorY += 4;
    }
}
