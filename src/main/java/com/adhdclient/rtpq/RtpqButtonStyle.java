package com.adhdclient.rtpq;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

/** Eigener Look für die RTPQ-Knöpfe: dunkler Rahmen, lila Fläche, rosa Streifen an den Seiten. */
final class RtpqButtonStyle {

    static final int OUTLINE = -15595233;
    static final int PINK = -3063855;
    private static final int FILL = -12899738;
    private static final int FILL_HOVER = -11715963;
    private static final int TOP = -9809240;
    private static final int TEXT = -923393;
    private static final int STRIPE = 6;

    private RtpqButtonStyle() {
    }

    static void draw(DrawContext ctx, ButtonWidget b) {
        int x1 = b.getX();
        int y1 = b.getY();
        int x2 = x1 + b.getWidth();
        int y2 = y1 + b.getHeight();
        ctx.fill(x1, y1, x2, y2, OUTLINE);
        ctx.fill(x1 + 2, y1 + 2, x2 - 2, y2 - 2, b.isHovered() ? FILL_HOVER : FILL);
        ctx.fill(x1 + 2, y1 + 2, x2 - 2, y1 + 4, TOP);
        ctx.fill(x1 + 2, y1 + 2, x1 + 2 + STRIPE, y2 - 2, PINK);
        ctx.fill(x2 - 2 - STRIPE, y1 + 2, x2 - 2, y2 - 2, PINK);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int tx = x1 + (b.getWidth() - tr.getWidth(b.getMessage())) / 2;
        int ty = y1 + (b.getHeight() - 8) / 2;
        ctx.drawTextWithShadow(tr, b.getMessage(), tx, ty, TEXT);
    }
}
