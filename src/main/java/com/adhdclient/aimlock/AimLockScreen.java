package com.adhdclient.aimlock;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Einstellungen von Aim Lock: Taste, Reichweite in Blöcken, Tempo, Abweichung, nur Spieler. */
public final class AimLockScreen extends ThemedScreen {

    private static final int W = 260;
    private static final int ROW = 20;

    public AimLockScreen(Screen parent) {
        super(Text.literal("Aim Lock"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + 12 + 4 * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        AimLockClient.Config c = AimLockClient.config();

        section(L.t("Taste", "Key"), x, y, inner);
        y += 12;
        keyButton("Lock", () -> c.key, v -> c.key = v, new AimLockClient.Config().key, x, y, inner, ROW - 2, null);
        y += ROW + 6;

        section(L.t("Lock", "Lock"), x, y, inner);
        y += 12;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Lösen ab", "Release at"), 2, 64, 0.5, c.range,
                v -> String.format(Locale.ROOT, L.t("%.1f Blöcke", "%.1f blocks"), v), v -> c.range = v));
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Tempo", "Speed"), 10, 100, 5, c.speed,
                v -> (int) Math.round(v) + " %", v -> c.speed = (int) Math.round(v)));
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Abweichung", "Deviation"), 0, 100, 5, c.deviation,
                v -> (int) Math.round(v) + " %", v -> c.deviation = (int) Math.round(v)));
        y += ROW;
        toggle(L.t("Nur Spieler", "Players only"), () -> c.playersOnly, v -> c.playersOnly = v, x, y, inner, ROW - 2,
                L.t("Nur andere Spieler anvisieren, keine Tiere oder Monster", "Only target other players, no animals or monsters"), null);
        y += ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(AimLockClient.enabled ? L.t("Taste ", "Press ") + AimLockClient.keyName() + L.t(" lockt das Ziel im Fadenkreuz", " to lock the target in your crosshair")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        AimLockClient.save();
    }

    @Override
    protected Runnable resetAction() {
        return () -> {
            com.adhdclient.gui.Defaults.reset(AimLockClient.config());
            AimLockClient.save();
        };
    }
}
