package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.ClientSession;
import com.adhdclient.casino.core.scratch.ScratchSymbol;
import com.adhdclient.casino.core.scratch.ScratchTicket;
import com.adhdclient.casino.core.scratch.ScratchTier;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Rubbellose: Felder mit gedrückter Maustaste freirubbeln, drei gleiche Symbole gewinnen. */
public class ScratchScreen extends CasinoScreen {

    private static final int FIELD = 40;
    private static final int FIELD_GAP = 6;
    private static final int GRID = 8;
    private static final int CELL = FIELD / GRID;
    private static final int RADIUS = 7;
    private static final float REVEAL_AT = 0.6f;

    private final ButtonWidget[] buyButtons = new ButtonWidget[ScratchTier.values().length];
    private ButtonWidget revealButton;
    private boolean scratching;
    private long lastScratchSound;
    private long finishedAt;

    public ScratchScreen(Screen parent) {
        super(Text.translatable("luckycasino.game.scratch"), parent);
    }

    private ScratchTicket ticket() {
        return ClientSession.scratchTicket;
    }

    private int ticketX() {
        return left + 124;
    }

    private int ticketY() {
        return top + 30;
    }

    private int fieldX(int f) {
        return ticketX() + 14 + (f % 3) * (FIELD + FIELD_GAP);
    }

    private int fieldY(int f) {
        return ticketY() + 34 + (f / 3) * (FIELD + FIELD_GAP);
    }

    @Override
    protected void initContent() {
        ScratchTier[] tiers = ScratchTier.values();
        for (int i = 0; i < tiers.length; i++) {
            ScratchTier tier = tiers[i];
            buyButtons[i] = addDrawableChild(ButtonWidget.builder(
                    Text.translatable("luckycasino.scratch.buy", Text.translatable(tier.translationKey()), Fmt.chips(tier.price())),
                    b -> buy(tier)).dimensions(left + 10, top + 46 + i * 24, 106, 20).build());
        }
        revealButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.scratch.reveal_all"), b -> revealAll())
                .dimensions(left + 10, top + 130, 106, 20).build());
        if (ticket() != null && !allRevealed()) {
            freezeBalance(casino.balance() - ticket().prize());
        }
        updateButtons();
    }

    private boolean allRevealed() {
        boolean[] r = ClientSession.scratchRevealed;
        if (r == null) {
            return true;
        }
        for (boolean b : r) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    private void updateButtons() {
        boolean active = ticket() != null && !allRevealed();
        ScratchTier[] tiers = ScratchTier.values();
        for (int i = 0; i < tiers.length; i++) {
            buyButtons[i].active = !active && casino.canAfford(tiers[i].price());
        }
        revealButton.active = active;
    }

    private void buy(ScratchTier tier) {
        long before = casino.balance();
        ScratchTicket t = casino.buyScratchTicket(tier);
        if (t == null) {
            CasinoSounds.error();
            return;
        }
        ClientSession.scratchTicket = t;
        ClientSession.scratchCells = new boolean[ScratchTicket.FIELDS][GRID * GRID];
        ClientSession.scratchRevealed = new boolean[ScratchTicket.FIELDS];
        finishedAt = 0;
        freezeBalance(before - tier.price());
        CasinoSounds.play(net.minecraft.sound.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0f, 0.8f);
        updateButtons();
    }

    private void revealAll() {
        if (ticket() == null) {
            return;
        }
        for (int f = 0; f < ScratchTicket.FIELDS; f++) {
            ClientSession.scratchRevealed[f] = true;
        }
        CasinoSounds.scratch();
        onAllRevealed();
    }

    private void onAllRevealed() {
        finishedAt = now();
        unfreezeBalance();
        ScratchTicket t = ticket();
        if (t.winSymbol() == null) {
            CasinoSounds.lose();
        } else if (t.winSymbol().multiplier() >= 50) {
            CasinoSounds.jackpot();
        } else if (t.winSymbol().multiplier() >= 5) {
            CasinoSounds.bigWin();
        } else if (t.winSymbol().multiplier() > 1) {
            CasinoSounds.win();
        } else {
            CasinoSounds.push();
        }
        updateButtons();
    }

    @Override
    public void finishPendingRounds() {
        // Gewinn ist schon gebucht; das Los bleibt zum Weiterrubbeln in der Sitzung
        unfreezeBalance();
    }

    // ------------------------------------------------------------------
    // Rubbeln
    // ------------------------------------------------------------------

    private boolean overTicket(double mx, double my) {
        return mx >= fieldX(0) - RADIUS && mx < fieldX(2) + FIELD + RADIUS && my >= fieldY(0) - RADIUS && my < fieldY(8) + FIELD + RADIUS;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        if (click.button() == 0 && ticket() != null && !allRevealed() && overTicket(click.x(), click.y())) {
            scratching = true;
            scratchAt(click.x(), click.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (scratching) {
            // Zwischenpunkte, damit schnelle Mausbewegungen keine Lücken lassen
            int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 3) + 1;
            for (int i = 1; i <= steps; i++) {
                scratchAt(click.x() - dx + dx * i / steps, click.y() - dy + dy * i / steps);
            }
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (scratching && click.button() == 0) {
            scratching = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void scratchAt(double mx, double my) {
        if (ticket() == null || allRevealed()) {
            return;
        }
        boolean any = false;
        for (int f = 0; f < ScratchTicket.FIELDS; f++) {
            if (ClientSession.scratchRevealed[f]) {
                continue;
            }
            int fx = fieldX(f);
            int fy = fieldY(f);
            if (mx < fx - RADIUS || mx > fx + FIELD + RADIUS || my < fy - RADIUS || my > fy + FIELD + RADIUS) {
                continue;
            }
            boolean[] cells = ClientSession.scratchCells[f];
            int cleared = 0;
            for (int c = 0; c < cells.length; c++) {
                double ccx = fx + (c % GRID) * CELL + CELL / 2.0;
                double ccy = fy + (c / GRID) * CELL + CELL / 2.0;
                if (!cells[c] && (ccx - mx) * (ccx - mx) + (ccy - my) * (ccy - my) <= RADIUS * RADIUS) {
                    cells[c] = true;
                    any = true;
                }
                if (cells[c]) {
                    cleared++;
                }
            }
            if (cleared >= cells.length * REVEAL_AT) {
                ClientSession.scratchRevealed[f] = true;
                CasinoSounds.play(net.minecraft.sound.SoundEvents.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 1.4f, 0.5f);
            }
        }
        long now = now();
        if (any && now - lastScratchSound > 90) {
            lastScratchSound = now;
            CasinoSounds.scratch();
        }
        if (allRevealed()) {
            scratching = false;
            onAllRevealed();
        }
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawText(textRenderer, Text.translatable("luckycasino.scratch.choose"), left + 10, top + 32, Ui.GOLD, true);
        Ui.wrapped(ctx, textRenderer, Text.translatable("luckycasino.scratch.help"), left + 10, top + 158, 106, Ui.GRAY);
        drawTicket(ctx, mouseX, mouseY);
        drawPrizes(ctx);
    }

    private static int tierColor(ScratchTier tier) {
        return switch (tier) {
            case BRONZE -> 0xFFB0703A;
            case SILVER -> 0xFFAEB8C6;
            case GOLD -> 0xFFE8B830;
        };
    }

    private void drawTicket(DrawContext ctx, int mouseX, int mouseY) {
        int x = ticketX();
        int y = ticketY();
        int w = 3 * FIELD + 2 * FIELD_GAP + 28;
        int h = 3 * FIELD + 2 * FIELD_GAP + 48;
        ScratchTicket t = ticket();
        if (t == null) {
            ctx.fill(x, y, x + w, y + h, 0x40000000);
            ctx.drawStrokedRectangle(x, y, w, h, 0x80FFFFFF);
            Ui.wrapped(ctx, textRenderer, Text.translatable("luckycasino.scratch.none"), x + 12, y + h / 2 - 10, w - 24, Ui.GRAY);
            return;
        }
        int base = tierColor(t.tier());
        Ui.bevel(ctx, x, y, w, h, base);
        ctx.fill(x + 4, y + 4, x + w - 4, y + 28, Ui.darken(base, 0.35f));
        Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.scratch.ticket", Text.translatable(t.tier().translationKey())),
                x + w / 2, y + 7, Ui.WHITE);
        Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.scratch.rule"), x + w / 2, y + 17, 0xFFFFF2B0);

        boolean done = allRevealed();
        long now = now();
        for (int f = 0; f < ScratchTicket.FIELDS; f++) {
            int fx = fieldX(f);
            int fy = fieldY(f);
            ctx.fill(fx - 1, fy - 1, fx + FIELD + 1, fy + FIELD + 1, Ui.darken(base, 0.5f));
            ctx.fill(fx, fy, fx + FIELD, fy + FIELD, 0xFFF5ECD0);
            ScratchSymbol s = t.symbol(f);
            Ui.item(ctx, Icons.of(s.itemId()), fx + (FIELD - 24) / 2f, fy + (FIELD - 24) / 2f, 1.5f);
            if (!ClientSession.scratchRevealed[f]) {
                boolean[] cells = ClientSession.scratchCells[f];
                boolean untouched = true;
                for (int c = 0; c < cells.length; c++) {
                    if (cells[c]) {
                        untouched = false;
                        continue;
                    }
                    int cx = fx + (c % GRID) * CELL;
                    int cy = fy + (c / GRID) * CELL;
                    int shade = ((c % GRID) + (c / GRID)) % 2 == 0 ? 0xFFB4B4BE : 0xFFC4C4CE;
                    ctx.fill(cx, cy, cx + CELL, cy + CELL, shade);
                }
                if (untouched) {
                    Ui.centered(ctx, textRenderer, Text.literal("?"), fx + FIELD / 2, fy + FIELD / 2 - 4, 0xFF7A7A88);
                }
            } else if (done && t.isWinningField(f)) {
                int c = Ui.lerpColor(0xFFFFD34D, 0xFFFFFFFF, Ui.pulse(now, 450));
                ctx.drawStrokedRectangle(fx - 1, fy - 1, FIELD + 2, FIELD + 2, c);
                ctx.drawStrokedRectangle(fx, fy, FIELD, FIELD, c);
            }
        }
        // Münze als Mauszeiger beim Rubbeln
        if (!done && overTicket(mouseX, mouseY)) {
            Ui.chip(ctx, mouseX, mouseY, 5, 0xFFE8B830);
        }
    }

    private void drawPrizes(DrawContext ctx) {
        int x = left + 300;
        int y = top + 30;
        Ui.box(ctx, x, y, 110, 128, 0xB0000000, 0xFF2E4A36);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.scratch.prizes"), x + 6, y + 5, Ui.GOLD, true);
        ScratchTicket t = ticket();
        long price = t != null ? t.tier().price() : ScratchTier.BRONZE.price();
        ScratchSymbol[] all = ScratchSymbol.values();
        for (int i = 0; i < all.length; i++) {
            ScratchSymbol s = all[all.length - 1 - i];
            int ry = y + 18 + i * 17;
            Ui.item(ctx, Icons.of(s.itemId()), x + 6, ry, 0.75f);
            ctx.drawText(textRenderer, "x3", x + 20, ry + 2, Ui.GRAY, false);
            Ui.right(ctx, textRenderer, Text.literal(Fmt.chips(price * s.multiplier())), x + 104, ry + 2,
                    s == ScratchSymbol.STAR ? Ui.GOLD : Ui.WHITE);
        }
        ctx.drawText(textRenderer, Text.translatable("luckycasino.scratch.price_note", Fmt.chips(price)), x + 6, y + 120 - 4, Ui.DARK_GRAY, false);
    }

    @Override
    protected void renderOverlay(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ScratchTicket t = ticket();
        if (t == null || !allRevealed() || finishedAt == 0) {
            return;
        }
        float appear = Ui.clamp01((now() - finishedAt) / 250f);
        int cx = left + 300 + 55;
        int cy = top + 186;
        if (t.winSymbol() != null) {
            long profit = t.prize() - t.tier().price();
            Text l1 = Text.translatable("luckycasino.scratch.won", Fmt.chips(t.prize()));
            Text l2 = profit > 0 ? Text.translatable("luckycasino.result.chips", Fmt.signed(profit))
                    : Text.translatable("luckycasino.result.push_refund");
            ctx.drawText(textRenderer, l1, cx - textRenderer.getWidth(l1) / 2, cy, Ui.lerpColor(Ui.WHITE, Ui.GREEN, appear), true);
            ctx.drawText(textRenderer, l2, cx - textRenderer.getWidth(l2) / 2, cy + 12, Ui.GOLD, true);
        } else {
            Text l1 = Text.translatable("luckycasino.scratch.nothing");
            ctx.drawText(textRenderer, l1, cx - textRenderer.getWidth(l1) / 2, cy, 0xFFFF8080, true);
        }
    }
}
