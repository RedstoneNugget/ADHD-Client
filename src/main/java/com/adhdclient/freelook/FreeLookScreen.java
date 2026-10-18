package com.adhdclient.freelook;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Einstellungen von Free Look: Abstand, durch Blöcke, Halten/Umschalten, F5 und Mausrad. */
public final class FreeLookScreen extends ThemedScreen {

    private static final int W = 290;
    private static final int ROW = 20;

    public FreeLookScreen(Screen parent) {
        super(Text.literal("Free Look"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 11 + 6 + 12 + ROW + 6 + 12 + 2 * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        int half = (inner - 4) / 2;
        FreeLookConfig c = FreeLookClient.config();

        section(L.t("Taste", "Key"), x, y, inner);
        y += 12;
        keyButton(L.t("Umschauen", "Look around"), () -> c.key, v -> c.key = v, x, y, inner, ROW - 2, () -> {
            FreeLookClient.onConfigEdited();
            clearAndInit();
        });
        y += ROW;
        line(FreeLookClient.keyName() + (c.hold ? L.t(" halten", " hold") : L.t(" drücken", " press")) + L.t(" – umschauen", " – look around"), x, y + 1, Theme.textDim());
        y += 11 + 6;

        section(L.t("Kamera", "Camera"), x, y, inner);
        y += 12;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Abstand", "Distance"), 1, 32, 0.5, c.distance,
                v -> String.format(Locale.ROOT, L.t("%.1f Blöcke", "%.1f blocks"), v), v -> {
            c.distance = v;
            FreeLookClient.onConfigEdited();
        }));
        y += ROW + 6;

        section(L.t("Verhalten", "Behavior"), x, y, inner);
        y += 12;
        toggle(L.t("Durch Blöcke", "Through blocks"), () -> c.throughBlocks, v -> c.throughBlocks = v, x, y, half, ROW - 2,
                L.t("An: Kamera geht durch Wände. Aus: Kamera stoppt vor Blöcken wie in Minecraft.", "On: camera goes through walls. Off: camera stops at blocks like in Minecraft."), FreeLookClient::onConfigEdited);
        toggle(L.t("Taste halten", "Hold key"), () -> c.hold, v -> c.hold = v, x + half + 4, y, inner - half - 4, ROW - 2,
                L.t("An: Umschauen, solange die Taste gedrückt ist. Aus: einmal drücken an, nochmal drücken aus.", "On: look around while the key is held. Off: press once for on, again for off."), FreeLookClient::onConfigEdited);
        y += ROW;
        toggle(L.t("Auch für F5", "Also for F5"), () -> c.applyToF5, v -> c.applyToF5 = v, x, y, half, ROW - 2,
                L.t("Abstand und Durch-Blöcke gelten auch in der normalen F5-Ansicht", "Distance and through-blocks also apply to the normal F5 view"), FreeLookClient::onConfigEdited);
        toggle(L.t("Mausrad = Abstand", "Scroll = distance"), () -> c.scrollZoom, v -> c.scrollZoom = v, x + half + 4, y, inner - half - 4, ROW - 2,
                L.t("Beim Umschauen ändert das Mausrad den Abstand statt den Hotbar-Slot", "While looking around, the scroll wheel changes the distance instead of the hotbar slot"), FreeLookClient::onConfigEdited);
        y += ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(FreeLookClient.enabled ? L.t("Modul ist an", "Module is on") : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        FreeLookClient.saveNow();
    }
}
