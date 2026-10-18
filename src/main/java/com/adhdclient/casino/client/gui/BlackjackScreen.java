package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.ClientSession;
import com.adhdclient.casino.client.gui.widget.BetSelector;
import com.adhdclient.casino.core.RoundResult;
import com.adhdclient.casino.core.blackjack.BlackjackGame;
import com.adhdclient.casino.core.blackjack.BlackjackHand;
import com.adhdclient.casino.core.blackjack.BlackjackRules;
import com.adhdclient.casino.core.blackjack.Card;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/** Blackjack gegen den Dealer: Ziehen, Halten, Verdoppeln. Karten werden selbst gezeichnet. */
public class BlackjackScreen extends CasinoScreen {

    private static final int CARD_W = 32;
    private static final int CARD_H = 44;
    private static final int CARD_STEP = 36;
    private static final long SLIDE_MS = 260;

    private final BetSelector bets;
    private ButtonWidget dealButton;
    private ButtonWidget hitButton;
    private ButtonWidget standButton;
    private ButtonWidget doubleButton;

    // Zeitplan der Animation
    private final List<Long> playerTimes = new ArrayList<>();
    private final List<Long> dealerTimes = new ArrayList<>();
    private long holeRevealAt = -1;
    private long resultAt = -1;
    private long nextSlot;
    private int soundsPlayed;
    private RoundResult round;

    public BlackjackScreen(Screen parent) {
        super(Text.translatable("luckycasino.game.blackjack"), parent);
        this.bets = new BetSelector(casino);
        BlackjackGame g = ClientSession.blackjack;
        if (g != null) {
            // Laufende Hand aus der Sitzung sofort sichtbar machen
            long past = now() - 10_000;
            for (int i = 0; i < g.player().size(); i++) {
                playerTimes.add(past);
            }
            for (int i = 0; i < g.dealer().size(); i++) {
                dealerTimes.add(past);
            }
            if (g.isFinished()) {
                holeRevealAt = past;
                resultAt = past;
            }
            soundsPlayed = Integer.MAX_VALUE;
        }
    }

    private BlackjackGame game() {
        return ClientSession.blackjack;
    }

    private long gap() {
        return dur(380);
    }

    private int handX() {
        return left + 24;
    }

    private int dealerY() {
        return top + 42;
    }

    private int playerY() {
        return top + 114;
    }

    @Override
    protected void initContent() {
        bets.init(this::addDrawableChild, textRenderer, left + 10, top + ph - 24);
        int x = handX();
        int y = top + 172;
        dealButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.blackjack.deal"), b -> deal())
                .dimensions(x, y, 100, 20).build());
        hitButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.blackjack.hit"), b -> act(0))
                .dimensions(x, y, 70, 20).build());
        standButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.blackjack.stand"), b -> act(1))
                .dimensions(x + 74, y, 70, 20).build());
        doubleButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.blackjack.double"), b -> act(2))
                .dimensions(x + 148, y, 84, 20).build());
        updateButtons();
    }

    private boolean animating() {
        long now = now();
        if (resultAt > 0) {
            return now < resultAt;
        }
        return now < nextSlot;
    }

    private void updateButtons() {
        BlackjackGame g = game();
        boolean inHand = g != null && !g.isFinished();
        boolean busy = animating();
        dealButton.visible = !inHand;
        dealButton.active = !busy && bets.affordable();
        hitButton.visible = inHand;
        standButton.visible = inHand;
        doubleButton.visible = inHand;
        hitButton.active = !busy;
        standButton.active = !busy;
        doubleButton.active = !busy && g != null && casino.canDouble(g);
        bets.setLocked(inHand || busy);
    }

    private void deal() {
        if (animating()) {
            return;
        }
        BlackjackGame g = casino.startBlackjack(bets.bet());
        if (g == null) {
            CasinoSounds.error();
            return;
        }
        ClientSession.blackjack = g;
        playerTimes.clear();
        dealerTimes.clear();
        holeRevealAt = -1;
        resultAt = -1;
        round = null;
        soundsPlayed = 0;
        nextSlot = now();
        // Reihenfolge wie am Tisch: Spieler, Dealer, Spieler, Dealer
        playerTimes.add(slot());
        dealerTimes.add(slot());
        playerTimes.add(slot());
        dealerTimes.add(slot());
        if (g.isFinished()) {
            onFinished();
        }
        updateButtons();
    }

    @Override
    protected boolean primaryAction() {
        BlackjackGame g = game();
        if (g == null || g.isFinished()) {
            deal();
            return true;
        }
        return false;
    }

    private long slot() {
        long t = Math.max(nextSlot, now());
        nextSlot = t + gap();
        return t;
    }

    /** 0 = Ziehen, 1 = Halten, 2 = Verdoppeln. */
    private void act(int action) {
        BlackjackGame g = game();
        if (g == null || g.isFinished() || animating()) {
            return;
        }
        switch (action) {
            case 0 -> round = casino.blackjackHit(g);
            case 1 -> round = casino.blackjackStand(g);
            default -> {
                round = casino.blackjackDouble(g);
                if (round == null && !g.isFinished()) {
                    CasinoSounds.error();
                    return;
                }
            }
        }
        while (playerTimes.size() < g.player().size()) {
            playerTimes.add(slot());
        }
        if (g.isFinished()) {
            onFinished();
        }
        updateButtons();
    }

    /** Hand ist logisch vorbei: Aufdecken und Dealer-Karten einplanen. */
    private void onFinished() {
        BlackjackGame g = game();
        if (round == null) {
            // Natürlicher Blackjack direkt beim Austeilen – wurde bereits abgerechnet
            round = casino.lastRound();
        }
        freezeBalance(casino.balance() - round.payout());
        holeRevealAt = slot();
        while (dealerTimes.size() < g.dealer().size()) {
            dealerTimes.add(slot());
        }
        resultAt = slot();
    }

    @Override
    public void tick() {
        if (resultAt > 0 && now() >= resultAt && isAnimating()) {
            unfreezeBalance();
            playResultSound();
            bets.refresh();
        }
        updateButtons();
    }

    private void playResultSound() {
        BlackjackGame g = game();
        if (g == null || g.outcome() == null) {
            return;
        }
        switch (g.outcome()) {
            case BLACKJACK -> CasinoSounds.jackpot();
            case WIN, DEALER_BUST -> CasinoSounds.win();
            case PUSH -> CasinoSounds.push();
            case BUST -> {
                CasinoSounds.heavyLoss();
                CasinoSounds.lose();
            }
            default -> CasinoSounds.lose();
        }
    }

    @Override
    public void finishPendingRounds() {
        // Hand bleibt in der Sitzung erhalten; nur die Anzeige-Animation abschließen
        if (isAnimating()) {
            unfreezeBalance();
        }
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = now();
        BlackjackGame g = game();
        drawShoeAndRules(ctx);
        bets.renderLabel(ctx, textRenderer);
        if (g == null) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.blackjack.hint"), left + 130, top + 100, Ui.GRAY);
            return;
        }
        // Kartensounds, sobald eine Karte zu rutschen beginnt
        int started = countStarted(playerTimes, now) + countStarted(dealerTimes, now);
        if (started > soundsPlayed && soundsPlayed != Integer.MAX_VALUE) {
            soundsPlayed = started;
            CasinoSounds.cardDeal();
        }

        boolean holeHidden = holeRevealAt < 0 || now < holeRevealAt;
        drawHand(ctx, g.dealer(), dealerTimes, dealerY(), now, holeHidden);
        drawHand(ctx, g.player(), playerTimes, playerY(), now, false);

        // Punktzahlen
        int dealerVisible = countVisible(dealerTimes, now);
        int playerVisible = countVisible(playerTimes, now);
        Text dealerLabel = holeHidden
                ? Text.translatable("luckycasino.blackjack.dealer_shows", g.dealer().totalOfFirst(Math.min(1, dealerVisible)))
                : Text.translatable("luckycasino.blackjack.dealer", g.dealer().totalOfFirst(dealerVisible));
        ctx.drawText(textRenderer, dealerLabel, handX(), dealerY() - 12, Ui.WHITE, true);
        Text playerLabel = Text.translatable("luckycasino.blackjack.player", scoreText(g.player(), playerVisible));
        ctx.drawText(textRenderer, playerLabel, handX(), playerY() - 12, Ui.WHITE, true);
        Text stake = Text.translatable("luckycasino.blackjack.stake", Fmt.chips(g.stake()));
        ctx.drawText(textRenderer, stake, handX() + 150, playerY() - 12, g.isDoubled() ? Ui.GOLD : Ui.GRAY, true);
    }

    private String scoreText(BlackjackHand hand, int visible) {
        BlackjackHand h = new BlackjackHand();
        for (int i = 0; i < visible; i++) {
            h.add(hand.cards().get(i));
        }
        int total = h.total();
        if (h.isSoft() && total < 21) {
            return (total - 10) + " / " + total;
        }
        return Integer.toString(total);
    }

    private static int countStarted(List<Long> times, long now) {
        int n = 0;
        for (long t : times) {
            if (now >= t) {
                n++;
            }
        }
        return n;
    }

    private static int countVisible(List<Long> times, long now) {
        int n = 0;
        for (long t : times) {
            if (now >= t + SLIDE_MS) {
                n++;
            }
        }
        return n;
    }

    private void drawHand(DrawContext ctx, BlackjackHand hand, List<Long> times, int y, long now, boolean holeHidden) {
        float shoeX = left + 316;
        float shoeY = top + 40;
        for (int i = 0; i < hand.size() && i < times.size(); i++) {
            long t0 = times.get(i);
            if (now < t0) {
                continue;
            }
            float t = Ui.easeOutCubic((now - t0) / (float) dur(SLIDE_MS));
            float tx = handX() + i * CARD_STEP;
            float x = shoeX + (tx - shoeX) * t;
            float cy = shoeY + (y - shoeY) * t;
            boolean faceUp = !(holeHidden && i == 1 && times == dealerTimes);
            float flip = 1f;
            if (i == 1 && times == dealerTimes && holeRevealAt > 0 && now >= holeRevealAt) {
                float ft = Ui.clamp01((now - holeRevealAt) / 300f);
                flip = Math.abs((float) Math.cos(ft * Math.PI));
                faceUp = ft >= 0.5f;
            }
            drawCard(ctx, hand.cards().get(i), x, cy, faceUp, flip);
        }
    }

    private void drawCard(DrawContext ctx, Card card, float x, float y, boolean faceUp, float scaleX) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x + CARD_W / 2f, y);
        m.scale(Math.max(0.05f, scaleX), 1f);
        m.translate(-CARD_W / 2f, 0);
        ctx.fill(1, 1, CARD_W + 1, CARD_H + 1, 0x60000000);
        if (faceUp) {
            ctx.fill(1, 0, CARD_W - 1, CARD_H, 0xFFFAFAF5);
            ctx.fill(0, 1, CARD_W, CARD_H - 1, 0xFFFAFAF5);
            ctx.drawStrokedRectangle(0, 0, CARD_W, CARD_H, 0xFF9E9E9E);
            int color = card.suit().isRed() ? 0xFFC62828 : 0xFF1A1A1A;
            String rank = card.rankLabel();
            String suit = String.valueOf(card.suit().symbol());
            ctx.drawText(textRenderer, rank, 3, 3, color, false);
            ctx.drawText(textRenderer, suit, 3, 12, color, false);
            Ui.scaled(ctx, textRenderer, Text.literal(suit), CARD_W / 2f, CARD_H / 2f - 7, 2f, color, true);
            m.pushMatrix();
            m.translate(CARD_W - 3, CARD_H - 3);
            m.rotate((float) Math.PI);
            ctx.drawText(textRenderer, rank, 0, 0, color, false);
            m.popMatrix();
        } else {
            ctx.fill(1, 0, CARD_W - 1, CARD_H, 0xFFF0F0F0);
            ctx.fill(0, 1, CARD_W, CARD_H - 1, 0xFFF0F0F0);
            ctx.fill(2, 2, CARD_W - 2, CARD_H - 2, 0xFFA01818);
            for (int py = 4; py < CARD_H - 4; py += 4) {
                for (int px = 4 + (py / 4 % 2) * 2; px < CARD_W - 4; px += 4) {
                    ctx.fill(px, py, px + 2, py + 2, 0xFFD04040);
                }
            }
            ctx.drawStrokedRectangle(0, 0, CARD_W, CARD_H, 0xFF7A1010);
        }
        m.popMatrix();
    }

    private void drawShoeAndRules(DrawContext ctx) {
        int x = left + 290;
        int y = top + 30;
        Ui.box(ctx, x, y, 120, 168, 0xB0000000, 0xFF2E4A36);
        // Schlitten
        for (int i = 0; i < 4; i++) {
            int cx = x + 26 + i * 2;
            int cy = y + 10 + i;
            ctx.fill(cx, cy, cx + CARD_W, cy + CARD_H, 0xFFF0F0F0);
            ctx.fill(cx + 2, cy + 2, cx + CARD_W - 2, cy + CARD_H - 2, 0xFFA01818);
            ctx.drawStrokedRectangle(cx, cy, CARD_W, CARD_H, 0xFF7A1010);
        }
        ctx.fill(x + 20, y + 40, x + 70, y + 58, 0xFF3A2A1A);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.blackjack.shoe", casino.shoeRemaining()), x + 6, y + 64, Ui.GRAY, true);

        BlackjackRules rules = casino.blackjackRules();
        int ly = y + 82;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.blackjack.rules"), x + 6, ly, Ui.GOLD, true);
        ly += 12;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.blackjack.rule.decks", rules.decks()), x + 6, ly, Ui.WHITE, true);
        ly += 11;
        ctx.drawText(textRenderer, Text.translatable(rules.blackjackPayout() >= 1.5 ? "luckycasino.blackjack.rule.pays32"
                : "luckycasino.blackjack.rule.pays65"), x + 6, ly, Ui.WHITE, true);
        ly += 11;
        ctx.drawText(textRenderer, Text.translatable(rules.dealerHitsSoft17() ? "luckycasino.blackjack.rule.h17"
                : "luckycasino.blackjack.rule.s17"), x + 6, ly, Ui.WHITE, true);
        ly += 11;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.blackjack.rule.double"), x + 6, ly, Ui.WHITE, true);
        ly += 11;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.blackjack.rule.nosplit"), x + 6, ly, Ui.DARK_GRAY, true);
    }

    @Override
    protected void renderOverlay(DrawContext ctx, int mouseX, int mouseY, float delta) {
        BlackjackGame g = game();
        if (g == null || !g.isFinished() || resultAt < 0 || now() < resultAt || round == null && g.outcome() == null) {
            if (!bets.affordable() && (g == null || g.isFinished())) {
                ctx.drawText(textRenderer, Text.translatable("luckycasino.gui.not_enough"), handX() + 106, top + 178, Ui.RED, true);
            }
            return;
        }
        BlackjackGame.Outcome o = g.outcome();
        int color = o.isWin() ? (o == BlackjackGame.Outcome.BLACKJACK ? Ui.GOLD : Ui.GREEN)
                : o == BlackjackGame.Outcome.PUSH ? 0xFFE0E0E0 : Ui.RED;
        long profit = round != null ? round.profit() : g.payout() - g.stake();
        Text line2 = profit == 0 ? Text.translatable("luckycasino.result.push_refund")
                : round != null && round.bonusApplied()
                ? Text.translatable("luckycasino.result.chips_bonus", Fmt.signed(profit), round.bonusMultiplier())
                : Text.translatable("luckycasino.result.chips", Fmt.signed(profit));
        float appear = Ui.clamp01((now() - resultAt) / 250f);
        banner(ctx, Text.translatable(o.translationKey()), line2, left + 150, top + 96, color, appear);
    }
}
