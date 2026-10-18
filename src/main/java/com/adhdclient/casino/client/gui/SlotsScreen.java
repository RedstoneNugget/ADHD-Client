package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.gui.widget.BetSelector;
import com.adhdclient.casino.core.Casino;
import com.adhdclient.casino.core.Odds;
import com.adhdclient.casino.core.RoundResult;
import com.adhdclient.casino.core.slots.SlotMachine;
import com.adhdclient.casino.core.slots.SlotSymbol;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Slot-Maschine mit drei Walzen, Gewinnlinie und Gewinntabelle. */
public class SlotsScreen extends CasinoScreen {

    private static final int CELL = 28;
    private static final int REEL_W = 52;
    private static final int REEL_GAP = 8;
    private static final int REEL_H = CELL * 3;
    private static final int AUTO_SPINS = 10;
    /** Nur für die Füllsymbole der Animation – das Ergebnis kommt aus der CasinoRng. */
    private static final Random VISUAL = new Random();

    private final BetSelector bets;
    private ButtonWidget spinButton;
    private ButtonWidget freeSpinButton;
    private ButtonWidget autoButton;
    /** Verbleibende automatische Drehungen (0 = aus). */
    private int autoRemaining;
    private long nextAutoAt;

    private final SlotSymbol[][] strips = new SlotSymbol[3][];
    private final int[] targets = new int[3];
    private final boolean[] stopped = {true, true, true};
    private long spinStart;
    private boolean spinning;
    private boolean freeSpin;
    private long lastTickSound;
    private long lastFrame;
    private Casino.SlotSpin lastSpin;
    private long resultShownAt;
    private final List<Particle> particles = new ArrayList<>();

    /** Konfetti beim Jackpot. */
    private static final class Particle {
        final ItemStack stack;
        float x;
        float y;
        float vx;
        float vy;

        Particle(ItemStack stack, float x, float y, float vx, float vy) {
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }
    }

    public SlotsScreen(Screen parent) {
        super(Text.translatable("luckycasino.game.slots"), parent);
        this.bets = new BetSelector(casino);
        for (int i = 0; i < 3; i++) {
            strips[i] = randomStrip(4);
            targets[i] = 1;
        }
    }

    private int machineX() {
        return left + 16;
    }

    private int reelX(int i) {
        int total = 3 * REEL_W + 2 * REEL_GAP;
        return machineX() + (220 - total) / 2 + i * (REEL_W + REEL_GAP);
    }

    private int reelY() {
        return top + 44;
    }

    @Override
    protected void initContent() {
        bets.init(this::addDrawableChild, textRenderer, left + 10, top + ph - 24);
        spinButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.slots.spin"), b -> spin(false))
                .dimensions(machineX() + 4, top + 156, 70, 20).build());
        autoButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
            if (autoRemaining > 0) {
                autoRemaining = 0;
            } else if (!spinning) {
                autoRemaining = AUTO_SPINS;
                spin(false);
            }
            updateButtons();
        }).dimensions(machineX() + 78, top + 156, 66, 20).build());
        freeSpinButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> spin(true))
                .dimensions(machineX() + 148, top + 156, 68, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        spinButton.active = !spinning && bets.affordable();
        int spins = casino.profile().freeSpins;
        freeSpinButton.visible = spins > 0;
        freeSpinButton.active = !spinning;
        freeSpinButton.setMessage(Text.translatable("luckycasino.slots.free", spins));
        autoButton.setMessage(autoRemaining > 0 ? Text.translatable("luckycasino.slots.auto_stop", autoRemaining)
                : Text.translatable("luckycasino.slots.auto", AUTO_SPINS));
        autoButton.active = autoRemaining > 0 || (!spinning && bets.affordable());
        bets.setLocked(spinning || autoRemaining > 0);
    }

    private void spin(boolean free) {
        if (spinning) {
            return;
        }
        long before = casino.balance();
        long bet = bets.bet();
        Casino.SlotSpin result = free ? casino.playFreeSpin() : casino.playSlots(bet);
        if (result == null) {
            autoRemaining = 0;
            CasinoSounds.error();
            return;
        }
        if (!free && autoRemaining > 0) {
            autoRemaining--;
        }
        freeSpin = free;
        lastSpin = result;
        freezeBalance(free ? before : before - bet);
        for (int i = 0; i < 3; i++) {
            int len = 18 + i * 7;
            SlotSymbol[] strip = randomStrip(len + 3);
            // Start bei den aktuell sichtbaren Symbolen, Ziel ist das Ergebnis
            strip[0] = strips[i][Math.max(0, targets[i] - 1)];
            strip[1] = strips[i][targets[i]];
            strip[2] = strips[i][targets[i] + 1];
            strip[len] = result.result().reels()[i];
            strips[i] = strip;
            stopped[i] = false;
            targets[i] = len;
        }
        spinStart = now();
        spinning = true;
        particles.clear();
        updateButtons();
    }

    private static SlotSymbol[] randomStrip(int len) {
        SlotSymbol[] all = SlotSymbol.values();
        SlotSymbol[] s = new SlotSymbol[len];
        for (int i = 0; i < len; i++) {
            s[i] = all[VISUAL.nextInt(all.length)];
        }
        return s;
    }

    private long reelDuration(int i) {
        return dur(900 + i * 450L);
    }

    /** Aktuelle Position der Walze in "Symbolen" (1 = Startsymbol in der Mitte). */
    private float reelPos(int i) {
        if (stopped[i]) {
            return targets[i];
        }
        float t = (now() - spinStart) / (float) reelDuration(i);
        return 1 + (targets[i] - 1) * Ui.easeOutBack(t);
    }

    @Override
    protected boolean primaryAction() {
        if (!spinning && autoRemaining == 0) {
            spin(false);
        }
        return true;
    }

    @Override
    public void tick() {
        if (!spinning) {
            if (autoRemaining > 0 && now() >= nextAutoAt) {
                if (bets.affordable()) {
                    spin(false);
                } else {
                    autoRemaining = 0;
                    updateButtons();
                }
            }
            return;
        }
        long elapsed = now() - spinStart;
        for (int i = 0; i < 3; i++) {
            if (!stopped[i] && elapsed >= reelDuration(i)) {
                stopped[i] = true;
                CasinoSounds.stop();
            }
        }
        if (stopped[0] && stopped[1] && stopped[2]) {
            finishSpin();
        }
    }

    private void finishSpin() {
        spinning = false;
        resultShownAt = now();
        unfreezeBalance();
        RoundResult round = lastSpin.round();
        if (autoRemaining > 0) {
            nextAutoAt = now() + dur(600);
        }
        if (lastSpin.result().isJackpot()) {
            autoRemaining = 0; // beim Jackpot anhalten
            CasinoSounds.jackpot();
            spawnConfetti();
        } else if (round.isWin() && lastSpin.result().multiplier() >= 10) {
            CasinoSounds.bigWin();
        } else if (round.isWin()) {
            CasinoSounds.win();
        } else if (round.outcome() == RoundResult.Outcome.PUSH) {
            CasinoSounds.push();
        } else {
            CasinoSounds.lose();
        }
        bets.refresh();
        updateButtons();
    }

    @Override
    public void finishPendingRounds() {
        // Gewinn ist bereits gebucht – nur die Animation beenden
        spinning = false;
        autoRemaining = 0;
    }

    private void spawnConfetti() {
        String[] items = {"minecraft:gold_nugget", "minecraft:diamond", "minecraft:emerald", "minecraft:nether_star"};
        for (int i = 0; i < 40; i++) {
            particles.add(new Particle(Icons.of(items[i % items.length]),
                    left + pw / 2f + (VISUAL.nextFloat() - 0.5f) * 60, top + 90,
                    (VISUAL.nextFloat() - 0.5f) * 260, -120 - VISUAL.nextFloat() * 160));
        }
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = now();
        if (spinning && now - lastTickSound > 75) {
            lastTickSound = now;
            CasinoSounds.tick(1.6f + VISUAL.nextFloat() * 0.3f);
        }
        drawMachine(ctx, now);
        drawPaytable(ctx);
        bets.renderLabel(ctx, textRenderer);
        drawResultLine(ctx);
        if (!spinButton.active && !spinning && !bets.affordable()) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.gui.not_enough"), machineX() + 110, top + 203, Ui.RED);
        }
    }

    private void drawMachine(DrawContext ctx, long now) {
        int mx = machineX();
        int my = top + 30;
        // Gehäuse
        Ui.bevel(ctx, mx, my, 220, 116, 0xFF7A1E1E);
        ctx.fill(mx + 4, my + 4, mx + 216, my + 112, 0xFF3A0C0C);
        // Lichterkette oben
        for (int i = 0; i < 18; i++) {
            boolean on = spinning ? ((now / 120 + i) % 2 == 0) : ((now / 600 + i) % 3 == 0);
            ctx.fill(mx + 8 + i * 12, my + 6, mx + 12 + i * 12, my + 10, on ? 0xFFFFE066 : 0xFF6E5A20);
        }
        for (int i = 0; i < 3; i++) {
            drawReel(ctx, i);
        }
        // Gewinnlinie
        int ly = reelY() + REEL_H / 2;
        boolean win = !spinning && lastSpin != null && lastSpin.result().multiplier() > 0;
        int lineColor = win ? Ui.lerpColor(0xFFFFD34D, 0xFFFFFFFF, Ui.pulse(now, 500)) : 0x90FF4040;
        ctx.fill(mx + 10, ly, mx + 210, ly + 1, lineColor);
        // Pfeile links/rechts
        for (int k = 0; k < 4; k++) {
            ctx.fill(mx + 6 + k, ly - 3 + k, mx + 7 + k, ly + 4 - k, lineColor);
            ctx.fill(mx + 213 - k, ly - 3 + k, mx + 214 - k, ly + 4 - k, lineColor);
        }
        // Konfetti
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (!particles.isEmpty()) {
            particles.removeIf(p -> p.y > top + ph + 20);
            for (Particle p : particles) {
                p.vy += 300 * dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                Ui.item(ctx, p.stack, p.x, p.y, 0.75f);
            }
        }
    }

    private void drawReel(DrawContext ctx, int i) {
        int x = reelX(i);
        int y = reelY();
        ctx.fill(x - 2, y - 2, x + REEL_W + 2, y + REEL_H + 2, 0xFF1A1A1A);
        ctx.fillGradient(x, y, x + REEL_W, y + REEL_H, 0xFFBDBDBD, 0xFFF5F5F5);
        ctx.fillGradient(x, y + REEL_H / 2, x + REEL_W, y + REEL_H, 0xFFF5F5F5, 0xFFBDBDBD);

        float pos = reelPos(i);
        int base = (int) Math.floor(pos);
        int centerY = y + REEL_H / 2;
        ctx.enableScissor(x, y, x + REEL_W, y + REEL_H);
        for (int k = base - 2; k <= base + 2; k++) {
            if (k < 0 || k >= strips[i].length) {
                continue;
            }
            float cy = centerY + (pos - k) * CELL;
            Ui.item(ctx, Icons.of(strips[i][k].itemId()), x + REEL_W / 2f - 12, cy - 12, 1.5f);
        }
        ctx.disableScissor();
        // Schatten oben/unten für Walzen-Optik
        ctx.fillGradient(x, y, x + REEL_W, y + 12, 0x90000000, 0x00000000);
        ctx.fillGradient(x, y + REEL_H - 12, x + REEL_W, y + REEL_H, 0x00000000, 0x90000000);

        if (!spinning && lastSpin != null && lastSpin.result().isWinningReel(i)) {
            int c = Ui.lerpColor(0xFFFFD34D, 0xFFFFFFFF, Ui.pulse(now(), 400));
            ctx.drawStrokedRectangle(x + 2, centerY - CELL / 2, REEL_W - 4, CELL, c);
        }
    }

    private void drawPaytable(DrawContext ctx) {
        int x = left + 246;
        int y = top + 28;
        Ui.box(ctx, x, y, 164, 170, 0xB0000000, 0xFF2E4A36);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.slots.paytable"), x + 6, y + 5, Ui.GOLD, true);
        ctx.drawText(textRenderer, "3x", x + 90, y + 5, Ui.GRAY, true);
        ctx.drawText(textRenderer, "2x", x + 132, y + 5, Ui.GRAY, true);
        SlotSymbol[] all = SlotSymbol.values();
        for (int r = 0; r < all.length; r++) {
            SlotSymbol s = all[all.length - 1 - r];
            int ry = y + 18 + r * 15;
            ItemStack icon = Icons.of(s.itemId());
            for (int k = 0; k < 3; k++) {
                Ui.item(ctx, icon, x + 6 + k * 13, ry, 0.75f);
            }
            int color = s.isJackpot() ? Ui.GOLD : Ui.WHITE;
            ctx.drawText(textRenderer, (int) s.triplePayout() + "x", x + 90, ry + 2, color, true);
            String pair = s.pairPayout() <= 0 ? "-" : (int) s.pairPayout() + "x";
            ctx.drawText(textRenderer, pair, x + 132, ry + 2, s.pairPayout() <= 0 ? Ui.DARK_GRAY : Ui.GRAY, true);
        }
        int odds = (int) Math.round(1 / SlotMachine.jackpotChance(casino.rng().odds().slotWeights()));
        ctx.drawText(textRenderer, Text.translatable("luckycasino.slots.jackpot_odds", Fmt.chips(odds)), x + 6, y + 156, Ui.GRAY, true);
    }

    private void drawResultLine(DrawContext ctx) {
        int x = machineX() + 110;
        int y = top + 183;
        if (spinning) {
            Text t = freeSpin ? Text.translatable("luckycasino.slots.free_running", Fmt.chips(Odds.FREE_SPIN_BET))
                    : Text.translatable("luckycasino.slots.running");
            Ui.centered(ctx, textRenderer, t, x, y, Ui.GRAY);
            return;
        }
        if (lastSpin == null) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.slots.hint"), x, y, Ui.GRAY);
            return;
        }
        RoundResult r = lastSpin.round();
        Text line;
        int color;
        if (r.isWin()) {
            Text sym = Text.translatable(lastSpin.result().winSymbol().translationKey());
            line = Text.translatable("luckycasino.slots.win", Fmt.signed(r.profit()), lastSpin.result().matches(), sym);
            color = Ui.GREEN;
        } else if (r.outcome() == RoundResult.Outcome.PUSH) {
            line = Text.translatable("luckycasino.result.push_refund");
            color = Ui.GOLD;
        } else {
            line = Text.translatable("luckycasino.slots.lose");
            color = 0xFFFF8080;
        }
        Ui.centered(ctx, textRenderer, line, x, y, color);
        if (r.bonusApplied()) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.result.bonus", r.bonusMultiplier()), x, y + 11, 0xFFFFB0FF);
        }
    }

    @Override
    protected void renderOverlay(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!spinning && lastSpin != null && lastSpin.result().isJackpot() && now() - resultShownAt < 4_000) {
            float appear = Ui.clamp01((now() - resultShownAt) / 300f);
            int c = Ui.hsv((now() % 1000) / 1000f, 0.6f, 1f);
            banner(ctx, Text.translatable("luckycasino.slots.jackpot"),
                    Text.translatable("luckycasino.result.chips", Fmt.signed(lastSpin.round().profit())),
                    machineX() + 110, top + 86, c, appear);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (spinButton != null && !spinning) {
            updateButtons();
        }
        super.render(ctx, mouseX, mouseY, delta);
    }
}
