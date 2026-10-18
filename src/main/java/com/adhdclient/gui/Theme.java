package com.adhdclient.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Eigener Look des ADHD Client: Fenster mit Farbverlauf-Titelleiste und Akzent-Streifen links,
 * Schalter mit farbiger Anzeige-Leiste (an/aus), schmale Regler mit Knopf.
 * Alle Farben kommen aus {@link ThemeConfig} und sind im Design-Menü einstellbar.
 */
public final class Theme {

    public static final int TITLE_H = 16;

    private static ThemeConfig config;

    public enum State { NEUTRAL, ON, OFF }

    private Theme() {
    }

    public static ThemeConfig config() {
        if (config == null) config = ThemeConfig.load();
        return config;
    }

    // ---------------------------------------------------------------- Farben

    public static int argb(int alpha, int rgb) {
        return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
    }

    /** Mischt a und b (t = 0 → a, t = 1 → b). */
    public static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int accent() {
        return argb(255, config().accent);
    }

    public static int text() {
        return argb(255, config().text);
    }

    public static int textDim() {
        return argb(255, mix(config().text, config().background, 0.45f));
    }

    private static int surface() {
        return mix(config().background, config().text, 0.06f);
    }

    private static int line() {
        return argb(255, mix(config().background, config().text, 0.18f));
    }

    private static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    // ---------------------------------------------------------------- Bausteine

    public static void border(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
    }

    /** Fenster mit Titelleiste (Farbverlauf), Akzent-Streifen links und abgeschrägten Ecken. */
    public static void panel(DrawContext ctx, int x1, int y1, int x2, int y2, Text title) {
        ThemeConfig c = config();
        int alpha = Math.round(c.opacity * 2.55f);
        ctx.fill(x1, y1, x2, y2, argb(alpha, c.background));
        ctx.fillGradient(x1, y1, x2, y1 + TITLE_H, argb(255, c.accent), argb(255, mix(c.accent, c.background, 0.55f)));
        ctx.fill(x1, y1 + TITLE_H, x1 + 2, y2, accent());
        border(ctx, x1, y1, x2, y2, argb(255, mix(c.accent, c.background, 0.5f)));
        // abgeschrägte Ecken: je ein Pixel freistellen
        int clear = 0x00000000;
        ctx.fill(x1, y1, x1 + 1, y1 + 1, clear);
        ctx.fill(x2 - 1, y1, x2, y1 + 1, clear);
        ctx.drawTextWithShadow(font(), title, x1 + 7, y1 + (TITLE_H - 8) / 2, text());
    }

    /** Abschnitts-Überschrift: kleiner Akzent-Block, Text, feine Linie. */
    public static void section(DrawContext ctx, int x, int y, int width, String text) {
        TextRenderer tr = font();
        String t = text.toUpperCase(Locale.ROOT);
        ctx.fill(x, y + 1, x + 3, y + 8, accent());
        ctx.drawTextWithShadow(tr, t, x + 6, y, accent());
        int lx = x + 6 + tr.getWidth(t) + 5;
        if (lx < x + width) ctx.fill(lx, y + 4, x + width, y + 5, line());
    }

    public static void button(DrawContext ctx, ClickableWidget w, State state) {
        ThemeConfig c = config();
        int x1 = w.getX();
        int y1 = w.getY();
        int x2 = x1 + w.getWidth();
        int y2 = y1 + w.getHeight();
        boolean hover = w.isHovered() && w.active;

        int bg = surface();
        if (state == State.ON) bg = mix(bg, c.on, 0.16f);
        if (hover) bg = mix(bg, c.text, 0.08f);
        ctx.fill(x1, y1, x2, y2, argb(255, bg));
        border(ctx, x1, y1, x2, y2, hover ? accent() : line());

        int textX0 = x1;
        if (state != State.NEUTRAL) {
            int ind = state == State.ON ? c.on : c.off;
            ctx.fill(x1 + 1, y1 + 1, x1 + 4, y2 - 1, argb(255, ind));
            textX0 = x1 + 4;
        }

        TextRenderer tr = font();
        Text msg = w.getMessage();
        int avail = x2 - textX0;
        int tw = tr.getWidth(msg);
        int tx = tw > avail - 6 ? textX0 + 4 : textX0 + (avail - tw) / 2;
        int color = state == State.OFF || !w.active ? textDim() : text();
        ctx.enableScissor(textX0 + 1, y1, x2 - 1, y2);
        ctx.drawTextWithShadow(tr, msg, tx, y1 + (w.getHeight() - 8) / 2, color);
        ctx.disableScissor();
    }

    public static void slider(DrawContext ctx, ValueSlider s) {
        int x1 = s.getX();
        int y1 = s.getY();
        int x2 = x1 + s.getWidth();
        int y2 = y1 + s.getHeight();
        boolean hover = s.isHovered();
        ctx.fill(x1, y1, x2, y2, argb(255, surface()));
        border(ctx, x1, y1, x2, y2, hover ? accent() : line());

        int trackY = y2 - 4;
        int knobX = x1 + 2 + (int) Math.round((s.getWidth() - 8) * s.fraction());
        ctx.fill(x1 + 2, trackY, x2 - 2, trackY + 1, line());
        ctx.fill(x1 + 2, trackY, knobX + 2, trackY + 1, accent());
        ctx.fill(knobX, trackY - 2, knobX + 4, trackY + 3, accent());

        TextRenderer tr = font();
        Text msg = s.getMessage();
        ctx.drawTextWithShadow(tr, msg, x1 + (s.getWidth() - tr.getWidth(msg)) / 2, y1 + Math.max(2, (s.getHeight() - 12) / 2), text());
    }

    public static void field(DrawContext ctx, TextFieldWidget f) {
        border(ctx, f.getX(), f.getY(), f.getX() + f.getWidth(), f.getY() + f.getHeight(), f.isFocused() ? accent() : line());
    }

    /** Kleines Farbfeld. */
    public static void swatch(DrawContext ctx, int x, int y, int size, int rgb) {
        ctx.fill(x, y, x + size, y + size, line());
        ctx.fill(x + 1, y + 1, x + size - 1, y + size - 1, argb(255, rgb));
    }
}
