package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.ClientSession;
import com.adhdclient.casino.client.gui.widget.BetSelector;
import com.adhdclient.casino.core.Odds;
import com.adhdclient.casino.core.RoundResult;
import com.adhdclient.casino.core.coinflip.CoinFlipSeries;
import com.adhdclient.casino.core.coinflip.CoinSide;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

/** Münzwurf: Kopf oder Zahl, 2x Auszahlung, optional "Doppelt oder nichts". */
public class CoinFlipScreen extends CasinoScreen {

    private static final int COIN_R = 34;

    private final BetSelector bets;
    private ButtonWidget headsButton;
    private ButtonWidget tailsButton;
    private ButtonWidget flipButton;
    private ButtonWidget cashOutButton;

    private boolean flipping;
    private long flipStart;
    private int halfTurns;
    private CoinSide startFace = CoinSide.HEADS;
    private CoinSide shownFace = CoinSide.HEADS;
    private boolean lastWin;
    private RoundResult lastRound;
    private long resultAt;

    public CoinFlipScreen(Screen parent) {
        super(Text.translatable("luckycasino.game.coinflip"), parent);
        this.bets = new BetSelector(casino);
        CoinFlipSeries s = ClientSession.coinSeries;
        if (s != null && s.lastResult() != null) {
            shownFace = s.lastResult();
        }
    }

    private int coinCx() {
        return left + 140;
    }

    private int coinCy() {
        return top + 92;
    }

    private CoinFlipSeries series() {
        CoinFlipSeries s = ClientSession.coinSeries;
        return s != null && s.isActive() ? s : null;
    }

    @Override
    protected void initContent() {
        bets.init(this::addDrawableChild, textRenderer, left + 10, top + ph - 24);
        int cx = coinCx();
        headsButton = addDrawableChild(ButtonWidget.builder(Text.translatable(CoinSide.HEADS.translationKey()),
                b -> choose(CoinSide.HEADS)).dimensions(cx - 74, top + 150, 72, 18).build());
        tailsButton = addDrawableChild(ButtonWidget.builder(Text.translatable(CoinSide.TAILS.translationKey()),
                b -> choose(CoinSide.TAILS)).dimensions(cx + 2, top + 150, 72, 18).build());
        flipButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> flip())
                .dimensions(cx - 74, top + 172, 148, 20).build());
        cashOutButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> cashOut())
                .dimensions(cx + 80, top + 172, 96, 20).build());
        updateButtons();
    }

    private void choose(CoinSide side) {
        ClientSession.coinChoice = side;
        CasinoSounds.click();
        updateButtons();
    }

    private void updateButtons() {
        CoinFlipSeries s = series();
        boolean running = s != null && s.streak() > 0;
        CoinSide choice = ClientSession.coinChoice;
        headsButton.setMessage(Text.translatable(CoinSide.HEADS.translationKey()).copy()
                .withColor(choice == CoinSide.HEADS ? Ui.GOLD : Ui.WHITE));
        tailsButton.setMessage(Text.translatable(CoinSide.TAILS.translationKey()).copy()
                .withColor(choice == CoinSide.TAILS ? Ui.GOLD : Ui.WHITE));
        headsButton.active = !flipping;
        tailsButton.active = !flipping;
        flipButton.setMessage(Text.translatable(running ? "luckycasino.coin.double" : "luckycasino.coin.flip"));
        flipButton.active = !flipping && (running ? s.canFlip() : bets.affordable());
        cashOutButton.visible = running;
        cashOutButton.active = !flipping && running;
        if (running) {
            cashOutButton.setMessage(Text.translatable("luckycasino.coin.cashout", Fmt.chips(s.pot())));
        }
        bets.setLocked(flipping || running);
    }

    private void flip() {
        if (flipping) {
            return;
        }
        CoinFlipSeries s = series();
        if (s == null || s.streak() == 0) {
            s = casino.startCoinFlip(bets.bet());
            if (s == null) {
                CasinoSounds.error();
                return;
            }
            ClientSession.coinSeries = s;
        }
        freezeBalance(casino.balance());
        lastRound = casino.flipCoin(s, ClientSession.coinChoice);
        lastWin = lastRound == null;
        CoinSide result = s.lastResult();
        startFace = shownFace;
        halfTurns = 9 + (result == startFace ? 1 : 0) + 2 * (int) (Math.random() * 2);
        flipStart = now();
        flipping = true;
        CasinoSounds.coinFlip();
        updateButtons();
    }

    @Override
    protected boolean primaryAction() {
        if (flipButton.active) {
            flip();
        }
        return true;
    }

    private long flipDuration() {
        return dur(1_400);
    }

    @Override
    public void tick() {
        if (flipping && now() - flipStart >= flipDuration()) {
            finishFlip();
        }
        if (!flipping) {
            updateButtons();
        }
    }

    private void finishFlip() {
        flipping = false;
        CoinFlipSeries s = ClientSession.coinSeries;
        shownFace = s.lastResult();
        ClientSession.remember(ClientSession.coinHistory, shownFace, 12);
        resultAt = now();
        unfreezeBalance();
        CasinoSounds.coinLand();
        if (lastWin) {
            CasinoSounds.win();
        } else {
            CasinoSounds.heavyLoss();
            CasinoSounds.lose();
        }
        bets.refresh();
        updateButtons();
    }

    private void cashOut() {
        CoinFlipSeries s = series();
        if (s == null || !s.canCashOut() || flipping) {
            return;
        }
        lastRound = casino.cashOutCoin(s);
        lastWin = true;
        resultAt = now();
        CasinoSounds.cashOut();
        if (s.streak() >= 3) {
            CasinoSounds.bigWin();
        }
        bets.refresh();
        updateButtons();
    }

    @Override
    public void finishPendingRounds() {
        if (flipping) {
            flipping = false;
            CoinFlipSeries s = ClientSession.coinSeries;
            if (s != null) {
                shownFace = s.lastResult();
                ClientSession.remember(ClientSession.coinHistory, shownFace, 12);
            }
        }
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = now();
        int cx = coinCx();
        int cy = coinCy();
        float squash = 1f;
        float lift = 0;
        CoinSide face = shownFace;
        if (flipping) {
            float t = Ui.clamp01((now - flipStart) / (float) flipDuration());
            double theta = halfTurns * Math.PI * Ui.easeOutCubic(t);
            squash = (float) Math.abs(Math.cos(theta));
            long half = (long) Math.floor((theta + Math.PI / 2) / Math.PI);
            face = half % 2 == 0 ? startFace : startFace.opposite();
            lift = (float) Math.sin(Math.PI * t) * 48;
        }
        // Schatten auf dem Tisch
        int shadowW = (int) (COIN_R * (1 - lift / 110f));
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy + COIN_R + 10);
        m.scale(1f, 0.22f);
        Ui.fillCircle(ctx, 0, 0, shadowW, 0x60000000);
        m.popMatrix();

        drawCoin(ctx, cx, cy - lift, Math.max(0.06f, squash), face);

        drawResult(ctx, now);
        drawInfo(ctx);
        bets.renderLabel(ctx, textRenderer);
    }

    private void drawCoin(DrawContext ctx, float cx, float cy, float squash, CoinSide face) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.scale(1f, squash);
        Ui.fillCircle(ctx, 0, 0, COIN_R, 0xFF9A6B0A);
        Ui.fillCircle(ctx, 0, 0, COIN_R - 2, 0xFFE8B830);
        Ui.fillCircle(ctx, 0, 0, COIN_R - 5, 0xFFC99A1E);
        Ui.fillCircle(ctx, 0, 0, COIN_R - 7, 0xFFF2C94C);
        // Riffelung am Rand
        for (int i = 0; i < 24; i++) {
            m.pushMatrix();
            m.rotate((float) (i * Math.PI / 12));
            ctx.fill(COIN_R - 5, 0, COIN_R - 3, 1, 0xFFB8860B);
            m.popMatrix();
        }
        int dark = 0xFF8B6508;
        if (face == CoinSide.HEADS) {
            // Krone
            ctx.fill(-13, 1, 13, 9, dark);
            ctx.fill(-13, -9, -8, 1, dark);
            ctx.fill(-3, -13, 3, 1, dark);
            ctx.fill(8, -9, 13, 1, dark);
            ctx.fill(-11, 3, -7, 7, 0xFFD02020);
            ctx.fill(-2, 3, 2, 7, 0xFF2050D0);
            ctx.fill(7, 3, 11, 7, 0xFFD02020);
            ctx.fill(-1, -16, 1, -13, dark);
            ctx.fill(-14, 12, 14, 14, dark);
        } else {
            Ui.scaled(ctx, textRenderer, Text.literal("1"), 0, -12, 3f, dark, true);
        }
        m.popMatrix();
    }

    private void drawResult(DrawContext ctx, long now) {
        int cx = coinCx();
        int y = top + 136;
        if (flipping) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.coin.flipping"), cx, y, Ui.GRAY);
            return;
        }
        if (resultAt == 0) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.coin.hint"), cx, y, Ui.GRAY);
            return;
        }
        CoinFlipSeries s = ClientSession.coinSeries;
        Text t;
        int color;
        if (lastRound != null && lastRound.isLoss()) {
            t = Text.translatable("luckycasino.coin.lost", Text.translatable(shownFace.translationKey()), Fmt.chips(lastRound.stake()));
            color = 0xFFFF8080;
        } else if (lastRound != null) {
            t = Text.translatable("luckycasino.coin.cashed", Fmt.signed(lastRound.profit()));
            color = Ui.GREEN;
        } else if (s != null) {
            t = Text.translatable("luckycasino.coin.hit", Text.translatable(shownFace.translationKey()), Fmt.chips(s.pot()));
            color = Ui.GOLD;
        } else {
            return;
        }
        float a = Ui.clamp01((now - resultAt) / 200f);
        Ui.scaled(ctx, textRenderer, t, cx, y - 2, 0.9f + 0.1f * a, color, true);
        if (lastRound != null && lastRound.bonusApplied()) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.result.bonus", lastRound.bonusMultiplier()), cx, y + 9, 0xFFFFB0FF);
        }
    }

    private void drawInfo(DrawContext ctx) {
        int x = left + 262;
        int y = top + 30;
        Ui.box(ctx, x, y, 148, 168, 0xB0000000, 0xFF2E4A36);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.coin.series"), x + 6, y + 6, Ui.GOLD, true);
        CoinFlipSeries s = series();
        int streak = s == null ? 0 : s.streak();
        long pot = s == null ? 0 : s.pot();
        int ly = y + 20;
        ly = kv(ctx, x, ly, "luckycasino.coin.streak", Integer.toString(streak), Ui.WHITE);
        ly = kv(ctx, x, ly, "luckycasino.coin.pot", Fmt.chips(pot), Ui.GOLD);
        ly = kv(ctx, x, ly, "luckycasino.coin.next", s != null && streak > 0 ? Fmt.chips(pot * Odds.COIN_PAYOUT) : "-", Ui.GREEN);
        ly = kv(ctx, x, ly, "luckycasino.coin.chance", Fmt.percent(casino.rng().odds().coinWinChance()), Ui.WHITE);
        ly = kv(ctx, x, ly, "luckycasino.coin.best", Integer.toString(casino.profile().records.longestCoinStreak), Ui.WHITE);
        ly += 4;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.coin.legend"), x + 6, ly, Ui.DARK_GRAY, true);
        ly += 14;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.gui.history"), x + 6, ly, Ui.GRAY, true);
        ly += 12;
        int hx = x + 6;
        for (CoinSide side : ClientSession.coinHistory) {
            Ui.fillCircle(ctx, hx + 5, ly + 5, 5, 0xFF9A6B0A);
            Ui.fillCircle(ctx, hx + 5, ly + 5, 4, 0xFFF2C94C);
            Ui.centered(ctx, textRenderer, Text.translatable(side.translationKey() + ".short"), hx + 6, ly + 1, 0xFF8B6508);
            hx += 12;
            if (hx > x + 136) {
                break;
            }
        }
        if (s != null && streak > 0) {
            Ui.wrapped(ctx, textRenderer, Text.translatable("luckycasino.coin.decide"), x + 6, y + 136, 136, 0xFFFFE9A8);
        }
    }

    private int kv(DrawContext ctx, int x, int y, String key, String value, int color) {
        ctx.drawText(textRenderer, Text.translatable(key), x + 6, y, Ui.GRAY, true);
        Ui.right(ctx, textRenderer, Text.literal(value), x + 142, y, color);
        return y + 12;
    }
}
