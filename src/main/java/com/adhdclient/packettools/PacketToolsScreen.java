package com.adhdclient.packettools;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Einstellungen von Packet Tools: Wiederherstellen-Taste ganz oben, Anzeige der Sync-ID, kurze Erklärung. */
public final class PacketToolsScreen extends ThemedScreen {

    private static final int W = 300;
    private static final int ROW = 20;

    public PacketToolsScreen(Screen parent) {
        super(Text.literal("Packet Tools"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + 12 + ROW + 6 + 12 + 5 * 11 + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        PacketToolsConfig c = PacketToolsClient.config();

        section(L.t("Taste", "Key"), x, y, inner);
        y += 12;
        keyButton(L.t("Menü wiederherstellen", "Restore menu"), () -> c.restoreKey, v -> c.restoreKey = v, x, y, inner, ROW - 2, c::save);
        y += ROW + 6;

        section(L.t("Anzeige", "Display"), x, y, inner);
        y += 12;
        toggle(L.t("Sync-ID und Revision zeigen", "Show sync ID and revision"), () -> c.showSyncInfo, v -> c.showSyncInfo = v, x, y, inner, ROW - 2,
                L.t("Unter der Leiste: Nummer und Stand des offenen Menüs", "Below the bar: number and state of the open menu"), c::save);
        y += ROW + 6;

        section(L.t("So geht's", "How it works"), x, y, inner);
        y += 12;
        line(L.t("Öffne eine Truhe oder ein Server-Menü –", "Open a chest or a server menu –"), x, y, Theme.text());
        line(L.t("links erscheint die Packet-Tools-Leiste.", "the Packet Tools bar shows up on the left."), x, y + 11, Theme.text());
        line(L.t("Verzögern: Klicks sammeln, beim Ausschalten senden", "Delay: collect clicks, send them when turned off"), x, y + 22, Theme.textDim());
        line(L.t("Menü speichern, dann mit der Taste wieder öffnen", "Save a menu, then reopen it with the key"), x, y + 33, Theme.textDim());
        line(L.t("Viele Server erkennen das – Regeln beachten", "Many servers detect this – check the rules"), x, y + 44, Theme.textDim());
        y += 5 * 11 + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(PacketToolsClient.enabled ? L.t("Modul ist an", "Module is on") : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        PacketToolsClient.config().save();
    }
}
