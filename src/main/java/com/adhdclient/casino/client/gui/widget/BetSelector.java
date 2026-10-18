package com.adhdclient.casino.client.gui.widget;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.gui.Ui;
import com.adhdclient.casino.core.BetMath;
import com.adhdclient.casino.core.Casino;
import com.adhdclient.casino.core.Odds;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;

/**
 * Einsatz-Steuerung: Eingabefeld + Buttons ½, x2, +10, +100, Max.
 * Der Einsatz wird im Spielstand gemerkt und gilt für alle Spiele.
 */
public final class BetSelector {

    /** Gesamtbreite der Leiste. */
    public static final int WIDTH = 232;

    private final Casino casino;
    private final List<ClickableWidget> widgets = new ArrayList<>();
    private TextFieldWidget field;
    private int x;
    private int y;
    private boolean locked;

    public BetSelector(Casino casino) {
        this.casino = casino;
    }

    public void init(Consumer<ClickableWidget> add, TextRenderer tr, int x, int y) {
        this.x = x;
        this.y = y;
        widgets.clear();
        field = new TextFieldWidget(tr, x + 44, y + 1, 56, 14, Text.translatable("luckycasino.bet"));
        field.setMaxLength(12);
        field.setText(Long.toString(BetMath.clamp(casino.bet(), Math.max(casino.balance(), Odds.MIN_BET))));
        field.setChangedListener(text -> {
            long v = BetMath.parse(text);
            if (v > 0) {
                casino.setBet(v);
            }
            updateColor();
        });
        widgets.add(field);
        add.accept(field);

        int bx = x + 104;
        bx = button(add, bx, 18, "½", "luckycasino.bet.half", b -> BetMath.halved(b, casino.balance()));
        bx = button(add, bx, 20, "x2", "luckycasino.bet.double", b -> BetMath.doubled(b, casino.balance()));
        bx = button(add, bx, 26, "+10", "luckycasino.bet.plus10", b -> BetMath.add(b, 10, casino.balance()));
        bx = button(add, bx, 32, "+100", "luckycasino.bet.plus100", b -> BetMath.add(b, 100, casino.balance()));
        button(add, bx, 28, "Max", "luckycasino.bet.max", b -> BetMath.maxBet(casino.balance()));
        setLocked(locked);
        updateColor();
    }

    private int button(Consumer<ClickableWidget> add, int bx, int w, String label, String tooltipKey, LongUnaryOperator op) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
                    long v = op.applyAsLong(bet());
                    field.setText(Long.toString(v));
                    casino.setBet(v);
                    CasinoSounds.click();
                })
                .dimensions(bx, y, w, 16)
                .tooltip(Tooltip.of(Text.translatable(tooltipKey)))
                .build();
        widgets.add(b);
        add.accept(b);
        return bx + w + 2;
    }

    /** Aktuell eingestellter Einsatz (nicht ans Guthaben angepasst). */
    public long bet() {
        long v = field == null ? -1 : BetMath.parse(field.getText());
        return v > 0 ? Math.min(v, Odds.MAX_BET) : casino.bet();
    }

    public boolean affordable() {
        return casino.canAfford(bet());
    }

    /** Während einer Runde sperren. */
    public void setLocked(boolean locked) {
        this.locked = locked;
        for (ClickableWidget w : widgets) {
            w.active = !locked;
        }
        if (field != null) {
            field.setEditable(!locked);
        }
    }

    /** Nach einer Runde: Einsatz an ein gesunkenes Guthaben anpassen. */
    public void refresh() {
        if (field == null) {
            return;
        }
        if (casino.balance() > 0 && bet() > casino.balance()) {
            long v = BetMath.clamp(bet(), casino.balance());
            field.setText(Long.toString(v));
            casino.setBet(v);
        }
        updateColor();
    }

    private void updateColor() {
        if (field != null) {
            field.setEditableColor(affordable() ? Ui.WHITE : Ui.RED);
        }
    }

    public void renderLabel(DrawContext ctx, TextRenderer tr) {
        ctx.drawText(tr, Text.translatable("luckycasino.bet.label"), x, y + 4, Ui.GOLD, true);
    }
}
