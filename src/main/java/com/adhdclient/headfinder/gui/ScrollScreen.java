package com.adhdclient.headfinder.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Basis für Bildschirme mit festem Kopf-/Fußbereich und einem scrollbaren Inhaltsbereich dazwischen.
 * Inhalts-Widgets werden mit einer Basis-Y-Position (relativ zum Inhaltsanfang) registriert und beim
 * Scrollen verschoben; außerhalb des sichtbaren Bereichs werden sie ausgeblendet.
 */
public abstract class ScrollScreen extends Screen {

    protected record Placed(ClickableWidget widget, int baseY) {
    }

    protected record Label(String text, int x, int baseY, int color, boolean centered) {
    }

    protected final Screen parent;
    protected final List<Placed> placed = new ArrayList<>();
    protected final List<Label> labels = new ArrayList<>();
    protected double scroll;
    protected int contentHeight;

    protected ScrollScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** Oberkante des scrollbaren Bereichs. */
    protected abstract int viewTop();

    /** Unterkante des scrollbaren Bereichs. */
    protected int viewBottom() {
        return height - 32;
    }

    /** Rechter Rand des Inhalts (für den Scrollbalken). */
    protected abstract int contentRight();

    protected <T extends ClickableWidget> T place(T w, int baseY) {
        addDrawableChild(w);
        placed.add(new Placed(w, baseY));
        return w;
    }

    protected void clearPlaced() {
        placed.clear();
        labels.clear();
    }

    protected void clampScroll() {
        int view = viewBottom() - viewTop();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - view)));
    }

    protected void relayout() {
        int top = viewTop();
        int bottom = viewBottom();
        for (Placed p : placed) {
            int y = top + p.baseY - (int) scroll;
            p.widget.setY(y);
            p.widget.visible = y >= top && y + p.widget.getHeight() <= bottom;
        }
    }

    /** Neu aufbauen, Scrollposition behalten. */
    protected void rebuildKeepScroll() {
        double s = scroll;
        clearAndInit();
        scroll = s;
        clampScroll();
        relayout();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= viewTop() && mouseY <= viewBottom() && contentHeight > viewBottom() - viewTop()) {
            scroll -= vertical * 22;
            clampScroll();
            relayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int top = viewTop();
        int bottom = viewBottom();
        for (Label l : labels) {
            int y = top + l.baseY - (int) scroll;
            if (y < top || y + 9 > bottom) continue;
            if (l.centered) ctx.drawCenteredTextWithShadow(textRenderer, l.text, l.x, y, l.color);
            else ctx.drawTextWithShadow(textRenderer, l.text, l.x, y, l.color);
        }
        // Scrollbalken
        int view = bottom - top;
        if (contentHeight > view) {
            int barH = Math.max(20, view * view / contentHeight);
            int barY = top + (int) ((view - barH) * (scroll / (contentHeight - view)));
            int x = contentRight() + 6;
            ctx.fill(x, top, x + 3, bottom, 0x40FFFFFF);
            ctx.fill(x, barY, x + 3, barY + barH, 0xC0FFFFFF);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
