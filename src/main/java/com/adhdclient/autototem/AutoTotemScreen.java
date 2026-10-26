package com.adhdclient.autototem;

import com.adhdclient.core.AdhdModuleState;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Auto Totem: auch im Inventar suchen, Pause zwischen Tauschen. */
public final class AutoTotemScreen extends ThemedScreen {

    private static final int W = 260;
    private static final int ROW = 20;

    public AutoTotemScreen(Screen parent) {
        super(Text.literal("Auto Totem"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + ROW + 6 + 12 + 2 * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        keyButton(L.t("An/Aus", "On/off"), () -> AutoTotemClient.toggleKey, v -> AutoTotemClient.toggleKey = v, com.adhdclient.gui.HotKey.NONE, x, y, inner, ROW - 2, AdhdModuleState::save);
        y += ROW + 6;
        section(L.t("Totem in die Offhand", "Totem to offhand"), x, y, inner);
        y += 12;
        toggle(L.t("Auch im Inventar suchen", "Also search inventory"), () -> AutoTotemClient.wholeInventory, v -> AutoTotemClient.wholeInventory = v,
                x, y, inner, ROW - 2, L.t("Aus = nur Hotbar; an = auch die 27 Inventar-Felder", "Off = hotbar only; on = also the 27 inventory slots"), null);
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Tempo", "Speed"), 0, 200, 0.5, AutoTotemClient.delay,
                v -> v < 0.01 ? L.t("sofort", "instant") : v < 0.6 ? L.t("halber Tick pro Feld", "half a tick per slot")
                        : (int) Math.round(v) + L.t(" Ticks pro Feld", " ticks per slot"),
                v -> AutoTotemClient.delay = Math.round(v * 2) / 2.0));
        y += ROW + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(AutoTotemClient.enabled ? L.t("Fehlt das Totem in der Offhand, kommt eins nach", "If the offhand totem is missing, another one is moved in")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        AdhdModuleState.save();
    }

    @Override
    protected Runnable resetAction() {
        return () -> {
            AutoTotemClient.wholeInventory = false;
            AutoTotemClient.delay = 0.5;
            AutoTotemClient.toggleKey = com.adhdclient.gui.HotKey.NONE;
            com.adhdclient.core.AdhdModuleState.save();
        };
    }
}
