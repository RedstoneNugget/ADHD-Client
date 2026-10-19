package com.adhdclient.packettools;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Einstellungen von Packet Tools: Tasten ganz oben, dann Anzeige/Verhalten und eine kurze Erklärung. */
public final class PacketToolsScreen extends ThemedScreen {

    private static final int W = 310;
    private static final int ROW = 20;

    public PacketToolsScreen(Screen parent) {
        super(Text.literal("Packet Tools"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + 2 * ROW + 6 + 12 + 2 * ROW + 6 + 12 + 3 * 11 + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        int half = (inner - 4) / 2;
        PacketToolsConfig c = PacketToolsClient.config();
        PacketToolsConfig defaults = new PacketToolsConfig();

        section(L.t("Tasten", "Keys"), x, y, inner);
        y += 12;
        keyButton(L.t("Alles anhalten", "Hold all"), () -> c.holdKey, v -> c.holdKey = v, defaults.holdKey, x, y, half, ROW - 2, c::save);
        keyButton(L.t("Verwerfen", "Discard"), () -> c.discardKey, v -> c.discardKey = v, defaults.discardKey, x + half + 4, y, inner - half - 4, ROW - 2, c::save);
        y += ROW;
        keyButton(L.t("Menü zurück", "Restore menu"), () -> c.restoreKey, v -> c.restoreKey = v, defaults.restoreKey, x, y, half, ROW - 2, c::save);
        keyButton(L.t("Paket-Log", "Packet log"), () -> c.logKey, v -> c.logKey = v, defaults.logKey, x + half + 4, y, inner - half - 4, ROW - 2, c::save);
        y += ROW + 6;

        section(L.t("Verhalten und Anzeige", "Behavior and display"), x, y, inner);
        y += 12;
        toggle(L.t("Keep-Alive durchlassen", "Let keep-alive through"), () -> c.passKeepAlive, v -> c.passKeepAlive = v, x, y, half, ROW - 2,
                L.t("Beim Anhalten Keep-Alive trotzdem senden – sonst trennt der Server nach etwa 30 Sekunden",
                        "Still send keep-alive while holding – otherwise the server disconnects you after about 30 seconds"), c::save);
        toggle(L.t("Info über der Hotbar", "Info above hotbar"), () -> c.showHoldInfo, v -> c.showHoldInfo = v, x + half + 4, y, inner - half - 4, ROW - 2,
                L.t("Beim Anhalten Sekunden und Paketanzahl zeigen", "Show seconds and packet count while holding"), c::save);
        y += ROW;
        toggle(L.t("Paket-Log", "Packet log"), () -> c.showLog, v -> c.showLog = v, x, y, half, ROW - 2,
                L.t("Oben rechts: welche Pakete gerade gesendet werden", "Top right: which packets are being sent right now"), c::save);
        toggle(L.t("Sync-ID zeigen", "Show sync ID"), () -> c.showSyncInfo, v -> c.showSyncInfo = v, x + half + 4, y, inner - half - 4, ROW - 2,
                L.t("Unter der Menü-Leiste: Nummer und Stand des offenen Menüs", "Below the menu bar: number and state of the open menu"), c::save);
        y += ROW + 6;

        section(L.t("So geht's", "How it works"), x, y, inner);
        y += 12;
        line(L.t("Alles anhalten: nichts geht mehr an den Server, beim", "Hold all: nothing goes to the server, turning it off"), x, y, Theme.text());
        line(L.t("Ausschalten wird alles auf einmal gesendet", "sends everything at once"), x, y + 11, Theme.text());
        line(L.t("Menü-Leiste: erscheint neben Truhen und Server-Menüs", "Menu bar: shows up next to chests and server menus"), x, y + 22, Theme.textDim());
        y += 3 * 11 + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Großes Paket-Log", "Large packet log")), b -> {
            onClose();
            if (client != null) client.setScreen(new PacketLogScreen(this));
        }).dimensions(x, y, half, ROW - 2).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x + half + 4, y, inner - half - 4, ROW - 2).build());
        status(!PacketToolsClient.enabled ? L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu")
                : PacketToolsClient.holdAll ? L.t("Pakete angehalten: ", "Packets on hold: ") + PacketToolsClient.heldCount()
                : L.t("Viele Server erkennen das – Regeln beachten", "Many servers detect this – check the rules"));
    }

    @Override
    protected void onClose() {
        PacketToolsClient.config().save();
    }
}
