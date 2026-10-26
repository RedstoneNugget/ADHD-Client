package com.adhdclient.reconnect;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Einstellungen von Auto-Reconnect: Wartezeit und Anzahl Versuche. */
public final class AutoReconnectScreen extends ThemedScreen {

    private static final int W = 260;
    private static final int ROW = 20;

    public AutoReconnectScreen(Screen parent) {
        super(Text.literal("Auto Reconnect"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + 2 * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        AutoReconnectClient.Config c = AutoReconnectClient.config();

        section(L.t("Neu verbinden", "Reconnect"), x, y, inner);
        y += 12;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Warten", "Wait"), 0, 3600, 1, c.delay,
                v -> v < 0.5 ? L.t("sofort", "instant") : (int) Math.round(v) + " s", v -> c.delay = (int) Math.round(v)));
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Versuche", "Attempts"), 0, 1000, 1, c.maxAttempts,
                v -> v < 0.5 ? L.t("unbegrenzt", "unlimited") : String.valueOf((int) Math.round(v)), v -> c.maxAttempts = (int) Math.round(v)));
        y += ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(AutoReconnectClient.enabled ? L.t("Nach einem Rauswurf wird automatisch neu verbunden", "After a disconnect you are reconnected automatically")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        AutoReconnectClient.save();
    }

    @Override
    protected Runnable resetAction() {
        return () -> {
            com.adhdclient.gui.Defaults.reset(AutoReconnectClient.config());
            AutoReconnectClient.save();
        };
    }
}
