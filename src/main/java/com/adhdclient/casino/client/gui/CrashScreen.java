package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.ClientSession;
import com.adhdclient.casino.client.gui.widget.BetSelector;
import com.adhdclient.casino.core.RoundResult;
import com.adhdclient.casino.core.crash.CrashRound;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Crash: Der Multiplikator steigt live, bis er abstürzt. Rechtzeitig "Cash Out" drücken!
 * Nach einem Cash-out läuft die Kurve grau weiter, damit man sieht, wann es gecrasht wäre.
 */
public class CrashScreen extends CasinoScreen {

    private static final int GX = 12;
    private static final int GY = 30;
    private static final int GW = 280;
    private static final int GH = 142;

    /** Auto-Ausstieg bleibt für die Sitzung erhalten. */
    private static String autoText = "";

    private final BetSelector bets;
    private ButtonWidget actionButton;
    private TextFieldWidget autoField;
    private RoundResult lastResult;
    private double lastTickStep;
    private long endShownAt;

    public CrashScreen(Screen parent) {
        super(Text.translatable("luckycasino.game.crash"), parent);
        this.bets = new BetSelector(casino);
    }

    private CrashRound round() {
        return ClientSession.crash;
    }

    @Override
    protected void initContent() {
        bets.init(this::addDrawableChild, textRenderer, left + 10, top + ph - 24);
        actionButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> action())
                .dimensions(left + 300, top + 30, 110, 24).build());
        autoField = new TextFieldWidget(textRenderer, left + 360, top + 62, 50, 14, Text.translatable("luckycasino.crash.auto"));
        autoField.setMaxLength(8);
        autoField.setText(autoText);
        autoField.setPlaceholder(Text.translatable("luckycasino.crash.auto_off"));
        autoField.setChangedListener(t -> autoText = t);
        addDrawableChild(autoField);
        updateButton();
    }

    private static double parseAuto(String text) {
        try {
            double v = Double.parseDouble(text.trim().replace(',', '.').replace("x", ""));
            return v > 1.0 ? Math.min(v, 1000) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void updateButton() {
        CrashRound r = round();
        boolean running = r != null && r.isRunning();
        if (running) {
            long payout = (long) Math.floor(r.stake() * r.currentMultiplier(now()));
            actionButton.setMessage(Text.translatable("luckycasino.crash.cashout", Fmt.chips(payout)));
            actionButton.active = true;
        } else {
            actionButton.setMessage(Text.translatable("luckycasino.crash.start"));
            actionButton.active = bets.affordable();
        }
        autoField.setEditable(!running);
        bets.setLocked(running);
    }

    private void action() {
        CrashRound r = round();
        long now = now();
        if (r != null && r.isRunning()) {
            RoundResult res = casino.cashOutCrash(r, now);
            onEnded(r, res);
            return;
        }
        double auto = parseAuto(autoField.getText());
        CrashRound nr = casino.startCrash(bets.bet(), auto, now);
        if (nr == null) {
            CasinoSounds.error();
            return;
        }
        ClientSession.crash = nr;
        lastResult = null;
        lastTickStep = 1.0;
        endShownAt = 0;
        CasinoSounds.launch();
        if (!nr.isRunning()) {
            onEnded(nr, casino.lastRound());
        }
        updateButton();
    }

    @Override
    protected boolean primaryAction() {
        if (actionButton.active) {
            action();
        }
        return true;
    }

    private void onEnded(CrashRound r, RoundResult res) {
        lastResult = res;
        endShownAt = now();
        ClientSession.remember(ClientSession.crashHistory, r.crashPoint(), 10);
        if (r.state() == CrashRound.State.CASHED_OUT) {
            CasinoSounds.cashOut();
            if (r.cashOutMultiplier() >= 5) {
                CasinoSounds.bigWin();
            }
        } else {
            CasinoSounds.explode();
            CasinoSounds.heavyLoss();
        }
        bets.refresh();
        updateButton();
    }

    @Override
    public void finishPendingRounds() {
        CrashRound r = round();
        if (r != null && r.isRunning()) {
            // Fenster schließen = sofort aussteigen (gleich wie der Button)
            RoundResult res = casino.cashOutCrash(r, now());
            lastResult = res;
            ClientSession.remember(ClientSession.crashHistory, r.crashPoint(), 10);
        }
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = now();
        CrashRound r = round();
        if (r != null && r.isRunning()) {
            RoundResult res = casino.updateCrash(r, now);
            if (res != null) {
                onEnded(r, res);
            } else {
                double m = r.currentMultiplier(now);
                double step = m < 2 ? 0.1 : m < 5 ? 0.25 : 1.0;
                if (m >= lastTickStep + step) {
                    lastTickStep = m;
                    CasinoSounds.crashTick((float) Math.min(2.0, 0.8 + m * 0.12));
                }
            }
        }
        updateButton();
        drawGraph(ctx, r, now);
        drawSidePanel(ctx);
        bets.renderLabel(ctx, textRenderer);
        drawStatus(ctx, r);
    }

    private void drawGraph(DrawContext ctx, CrashRound r, long now) {
        int gx = left + GX;
        int gy = top + GY;
        Ui.box(ctx, gx, gy, GW, GH, 0xE0081008, 0xFF2E4A36);

        long elapsed = 0;
        long cashAt = -1;
        boolean crashed = false;
        if (r != null) {
            long crashTime = CrashRound.timeToReach(r.crashPoint());
            long shownEnd = r.isRunning() ? now - r.startMs() : Math.min(now - r.startMs(), crashTime);
            elapsed = Math.max(0, shownEnd);
            if (r.state() == CrashRound.State.CASHED_OUT) {
                cashAt = r.endMs() - r.startMs();
            }
            crashed = !r.isRunning() && now - r.startMs() >= crashTime;
        }
        double mNow = CrashRound.multiplierAt(elapsed);
        double tMax = Math.max(8_000, elapsed * 1.15);
        double mMax = Math.max(2.0, mNow * 1.15);

        // Raster
        double gridStep = niceStep(mMax - 1);
        for (double gm = 1 + gridStep; gm < mMax; gm += gridStep) {
            int y = yFor(gm, mMax, gy);
            ctx.fill(gx + 1, y, gx + GW - 1, y + 1, 0x30FFFFFF);
            ctx.drawText(textRenderer, Fmt.multiplier(gm), gx + 3, y - 9, 0x80FFFFFF, false);
        }
        int secStep = tMax > 40_000 ? 10 : tMax > 16_000 ? 5 : 2;
        for (int s = secStep; s * 1000L < tMax; s += secStep) {
            int x = gx + (int) (s * 1000L / tMax * GW);
            ctx.fill(x, gy + 1, x + 1, gy + GH - 1, 0x20FFFFFF);
            ctx.drawText(textRenderer, s + "s", x + 2, gy + GH - 10, 0x60FFFFFF, false);
        }

        if (r == null) {
            Ui.scaled(ctx, textRenderer, Text.literal("1.00x"), gx + GW / 2f, gy + GH / 2f - 12, 3f, 0x60FFFFFF, true);
            return;
        }

        // Kurve + Fläche darunter
        int segments = 70;
        float px = gx;
        float py = yFor(1.0, mMax, gy);
        for (int i = 1; i <= segments; i++) {
            double t = elapsed * i / (double) segments;
            float x = (float) (gx + t / tMax * GW);
            float y = yFor(CrashRound.multiplierAt((long) t), mMax, gy);
            boolean ghost = cashAt >= 0 && t > cashAt;
            int color = crashed && !ghost && cashAt < 0 ? 0xFFFF4040 : ghost ? 0xFF808080 : cashAt >= 0 ? Ui.GOLD : 0xFF40FF70;
            for (int fx = (int) px; fx < (int) x; fx++) {
                float fy = py + (y - py) * ((fx - px) / Math.max(1f, x - px));
                ctx.fill(fx, (int) fy, fx + 1, gy + GH - 1, Ui.withAlpha(color, ghost ? 0x18 : 0x30));
            }
            Ui.line(ctx, px, py, x, y, 2, color);
            px = x;
            py = y;
        }

        if (r.isRunning()) {
            Ui.item(ctx, Icons.of("minecraft:firework_rocket"), px - 6, py - 14, 1f);
        } else if (crashed) {
            float age = Ui.clamp01((now - r.startMs() - CrashRound.timeToReach(r.crashPoint())) / 600f);
            if (age < 1) {
                Ui.fillCircle(ctx, (int) px, (int) py, (int) (4 + age * 20), Ui.withAlpha(0xFFFF8020, (int) (200 * (1 - age))));
                Ui.fillCircle(ctx, (int) px, (int) py, (int) (2 + age * 10), Ui.withAlpha(0xFFFFE060, (int) (220 * (1 - age))));
            }
        }

        // Großer Multiplikator
        double shown = r.isRunning() ? r.currentMultiplier(now) : r.state() == CrashRound.State.CASHED_OUT
                ? r.cashOutMultiplier() : r.crashPoint();
        int color = r.isRunning() ? Ui.WHITE : r.state() == CrashRound.State.CASHED_OUT ? Ui.GOLD : 0xFFFF4040;
        Ui.scaled(ctx, textRenderer, Text.literal(Fmt.multiplier(shown)), gx + GW / 2f, gy + 18, 3f, color, true);
        if (!r.isRunning()) {
            Text sub = r.state() == CrashRound.State.CASHED_OUT
                    ? Text.translatable("luckycasino.crash.cashed_at", Fmt.multiplier(r.cashOutMultiplier()))
                    : Text.translatable("luckycasino.crash.crashed_at", Fmt.multiplier(r.crashPoint()));
            Ui.centered(ctx, textRenderer, sub, gx + GW / 2, gy + 48, color);
            if (crashed && r.state() == CrashRound.State.CASHED_OUT) {
                Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.crash.would_crash", Fmt.multiplier(r.crashPoint())),
                        gx + GW / 2, gy + 60, Ui.GRAY);
            }
        }
    }

    private static double niceStep(double range) {
        double[] steps = {0.1, 0.25, 0.5, 1, 2, 5, 10, 25, 50, 100, 250};
        for (double s : steps) {
            if (range / s <= 5) {
                return s;
            }
        }
        return 500;
    }

    private static int yFor(double m, double mMax, int gy) {
        double f = (m - 1) / (mMax - 1);
        return (int) Math.round(gy + GH - 2 - f * (GH - 24));
    }

    private void drawSidePanel(DrawContext ctx) {
        int x = left + 300;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.crash.auto"), x, top + 65, Ui.GRAY, true);
        int y = top + 84;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.crash.history"), x, y, Ui.GOLD, true);
        y += 12;
        int i = 0;
        for (double cp : ClientSession.crashHistory) {
            int bx = x + (i % 2) * 56;
            int by = y + (i / 2) * 14;
            int col = cp >= 10 ? 0xFFFFAA00 : cp >= 2 ? 0xFF2E9E3E : 0xFFB71C1C;
            ctx.fill(bx, by, bx + 52, by + 12, col);
            Ui.centered(ctx, textRenderer, Text.literal(Fmt.multiplier(cp)), bx + 26, by + 2, Ui.WHITE);
            i++;
        }
        ctx.drawText(textRenderer, Text.translatable("luckycasino.crash.best",
                Fmt.multiplier(Math.max(1.0, casino.profile().records.bestCrashMultiplier))), x, top + 170, Ui.GRAY, true);
    }

    private void drawStatus(DrawContext ctx, CrashRound r) {
        int cx = left + GX + GW / 2;
        int y = top + 180;
        if (r == null) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.crash.hint"), cx, y, Ui.GRAY);
            return;
        }
        if (r.isRunning()) {
            long payout = (long) Math.floor(r.stake() * r.currentMultiplier(now()));
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.crash.running", Fmt.chips(r.stake()), Fmt.chips(payout)), cx, y, Ui.WHITE);
            if (r.autoCashOut() > 1) {
                Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.crash.auto_active", Fmt.multiplier(r.autoCashOut())),
                        cx, y + 11, Ui.GRAY);
            }
            return;
        }
        if (lastResult != null) {
            Text t = lastResult.isWin() ? Text.translatable("luckycasino.result.win", Fmt.signed(lastResult.profit()))
                    : lastResult.outcome() == RoundResult.Outcome.PUSH ? Text.translatable("luckycasino.result.push")
                    : Text.translatable("luckycasino.result.lose", Fmt.chips(lastResult.stake()));
            Ui.centered(ctx, textRenderer, t, cx, y, lastResult.isWin() ? Ui.GREEN : 0xFFFF8080);
            if (lastResult.bonusApplied()) {
                Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.result.bonus", lastResult.bonusMultiplier()), cx, y + 11, 0xFFFFB0FF);
            }
        }
        if (!bets.affordable()) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.gui.not_enough"), cx, y + 22, Ui.RED);
        }
    }
}
