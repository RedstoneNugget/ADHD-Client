package com.adhdclient.intro;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Client-Intro: Intro jetzt noch einmal ansehen. */
public final class IntroScreen extends ThemedScreen {

    private static final int W = 240;
    private static final int ROW = 20;

    public IntroScreen(Screen parent) {
        super(Text.literal(L.t("Client-Intro", "Client intro")), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Intro ansehen", "Watch intro")), b -> {
            IntroClient.replay();
            client.setScreen(new TitleScreen());
        }).dimensions(x, y, inner, ROW - 2).build());
        y += ROW + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(IntroClient.enabled ? L.t("Läuft beim Start im Hauptmenü – Klick überspringt", "Plays on the title screen at start – click skips")
                : L.t("Aus – im ADHD-Menü einschalten", "Off – turn it on in the ADHD menu"));
    }
}
