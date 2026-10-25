package com.adhdclient.stronghold;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Stronghold Finder: kurze Anleitung und Zurücksetzen. */
public final class StrongholdScreen extends ThemedScreen {

    private static final int W = 280;
    private static final int ROW = 20;

    public StrongholdScreen(Screen parent) {
        super(Text.literal("Stronghold Finder"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + 4 * 11 + 6 + ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;

        section(L.t("So geht's", "How it works"), x, y, inner);
        y += 12;
        String[] steps = {
                L.t("1. Ender-Auge werfen", "1. Throw an eye of ender"),
                L.t("2. 100–300 Blöcke seitlich gehen", "2. Walk 100–300 blocks sideways"),
                L.t("3. Noch ein Auge werfen", "3. Throw another eye"),
                L.t("4. Koordinaten stehen oben links", "4. Coordinates show top left")};
        for (String s : steps) {
            line(s, x, y, Theme.text());
            y += 11;
        }
        y += 6;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Würfe zurücksetzen", "Reset throws")), b -> {
            StrongholdClient.reset();
            status(L.t("Zurückgesetzt", "Reset"));
        }).dimensions(x, y, inner, ROW - 2).build());
        y += ROW + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(StrongholdClient.enabled ? StrongholdClient.throwsMade().size() + L.t(" Würfe erfasst", " throws recorded")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }
}
