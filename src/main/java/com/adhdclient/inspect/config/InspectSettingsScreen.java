package com.adhdclient.inspect.config;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import com.adhdclient.inspect.InspectClient;
import com.adhdclient.inspect.data.Category;
import com.adhdclient.inspect.world.WorldMarkers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Einstellungen von Inspect All: Tasten ganz oben, darunter drei Reiter (Allgemein, Kategorien, Farben). */
public final class InspectSettingsScreen extends ThemedScreen {

    private enum Tab {
        GENERAL("Allgemein", "General"), CATEGORIES("Kategorien", "Categories"), COLORS("Farben", "Colors");

        final String de;
        final String en;

        Tab(String de, String en) {
            this.de = de;
            this.en = en;
        }
    }

    private static final int W = 320;
    private static final int ROW = 20;
    private static final int CONTENT_ROWS = 5;
    private static Tab tab = Tab.GENERAL;

    private record Swatch(int x, int y, Supplier<String> color) {
    }

    private final List<Swatch> swatches = new ArrayList<>();

    public InspectSettingsScreen(Screen parent) {
        super(Text.literal("Inspect All"), parent);
    }

    @Override
    protected void build() {
        swatches.clear();
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + ROW + 6 + CONTENT_ROWS * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        InspectConfig c = InspectConfig.get();

        section(L.t("Tasten", "Keys"), x, y, inner);
        y += 12;
        int third = (inner - 8) / 3;
        keyButton("Panel", () -> c.openKey, v -> c.openKey = v, new InspectConfig().openKey, x, y, third, ROW - 2, InspectConfig::save);
        keyButton(L.t("Sperren", "Lock"), () -> c.lockKey, v -> c.lockKey = v, new InspectConfig().lockKey, x + third + 4, y, third, ROW - 2, InspectConfig::save);
        keyButton(L.t("Löschen", "Clear"), () -> c.clearKey, v -> c.clearKey = v, new InspectConfig().clearKey, x + 2 * (third + 4), y, inner - 2 * (third + 4), ROW - 2, InspectConfig::save);
        y += ROW + 6;

        // Reiter
        for (Tab t : Tab.values()) {
            int tx = x + t.ordinal() * (third + 4);
            int tw = t.ordinal() == 2 ? inner - 2 * (third + 4) : third;
            ButtonWidget b = ButtonWidget.builder(Text.literal(L.t(t.de, t.en)), w -> {
                tab = t;
                clearAndInit();
            }).dimensions(tx, y, tw, ROW - 2).build();
            addDrawableChild(b);
            if (t == tab) markToggle(b, () -> true);
        }
        y += ROW + 6;

        int half = (inner - 4) / 2;
        switch (tab) {
            case GENERAL -> general(c, x, y, inner, half);
            case CATEGORIES -> categories(c, x, y, inner);
            case COLORS -> colors(c, x, y, half);
        }
        y += CONTENT_ROWS * ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(InspectClient.enabled
                ? L.t("Panel öffnen: ", "Open the panel: ") + InspectClient.keyName(c.openKey) + L.t(" (Block/Entity ansehen oder Item im Inventar)", " (look at a block/entity or hover an item)")
                : L.t("Modul ist aus – im ADHD-Menü einschalten, dann gehen die Tasten", "Module is off – turn it on in the ADHD menu, then the keys work"));
    }

    private void general(InspectConfig c, int x, int y, int inner, int half) {
        ButtonWidget position = ButtonWidget.builder(Text.literal(L.t("Position: ", "Position: ") + c.panelPosition.label()), b -> {
            c.panelPosition = c.panelPosition.next();
            b.setMessage(Text.literal(L.t("Position: ", "Position: ") + c.panelPosition.label()));
            InspectConfig.save();
        }).dimensions(x, y, half, ROW - 2).build();
        position.setTooltip(Tooltip.of(Text.literal(L.t("Wo das Info-Panel auf dem Bildschirm steht", "Where the info panel sits on screen"))));
        addDrawableChild(position);
        addDrawableChild(new ValueSlider(x + half + 4, y, inner - half - 4, ROW - 2, L.t("Breite", "Width"), 30, 100, 1, c.panelWidthPercent,
                v -> (int) Math.round(v) + " %", v -> c.panelWidthPercent = (int) Math.round(v)));
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, half, ROW - 2, L.t("Reichweite", "Range"), 4, 256, 1, c.lookRange,
                v -> (int) Math.round(v) + L.t(" Blöcke", " blocks"), v -> c.lookRange = (int) Math.round(v)));
        int[] steps = {5000, 20000, 50000, 100000, 250000};
        ButtonWidget lines = ButtonWidget.builder(Text.literal(linesText(c)), b -> {
            int next = steps[0];
            for (int i = 0; i < steps.length; i++) {
                if (steps[i] == c.maxSnbtLines) next = steps[(i + 1) % steps.length];
            }
            c.maxSnbtLines = next;
            b.setMessage(Text.literal(linesText(c)));
            InspectConfig.save();
        }).dimensions(x + half + 4, y, inner - half - 4, ROW - 2).build();
        lines.setTooltip(Tooltip.of(Text.literal(L.t("Höchstens so viele SNBT-Zeilen anzeigen (sehr große Kisten/Shulker)", "Show at most this many SNBT lines (very large chests/shulkers)"))));
        addDrawableChild(lines);
        y += ROW;
        toggle(L.t("Tooltip-Hinweis", "Tooltip hint"), () -> c.tooltipHint, v -> c.tooltipHint = v, x, y, half, ROW - 2,
                L.t("Zeigt in Item-Tooltips, mit welcher Taste das Panel aufgeht", "Shows in item tooltips which key opens the panel"), InspectConfig::save);
        toggle(L.t("Overlay bei Sperre", "Overlay when locked"), () -> c.hudWhenLocked, v -> c.hudWhenLocked = v, x + half + 4, y, inner - half - 4, ROW - 2,
                L.t("Gesperrtes Ziel bleibt als kleines Overlay sichtbar, während du spielst", "A locked target stays visible as a small overlay while you play"), InspectConfig::save);
        y += ROW;
        toggle(L.t("OP: Server-NBT", "OP: server NBT"), () -> c.opQuery, v -> c.opQuery = v, x, y, half, ROW - 2,
                L.t("Mit OP-Rechten volle Daten vom Server holen (wie F3+I)", "With OP rights, fetch full data from the server (like F3+I)"), InspectConfig::save);
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Markierungen löschen", "Clear markers")), b -> {
            int n = WorldMarkers.clear();
            status(n + L.t(" Markierung(en) gelöscht", " marker(s) removed"));
        }).dimensions(x + half + 4, y, inner - half - 4, ROW - 2).build());
        y += ROW;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Standardwerte", "Defaults")), b -> {
            InspectConfig.resetToDefaults();
            clearAndInit();
        }).dimensions(x, y, half, ROW - 2).tooltip(Tooltip.of(Text.literal(L.t("Alles außer den Tasten zurücksetzen", "Reset everything except the keys")))).build());
    }

    private static String linesText(InspectConfig c) {
        return L.t("Max. SNBT: ", "Max SNBT: ") + c.maxSnbtLines / 1000 + " k";
    }

    private void categories(InspectConfig c, int x, int y, int inner) {
        int colW = (inner - 8) / 3;
        int i = 0;
        for (Category cat : Category.values()) {
            int col = i % 3;
            int row = i / 3;
            toggle(cat.label(), () -> c.isVisible(cat), v -> c.setVisible(cat, v), x + col * (colW + 4), y + row * ROW,
                    col == 2 ? inner - 2 * (colW + 4) : colW, ROW - 2, null, InspectConfig::save);
            i++;
        }
    }

    private void colors(InspectConfig c, int x, int y, int half) {
        color(L.t("Schlüssel", "Keys"), () -> c.colorKey, v -> c.colorKey = v, x, y, half);
        color(L.t("Texte", "Strings"), () -> c.colorString, v -> c.colorString = v, x + half + 4, y, half);
        color(L.t("Zahlen", "Numbers"), () -> c.colorNumber, v -> c.colorNumber = v, x, y + ROW, half);
        color(L.t("Ja/Nein", "Yes/No"), () -> c.colorBoolean, v -> c.colorBoolean = v, x + half + 4, y + ROW, half);
        color(L.t("Koordinaten", "Coordinates"), () -> c.colorCoord, v -> c.colorCoord = v, x, y + 2 * ROW, half);
        color(L.t("Warnungen", "Warnings"), () -> c.colorWarn, v -> c.colorWarn = v, x + half + 4, y + 2 * ROW, half);
        color(L.t("Markierungen", "Markers"), () -> c.colorMarker, v -> c.colorMarker = v, x, y + 3 * ROW, half);
        line(L.t("Hintergrund, Rahmen und Text: im Design-Menü", "Background, border and text: in the Design menu"), x, y + 4 * ROW + 4, Theme.textDim());
    }

    /** Zeile "Name  ■ [#RRGGBB]". */
    private void color(String label, Supplier<String> get, Consumer<String> set, int x, int y, int w) {
        int fieldW = 62;
        line(label, x, y + 5, Theme.text());
        swatches.add(new Swatch(x + w - fieldW - 18, y + 1, get));
        TextFieldWidget field = new TextFieldWidget(textRenderer, x + w - fieldW, y, fieldW, ROW - 2, Text.literal(label));
        field.setMaxLength(9);
        field.setText(get.get());
        field.setChangedListener(s -> {
            Integer parsed = InspectConfig.tryParse(s);
            field.setEditableColor(parsed != null ? 0xFFE0E0E0 : 0xFFFF5555);
            if (parsed != null) {
                set.accept(s.trim());
                InspectConfig.save();
            }
        });
        addDrawableChild(field);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (Swatch s : swatches) {
            Theme.swatch(ctx, s.x, s.y, 16, InspectConfig.parse(s.color.get(), 0xFF555555) & 0xFFFFFF);
        }
    }

    @Override
    protected void onClose() {
        InspectConfig.save();
    }
}
