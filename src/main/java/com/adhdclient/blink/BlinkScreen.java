package com.adhdclient.blink;

import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Einstellungen von Blink: Tasten, Kopie, automatisch senden, Anzeige. */
public final class BlinkScreen extends ThemedScreen {

    private static final int W = 290;
    private static final int ROW = 20;

    public BlinkScreen(Screen parent) {
        super(Text.literal("Blink"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + 2 * ROW + 6 + 12 + 2 * ROW + 6 + 12 + 3 * 11 + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        int half = (inner - 4) / 2;
        BlinkConfig c = BlinkClient.config();

        section(L.t("Tasten", "Keys"), x, y, inner);
        y += 12;
        keyButton(L.t("An/Aus", "On/Off"), () -> c.key, v -> c.key = v, new BlinkConfig().key, x, y, inner, ROW - 2, BlinkClient::onConfigEdited);
        y += ROW;
        keyButton(L.t("Abbrechen", "Cancel"), () -> c.cancelKey, v -> c.cancelKey = v, new BlinkConfig().cancelKey, x, y, inner, ROW - 2, BlinkClient::onConfigEdited);
        y += ROW + 6;

        section(L.t("Verhalten", "Behavior"), x, y, inner);
        y += 12;
        toggle(L.t("Kopie anzeigen", "Show copy"), () -> c.fakePlayer, v -> c.fakePlayer = v, x, y, half, ROW - 2,
                L.t("Zeigt eine Kopie von dir dort, wo der Server dich gerade sieht", "Shows a copy of you where the server currently sees you"), BlinkClient::onConfigEdited);
        toggle(L.t("Info-Anzeige", "Info display"), () -> c.showInfo, v -> c.showInfo = v, x + half + 4, y, inner - half - 4, ROW - 2,
                L.t("Sekunden und Paketanzahl über der Hotbar", "Seconds and packet count above the hotbar"), BlinkClient::onConfigEdited);
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Automatisch senden", "Auto send"), 0, 30, 1, c.autoSendSeconds,
                v -> v < 0.5 ? L.t("nie", "never") : L.t("nach ", "after ") + (int) Math.round(v) + " s", v -> {
            c.autoSendSeconds = (int) Math.round(v);
            BlinkClient.onConfigEdited();
        }));
        y += ROW + 6;

        section(L.t("So geht's", "How it works"), x, y, inner);
        y += 12;
        line(L.t("An: der Server sieht dich nicht mehr laufen", "On: the server stops seeing you move"), x, y, Theme.text());
        line(L.t("Aus: alles wird gesendet, du springst zur neuen Stelle", "Off: everything is sent, you jump to the new spot"), x, y + 11, Theme.textDim());
        line(L.t("Abbrechen: du landest wieder am Start", "Cancel: you end up back at the start"), x, y + 22, Theme.textDim());
        y += 3 * 11 + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close())
                .dimensions(x, y, inner, ROW - 2).build());

        String warn = L.t("Achtung: Anticheats erkennen Blink oft", "Careful: anticheats often detect Blink");
        status(!BlinkClient.enabled ? L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu")
                : BlinkClient.active ? L.t("Blink ist an – ", "Blink is on – ") + BlinkClient.heldCount() + L.t(" Pakete", " packets")
                : (c.key == HotKey.NONE ? warn : L.t("Starten mit ", "Start with ") + BlinkClient.keyName() + L.t(" oder im Menü", " or in the menu")));
    }

    @Override
    protected void onClose() {
        BlinkClient.saveNow();
    }
}
