package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.gui.widget.BetSelector;
import com.adhdclient.casino.core.RoundResult;
import com.adhdclient.casino.core.plinko.Plinko;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Plinko: Kugeln fallen durch 12 Stiftreihen in Fächer mit Multiplikatoren. Mehrere Kugeln gleichzeitig möglich. */
public class PlinkoScreen extends CasinoScreen {

    private static final int SPACING = 18;
    private static final int ROW_H = 12;
    private static final int MAX_BALLS = 100;
    /** Angezeigte Verlaufseinträge (3 Zeilen × 3). */
    private static final int HISTORY_SHOWN = 9;

    /** Risikostufe bleibt für die Sitzung erhalten. */
    private static Plinko.Risk risk = Plinko.Risk.MEDIUM;
    private static final Deque<Double> HISTORY = new ArrayDeque<>();

    private final BetSelector bets;
    private ButtonWidget dropButton;
    private ButtonWidget riskButton;
    private final List<Ball> balls = new ArrayList<>();
    private final long[] bucketFlash = new long[Plinko.ROWS + 1];
    private RoundResult lastRound;
    private double lastMultiplier;

    /** Eine fallende Kugel (bereits abgerechnet, nur noch Optik). */
    private static final class Ball {
        final Plinko.Drop drop;
        final RoundResult round;
        final long start;
        boolean landed;

        Ball(Plinko.Drop drop, RoundResult round, long start) {
            this.drop = drop;
            this.round = round;
            this.start = start;
        }
    }

    public PlinkoScreen(Screen parent) {
        super(Text.translatable("luckycasino.game.plinko"), parent);
        this.bets = new BetSelector(casino);
    }

    private int boardCx() {
        return left + 148;
    }

    private int boardTop() {
        return top + 40;
    }

    private long stepMs() {
        return dur(110);
    }

    @Override
    protected void initContent() {
        bets.init(this::addDrawableChild, textRenderer, left + 10, top + ph - 24);
        dropButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.plinko.drop"), b -> drop())
                .dimensions(left + 300, top + 30, 110, 22).build());
        riskButton = addDrawableChild(ButtonWidget.builder(riskText(), b -> {
            risk = risk.next();
            b.setMessage(riskText());
            CasinoSounds.click();
        }).dimensions(left + 300, top + 56, 110, 18).build());
    }

    private Text riskText() {
        return Text.translatable("luckycasino.plinko.risk", Text.translatable(risk.translationKey()));
    }

    private void drop() {
        if (balls.size() >= MAX_BALLS) {
            return;
        }
        long stake = bets.bet();
        Plinko.Drop d = casino.playPlinko(stake, risk);
        if (d == null) {
            CasinoSounds.error();
            return;
        }
        balls.add(new Ball(d, casino.lastRound(), now()));
        CasinoSounds.play(net.minecraft.sound.SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.4f, 0.5f);
        updateFrozen();
    }

    @Override
    protected boolean primaryAction() {
        drop();
        return true;
    }

    /** Angezeigtes Guthaben: Auszahlungen von Kugeln, die noch fallen, sind noch nicht sichtbar. */
    private void updateFrozen() {
        long pending = 0;
        for (Ball b : balls) {
            if (!b.landed) {
                pending += b.round.payout();
            }
        }
        if (pending > 0 || balls.stream().anyMatch(b -> !b.landed)) {
            freezeBalance(casino.balance() - pending);
        } else {
            unfreezeBalance();
        }
    }

    @Override
    public void tick() {
        long now = now();
        long total = stepMs() * (Plinko.ROWS + 1);
        for (Ball b : balls) {
            if (!b.landed && now - b.start >= total) {
                land(b);
            }
        }
        balls.removeIf(b -> b.landed && now - b.start > total + 400);
        updateFrozen();
        dropButton.active = balls.size() < MAX_BALLS && bets.affordable();
        bets.setLocked(false);
    }

    private void land(Ball b) {
        b.landed = true;
        bucketFlash[b.drop.bucket()] = now();
        lastRound = b.round;
        lastMultiplier = b.drop.multiplier();
        remember(b.drop.multiplier());
        double m = b.drop.multiplier();
        if (m >= 10) {
            CasinoSounds.bigWin();
        } else if (m > 1) {
            CasinoSounds.play(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, (float) Math.min(2.0, 0.9 + m * 0.1), 0.6f);
        } else {
            CasinoSounds.play(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.4f);
        }
        bets.refresh();
    }

    private static void remember(double m) {
        HISTORY.addFirst(m);
        while (HISTORY.size() > 12) {
            HISTORY.removeLast();
        }
    }

    @Override
    public void finishPendingRounds() {
        for (Ball b : balls) {
            b.landed = true;
        }
        balls.clear();
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    private static int bucketColor(double m) {
        if (m >= 10) {
            return 0xFFE53935;
        }
        if (m >= 3) {
            return 0xFFFF7A1A;
        }
        if (m >= 1.5) {
            return 0xFFFFB000;
        }
        if (m >= 1) {
            return 0xFFD4C21A;
        }
        return 0xFF5E7A62;
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = now();
        int cx = boardCx();
        int y0 = boardTop();
        // Brett
        Ui.box(ctx, cx - 132, y0 - 12, 264, ROW_H * Plinko.ROWS + 34, 0xC0081008, 0xFF2E4A36);
        // Stifte: Reihe r hat r + 3 Stifte
        for (int r = 0; r < Plinko.ROWS; r++) {
            int count = r + 3;
            for (int j = 0; j < count; j++) {
                float px = cx + (j - (count - 1) / 2f) * SPACING;
                int py = y0 + r * ROW_H;
                ctx.fill(Math.round(px) - 1, py - 1, Math.round(px) + 2, py + 2, 0xFFE8E8E8);
            }
        }
        // Fächer
        double edge = casino.rng().odds().plinkoHouseEdge();
        int by = y0 + Plinko.ROWS * ROW_H + 2;
        int hoverBucket = -1;
        for (int k = 0; k <= Plinko.ROWS; k++) {
            int bx = cx + (k - Plinko.ROWS / 2) * SPACING - SPACING / 2 + 1;
            double m = Plinko.multiplier(risk, k, edge);
            int color = bucketColor(m);
            float flash = Ui.clamp01(1 - (now - bucketFlash[k]) / 400f);
            int yOff = (int) (flash * 3);
            ctx.fill(bx, by + yOff, bx + SPACING - 2, by + 14 + yOff, Ui.lerpColor(color, 0xFFFFFFFF, flash * 0.5f));
            // Kurze Beschriftung ohne Schatten (Schatten verschmiert bei kleiner Schrift); exakte Werte im Tooltip
            String label = shortLabel(m);
            int tw = textRenderer.getWidth(label);
            float scale = Math.min(0.75f, (SPACING - 4) / (float) tw);
            var mat = ctx.getMatrices();
            mat.pushMatrix();
            mat.translate(bx + (SPACING - 2) / 2f, by + 7 + yOff - 4 * scale);
            mat.scale(scale, scale);
            ctx.drawText(textRenderer, label, -tw / 2, 0, m < 1 ? 0xFFE8F0E8 : 0xFF141414, false);
            mat.popMatrix();
            if (mouseX >= bx && mouseX < bx + SPACING - 2 && mouseY >= by && mouseY < by + 14) {
                hoverBucket = k;
            }
        }
        if (hoverBucket >= 0) {
            double hm = Plinko.multiplier(risk, hoverBucket, edge);
            ctx.drawTooltip(textRenderer, java.util.List.of(
                    Text.literal(trim(hm) + "x"),
                    Text.translatable("luckycasino.plinko.chance", Fmt.percent(Plinko.bucketChance(hoverBucket)))), mouseX, mouseY);
        }
        // Kugeln
        for (Ball b : balls) {
            float[] pos = ballPos(b, now);
            Ui.fillCircle(ctx, Math.round(pos[0]) + 1, Math.round(pos[1]) + 1, 3, 0x60000000);
            Ui.fillCircle(ctx, Math.round(pos[0]), Math.round(pos[1]), 3, b.landed ? 0xFFFFD34D : 0xFFFF5AA0);
            ctx.fill(Math.round(pos[0]) - 1, Math.round(pos[1]) - 2, Math.round(pos[0]), Math.round(pos[1]) - 1, 0xFFFFFFFF);
        }
        drawSidePanel(ctx);
        bets.renderLabel(ctx, textRenderer);
    }

    /** Kurzform für die Fächer: ab 10 ganzzahlig, darunter eine Nachkommastelle (0,61 → 0.6). */
    private static String shortLabel(double m) {
        if (m >= 10) {
            return String.valueOf((int) Math.floor(m));
        }
        return trim(Math.floor(m * 10) / 10.0);
    }

    private static String trim(double m) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", m);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Position einer Kugel: hüpft Reihe für Reihe nach links/rechts. */
    private float[] ballPos(Ball b, long now) {
        int cx = boardCx();
        int y0 = boardTop();
        float t = (now - b.start) / (float) stepMs();
        int step = (int) Math.floor(t);
        float f = t - step;
        if (step >= Plinko.ROWS + 1) {
            return new float[]{cx + (b.drop.bucket() - Plinko.ROWS / 2f) * SPACING, y0 + Plinko.ROWS * ROW_H + 9};
        }
        // Versatz nach "step" Reihen (in halben Abständen)
        float fromX = offsetAfter(b, step);
        float toX = offsetAfter(b, Math.min(step + 1, Plinko.ROWS));
        float x = cx + (fromX + (toX - fromX) * f) * SPACING;
        float fromY = y0 + (step - 1) * ROW_H - 4;
        float toY = y0 + step * ROW_H - 4;
        if (step >= Plinko.ROWS) {
            toY = y0 + Plinko.ROWS * ROW_H + 9;
        }
        float y = fromY + (toY - fromY) * f - (float) Math.sin(Math.PI * f) * 4;
        return new float[]{x, y};
    }

    /** Horizontaler Versatz (in Stiftabständen) nach {@code rows} Reihen. */
    private static float offsetAfter(Ball b, int rows) {
        int rights = 0;
        for (int i = 0; i < rows; i++) {
            if (b.drop.path()[i]) {
                rights++;
            }
        }
        return rights - rows / 2f;
    }

    private void drawSidePanel(DrawContext ctx) {
        int x = left + 300;
        int y = top + 80;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.plinko.max",
                trim(Plinko.multiplier(risk, 0, casino.rng().odds().plinkoHouseEdge())) + "x"), x, y, Ui.GRAY, true);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.plinko.balls", balls.stream().filter(b -> !b.landed).count(), MAX_BALLS),
                x, y + 12, Ui.GRAY, true);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.gui.history"), x, y + 28, Ui.GOLD, true);
        int i = 0;
        for (double m : HISTORY) {
            if (i >= HISTORY_SHOWN) {
                break;
            }
            int bx = x + (i % 3) * 37;
            int byy = y + 40 + (i / 3) * 14;
            ctx.fill(bx, byy, bx + 34, byy + 12, bucketColor(m));
            String label = trim(m) + "x";
            // ohne Schatten, sonst verschmiert die dunkle Schrift
            ctx.drawText(textRenderer, label, bx + 17 - textRenderer.getWidth(label) / 2, byy + 2,
                    m < 1 ? 0xFFE8F0E8 : 0xFF141414, false);
            i++;
        }
        if (lastRound != null) {
            Text t = lastRound.isWin() ? Text.translatable("luckycasino.result.win", Fmt.signed(lastRound.profit()))
                    : lastRound.outcome() == RoundResult.Outcome.PUSH ? Text.translatable("luckycasino.result.push_refund")
                    : Text.translatable("luckycasino.result.chips", Fmt.signed(lastRound.profit()));
            float scale = Math.min(1f, 110f / textRenderer.getWidth(t));
            Ui.scaled(ctx, textRenderer, t, x, top + ph - 18, scale, lastRound.isWin() ? Ui.GREEN
                    : lastRound.outcome() == RoundResult.Outcome.PUSH ? Ui.GOLD : 0xFFFF8080, false);
        }
        // Hilfetext unterhalb des Verlaufs (3 Zeilen à 14 px)
        Ui.wrapped(ctx, textRenderer, Text.translatable("luckycasino.plinko.help"), x, y + 40 + 3 * 14 + 6, 110, Ui.DARK_GRAY);
    }
}
