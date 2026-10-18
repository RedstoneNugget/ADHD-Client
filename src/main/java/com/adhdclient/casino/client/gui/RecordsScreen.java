package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.core.GameType;
import com.adhdclient.casino.core.progress.Records;
import com.adhdclient.casino.core.progress.WinEntry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Lokale Bestenliste: persönliche Rekorde und die zehn größten Einzelgewinne. */
public class RecordsScreen extends CasinoScreen {

    public RecordsScreen(Screen parent) {
        super(Text.translatable("luckycasino.menu.records"), parent);
    }

    @Override
    protected void initContent() {
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Records r = casino.profile().records;
        int x = left + 10;
        int y = top + HEADER_H + 8;
        int w = 160;
        Ui.box(ctx, x, y, w, ph - HEADER_H - 16, 0xB0000000, 0xFF2E4A36);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.records.personal"), x + 6, y + 6, Ui.GOLD, true);
        int ly = y + 22;
        ly = record(ctx, x + 6, ly, w - 12, "minecraft:gold_block", "luckycasino.records.highest", Fmt.chips(r.highestBalance));
        ly = record(ctx, x + 6, ly, w - 12, "minecraft:blaze_powder", "luckycasino.records.streak", Integer.toString(r.longestWinStreak));
        ly = record(ctx, x + 6, ly, w - 12, "minecraft:glowstone_dust", "luckycasino.records.current_streak", Integer.toString(r.currentWinStreak));
        ly = record(ctx, x + 6, ly, w - 12, "minecraft:emerald", "luckycasino.records.biggest", r.biggestWin > 0 ? Fmt.chips(r.biggestWin) : "-");
        ly = record(ctx, x + 6, ly, w - 12, "minecraft:firework_rocket", "luckycasino.records.crash",
                r.bestCrashMultiplier > 0 ? Fmt.multiplier(r.bestCrashMultiplier) : "-");
        record(ctx, x + 6, ly, w - 12, "minecraft:gold_nugget", "luckycasino.records.coin", Integer.toString(r.longestCoinStreak));

        // Top 10
        int tx = x + w + 8;
        int tw = pw - w - 28;
        Ui.box(ctx, tx, y, tw, ph - HEADER_H - 16, 0xB0000000, 0xFF2E4A36);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.records.top"), tx + 6, y + 6, Ui.GOLD, true);
        int ry = y + 22;
        if (r.topWins.isEmpty()) {
            Ui.wrapped(ctx, textRenderer, Text.translatable("luckycasino.records.empty"), tx + 6, ry, tw - 12, Ui.GRAY);
            return;
        }
        int rank = 1;
        for (WinEntry e : r.topWins) {
            int color = rank == 1 ? 0xFFFFD700 : rank == 2 ? 0xFFC0C0C0 : rank == 3 ? 0xFFCD7F32 : Ui.WHITE;
            ctx.drawText(textRenderer, rank + ".", tx + 6, ry + 4, color, true);
            // Spalten von rechts nach links aufbauen: Datum (klein), Gewinn, Spielname (passt sich an)
            Text date = Text.literal(Fmt.date(e.time));
            float dateScale = 0.75f;
            float dateX = tx + tw - 6 - textRenderer.getWidth(date) * dateScale;
            Ui.scaled(ctx, textRenderer, date, dateX, ry + 5, dateScale, Ui.DARK_GRAY, false);
            String profit = "+" + (e.profit >= 100_000 ? Fmt.compact(e.profit) : Fmt.chips(e.profit));
            int profitRight = (int) dateX - 6;
            Ui.right(ctx, textRenderer, Text.literal(profit), profitRight, ry + 4, Ui.GREEN);
            GameType g = parseGame(e.game);
            if (g != null) {
                Ui.item(ctx, Icons.of(g.iconItem()), tx + 22, ry + 1, 0.85f);
                Text name = Text.translatable(g.translationKey());
                int room = profitRight - textRenderer.getWidth(profit) - 6 - (tx + 40);
                float nameScale = Math.min(1f, room / (float) Math.max(1, textRenderer.getWidth(name)));
                Ui.scaled(ctx, textRenderer, name, tx + 40, ry + 4 + (1 - nameScale) * 4, nameScale, Ui.WHITE, false);
            }
            ry += 17;
            rank++;
        }
    }

    private static GameType parseGame(String name) {
        try {
            return GameType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private int record(DrawContext ctx, int x, int y, int w, String icon, String key, String value) {
        Ui.item(ctx, Icons.of(icon), x, y, 1f);
        ctx.drawText(textRenderer, Text.translatable(key), x + 20, y, Ui.GRAY, true);
        ctx.drawText(textRenderer, Text.literal(value), x + 20, y + 10, Ui.WHITE, true);
        return y + 30;
    }
}
