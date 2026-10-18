package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.ClientSession;
import com.adhdclient.casino.client.gui.widget.BetSelector;
import com.adhdclient.casino.core.RoundResult;
import com.adhdclient.casino.core.mines.MinesGame;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Minenfeld: sichere Felder aufdecken, Multiplikator steigt, rechtzeitig auszahlen. */
public class MinesScreen extends CasinoScreen {

    private static final int TILE = 30;
    private static final int GAP = 4;

    /** Gewählte Minenanzahl bleibt für die Sitzung erhalten. */
    private static int mineIndex = 1;

    private final BetSelector bets;
    private final ButtonWidget[] mineButtons = new ButtonWidget[MinesGame.MINE_OPTIONS.length];
    private ButtonWidget actionButton;
    private RoundResult lastResult;
    private final long[] revealedAt = new long[MinesGame.TILES];

    public MinesScreen(Screen parent) {
        super(Text.translatable("luckycasino.game.mines"), parent);
        this.bets = new BetSelector(casino);
    }

    private MinesGame game() {
        return ClientSession.mines;
    }

    private boolean running() {
        return game() != null && !game().isFinished();
    }

    private int gridX() {
        return left + 22;
    }

    private int gridY() {
        return top + 32;
    }

    @Override
    protected void initContent() {
        bets.init(this::addDrawableChild, textRenderer, left + 10, top + ph - 24);
        int x = left + 210;
        for (int i = 0; i < MinesGame.MINE_OPTIONS.length; i++) {
            int idx = i;
            mineButtons[i] = addDrawableChild(ButtonWidget.builder(Text.literal(String.valueOf(MinesGame.MINE_OPTIONS[i])), b -> {
                mineIndex = idx;
                CasinoSounds.click();
                update();
            }).dimensions(x + i * 40, top + 44, 36, 16).build());
        }
        actionButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> action())
                .dimensions(x, top + 66, 196, 22).build());
        update();
    }

    private void update() {
        boolean running = running();
        for (int i = 0; i < mineButtons.length; i++) {
            mineButtons[i].active = !running && i != mineIndex;
        }
        MinesGame g = game();
        if (running) {
            boolean canCash = g.canCashOut();
            actionButton.setMessage(canCash
                    ? Text.translatable("luckycasino.mines.cashout", Fmt.chips((long) Math.floor(g.stake() * g.currentMultiplier())))
                    : Text.translatable("luckycasino.mines.pick"));
            actionButton.active = canCash;
        } else {
            actionButton.setMessage(Text.translatable("luckycasino.mines.start"));
            actionButton.active = bets.affordable();
        }
        bets.setLocked(running);
    }

    private void action() {
        MinesGame g = game();
        if (g != null && !g.isFinished()) {
            if (g.canCashOut()) {
                lastResult = casino.cashOutMines(g);
                CasinoSounds.cashOut();
                if (g.currentMultiplier() >= 5) {
                    CasinoSounds.bigWin();
                }
                bets.refresh();
            }
        } else {
            MinesGame ng = casino.startMines(bets.bet(), MinesGame.MINE_OPTIONS[mineIndex]);
            if (ng == null) {
                CasinoSounds.error();
                return;
            }
            ClientSession.mines = ng;
            lastResult = null;
            java.util.Arrays.fill(revealedAt, 0);
            CasinoSounds.play(net.minecraft.sound.SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 0.6f);
        }
        update();
    }

    @Override
    protected boolean primaryAction() {
        action();
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        MinesGame g = game();
        if (click.button() != 0 || g == null || g.isFinished()) {
            return false;
        }
        int tile = tileAt(click.x(), click.y());
        if (tile < 0 || !g.canReveal(tile)) {
            return false;
        }
        revealedAt[tile] = now();
        RoundResult r = casino.revealMine(g, tile);
        if (g.isMine(tile)) {
            lastResult = r;
            CasinoSounds.explode();
            CasinoSounds.heavyLoss();
        } else {
            CasinoSounds.play(net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f + 0.06f * g.safeRevealed(), 1.0f);
            if (r != null) { // alle sicheren Felder gefunden
                lastResult = r;
                CasinoSounds.jackpot();
            }
        }
        bets.refresh();
        update();
        return true;
    }

    private int tileAt(double mx, double my) {
        for (int i = 0; i < MinesGame.TILES; i++) {
            int tx = gridX() + (i % 5) * (TILE + GAP);
            int ty = gridY() + (i / 5) * (TILE + GAP);
            if (mx >= tx && mx < tx + TILE && my >= ty && my < ty + TILE) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void tick() {
        update();
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = now();
        MinesGame g = game();
        int hover = tileAt(mouseX, mouseY);
        for (int i = 0; i < MinesGame.TILES; i++) {
            int tx = gridX() + (i % 5) * (TILE + GAP);
            int ty = gridY() + (i / 5) * (TILE + GAP);
            boolean revealed = g != null && g.isRevealed(i);
            boolean showMine = g != null && g.isFinished() && g.isMine(i);
            if (revealed || showMine) {
                boolean mine = g.isMine(i);
                float pop = revealed ? Ui.clamp01((now - revealedAt[i]) / 150f) : 1f;
                int bg = mine ? (i == g.explodedTile() ? 0xFFD02020 : 0xFF5A1A1A) : 0xFF1F7A3A;
                ctx.fill(tx, ty, tx + TILE, ty + TILE, bg);
                ctx.drawStrokedRectangle(tx, ty, TILE, TILE, Ui.darken(bg, 0.4f));
                float s = 1.2f * Ui.easeOutBack(pop);
                Ui.item(ctx, Icons.of(mine ? "minecraft:tnt" : "minecraft:emerald"),
                        tx + TILE / 2f - 8 * s, ty + TILE / 2f - 8 * s, Math.max(0.05f, s));
                if (!revealed) {
                    ctx.fill(tx, ty, tx + TILE, ty + TILE, 0x70000000);
                }
            } else {
                boolean hl = i == hover && running();
                Ui.bevel(ctx, tx, ty, TILE, TILE, hl ? 0xFF8A8A9A : 0xFF6A6A78);
                ctx.fill(tx + 4, ty + 4, tx + TILE - 4, ty + TILE - 4, hl ? 0xFF7A7A8A : 0xFF5C5C68);
            }
        }
        drawSidePanel(ctx, g);
        bets.renderLabel(ctx, textRenderer);
    }

    private void drawSidePanel(DrawContext ctx, MinesGame g) {
        int x = left + 210;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.mines.count"), x, top + 32, Ui.GOLD, true);
        // Markierung der gewählten Minenanzahl
        ButtonWidget sel = mineButtons[mineIndex];
        ctx.drawStrokedRectangle(sel.getX() - 1, sel.getY() - 1, sel.getWidth() + 2, sel.getHeight() + 2, Ui.GOLD);

        int y = top + 96;
        int mines = g != null && !g.isFinished() ? g.mines() : MinesGame.MINE_OPTIONS[mineIndex];
        double edge = casino.rng().odds().minesHouseEdge();
        if (g != null && !g.isFinished()) {
            kv(ctx, x, y, "luckycasino.mines.current", Fmt.multiplier(g.currentMultiplier()), Ui.GOLD);
            kv(ctx, x, y + 12, "luckycasino.mines.next", Fmt.multiplier(g.nextMultiplier()), Ui.GREEN);
            kv(ctx, x, y + 24, "luckycasino.mines.chance", Fmt.percent(g.nextSafeChance()), Ui.WHITE);
            kv(ctx, x, y + 36, "luckycasino.mines.found", g.safeRevealed() + " / " + (MinesGame.TILES - g.mines()), Ui.WHITE);
        } else {
            kv(ctx, x, y, "luckycasino.mines.first", Fmt.multiplier(MinesGame.multiplierFor(mines, 1, edge)), Ui.GREEN);
            kv(ctx, x, y + 12, "luckycasino.mines.five", Fmt.multiplier(MinesGame.multiplierFor(mines, Math.min(5, 25 - mines), edge)), Ui.GOLD);
            Ui.wrapped(ctx, textRenderer, Text.translatable("luckycasino.mines.help"), x, y + 30, 196, Ui.DARK_GRAY);
        }
        if (lastResult != null && g != null && g.isFinished()) {
            Text t = lastResult.isWin() ? Text.translatable("luckycasino.result.win", Fmt.signed(lastResult.profit()))
                    : Text.translatable("luckycasino.mines.boom", Fmt.chips(lastResult.stake()));
            Ui.scaled(ctx, textRenderer, t, x, top + 160, 1.2f, lastResult.isWin() ? Ui.GREEN : 0xFFFF6060, false);
            if (lastResult.bonusApplied()) {
                ctx.drawText(textRenderer, Text.translatable("luckycasino.result.bonus", lastResult.bonusMultiplier()), x, top + 175, 0xFFFFB0FF, true);
            }
        }
    }

    private void kv(DrawContext ctx, int x, int y, String key, String value, int color) {
        ctx.drawText(textRenderer, Text.translatable(key), x, y, Ui.GRAY, true);
        Ui.right(ctx, textRenderer, Text.literal(value), x + 196, y, color);
    }
}
