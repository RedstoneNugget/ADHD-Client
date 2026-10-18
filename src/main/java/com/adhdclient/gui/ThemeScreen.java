package com.adhdclient.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/** Design-Menü: Farben der Menüs selbst wählen – Vorlagen, RGB-Regler und Hex-Eingabe. */
public final class ThemeScreen extends ThemedScreen {

    private static String[] names() {
        return new String[]{L.t("Akzent", "Accent"), L.t("Hintergrund", "Background"), L.t("Schrift", "Text"), L.t("An", "On"), L.t("Aus", "Off")};
    }

    private static final int COLORS = 5;
    private static final int W = 320;
    private static final int ROW = 20;
    private int row = ROW;

    private static int selected;

    private ValueSlider red;
    private ValueSlider green;
    private ValueSlider blue;
    private TextFieldWidget hex;
    private boolean updatingHex;
    private int[] swatchY = new int[COLORS];
    private int swatchX;
    private int previewX;
    private int previewY;

    public ThemeScreen(Screen parent) {
        super(Text.literal("Design"), parent);
    }

    private static ThemeConfig cfg() {
        return Theme.config();
    }

    private static int get(int i) {
        ThemeConfig c = cfg();
        return switch (i) {
            case 0 -> c.accent;
            case 1 -> c.background;
            case 2 -> c.text;
            case 3 -> c.on;
            default -> c.off;
        };
    }

    private static void set(int i, int rgb) {
        ThemeConfig c = cfg();
        rgb &= 0xFFFFFF;
        switch (i) {
            case 0 -> c.accent = rgb;
            case 1 -> c.background = rgb;
            case 2 -> c.text = rgb;
            case 3 -> c.on = rgb;
            default -> c.off = rgb;
        }
    }

    @Override
    protected void build() {
        // Auf kleinen Bildschirmen enger und die letzte Zeile ohne Überschrift, damit Fertig sichtbar bleibt
        boolean compact = height < 290;
        row = compact ? 18 : ROW;
        int h = compact ? Theme.TITLE_H + 6 + 12 + 2 * row + 6 + 12 + COLORS * row + 6 + row + 18
                : Theme.TITLE_H + 6 + 12 + 2 * row + 6 + 12 + COLORS * row + 6 + 12 + row + 6 + row + 18;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;

        // Vorlagen
        section(L.t("Vorlagen", "Presets"), x, y, inner);
        y += 12;
        int pw = (inner - 3 * 4) / 4;
        int i = 0;
        for (Map.Entry<String, int[]> p : ThemeConfig.PRESETS.entrySet()) {
            int[] colors = p.getValue();
            int px = x + (i % 4) * (pw + 4);
            int py = y + (i / 4) * row;
            toggle(presetName(p.getKey()), () -> matches(colors), v -> {
                cfg().apply(colors);
                clearAndInit();
            }, px, py, pw, row - 2, null, null);
            i++;
        }
        y += 2 * row + 6;

        // Farben: links Auswahl, rechts Regler
        section(L.t("Farben", "Colors"), x, y, inner);
        y += 12;
        int leftW = 118;
        swatchX = x + leftW - 14;
        for (int k = 0; k < COLORS; k++) {
            final int idx = k;
            toggle(names()[k], () -> selected == idx, v -> {
                selected = idx;
                clearAndInit();
            }, x, y + k * row, leftW - 18, row - 2, null, null);
            swatchY[k] = y + k * row + 2;
        }
        int rx = x + leftW + 8;
        int rw = inner - leftW - 8;
        int rgb = get(selected);
        red = addDrawableChild(new ValueSlider(rx, y, rw, row - 2, L.t("Rot", "Red"), 0, 255, 1, (rgb >> 16) & 0xFF, v -> String.valueOf((int) v), v -> fromSliders()));
        green = addDrawableChild(new ValueSlider(rx, y + row, rw, row - 2, L.t("Grün", "Green"), 0, 255, 1, (rgb >> 8) & 0xFF, v -> String.valueOf((int) v), v -> fromSliders()));
        blue = addDrawableChild(new ValueSlider(rx, y + 2 * row, rw, row - 2, L.t("Blau", "Blue"), 0, 255, 1, rgb & 0xFF, v -> String.valueOf((int) v), v -> fromSliders()));
        hex = new TextFieldWidget(textRenderer, rx, y + 3 * row, rw - 30, row - 2, Text.literal("Hex"));
        hex.setMaxLength(7);
        hex.setText(hexOf(rgb));
        hex.setChangedListener(this::fromHex);
        addDrawableChild(hex);
        previewX = rx + rw - 24;
        previewY = y + 3 * row;
        y += COLORS * row + 6;

        // Fenster und Sprache (kompakt: alles in einer Zeile mit Zurücksetzen/Fertig)
        int half = (inner - 4) / 2;
        if (!compact) {
            section(L.t("Fenster und Sprache", "Window and language"), x, y, inner);
            y += 12;
        }
        // kompakt: Deckkraft, Sprache, Zurücksetzen und Fertig nebeneinander; sonst zwei Zeilen
        int quarter = (inner - 12) / 4;
        int[] xs = compact ? new int[]{x, x + quarter + 4, x + 2 * (quarter + 4), x + 3 * (quarter + 4)}
                : new int[]{x, x + half + 4, x, x + half + 4};
        int[] ws = compact ? new int[]{quarter, quarter, quarter, inner - 3 * (quarter + 4)}
                : new int[]{half, inner - half - 4, half, inner - half - 4};
        int secondY = compact ? y : y + row + 6;
        addDrawableChild(new ValueSlider(xs[0], y, ws[0], row - 2, L.t("Deckkraft", "Opacity"), 40, 100, 1, cfg().opacity,
                v -> (int) Math.round(v) + " %", v -> cfg().opacity = (int) Math.round(v)));
        ButtonWidget lang = ButtonWidget.builder(Text.literal(compact ? languageValue() : languageText()), b -> {
            L.Mode[] modes = L.Mode.values();
            cfg().language = modes[(cfg().language.ordinal() + 1) % modes.length];
            clearAndInit();
        }).dimensions(xs[1], y, ws[1], row - 2).build();
        lang.setTooltip(Tooltip.of(Text.literal(L.t("Sprache des Clients – Automatisch = wie die Spielsprache",
                "Client language – Automatic = same as the game language"))));
        addDrawableChild(lang);

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Zurücksetzen", "Reset")), b -> {
            ThemeConfig fresh = new ThemeConfig();
            cfg().apply(ThemeConfig.PRESETS.get("Lila"));
            cfg().opacity = fresh.opacity;
            clearAndInit();
        }).dimensions(xs[2], secondY, ws[2], row - 2).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close())
                .dimensions(xs[3], secondY, ws[3], row - 2).build());

        status(L.t("Änderungen gelten sofort für alle Menüs", "Changes apply to all menus right away"));
    }

    private static String languageValue() {
        return switch (cfg().language) {
            case AUTO -> L.t("Automatisch", "Automatic");
            case DE -> "Deutsch";
            case EN -> "English";
        };
    }

    private static String languageText() {
        return L.t("Sprache: ", "Language: ") + languageValue();
    }

    private static String presetName(String key) {
        return switch (key) {
            case "Lila" -> L.t("Lila", "Purple");
            case "Ozean" -> L.t("Ozean", "Ocean");
            case "Rot" -> L.t("Rot", "Red");
            case "Glut" -> L.t("Glut", "Ember");
            case "Grau" -> L.t("Grau", "Gray");
            default -> key;
        };
    }

    private static boolean matches(int[] p) {
        ThemeConfig c = cfg();
        return Arrays.equals(p, new int[]{c.accent, c.background, c.text, c.on, c.off});
    }

    private void fromSliders() {
        int rgb = ((int) Math.round(red.current()) << 16) | ((int) Math.round(green.current()) << 8) | (int) Math.round(blue.current());
        set(selected, rgb);
        if (hex != null) {
            updatingHex = true;
            hex.setText(hexOf(rgb));
            updatingHex = false;
        }
    }

    private void fromHex(String s) {
        if (updatingHex) return;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        boolean ok = t.length() == 6 && t.chars().allMatch(ch -> Character.digit(ch, 16) >= 0);
        hex.setEditableColor(ok ? 0xFFE0E0E0 : 0xFFFF5555);
        if (!ok) return;
        int rgb = Integer.parseInt(t, 16);
        set(selected, rgb);
        red.setCurrent((rgb >> 16) & 0xFF);
        green.setCurrent((rgb >> 8) & 0xFF);
        blue.setCurrent(rgb & 0xFF);
    }

    private static String hexOf(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (int k = 0; k < COLORS; k++) Theme.swatch(ctx, swatchX, swatchY[k], 14, get(k));
        Theme.swatch(ctx, previewX, previewY, 18, get(selected));
    }

    @Override
    protected void onClose() {
        cfg().sanitize();
        cfg().save();
    }
}
