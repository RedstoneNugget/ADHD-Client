package com.adhdclient.casino.client;

import com.adhdclient.casino.client.gui.CasinoScreen;
import com.adhdclient.casino.client.gui.Fmt;
import com.adhdclient.casino.client.gui.HudPositionScreen;
import com.adhdclient.casino.client.gui.Ui;
import com.adhdclient.casino.config.CasinoConfig;
import com.adhdclient.casino.core.Casino;
import com.adhdclient.casino.core.loot.Cosmetic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/** Kleines, verschiebbares Chip-Guthaben im Spiel (per Einstellung abschaltbar). */
public final class CasinoHud {

    /** Abstand zum Bildschirmrand. */
    public static final int MARGIN = 4;

    private CasinoHud() {
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        CasinoConfig cfg = LuckyCasinoClient.config();
        if (!LuckyCasinoClient.enabled || !cfg.hudEnabled || mc.options.hudHidden || mc.player == null
                || mc.currentScreen instanceof CasinoScreen || mc.currentScreen instanceof HudPositionScreen) {
            return;
        }
        int[] box = bounds(mc.textRenderer, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
        draw(ctx, mc.textRenderer, box[0], box[1]);
    }

    /** Breite und Höhe der Anzeige. */
    public static int[] size(TextRenderer tr) {
        Casino casino = LuckyCasinoClient.casino();
        int w = 20 + tr.getWidth(Fmt.chips(casino.balance()));
        int h = 14;
        Cosmetic title = casino.equippedTitle();
        if (title != null && LuckyCasinoClient.config().hudShowTitle) {
            w = Math.max(w, 8 + tr.getWidth(Text.translatable(title.translationKey())));
            h += 10;
        }
        return new int[]{w, h};
    }

    /** Position (x, y) und Größe (w, h) gemäß Einstellungen. */
    public static int[] bounds(TextRenderer tr, int screenW, int screenH) {
        CasinoConfig cfg = LuckyCasinoClient.config();
        int[] s = size(tr);
        int x = MARGIN + Math.round(cfg.hudX * Math.max(0, screenW - s[0] - 2 * MARGIN));
        int y = MARGIN + Math.round(cfg.hudY * Math.max(0, screenH - s[1] - 2 * MARGIN));
        return new int[]{x, y, s[0], s[1]};
    }

    public static void draw(DrawContext ctx, TextRenderer tr, int x, int y) {
        Casino casino = LuckyCasinoClient.casino();
        int[] s = size(tr);
        int frame = Ui.frameColor(casino.equippedFrame(), Util.getMeasuringTimeMs());
        ctx.fill(x, y, x + s[0], y + s[1], 0xA0000000);
        ctx.drawStrokedRectangle(x, y, s[0], s[1], Ui.withAlpha(frame, 0xC0));
        Ui.chip(ctx, x + 8, y + 7, 5, 0xFFD23C3C);
        ctx.drawText(tr, Fmt.chips(casino.balance()), x + 16, y + 3, 0xFFFFD34D, true);
        Cosmetic title = casino.equippedTitle();
        if (title != null && LuckyCasinoClient.config().hudShowTitle) {
            ctx.drawText(tr, Text.translatable(title.translationKey()), x + 4, y + 14, title.color(), true);
        }
    }
}
