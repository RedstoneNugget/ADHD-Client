package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.core.GameType;
import com.adhdclient.casino.core.progress.Achievement;
import com.adhdclient.casino.core.progress.GameStats;
import com.adhdclient.casino.core.progress.PlayerProfile;
import com.adhdclient.casino.core.progress.Statistics;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Statistik: Übersicht, Werte pro Spiel und Guthaben-Verlauf als Liniendiagramm. */
public class StatsScreen extends CasinoScreen {

    private static int tab;
    private final ButtonWidget[] tabs = new ButtonWidget[3];

    public StatsScreen(Screen parent) {
        super(Text.translatable("luckycasino.menu.stats"), parent);
    }

    @Override
    protected void initContent() {
        String[] keys = {"luckycasino.stats.tab.overview", "luckycasino.stats.tab.games", "luckycasino.stats.tab.chart"};
        int w = 100;
        int x0 = left + pw / 2 - (3 * w + 8) / 2;
        for (int i = 0; i < 3; i++) {
            int idx = i;
            tabs[i] = addDrawableChild(ButtonWidget.builder(Text.translatable(keys[i]), b -> {
                tab = idx;
                updateTabs();
            }).dimensions(x0 + i * (w + 4), top + HEADER_H + 4, w, 16).build());
        }
        updateTabs();
    }

    private void updateTabs() {
        for (int i = 0; i < 3; i++) {
            tabs[i].active = tab != i;
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        switch (tab) {
            case 0 -> drawOverview(ctx);
            case 1 -> drawGames(ctx);
            default -> drawChart(ctx, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------
    // Übersicht
    // ------------------------------------------------------------------

    private void drawOverview(DrawContext ctx) {
        PlayerProfile p = casino.profile();
        Statistics s = p.stats;
        int colW = (pw - 30) / 2;
        int x1 = left + 10;
        int x2 = left + 20 + colW;
        int y = top + HEADER_H + 30;
        long net = s.netResult();

        int ly = y;
        ly = kv(ctx, x1, ly, colW, "luckycasino.stats.balance", Fmt.chips(p.balance), Ui.GOLD);
        ly = kv(ctx, x1, ly, colW, "luckycasino.stats.rounds", Fmt.chips(s.totalRounds()), Ui.WHITE);
        ly = kv(ctx, x1, ly, colW, "luckycasino.stats.wins_losses", Fmt.chips(s.totalWins()) + " / " + Fmt.chips(s.totalLosses()), Ui.WHITE);
        ly = kv(ctx, x1, ly, colW, "luckycasino.stats.winrate", Fmt.percent(s.winRate()), Ui.WHITE);
        ly = kv(ctx, x1, ly, colW, "luckycasino.stats.wagered", Fmt.chips(s.totalWagered()), Ui.WHITE);
        ly = kv(ctx, x1, ly, colW, "luckycasino.stats.paid", Fmt.chips(s.totalPaidOut()), Ui.WHITE);
        ly = kv(ctx, x1, ly, colW, "luckycasino.stats.net", Fmt.signed(net), net >= 0 ? Ui.GREEN : Ui.RED);
        kv(ctx, x1, ly, colW, "luckycasino.stats.playtime", Fmt.duration(s.playTimeMs), Ui.WHITE);

        ly = y;
        String biggest = p.records.biggestWin > 0
                ? Fmt.chips(p.records.biggestWin) + " (" + gameName(p.records.biggestWinGame) + ")" : "-";
        ly = kv(ctx, x2, ly, colW, "luckycasino.stats.biggest", biggest, Ui.GREEN);
        ly = kv(ctx, x2, ly, colW, "luckycasino.stats.bonus_income", Fmt.chips(s.bonusIncome), Ui.WHITE);
        ly = kv(ctx, x2, ly, colW, "luckycasino.stats.loans", Fmt.chips(s.loansTaken), Ui.WHITE);
        ly = kv(ctx, x2, ly, colW, "luckycasino.stats.wheel", Fmt.chips(s.wheelSpins), Ui.WHITE);
        ly = kv(ctx, x2, ly, colW, "luckycasino.stats.lootboxes", Fmt.chips(s.lootBoxesOpened), Ui.WHITE);
        ly = kv(ctx, x2, ly, colW, "luckycasino.stats.shop", Fmt.chips(s.shopSpending), Ui.WHITE);
        ly = kv(ctx, x2, ly, colW, "luckycasino.stats.achievements",
                casino.unlockedCount() + " / " + Achievement.values().length, Ui.GOLD);
        kv(ctx, x2, ly, colW, "luckycasino.stats.level", p.level + " (" + Fmt.chips(p.xp) + " XP)", 0xFF7CFC6A);
        kv(ctx, x1, y + 8 * 15, colW, "luckycasino.stats.missions", Fmt.chips(p.missionsCompleted), Ui.WHITE);

        Ui.wrapped(ctx, textRenderer, Text.translatable("luckycasino.stats.note"), x1, top + ph - 34, pw - 20, Ui.DARK_GRAY);
    }

    private String gameName(String enumName) {
        try {
            return Text.translatable(GameType.valueOf(enumName).translationKey()).getString();
        } catch (IllegalArgumentException e) {
            return "?";
        }
    }

    private int kv(DrawContext ctx, int x, int y, int w, String key, String value, int color) {
        ctx.fill(x, y + 10, x + w, y + 11, 0x30FFFFFF);
        ctx.drawText(textRenderer, Text.translatable(key), x, y, Ui.GRAY, true);
        Ui.right(ctx, textRenderer, Text.literal(value), x + w, y, color);
        return y + 15;
    }

    // ------------------------------------------------------------------
    // Spiele
    // ------------------------------------------------------------------

    private void drawGames(DrawContext ctx) {
        int x = left + 10;
        int y = top + HEADER_H + 30;
        int[] col = {x + 20, x + 128, x + 176, x + 250, x + 310, x + 360, x + 400};
        int head = Ui.GOLD;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.stats.col.game"), col[0], y, head, true);
        Ui.right(ctx, textRenderer, Text.translatable("luckycasino.stats.col.rounds"), col[1] + 14, y, head);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.stats.col.winrate"), col[2], y, head, true);
        Ui.right(ctx, textRenderer, Text.translatable("luckycasino.stats.col.wagered"), col[4] - 6, y, head);
        Ui.right(ctx, textRenderer, Text.translatable("luckycasino.stats.col.net"), col[5] - 4, y, head);
        Ui.right(ctx, textRenderer, Text.translatable("luckycasino.stats.col.best"), col[6], y, head);
        ctx.fill(x, y + 11, x + pw - 20, y + 12, 0x60FFFFFF);
        int ry = y + 18;
        for (GameType g : GameType.values()) {
            GameStats gs = casino.profile().stats.get(g);
            ctx.drawItem(Icons.of(g.iconItem()), x, ry - 4);
            ctx.drawText(textRenderer, Text.translatable(g.translationKey()), col[0], ry, Ui.WHITE, true);
            Ui.right(ctx, textRenderer, Text.literal(Fmt.chips(gs.rounds)), col[1] + 14, ry, Ui.WHITE);
            // Win-Rate-Balken
            int bw = 46;
            ctx.fill(col[2], ry + 1, col[2] + bw, ry + 7, 0xFF303030);
            ctx.fill(col[2], ry + 1, col[2] + (int) Math.round(bw * gs.winRate()), ry + 7, 0xFF3FBF5A);
            ctx.drawText(textRenderer, gs.rounds == 0 ? "-" : Fmt.percent(gs.winRate()), col[2] + bw + 4, ry, Ui.WHITE, true);
            Ui.right(ctx, textRenderer, Text.literal(Fmt.compact(gs.wagered)), col[4] - 6, ry, Ui.WHITE);
            long net = gs.net();
            String netText = (net > 0 ? "+" : net < 0 ? "-" : "") + Fmt.compact(Math.abs(net));
            Ui.right(ctx, textRenderer, Text.literal(netText), col[5] - 4, ry, net > 0 ? Ui.GREEN : net < 0 ? Ui.RED : Ui.GRAY);
            Ui.right(ctx, textRenderer, Text.literal(gs.biggestWin > 0 ? Fmt.compact(gs.biggestWin) : "-"), col[6], ry, Ui.GOLD);
            ry += 22;
        }
        Ui.wrapped(ctx, textRenderer, Text.translatable("luckycasino.stats.games_note"), x, top + ph - 24, pw - 20, Ui.DARK_GRAY);
    }

    // ------------------------------------------------------------------
    // Verlauf (Liniendiagramm)
    // ------------------------------------------------------------------

    private void drawChart(DrawContext ctx, int mouseX, int mouseY) {
        List<Long> hist = casino.profile().balanceHistory;
        int cx = left + 52;
        int cy = top + HEADER_H + 30;
        int cw = pw - 66;
        int ch = ph - HEADER_H - 64;
        Ui.box(ctx, cx, cy, cw, ch, 0xC0081008, 0xFF2E4A36);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.stats.chart_title", hist.size()), cx, cy + ch + 6, Ui.GRAY, true);
        if (hist.size() < 2) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.stats.chart_empty"), cx + cw / 2, cy + ch / 2 - 4, Ui.GRAY);
            return;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long v : hist) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (max == min) {
            max = min + 1;
        }
        long pad = Math.max(1, (max - min) / 10);
        min = Math.max(0, min - pad);
        max = max + pad;
        // Rasterlinien mit Beschriftung
        for (int i = 0; i <= 4; i++) {
            long v = min + (max - min) * i / 4;
            int y = cy + ch - 1 - (int) ((ch - 2) * (v - min) / (double) (max - min));
            ctx.fill(cx + 1, y, cx + cw - 1, y + 1, 0x25FFFFFF);
            Ui.right(ctx, textRenderer, Text.literal(Fmt.compact(v)), cx - 3, y - 4, Ui.GRAY);
        }
        // Startguthaben-Linie
        int n = hist.size();
        float px = 0;
        float py = 0;
        int hoverIndex = -1;
        if (mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch) {
            hoverIndex = Math.round((mouseX - cx) / (float) (cw - 2) * (n - 1));
        }
        for (int i = 0; i < n; i++) {
            float x = cx + 1 + (cw - 2) * i / (float) (n - 1);
            float y = cy + ch - 1 - (float) ((ch - 2) * (hist.get(i) - min) / (double) (max - min));
            if (i > 0) {
                // Fläche unter der Linie
                for (int fx = (int) px; fx < (int) x; fx++) {
                    float fy = py + (y - py) * ((fx - px) / Math.max(1f, x - px));
                    ctx.fill(fx, (int) fy, fx + 1, cy + ch - 1, 0x30FFD34D);
                }
                Ui.line(ctx, px, py, x, y, 1, Ui.GOLD);
            }
            px = x;
            py = y;
        }
        if (hoverIndex >= 0 && hoverIndex < n) {
            float hx = cx + 1 + (cw - 2) * hoverIndex / (float) (n - 1);
            float hy = cy + ch - 1 - (float) ((ch - 2) * (hist.get(hoverIndex) - min) / (double) (max - min));
            ctx.fill((int) hx, cy + 1, (int) hx + 1, cy + ch - 1, 0x60FFFFFF);
            Ui.fillCircle(ctx, Math.round(hx), Math.round(hy), 2, Ui.WHITE);
            ctx.drawTooltip(textRenderer, List.of(
                    Text.translatable("luckycasino.stats.chart_point", hoverIndex + 1),
                    Text.literal(Fmt.chips(hist.get(hoverIndex)))), mouseX, mouseY);
        }
    }
}
