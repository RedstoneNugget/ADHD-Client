package com.adhdclient.anticrystal;

import com.adhdclient.core.AdhdModuleState;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Anti Crystal Death: Reichweite. */
public final class AntiCrystalScreen extends ThemedScreen {

    private static final int W = 260;
    private static final int ROW = 20;

    public AntiCrystalScreen(Screen parent) {
        super(Text.literal("Anti Crystal Death"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        section(L.t("Kristall in der Nähe", "Crystal nearby"), x, y, inner);
        y += 12;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Reichweite", "Range"), 2, 16, 0.5, AntiCrystalClient.radius,
                v -> String.format(Locale.ROOT, L.t("%.1f Blöcke", "%.1f blocks"), v), v -> AntiCrystalClient.radius = v));
        y += ROW + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(AntiCrystalClient.enabled ? L.t("Öffnet das Inventar und legt Totems in beide Hände", "Opens the inventory and puts totems in both hands")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        AdhdModuleState.save();
    }
}
