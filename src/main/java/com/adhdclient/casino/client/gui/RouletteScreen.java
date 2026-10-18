package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.ClientSession;
import com.adhdclient.casino.client.gui.widget.BetSelector;
import com.adhdclient.casino.core.Casino;
import com.adhdclient.casino.core.RoundResult;
import com.adhdclient.casino.core.roulette.Roulette;
import com.adhdclient.casino.core.roulette.RouletteBet;
import com.adhdclient.casino.core.roulette.RouletteVariant;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Roulette mit animiertem Kessel und Wett-Tisch (mehrere Einsätze pro Runde). */
public class RouletteScreen extends CasinoScreen {

    private static final int R_RIM = 72;
    private static final int R_TRACK = 69;
    private static final int R_OUT = 61;
    private static final int R_IN = 45;
    private static final int CELL_W = 18;
    private static final int CELL_H = 16;
    private static final double TAU = Math.PI * 2;
    private static final Random VISUAL = new Random();

    private static final int COLOR_RED = 0xFFB71C1C;
    private static final int COLOR_BLACK = 0xFF1C1C1C;
    private static final int COLOR_GREEN = 0xFF1B6E2A;

    private final BetSelector bets;
    private final Map<RouletteBet, Long> table = new LinkedHashMap<>();
    private final List<Cell> cells = new ArrayList<>();
    private ButtonWidget spinButton;
    private ButtonWidget clearButton;
    private ButtonWidget rebetButton;
    private Text message;
    private int messageColor;

    // Animation
    private RouletteVariant variant;
    private double wheelAngle;
    private double ballAngle = -Math.PI / 2;
    private double wheelStart;
    private double wheelEnd;
    private double ballStart;
    private double ballEnd;
    private long spinStart;
    private boolean spinning;
    private boolean ballInPocket;
    private Casino.RouletteSpin lastSpin;
    private long resultShownAt;
    private int lastTickPocket = -1;
    private long lastFrame;

    /** Klickbares Feld des Tisches. */
    private record Cell(RouletteBet bet, int x, int y, int w, int h, String label, int color) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public RouletteScreen(Screen parent) {
        super(Text.translatable("luckycasino.game.roulette"), parent);
        this.bets = new BetSelector(casino);
    }

    private int wheelCx() {
        return left + 84;
    }

    private int wheelCy() {
        return top + 108;
    }

    private int tableX() {
        return left + 168;
    }

    private int tableY() {
        return top + 30;
    }

    @Override
    protected void initContent() {
        if (!spinning) {
            variant = casino.rng().odds().rouletteVariant();
        }
        buildCells();
        bets.init(this::addDrawableChild, textRenderer, left + 10, top + ph - 24);
        int bx = tableX();
        spinButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.roulette.spin"), b -> spin())
                .dimensions(bx, top + 116, 76, 18).build());
        clearButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.roulette.clear"), b -> {
            table.clear();
            CasinoSounds.click();
        }).dimensions(bx + 80, top + 116, 76, 18).build());
        rebetButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.roulette.rebet"), b -> rebet())
                .dimensions(bx + 160, top + 116, 74, 18).build());
    }

    private void buildCells() {
        cells.clear();
        int x0 = tableX();
        int y0 = tableY();
        // Null-Fächer
        if (variant.hasDoubleZero()) {
            cells.add(new Cell(RouletteBet.straight(0), x0, y0, CELL_W, CELL_H * 3 / 2, "0", COLOR_GREEN));
            cells.add(new Cell(RouletteBet.straight(Roulette.DOUBLE_ZERO), x0, y0 + CELL_H * 3 / 2, CELL_W,
                    CELL_H * 3 - CELL_H * 3 / 2, "00", COLOR_GREEN));
        } else if (variant.hasZero()) {
            cells.add(new Cell(RouletteBet.straight(0), x0, y0, CELL_W, CELL_H * 3, "0", COLOR_GREEN));
        }
        // Zahlen 1–36: oben 3, 6, …; unten 1, 4, …
        for (int c = 0; c < 12; c++) {
            for (int r = 0; r < 3; r++) {
                int n = 3 * c + (3 - r);
                int color = Roulette.colorOf(n) == Roulette.Color.RED ? COLOR_RED : COLOR_BLACK;
                cells.add(new Cell(RouletteBet.straight(n), x0 + CELL_W + c * CELL_W, y0 + r * CELL_H, CELL_W, CELL_H,
                        Integer.toString(n), color));
            }
        }
        int dy = y0 + 3 * CELL_H + 2;
        RouletteBet.Kind[] dozens = {RouletteBet.Kind.DOZEN_1, RouletteBet.Kind.DOZEN_2, RouletteBet.Kind.DOZEN_3};
        for (int i = 0; i < 3; i++) {
            cells.add(new Cell(RouletteBet.of(dozens[i]), x0 + CELL_W + i * 4 * CELL_W, dy, 4 * CELL_W, 14,
                    Text.translatable(dozens[i].translationKey()).getString(), 0xFF145A30));
        }
        int oy = dy + 16;
        RouletteBet.Kind[] outside = {RouletteBet.Kind.LOW, RouletteBet.Kind.EVEN, RouletteBet.Kind.RED,
                RouletteBet.Kind.BLACK, RouletteBet.Kind.ODD, RouletteBet.Kind.HIGH};
        for (int i = 0; i < 6; i++) {
            int color = outside[i] == RouletteBet.Kind.RED ? COLOR_RED
                    : outside[i] == RouletteBet.Kind.BLACK ? COLOR_BLACK : 0xFF145A30;
            cells.add(new Cell(RouletteBet.of(outside[i]), x0 + CELL_W + i * 2 * CELL_W, oy, 2 * CELL_W, 14,
                    Text.translatable(outside[i].translationKey()).getString(), color));
        }
    }

    private long tableTotal() {
        return table.values().stream().mapToLong(Long::longValue).sum();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        if (spinning) {
            return false;
        }
        for (Cell cell : cells) {
            if (cell.contains(click.x(), click.y())) {
                if (click.button() == 1) {
                    if (table.remove(cell.bet()) != null) {
                        CasinoSounds.click();
                    }
                } else {
                    long amount = bets.bet();
                    if (tableTotal() + amount > casino.balance()) {
                        CasinoSounds.error();
                        setMessage(Text.translatable("luckycasino.gui.not_enough"), Ui.RED);
                    } else {
                        table.merge(cell.bet(), amount, Long::sum);
                        CasinoSounds.play(net.minecraft.sound.SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, 1.8f, 0.4f);
                        message = null;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void setMessage(Text text, int color) {
        message = text;
        messageColor = color;
    }

    private void rebet() {
        Map<RouletteBet, Long> last = ClientSession.lastRouletteBets;
        long total = last.values().stream().mapToLong(Long::longValue).sum();
        if (last.isEmpty() || total > casino.balance()) {
            CasinoSounds.error();
            return;
        }
        table.clear();
        for (Map.Entry<RouletteBet, Long> e : last.entrySet()) {
            // Einzelzahlen, die es im aktuellen Kessel nicht gibt, überspringen
            if (e.getKey().kind() != RouletteBet.Kind.STRAIGHT || variant.indexOf(e.getKey().number()) >= 0) {
                table.put(e.getKey(), e.getValue());
            }
        }
        CasinoSounds.click();
    }

    private void spin() {
        if (spinning || table.isEmpty()) {
            return;
        }
        long total = tableTotal();
        long before = casino.balance();
        Map<RouletteBet, Long> placed = new LinkedHashMap<>(table);
        Casino.RouletteSpin result = casino.playRoulette(placed);
        if (result == null) {
            CasinoSounds.error();
            setMessage(Text.translatable("luckycasino.gui.not_enough"), Ui.RED);
            return;
        }
        ClientSession.lastRouletteBets = placed;
        lastSpin = result;
        freezeBalance(before - total);
        message = null;

        int idx = variant.indexOf(result.pocket());
        double step = TAU / variant.pocketCount();
        wheelStart = wheelAngle;
        wheelEnd = wheelAngle + TAU * 2 + VISUAL.nextDouble() * TAU;
        double target = wheelEnd + idx * step;
        ballStart = ballAngle;
        double minEnd = ballStart - TAU * 4;
        ballEnd = target - TAU * Math.ceil((target - minEnd) / TAU);
        spinStart = now();
        spinning = true;
        ballInPocket = false;
        CasinoSounds.play(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.3f);
    }

    @Override
    protected boolean primaryAction() {
        spin();
        return true;
    }

    private long spinDuration() {
        return dur(5_200);
    }

    @Override
    public void tick() {
        if (spinning && now() - spinStart >= spinDuration()) {
            finishSpin();
        }
    }

    private void finishSpin() {
        spinning = false;
        ballInPocket = true;
        wheelAngle = wheelEnd;
        ballAngle = ballEnd;
        resultShownAt = now();
        unfreezeBalance();
        table.clear();
        ClientSession.remember(ClientSession.rouletteHistory, lastSpin.pocket(), 14);
        RoundResult r = lastSpin.round();
        if (r.isWin()) {
            if (r.profit() >= r.stake() * 10) {
                CasinoSounds.bigWin();
            } else {
                CasinoSounds.win();
            }
        } else if (r.outcome() == RoundResult.Outcome.PUSH) {
            CasinoSounds.push();
        } else {
            CasinoSounds.lose();
        }
        CasinoSounds.play(net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, 1.6f, 0.35f);
        bets.refresh();
    }

    @Override
    public void finishPendingRounds() {
        if (spinning) {
            finishSpin();
        }
    }

    private void updateAngles(long now) {
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (spinning) {
            float t = Ui.clamp01((now - spinStart) / (float) spinDuration());
            wheelAngle = wheelStart + (wheelEnd - wheelStart) * Ui.easeOutCubic(t);
            ballAngle = ballStart + (ballEnd - ballStart) * Ui.easeOutQuart(t);
            // Ticken, wenn die Kugel über die Fächer hüpft
            double step = TAU / variant.pocketCount();
            int rel = (int) Math.floor((ballAngle - wheelAngle) / step);
            if (t > 0.45f && rel != lastTickPocket) {
                lastTickPocket = rel;
                CasinoSounds.tick(1.9f - t * 0.5f);
            }
        } else {
            // Leerlauf: Kessel dreht langsam weiter, Kugel liegt im Fach
            double idle = 0.35 * dt;
            wheelAngle += idle;
            if (ballInPocket) {
                ballAngle += idle;
            }
        }
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = now();
        updateAngles(now);
        boolean hasTable = !table.isEmpty();
        spinButton.active = !spinning && hasTable;
        clearButton.active = !spinning && hasTable;
        rebetButton.active = !spinning && !ClientSession.lastRouletteBets.isEmpty();
        bets.setLocked(spinning);

        drawWheel(ctx, now);
        Text wheelName = Text.translatable("luckycasino.roulette.variant." + variant.name().toLowerCase());
        Ui.centered(ctx, textRenderer, wheelName, wheelCx(), top + 186, Ui.GRAY);
        drawTable(ctx, mouseX, mouseY);
        drawInfo(ctx);
        bets.renderLabel(ctx, textRenderer);
    }

    private void drawWheel(DrawContext ctx, long now) {
        int cx = wheelCx();
        int cy = wheelCy();
        Ui.fillCircle(ctx, cx, cy, R_RIM + 2, 0xFF1E1206);
        Ui.fillCircle(ctx, cx, cy, R_RIM, 0xFF5A3518);
        Ui.fillCircle(ctx, cx, cy, R_TRACK, 0xFF2B1A0C);
        Ui.fillCircle(ctx, cx, cy, R_OUT + 1, 0xFFC9A227);

        int n = variant.pocketCount();
        double step = TAU / n;
        Matrix3x2fStack m = ctx.getMatrices();
        int winner = !spinning && lastSpin != null ? variant.indexOf(lastSpin.pocket()) : -1;
        for (int i = 0; i < n; i++) {
            int number = variant.pocketAt(i);
            int color = switch (Roulette.colorOf(number)) {
                case RED -> COLOR_RED;
                case BLACK -> COLOR_BLACK;
                case GREEN -> COLOR_GREEN;
            };
            if (i == winner) {
                color = Ui.lerpColor(color, 0xFFFFE066, 0.35f + 0.35f * Ui.pulse(now, 600));
            }
            Ui.wedge(ctx, cx, cy, wheelAngle + i * step, step / 2, R_IN, R_OUT, color);
        }
        for (int i = 0; i < n; i++) {
            double a = wheelAngle + i * step;
            m.pushMatrix();
            m.translate(cx, cy);
            m.rotate((float) (a - step / 2));
            m.translate(0, -0.5f); // 1 px breit, exakt mittig auf der Fachgrenze
            ctx.fill(R_IN, 0, R_OUT, 1, 0xFFC9A227);
            m.popMatrix();
            // Zahl tangential; in der unteren Kesselhälfte um 180° gedreht, damit sie lesbar bleibt
            m.pushMatrix();
            m.translate(cx, cy);
            m.rotate((float) a);
            m.translate(R_OUT - 7, 0);
            m.rotate((float) (Math.sin(a) > 0 ? -Math.PI / 2 : Math.PI / 2));
            m.scale(0.55f, 0.55f);
            String label = Roulette.label(variant.pocketAt(i));
            ctx.drawText(textRenderer, label, -textRenderer.getWidth(label) / 2, -4, Ui.WHITE, false);
            m.popMatrix();
        }
        // Innerer Konus mit Drehkreuz
        Ui.fillCircle(ctx, cx, cy, R_IN, 0xFFC9A227);
        Ui.fillCircle(ctx, cx, cy, R_IN - 1, 0xFF6B4423);
        Ui.fillCircle(ctx, cx, cy, R_IN - 9, 0xFF8B5A2B);
        for (int k = 0; k < 4; k++) {
            m.pushMatrix();
            m.translate(cx, cy);
            m.rotate((float) (wheelAngle + k * Math.PI / 2));
            ctx.fill(0, -1, 24, 2, 0xFFE8C860);
            ctx.fill(22, -3, 26, 3, 0xFFE8C860);
            m.popMatrix();
        }
        Ui.fillCircle(ctx, cx, cy, 6, 0xFFE8C860);
        Ui.fillCircle(ctx, cx, cy, 3, 0xFFFFF2B0);

        // Kugel
        float t = spinning ? Ui.clamp01((now - spinStart) / (float) spinDuration()) : 1f;
        double r;
        if (t < 0.55f) {
            r = R_TRACK - 4;
        } else if (t < 0.8f) {
            float k = (t - 0.55f) / 0.25f;
            r = (R_TRACK - 4) - ((R_TRACK - 4) - (R_OUT - 9)) * Ui.easeOutBack(k);
        } else {
            r = R_OUT - 9;
        }
        if (!spinning && !ballInPocket) {
            r = R_TRACK - 4;
        }
        int bx = (int) Math.round(cx + Math.cos(ballAngle) * r);
        int by = (int) Math.round(cy + Math.sin(ballAngle) * r);
        Ui.fillCircle(ctx, bx + 1, by + 1, 3, 0x80000000);
        Ui.fillCircle(ctx, bx, by, 3, 0xFFF4F4F4);
        ctx.fill(bx - 1, by - 2, bx, by - 1, 0xFFFFFFFF);
    }

    private void drawTable(DrawContext ctx, int mouseX, int mouseY) {
        int x0 = tableX();
        int y0 = tableY();
        ctx.fill(x0 - 3, y0 - 3, x0 + CELL_W * 13 + 3, y0 + 3 * CELL_H + 34, 0xFF0A3A1E);
        if (!variant.hasZero()) {
            ctx.fill(x0, y0, x0 + CELL_W, y0 + 3 * CELL_H, 0xFF0A2A16);
            Ui.centered(ctx, textRenderer, Text.literal("-"), x0 + CELL_W / 2, y0 + 3 * CELL_H / 2 - 4, Ui.DARK_GRAY);
        }
        Cell hovered = null;
        int winningPocket = !spinning && lastSpin != null ? lastSpin.pocket() : -1;
        for (Cell c : cells) {
            boolean hover = !spinning && c.contains(mouseX, mouseY);
            if (hover) {
                hovered = c;
            }
            int color = c.color();
            if (winningPocket >= 0 && c.bet().wins(winningPocket) && now() - resultShownAt < 4_000) {
                color = Ui.lerpColor(color, 0xFFFFE066, 0.5f * Ui.pulse(now(), 500));
            }
            ctx.fill(c.x(), c.y(), c.x() + c.w(), c.y() + c.h(), color);
            ctx.drawStrokedRectangle(c.x(), c.y(), c.w(), c.h(), 0xFFD8C27A);
            if (hover) {
                ctx.fill(c.x() + 1, c.y() + 1, c.x() + c.w() - 1, c.y() + c.h() - 1, 0x40FFFFFF);
            }
            int tw = textRenderer.getWidth(c.label());
            float scale = Math.min(1f, (c.w() - 3) / (float) tw);
            Ui.scaled(ctx, textRenderer, Text.literal(c.label()), c.x() + c.w() / 2f,
                    c.y() + (c.h() - 8 * scale) / 2f + (scale < 1 ? 0.5f : 0), scale, Ui.WHITE, true);
        }
        // Chips auf dem Tisch
        for (Map.Entry<RouletteBet, Long> e : table.entrySet()) {
            for (Cell c : cells) {
                if (c.bet().equals(e.getKey())) {
                    int cx = c.x() + c.w() / 2;
                    int cy = c.y() + c.h() / 2;
                    Ui.chip(ctx, cx, cy, 6, chipColor(e.getValue()));
                    Ui.scaled(ctx, textRenderer, Text.literal(Fmt.compact(e.getValue())), cx, cy - 2, 0.5f, Ui.WHITE, true);
                }
            }
        }
        if (hovered != null) {
            long placed = table.getOrDefault(hovered.bet(), 0L);
            List<Text> lines = new ArrayList<>();
            lines.add(hovered.bet().kind() == RouletteBet.Kind.STRAIGHT
                    ? Text.translatable("luckycasino.roulette.bet.straight_n", hovered.label())
                    : Text.translatable(hovered.bet().kind().translationKey()));
            lines.add(Text.translatable("luckycasino.roulette.tooltip.payout", hovered.bet().kind().payout()));
            if (placed > 0) {
                lines.add(Text.translatable("luckycasino.roulette.tooltip.placed", Fmt.chips(placed)));
            }
            lines.add(Text.translatable("luckycasino.roulette.tooltip.help"));
            ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
        }
    }

    private static int chipColor(long amount) {
        if (amount >= 10_000) {
            return 0xFF7A2BD0;
        }
        if (amount >= 1_000) {
            return 0xFF202020;
        }
        if (amount >= 100) {
            return 0xFF2F6FD0;
        }
        if (amount >= 25) {
            return 0xFF2E9E3E;
        }
        return 0xFFD23C3C;
    }

    private void drawInfo(DrawContext ctx) {
        int x0 = tableX();
        ctx.drawText(textRenderer, Text.translatable("luckycasino.roulette.placed", Fmt.chips(tableTotal())), x0, top + 140, Ui.WHITE, true);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.roulette.help"), x0, top + 151, Ui.DARK_GRAY, true);

        int ry = top + 165;
        if (spinning) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.roulette.rolling"), x0 + 117, ry + 4, Ui.GRAY);
        } else if (message != null) {
            Ui.centered(ctx, textRenderer, message, x0 + 117, ry + 4, messageColor);
        } else if (lastSpin != null) {
            int pocket = lastSpin.pocket();
            Roulette.Color col = Roulette.colorOf(pocket);
            int c = switch (col) {
                case RED -> 0xFFFF5050;
                case BLACK -> 0xFFE0E0E0;
                case GREEN -> 0xFF50FF70;
            };
            Text name = Text.translatable("luckycasino.roulette.color." + col.name().toLowerCase());
            Text headline = Text.literal(Roulette.label(pocket) + " ").append(name);
            Ui.scaled(ctx, textRenderer, headline, x0 + 60, ry, 1.5f, c, true);
            RoundResult r = lastSpin.round();
            Text res = r.isWin() ? Text.translatable("luckycasino.result.win", Fmt.signed(r.profit()))
                    : r.outcome() == RoundResult.Outcome.PUSH ? Text.translatable("luckycasino.result.push")
                    : Text.translatable("luckycasino.result.lose", Fmt.chips(r.stake()));
            Ui.centered(ctx, textRenderer, res, x0 + 175, ry + 3, r.isWin() ? Ui.GREEN : 0xFFFF8080);
            if (r.bonusApplied()) {
                Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.result.bonus", r.bonusMultiplier()), x0 + 175, ry + 14, 0xFFFFB0FF);
            }
        }
        // Verlauf
        int hx = x0;
        int hy = top + 190;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.gui.history"), hx, hy + 2, Ui.GRAY, true);
        hx += textRenderer.getWidth(Text.translatable("luckycasino.gui.history")) + 4;
        for (int n : ClientSession.rouletteHistory) {
            if (hx > x0 + 220) {
                break;
            }
            int color = switch (Roulette.colorOf(n)) {
                case RED -> COLOR_RED;
                case BLACK -> COLOR_BLACK;
                case GREEN -> COLOR_GREEN;
            };
            ctx.fill(hx, hy, hx + 15, hy + 12, color);
            Ui.centered(ctx, textRenderer, Text.literal(Roulette.label(n)), hx + 8, hy + 2, Ui.WHITE);
            hx += 17;
        }
    }
}
