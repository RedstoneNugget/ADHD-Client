package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.LuckyCasinoClient;
import com.adhdclient.casino.core.Casino;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Basis aller Casino-Fenster: Filztisch-Panel mit (kosmetischer) Rahmenfarbe,
 * Titel, Guthabenanzeige und Zurück-Button.
 *
 * <p>Während einer Animation zeigt das Fenster ein "eingefrorenes" Guthaben an,
 * damit das Ergebnis nicht vorab verraten wird. Gebucht wird trotzdem sofort.</p>
 */
public abstract class CasinoScreen extends Screen {

    public static final int PANEL_W = 420;
    public static final int PANEL_H = 240;
    public static final int HEADER_H = 22;

    protected final Screen parent;
    protected final Casino casino;
    protected int left;
    protected int top;
    protected int pw;
    protected int ph;
    private long frozenBalance = -1;

    protected CasinoScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
        this.casino = LuckyCasinoClient.casino();
    }

    @Override
    protected final void init() {
        pw = Math.min(PANEL_W, width - 4);
        ph = Math.min(PANEL_H, height - 4);
        left = (width - pw) / 2;
        top = (height - ph) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable(parent == null ? "luckycasino.gui.close" : "luckycasino.gui.back"),
                b -> close()).dimensions(left + 4, top + 4, 50, 14).build());
        initContent();
    }

    /** Widgets des Fensters anlegen (wird auch nach Größenänderung erneut aufgerufen). */
    protected abstract void initContent();

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        drawPanel(ctx);
    }

    private void drawPanel(DrawContext ctx) {
        int frame = Ui.frameColor(casino.equippedFrame(), Util.getMeasuringTimeMs());
        // Schatten und Rahmen
        ctx.fill(left + 4, top + 4, left + pw + 4, top + ph + 4, 0x70000000);
        ctx.fill(left - 3, top - 3, left + pw + 3, top + ph + 3, Ui.darken(frame, 0.45f));
        ctx.fill(left - 2, top - 2, left + pw + 2, top + ph + 2, frame);
        ctx.fill(left - 1, top - 1, left + pw + 1, top + ph + 1, Ui.darken(frame, 0.3f));
        // Filz mit leichtem Verlauf
        ctx.fillGradient(left, top + HEADER_H, left + pw, top + ph, Ui.FELT, Ui.FELT_DARK);
        // Kopfzeile
        ctx.fillGradient(left, top, left + pw, top + HEADER_H, 0xFF202020, 0xFF101010);
        ctx.fill(left, top + HEADER_H - 1, left + pw, top + HEADER_H, Ui.darken(frame, 0.2f));

        Ui.centered(ctx, textRenderer, title, left + pw / 2, top + 7, Ui.GOLD);
        String bal = Fmt.chips(shownBalance());
        int bw = textRenderer.getWidth(bal);
        ctx.drawText(textRenderer, bal, left + pw - 8 - bw, top + 7, 0xFFFFE9A8, true);
        Ui.chip(ctx, left + pw - 16 - bw, top + 11, 5, 0xFFD23C3C);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderContent(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        renderOverlay(ctx, mouseX, mouseY, delta);
    }

    /** Spielfeld (unter den Buttons). */
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    /** Ergebnis-Banner u. Ä. (über den Buttons). */
    protected void renderOverlay(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        boolean typing = getFocused() instanceof TextFieldWidget field && field.isFocused();
        if (input.key() == GLFW.GLFW_KEY_SPACE && !typing && primaryAction()) {
            return true;
        }
        return super.keyPressed(input);
    }

    /**
     * Hauptaktion des Fensters (Leertaste): Drehen, Werfen, Austeilen, Cash Out …
     *
     * @return true, wenn etwas ausgelöst wurde
     */
    protected boolean primaryAction() {
        return false;
    }

    @Override
    public void removed() {
        finishPendingRounds();
        unfreezeBalance();
        LuckyCasinoClient.saveNow();
    }

    /** Wird beim Schließen und beim Beenden des Spiels aufgerufen. */
    public void finishPendingRounds() {
    }

    // ------------------------------------------------------------------
    // Anzeige-Guthaben während Animationen
    // ------------------------------------------------------------------

    protected void freezeBalance(long value) {
        frozenBalance = value;
    }

    protected void unfreezeBalance() {
        frozenBalance = -1;
    }

    /** Läuft gerade eine Animation? (Erfolgs-Toasts warten so lange.) */
    public boolean isAnimating() {
        return frozenBalance >= 0;
    }

    protected long shownBalance() {
        return frozenBalance >= 0 ? frozenBalance : casino.balance();
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    /** Animationsdauer gemäß Einstellung "schnelle Animationen". */
    protected static long dur(long ms) {
        return LuckyCasinoClient.config().fastAnimations ? ms / 2 : ms;
    }

    protected static long now() {
        return Util.getMeasuringTimeMs();
    }

    /** Großes Ergebnis-Banner mittig über einem Bereich. */
    protected void banner(DrawContext ctx, Text line1, Text line2, int cx, int cy, int color, float appear) {
        float a = Ui.easeOutBack(appear);
        int w1 = textRenderer.getWidth(line1) * 2;
        int w2 = line2 == null ? 0 : textRenderer.getWidth(line2);
        int w = Math.max(w1, w2) + 24;
        int h = line2 == null ? 28 : 38;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.scale(Math.max(0.01f, a), Math.max(0.01f, a));
        ctx.fill(-w / 2 - 2, -h / 2 - 2, w / 2 + 2, h / 2 + 2, Ui.withAlpha(color, 0xFF));
        ctx.fill(-w / 2, -h / 2, w / 2, h / 2, 0xF0101010);
        Ui.scaled(ctx, textRenderer, line1, 0, -h / 2f + 6, 2f, color, true);
        if (line2 != null) {
            Ui.centered(ctx, textRenderer, line2, 0, h / 2 - 12, Ui.WHITE);
        }
        m.popMatrix();
    }
}
