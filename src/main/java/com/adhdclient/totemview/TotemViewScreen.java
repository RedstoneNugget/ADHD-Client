package com.adhdclient.totemview;

import com.adhdclient.core.AdhdModuleState;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Gegner-Totem-Anzeige: Pops anzeigen ja/nein. */
public final class TotemViewScreen extends ThemedScreen {

    private static final int W = 260;
    private static final int ROW = 20;

    public TotemViewScreen(Screen parent) {
        super(Text.literal(L.t("Gegner-Totems", "Enemy totems")), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        section(L.t("Am Namensschild", "On the name tag"), x, y, inner);
        y += 12;
        toggle(L.t("Totem-Pops zählen", "Count totem pops"), () -> TotemViewClient.showPops, v -> TotemViewClient.showPops = v, x, y, inner, ROW - 2,
                L.t("Zeigt „×2“, wenn das Totem eines Spielers schon zweimal geplatzt ist", "Shows \"×2\" when a player's totem has popped twice"), AdhdModuleState::save);
        y += ROW + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(TotemViewClient.enabled ? L.t("„⚑ Totem“ hinter Spielern mit Totem in der Hand", "\"⚑ Totem\" after players holding a totem")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        AdhdModuleState.save();
    }

    @Override
    protected Runnable resetAction() {
        return () -> {
            TotemViewClient.showPops = true;
            AdhdModuleState.save();
        };
    }
}
