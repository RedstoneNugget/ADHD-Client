package com.adhdclient.casino;

import com.adhdclient.casino.client.LuckyCasinoClient;
import com.adhdclient.casino.client.gui.CasinoMenuScreen;
import com.adhdclient.casino.client.gui.Fmt;
import com.adhdclient.casino.client.gui.SettingsScreen;
import com.adhdclient.casino.config.CasinoConfig;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Modul-Menü von Lucky Casino: Taste ganz oben, Casino öffnen, Casino-Einstellungen, kurze Erklärung. */
public final class CasinoModuleScreen extends ThemedScreen {

    private static final int W = 290;
    private static final int ROW = 20;

    public CasinoModuleScreen(Screen parent) {
        super(Text.literal("Lucky Casino"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + 12 + 2 * ROW + 6 + 12 + 3 * 11 + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        CasinoConfig c = LuckyCasinoClient.config();

        section(L.t("Taste", "Key"), x, y, inner);
        y += 12;
        keyButton(L.t("Casino öffnen", "Open casino"), () -> c.adhdOpenKey, v -> c.adhdOpenKey = v, new CasinoConfig().adhdOpenKey,
                x, y, inner, ROW - 2, LuckyCasinoClient::markDirty);
        y += ROW + 6;

        section("Casino", x, y, inner);
        y += 12;
        ButtonWidget open = ButtonWidget.builder(Text.literal(L.t("Casino jetzt öffnen", "Open casino now")),
                b -> client.setScreen(new CasinoMenuScreen(null))).dimensions(x, y, inner, ROW - 2).build();
        open.active = LuckyCasinoClient.enabled;
        addDrawableChild(open);
        y += ROW;
        ButtonWidget settings = ButtonWidget.builder(Text.literal(L.t("Casino-Einstellungen …", "Casino settings …")),
                b -> client.setScreen(new SettingsScreen(this))).dimensions(x, y, inner, ROW - 2).build();
        settings.active = !LuckyCasinoClient.STANDALONE_INSTALLED;
        addDrawableChild(settings);
        y += ROW + 6;

        section(L.t("So geht's", "How it works"), x, y, inner);
        y += 12;
        line(L.t("Nur Spielgeld: Chips sind virtuell und lokal gespeichert", "Play money only: chips are virtual and stored locally"), x, y, Theme.text());
        line(L.t("Auch per Befehl: /casino open, /casino balance", "Also by command: /casino open, /casino balance"), x, y + 11, Theme.textDim());
        line(L.t("Dein Spielstand von LuckyCasino wird übernommen", "Your LuckyCasino progress carries over"), x, y + 22, Theme.textDim());
        y += 3 * 11 + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        if (LuckyCasinoClient.STANDALONE_INSTALLED) status(LuckyCasinoClient.doubleInstalledHint());
        else if (!LuckyCasinoClient.enabled) status(L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
        else status(L.t("Guthaben: ", "Balance: ") + Fmt.chips(LuckyCasinoClient.casino().balance()));
    }

    @Override
    protected void onClose() {
        LuckyCasinoClient.saveNow();
    }
}
