package com.adhdclient.freecam;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/** Einstellungen von Free Cam: Taste, Geschwindigkeit, Sprint-Faktor, sanfte Bewegung. */
public final class FreeCamScreen extends ThemedScreen {

    private static final int W = 290;
    private static final int ROW = 20;

    public FreeCamScreen(Screen parent) {
        super(Text.literal("Free Cam"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + 12 + 3 * ROW + 6 + 12 + 3 * 11 + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        int half = (inner - 4) / 2;
        FreeCamConfig c = FreeCamClient.config();

        section(L.t("Taste", "Key"), x, y, inner);
        y += 12;
        keyButton(L.t("An/Aus", "On/Off"), () -> c.key, v -> c.key = v, x, y, inner, ROW - 2, FreeCamClient::onConfigEdited);
        y += ROW + 6;

        section(L.t("Flug", "Flight"), x, y, inner);
        y += 12;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Geschwindigkeit", "Speed"), 1, 60, 1, c.speed,
                v -> (int) Math.round(v) + L.t(" Blöcke/s", " blocks/s"), v -> {
            c.speed = Math.round(v);
            FreeCamClient.onConfigEdited();
        }));
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Sprint-Faktor", "Sprint factor"), 1, 10, 0.5, c.sprintFactor,
                v -> String.format(Locale.ROOT, "×%.1f", v), v -> {
            c.sprintFactor = v;
            FreeCamClient.onConfigEdited();
        }));
        y += ROW;
        toggle(L.t("Sanft bewegen", "Smooth movement"), () -> c.smooth, v -> c.smooth = v, x, y, inner, ROW - 2,
                L.t("Weich anfahren und abbremsen statt ruckartig", "Accelerate and brake smoothly instead of abruptly"), FreeCamClient::onConfigEdited);
        y += ROW + 6;

        section(L.t("Steuerung", "Controls"), x, y, inner);
        y += 12;
        line(L.t("Laufen-Tasten – fliegen, Springen/Schleichen – hoch/runter", "Movement keys – fly, Jump/Sneak – up/down"), x, y, Theme.text());
        line(L.t("Sprinten – schneller fliegen", "Sprint – fly faster"), x, y + 11, Theme.textDim());
        line(L.t("Dein Körper bleibt stehen, Klicks sind gesperrt", "Your body stays in place, clicks are blocked"), x, y + 22, Theme.textDim());
        y += 3 * 11 + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(FreeCamClient.enabled ? L.t("Free Cam beenden", "Stop Free Cam") : L.t("Free Cam starten", "Start Free Cam")), b -> {
            boolean start = !FreeCamClient.enabled;
            onClose();
            if (client != null) client.setScreen(null);
            FreeCamClient.set(start);
        }).dimensions(x, y, half, ROW - 2).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close())
                .dimensions(x + half + 4, y, inner - half - 4, ROW - 2).build());

        status(FreeCamClient.enabled ? L.t("Free Cam ist gerade an", "Free Cam is on") : L.t("Starten mit ", "Start with ") + FreeCamClient.keyName() + L.t(" oder dem Knopf unten", " or the button below"));
    }

    @Override
    protected void onClose() {
        FreeCamClient.saveNow();
    }
}
