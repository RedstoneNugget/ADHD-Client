package com.adhdclient.fullbright;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Einstellungen von Full Bright: Stärke und Dunkelheits-Effekt. Keine Taste – an/aus nur im ADHD-Menü. */
public final class FullBrightScreen extends ThemedScreen {

    private static final int W = 260;
    private static final int ROW = 20;

    public FullBrightScreen(Screen parent) {
        super(Text.literal("Full Bright"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + 2 * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        FullBrightClient.Config c = FullBrightClient.config();

        section(L.t("Helligkeit", "Brightness"), x, y, inner);
        y += 12;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Stärke", "Strength"), 0, 100, 5, c.strength,
                v -> (int) Math.round(v) + " %", v -> c.strength = (int) Math.round(v)));
        y += ROW;
        toggle(L.t("Dunkelheit ignorieren", "Ignore darkness"), () -> c.ignoreDarkness, v -> c.ignoreDarkness = v, x, y, inner, ROW - 2,
                L.t("Der Dunkelheits-Effekt von Warden und Sculk-Kreischer wird nicht angezeigt", "The Darkness effect from the Warden and Sculk Shriekers is not shown"), null);
        y += ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(FullBrightClient.enabled ? L.t("Full Bright ist an", "Full Bright is on") : L.t("Im ADHD-Menü einschalten – keine Taste nötig", "Turn it on in the ADHD menu – no key needed"));
    }

    @Override
    protected void onClose() {
        FullBrightClient.save();
    }
}
