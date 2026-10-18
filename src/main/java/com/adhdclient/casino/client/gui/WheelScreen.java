package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.core.Casino;
import com.adhdclient.casino.core.Odds;
import com.adhdclient.casino.core.wheel.LuckyWheel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.time.LocalDate;
import java.util.Random;

/** Glücksrad: einmal pro (echtem) Tag gratis drehen. */
public class WheelScreen extends CasinoScreen {

    private static final int R = 90;
    /** Radius der Beschriftungsmitte und maximale Länge (zwischen Nabe und Symbolen). */
    private static final int LABEL_R = 51;
    private static final int LABEL_LEN = 38;
    private static final int ICON_R = 79;
    private static final double TAU = Math.PI * 2;
    private static final int SEGMENTS = LuckyWheel.SEGMENTS.size();
    private static final double SEG = TAU / SEGMENTS;
    private static final Random VISUAL = new Random();

    private static double wheelAngle = -Math.PI / 2 - SEG / 2;

    private ButtonWidget spinButton;
    private boolean spinning;
    private long spinStart;
    private double startAngle;
    private double endAngle;
    private Casino.WheelSpin result;
    private long resultAt;
    private int lastSegmentUnderPointer = -1;
    private long lastTickAt;

    public WheelScreen(Screen parent) {
        super(Text.translatable("luckycasino.menu.wheel"), parent);
    }

    private int cx() {
        return left + 128;
    }

    private int cy() {
        return top + 128;
    }

    @Override
    protected void initContent() {
        spinButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.wheel.spin"), b -> spin())
                .dimensions(left + 262, top + 72, 140, 22).build());
        update();
    }

    private void update() {
        spinButton.active = !spinning && casino.canSpinWheel(LocalDate.now());
    }

    private void spin() {
        if (spinning) {
            return;
        }
        long before = casino.balance();
        Casino.WheelSpin r = casino.spinWheel(LocalDate.now());
        if (r == null) {
            CasinoSounds.error();
            return;
        }
        result = r;
        freezeBalance(before);
        double center = r.index() * SEG + SEG / 2 + (VISUAL.nextDouble() - 0.5) * SEG * 0.7;
        double target = -Math.PI / 2 - center;
        startAngle = wheelAngle;
        double minEnd = startAngle + TAU * 5;
        endAngle = target + TAU * Math.ceil((minEnd - target) / TAU);
        spinStart = now();
        spinning = true;
        CasinoSounds.play(net.minecraft.sound.SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.8f, 0.8f);
        update();
    }

    @Override
    protected boolean primaryAction() {
        spin();
        return true;
    }

    private long spinDuration() {
        return dur(5_500);
    }

    @Override
    public void tick() {
        if (spinning && now() - spinStart >= spinDuration()) {
            finish();
        }
        update();
    }

    private void finish() {
        spinning = false;
        wheelAngle = endAngle;
        resultAt = now();
        unfreezeBalance();
        LuckyWheel.Segment seg = result.segment();
        if (seg.isJackpot()) {
            CasinoSounds.jackpot();
        } else if (seg.type() == LuckyWheel.PrizeType.CHIPS && seg.amount() < 250) {
            CasinoSounds.win();
        } else {
            CasinoSounds.bigWin();
        }
        CasinoSounds.play(net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, 1.2f, 0.6f);
    }

    @Override
    public void finishPendingRounds() {
        if (spinning) {
            finish();
        }
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    private static int segmentColor(int i, LuckyWheel.Segment s) {
        if (s.isJackpot()) {
            return 0xFFE8B830;
        }
        return switch (s.type()) {
            case BONUS -> 0xFFB03CC8;
            case FREE_SPINS -> 0xFF22A6B8;
            case CHIPS -> i % 2 == 0 ? 0xFFC62828 : 0xFF1E5AC8;
        };
    }

    private static String iconFor(LuckyWheel.Segment s) {
        if (s.isJackpot()) {
            return "minecraft:nether_star";
        }
        return switch (s.type()) {
            case BONUS -> "minecraft:experience_bottle";
            case FREE_SPINS -> "minecraft:diamond";
            case CHIPS -> s.amount() >= 500 ? "minecraft:gold_ingot" : "minecraft:gold_nugget";
        };
    }

    private static Text label(LuckyWheel.Segment s) {
        return switch (s.type()) {
            case CHIPS -> Text.literal(Fmt.chips(s.amount()));
            case BONUS -> Text.translatable("luckycasino.wheel.seg.bonus", s.amount());
            case FREE_SPINS -> Text.translatable("luckycasino.wheel.seg.spins", s.amount());
        };
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = now();
        double angle = wheelAngle;
        float wiggle = 0;
        if (spinning) {
            float t = Ui.clamp01((now - spinStart) / (float) spinDuration());
            angle = startAngle + (endAngle - startAngle) * Ui.easeOutQuart(t);
            wheelAngle = angle;
            int under = Math.floorMod((int) Math.floor((-Math.PI / 2 - angle) / SEG), SEGMENTS);
            if (under != lastSegmentUnderPointer) {
                lastSegmentUnderPointer = under;
                lastTickAt = now;
                CasinoSounds.tick(1.2f + 0.6f * (1 - t));
            }
        }
        if (now - lastTickAt < 90) {
            wiggle = (1 - (now - lastTickAt) / 90f) * 0.5f;
        }
        drawWheel(ctx, angle, now);
        drawPointer(ctx, wiggle);
        drawSidePanel(ctx, now);
    }

    private void drawWheel(DrawContext ctx, double angle, long now) {
        int cx = cx();
        int cy = cy();
        Matrix3x2fStack m = ctx.getMatrices();
        Ui.fillCircle(ctx, cx, cy, R + 7, 0xFF2A1A0C);
        Ui.fillCircle(ctx, cx, cy, R + 5, 0xFF6B4423);
        // Lichter am Rand
        for (int i = 0; i < 24; i++) {
            double a = i * TAU / 24;
            boolean on = spinning ? ((now / 90 + i) % 2 == 0) : ((now / 500 + i) % 3 != 0);
            int lx = (int) Math.round(cx + Math.cos(a) * (R + 2.5));
            int ly = (int) Math.round(cy + Math.sin(a) * (R + 2.5));
            ctx.fill(lx - 1, ly - 1, lx + 2, ly + 2, on ? 0xFFFFF2A0 : 0xFF8A7030);
        }
        Ui.fillCircle(ctx, cx, cy, R, 0xFFC9A227);

        int winner = !spinning && result != null && now - resultAt < 6_000 ? result.index() : -1;
        for (int i = 0; i < SEGMENTS; i++) {
            LuckyWheel.Segment s = LuckyWheel.SEGMENTS.get(i);
            int color = segmentColor(i, s);
            if (i == winner) {
                color = Ui.lerpColor(color, 0xFFFFFFFF, 0.25f + 0.25f * Ui.pulse(now, 400));
            }
            Ui.wedge(ctx, cx, cy, angle + (i + 0.5) * SEG, SEG / 2, 0, R - 1, color);
        }
        for (int i = 0; i < SEGMENTS; i++) {
            LuckyWheel.Segment s = LuckyWheel.SEGMENTS.get(i);
            double a0 = angle + i * SEG;
            m.pushMatrix();
            m.translate(cx, cy);
            m.rotate((float) a0);
            m.translate(0, -0.75f); // 1,5 px breit, exakt mittig auf der Feldgrenze
            m.scale(1f, 1.5f);
            ctx.fill(14, 0, R, 1, 0xFFFFE9A8);
            m.popMatrix();
            // Beschriftung radial
            double mid = a0 + SEG / 2;
            Text label = label(s);
            int tw = textRenderer.getWidth(label);
            float scale = Math.min(0.9f, LABEL_LEN / (float) tw);
            // Mittelpunkt der Beschriftung auf dem Radius; linke Radhälfte um 180° drehen,
            // damit der Text nie auf dem Kopf steht
            boolean flip = Math.cos(mid) < 0;
            m.pushMatrix();
            m.translate((float) (cx + Math.cos(mid) * LABEL_R), (float) (cy + Math.sin(mid) * LABEL_R));
            m.rotate((float) (flip ? mid + Math.PI : mid));
            m.scale(scale, scale);
            ctx.drawText(textRenderer, label, -tw / 2, -4, Ui.WHITE, true);
            m.popMatrix();
            // Symbol außen (nicht gedreht)
            int ix = (int) Math.round(cx + Math.cos(mid) * ICON_R);
            int iy = (int) Math.round(cy + Math.sin(mid) * ICON_R);
            Ui.item(ctx, Icons.of(iconFor(s)), ix - 5, iy - 5, 0.625f);
        }
        // Nabe
        Ui.fillCircle(ctx, cx, cy, 15, 0xFF8B6508);
        Ui.fillCircle(ctx, cx, cy, 13, 0xFFE8B830);
        Ui.fillCircle(ctx, cx, cy, 9, 0xFF3A2A1A);
        Ui.item(ctx, Icons.of("minecraft:sunflower"), cx - 6, cy - 6, 0.75f);
    }

    private void drawPointer(DrawContext ctx, float wiggle) {
        int cx = cx();
        int tipY = cy() - R + 6;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, tipY - 14);
        m.rotate(wiggle);
        for (int row = 0; row < 16; row++) {
            int half = Math.max(0, 8 - row / 2);
            ctx.fill(-half - 1, row, half + 1, row + 1, 0xFF3A2A1A);
            if (half > 0) {
                ctx.fill(-half, row, half, row + 1, row < 3 ? 0xFFFFF2A0 : 0xFFFFD34D);
            }
        }
        m.popMatrix();
    }

    private void drawSidePanel(DrawContext ctx, long now) {
        int x = left + 262;
        int y = top + 32;
        boolean available = casino.canSpinWheel(LocalDate.now());
        if (spinning) {
            ctx.drawText(textRenderer, Text.translatable("luckycasino.wheel.spinning"), x, y, Ui.GRAY, true);
        } else if (available) {
            ctx.drawText(textRenderer, Text.translatable("luckycasino.wheel.ready"), x, y, Ui.GREEN, true);
        } else {
            ctx.drawText(textRenderer, Text.translatable("luckycasino.wheel.used"), x, y, Ui.GRAY, true);
            ctx.drawText(textRenderer, Text.translatable("luckycasino.wheel.next", Fmt.duration(CasinoMenuScreen.untilMidnightMs())),
                    x, y + 12, Ui.WHITE, true);
        }
        Ui.wrapped(ctx, textRenderer, Text.translatable("luckycasino.wheel.info"), x, y + 66, 148, Ui.GRAY);

        int ly = y + 108;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.wheel.legend"), x, ly, Ui.GOLD, true);
        legend(ctx, x, ly + 12, "minecraft:gold_nugget", "luckycasino.wheel.legend.chips");
        legend(ctx, x, ly + 24, "minecraft:experience_bottle", "luckycasino.wheel.legend.bonus");
        legend(ctx, x, ly + 36, "minecraft:diamond", "luckycasino.wheel.legend.spins");
        legend(ctx, x, ly + 48, "minecraft:nether_star", "luckycasino.wheel.legend.jackpot");

        var p = casino.profile();
        ctx.drawText(textRenderer, Text.translatable("luckycasino.wheel.status_bonus",
                p.bonusMultiplier > 1 ? "x" + p.bonusMultiplier : "-"), x, top + ph - 28, 0xFFB8E0B8, true);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.wheel.status_spins",
                p.freeSpins, Fmt.chips(Odds.FREE_SPIN_BET)), x, top + ph - 16, 0xFFB8E0B8, true);
    }

    private void legend(DrawContext ctx, int x, int y, String item, String key) {
        Ui.item(ctx, Icons.of(item), x, y - 2, 0.6f);
        ctx.drawText(textRenderer, Text.translatable(key), x + 12, y, Ui.WHITE, true);
    }

    @Override
    protected void renderOverlay(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (spinning || result == null || now() - resultAt > 4_000) {
            return;
        }
        LuckyWheel.Segment s = result.segment();
        Text line1 = s.isJackpot() ? Text.translatable("luckycasino.wheel.jackpot") : Text.translatable("luckycasino.wheel.won");
        Text line2 = switch (s.type()) {
            case CHIPS -> Text.translatable("luckycasino.result.chips", Fmt.signed(s.amount()));
            case BONUS -> Text.translatable("luckycasino.wheel.prize.bonus", s.amount());
            case FREE_SPINS -> Text.translatable("luckycasino.wheel.prize.spins", s.amount());
        };
        float appear = Ui.clamp01((now() - resultAt) / 300f);
        banner(ctx, line1, line2, cx(), cy(), s.isJackpot() ? Ui.GOLD : Ui.GREEN, appear);
    }
}
