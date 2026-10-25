package com.adhdclient.airjump;

import com.adhdclient.core.AdhdModuleState;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Air Jump: Anzahl Luftsprünge und Sprungkraft. */
public final class AirJumpScreen extends ThemedScreen {

    private static final int W = 260;
    private static final int ROW = 20;

    public AirJumpScreen(Screen parent) {
        super(Text.literal("Air Jump"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + 2 * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        section(L.t("Sprünge", "Jumps"), x, y, inner);
        y += 12;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Luftsprünge", "Air jumps"), 0, 10, 1, AirJumpClient.maxJumps,
                v -> v < 0.5 ? L.t("unbegrenzt", "unlimited") : String.valueOf((int) Math.round(v)), v -> AirJumpClient.maxJumps = (int) Math.round(v)));
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Sprungkraft", "Strength"), 0.2, 1.0, 0.02, AirJumpClient.strength,
                v -> String.format(Locale.ROOT, "%.2f", v), v -> AirJumpClient.strength = v));
        y += ROW + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(AirJumpClient.enabled ? L.t("In der Luft Springen drücken – auf Servern meist erkannt", "Press jump in the air – usually detected on servers")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        AdhdModuleState.save();
    }

    @Override
    protected Runnable resetAction() {
        return () -> {
            AirJumpClient.maxJumps = 1;
            AirJumpClient.strength = 0.42;
            com.adhdclient.core.AdhdModuleState.save();
        };
    }
}
