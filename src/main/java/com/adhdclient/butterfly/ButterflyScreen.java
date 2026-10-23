package com.adhdclient.butterfly;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Einstellungen von Butterfly: Inspect-Taste, welche Items welcher Look werden, Farben, Tempo, Animationen. */
public final class ButterflyScreen extends ThemedScreen {

    private static final int W = 300;
    private static final int ROW = 20;
    private static final String[] PALETTE = {"#FFFFFF", "#FFD54A", "#DC2626", "#F97316", "#22C55E", "#3B82F6", "#A855F7", "#EC4899", "#4A4E55"};
    private static final int SW = 14;
    private static final int STEP = 17;

    private record Palette(int x, int y, Supplier<String> get, Consumer<String> set) {
    }

    private final List<Palette> palettes = new ArrayList<>();

    public ButterflyScreen(Screen parent) {
        super(Text.literal("Butterfly"), parent);
    }

    @Override
    protected void build() {
        palettes.clear();
        int looks = ButterflyLook.values().length;
        int h = Theme.TITLE_H + 6 + 12 + ROW + looks * ROW + 6 + 12 + 2 * 18 + 6 + 12 + 2 * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        int third = (inner - 8) / 3;
        ButterflyConfig c = ButterflyClient.config();

        section(L.t("Taste und Items", "Key and items"), x, y, inner);
        y += 12;
        keyButton("Inspect", () -> c.inspectKey, v -> c.inspectKey = v, new ButterflyConfig().inspectKey, x, y, inner, ROW - 2, ButterflyClient::onConfigEdited);
        y += ROW;
        for (ButterflyLook look : ButterflyLook.values()) {
            int n = c.looks.getOrDefault(look.id, List.of()).size();
            ButtonWidget pick = ButtonWidget.builder(Text.literal(look.title() + ": " + n + L.t(" Items wählen …", " items – choose …")),
                    b -> client.setScreen(new ItemLookPickerScreen(this, look))).dimensions(x, y, inner, ROW - 2).build();
            pick.setTooltip(Tooltip.of(Text.literal(L.t("Diese Items sehen in deiner Hand aus wie ein ", "These items look like a ")
                    + look.title() + L.t(" (nur bei dir)", " in your hand (only for you)"))));
            addDrawableChild(pick);
            y += ROW;
        }
        y += 6;

        section(L.t("Farben", "Colors"), x, y, inner);
        y += 12;
        palette(L.t("Klinge", "Blade"), x, y, () -> c.bladeColor, v -> c.bladeColor = v);
        y += 18;
        palette(L.t("Griffe", "Handles"), x, y, () -> c.handleColor, v -> c.handleColor = v);
        y += 18 + 6;

        section(L.t("Bewegung", "Movement"), x, y, inner);
        y += 12;
        toggle(L.t("Aufklappen", "Open flip"), () -> c.equipFlip, v -> c.equipFlip = v, x, y, third, ROW - 2,
                L.t("Beim Ziehen klappt das Messer mit einem Flip auf", "The knife flips open when you pull it out"), ButterflyClient::onConfigEdited);
        toggle(L.t("Flip beim Schlag", "Flip on hit"), () -> c.attackFlip, v -> c.attackFlip = v, x + third + 4, y, third, ROW - 2,
                L.t("Beim Angriff ein schneller Flip mit Stich", "A quick flip with a stab when you attack"), ButterflyClient::onConfigEdited);
        toggle(L.t("Spielen", "Fidget"), () -> c.idleFidget, v -> c.idleFidget = v, x + 2 * (third + 4), y, inner - 2 * (third + 4), ROW - 2,
                L.t("Im Stand spielt die Hand ab und zu mit dem Messer", "Standing still, your hand plays with the knife now and then"), ButterflyClient::onConfigEdited);
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Tempo", "Speed"), 0.5, 2.0, 0.1, c.speed,
                v -> String.format(Locale.ROOT, "×%.1f", v), v -> {
            c.speed = v;
            ButterflyClient.onConfigEdited();
        }));
        y += ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(ButterflyClient.enabled ? L.t("Inspect mit ", "Inspect with ") + ButterflyClient.keyName() + L.t(" – nur du siehst das Messer", " – only you see the knife")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    /** Zeile "Name ■ ■ ■ …": ein Klick auf ein Farbfeld wählt die Farbe. */
    private void palette(String label, int x, int y, Supplier<String> get, Consumer<String> set) {
        line(label, x, y + 5, Theme.text());
        palettes.add(new Palette(x + 64, y + 2, get, set));
    }

    private int swatchAt(Palette p, double mx, double my) {
        if (my < p.y || my >= p.y + SW || mx < p.x) return -1;
        int i = (int) ((mx - p.x) / STEP);
        if (i >= PALETTE.length || mx - p.x - i * STEP >= SW) return -1;
        return i;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (Palette p : palettes) {
            int hovered = swatchAt(p, mouseX, mouseY);
            for (int i = 0; i < PALETTE.length; i++) {
                int sx = p.x + i * STEP;
                Theme.swatch(ctx, sx, p.y, SW, Math.max(0, ButterflyConfig.parse(PALETTE[i])));
                boolean selected = PALETTE[i].equalsIgnoreCase(p.get.get());
                if (selected || i == hovered) {
                    Theme.border(ctx, sx - 1, p.y - 1, sx + SW + 1, p.y + SW + 1, selected ? Theme.accent() : Theme.textDim());
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            for (Palette p : palettes) {
                int i = swatchAt(p, click.x(), click.y());
                if (i >= 0) {
                    p.set.accept(PALETTE[i]);
                    ButterflyClient.onConfigEdited();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    protected void onClose() {
        ButterflyClient.saveNow();
    }
}
