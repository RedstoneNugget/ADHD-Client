package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.core.loot.Cosmetic;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

/**
 * Zeichenhilfen für die Casino-GUI. Alles wird aus einfachen Rechtecken
 * zusammengesetzt (Kreise, gedrehte Linien, Chips) – keine eigenen Texturen nötig.
 */
public final class Ui {

    public static final int GOLD = 0xFFFFD34D;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFA0A0A0;
    public static final int DARK_GRAY = 0xFF606060;
    public static final int GREEN = 0xFF55FF55;
    public static final int RED = 0xFFFF5555;
    public static final int FELT_DARK = 0xFF082715;
    public static final int FELT = 0xFF0F4426;

    private Ui() {
    }

    // ------------------------------------------------------------------
    // Formen
    // ------------------------------------------------------------------

    /** Gefüllter Kreis aus horizontalen Linien. */
    public static void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt(r * r - dy * dy + 0.25 * r));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Linie mit Dicke, über eine gedrehte Matrix gezeichnet. */
    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, int thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        int len = Math.round((float) Math.sqrt(dx * dx + dy * dy));
        if (len <= 0) {
            return;
        }
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x1, y1);
        m.rotate((float) Math.atan2(dy, dx));
        int half = thickness / 2;
        ctx.fill(0, -half, len, thickness - half, color);
        m.popMatrix();
    }

    /**
     * Exakter Kreissektor (Keil) zwischen zwei Radien, z. B. für Roulette-Fächer oder Glücksrad-Felder.
     * Gezeichnet wird spaltenweise in einem um die Feldmitte gedrehten Koordinatensystem:
     * jede Spalte ist genau so hoch, wie die beiden Feldgrenzen und der Außenkreis erlauben.
     * Benachbarte Keile überlappen sich deshalb nicht.
     *
     * @param mid  Winkel der Feldmitte (Bogenmaß)
     * @param half halber Öffnungswinkel
     */
    public static void wedge(DrawContext ctx, float cx, float cy, double mid, double half, float rInner, float rOuter, int color) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate((float) mid);
        m.scale(0.25f, 0.25f); // Viertelpixel-Genauigkeit für die Spaltenhöhen
        double tan = Math.tan(half);
        int start = (int) Math.floor(rInner * Math.cos(half) * 4);
        int end = (int) Math.ceil(rOuter * 4);
        for (int xs = start; xs < end; xs += 4) {
            double x = (xs + 2) / 4.0; // Spaltenmitte in Pixeln
            double h = Math.min(x * tan, Math.sqrt(Math.max(0, rOuter * rOuter - x * x)));
            int hs = (int) Math.round(h * 4);
            if (hs > 0) {
                ctx.fill(xs, -hs, xs + 4, hs, color);
            }
        }
        m.popMatrix();
    }

    /** Box mit Hintergrund und Rahmen. */
    public static void box(DrawContext ctx, int x, int y, int w, int h, int bg, int border) {
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawStrokedRectangle(x, y, w, h, border);
    }

    /** Leicht plastische Box (hell oben/links, dunkel unten/rechts). */
    public static void bevel(DrawContext ctx, int x, int y, int w, int h, int base) {
        ctx.fill(x, y, x + w, y + h, base);
        ctx.fill(x, y, x + w, y + 1, lighten(base, 0.35f));
        ctx.fill(x, y, x + 1, y + h, lighten(base, 0.2f));
        ctx.fill(x, y + h - 1, x + w, y + h, darken(base, 0.4f));
        ctx.fill(x + w - 1, y, x + w, y + h, darken(base, 0.3f));
    }

    /** Casino-Chip mit weißen Randstreifen. */
    public static void chip(DrawContext ctx, int cx, int cy, int r, int color) {
        fillCircle(ctx, cx, cy, r, darken(color, 0.35f));
        fillCircle(ctx, cx, cy, r - 1, color);
        if (r >= 4) {
            int s = Math.max(1, r / 3);
            ctx.fill(cx - s / 2, cy - r + 1, cx + (s + 1) / 2, cy - r + 1 + s, 0xFFF0F0F0);
            ctx.fill(cx - s / 2, cy + r - s, cx + (s + 1) / 2, cy + r, 0xFFF0F0F0);
            ctx.fill(cx - r + 1, cy - s / 2, cx - r + 1 + s, cy + (s + 1) / 2, 0xFFF0F0F0);
            ctx.fill(cx + r - s, cy - s / 2, cx + r, cy + (s + 1) / 2, 0xFFF0F0F0);
            fillCircle(ctx, cx, cy, Math.max(1, r / 2), lighten(color, 0.25f));
        }
    }

    // ------------------------------------------------------------------
    // Text & Items
    // ------------------------------------------------------------------

    public static void text(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int color) {
        ctx.drawText(tr, text, x, y, color, true);
    }

    public static void centered(DrawContext ctx, TextRenderer tr, Text text, int cx, int y, int color) {
        ctx.drawText(tr, text, cx - tr.getWidth(text) / 2, y, color, true);
    }

    public static void right(DrawContext ctx, TextRenderer tr, Text text, int rightX, int y, int color) {
        ctx.drawText(tr, text, rightX - tr.getWidth(text), y, color, true);
    }

    /** Skalierter Text; bei {@code centered} ist x die Mitte. */
    public static void scaled(DrawContext ctx, TextRenderer tr, Text text, float x, float y, float scale, int color, boolean centered) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        int dx = centered ? -tr.getWidth(text) / 2 : 0;
        ctx.drawText(tr, text, dx, 0, color, true);
        m.popMatrix();
    }

    /** Item in beliebiger Größe (16 px × scale), (x, y) = linke obere Ecke. */
    public static void item(DrawContext ctx, ItemStack stack, float x, float y, float scale) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        ctx.drawItem(stack, 0, 0);
        m.popMatrix();
    }

    /** Zeilenumbruch-Text, liefert die benutzte Höhe. */
    public static int wrapped(DrawContext ctx, TextRenderer tr, Text text, int x, int y, int width, int color) {
        var lines = tr.wrapLines(text, width);
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawText(tr, lines.get(i), x, y + i * 10, color, true);
        }
        return lines.size() * 10;
    }

    // ------------------------------------------------------------------
    // Farben & Animation
    // ------------------------------------------------------------------

    public static int withAlpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | (color & 0xFFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16
                | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    public static int lighten(int color, float amount) {
        return lerpColor(color, color | 0xFFFFFF, amount);
    }

    public static int darken(int color, float amount) {
        return lerpColor(color, color & 0xFF000000, amount);
    }

    /** HSV (0–1) nach ARGB (ohne java.awt, das auf manchen Systemen mit GLFW kollidiert). */
    public static int hsv(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int sector = (int) hh;
        float f = hh - sector;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    /** Rahmenfarbe des ausgerüsteten Rahmens (Regenbogen animiert). */
    public static int frameColor(Cosmetic frame, long timeMs) {
        if (frame != null && frame.isRainbow()) {
            return hsv((timeMs % 4000) / 4000f, 0.75f, 1f);
        }
        return frame == null ? Cosmetic.FRAME_CLASSIC.color() : frame.color();
    }

    /** Pulsieren zwischen 0 und 1. */
    public static float pulse(long timeMs, long periodMs) {
        return (float) (0.5 + 0.5 * Math.sin(timeMs * 2 * Math.PI / periodMs));
    }

    public static float clamp01(float t) {
        return Math.max(0, Math.min(1, t));
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    public static float easeOutQuart(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u * u;
    }

    public static float easeInOutSine(float t) {
        return (float) (-(Math.cos(Math.PI * clamp01(t)) - 1) / 2);
    }

    /** Leichtes Überschwingen am Ende (Walzen rasten ein). */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.2f;
        float c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }
}
