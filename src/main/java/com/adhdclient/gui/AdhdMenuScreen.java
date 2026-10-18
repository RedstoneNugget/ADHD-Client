package com.adhdclient.gui;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.blockesp.BlockESPClient;
import com.adhdclient.core.AdhdModuleState;
import com.adhdclient.esp.EspClient;
import com.adhdclient.freecam.FreeCamClient;
import com.adhdclient.freelook.FreeLookClient;
import com.adhdclient.fullbright.FullBrightClient;
import com.adhdclient.rtpq.RtpqInvseeClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Hauptmenü des ADHD Client (Taste: Rechte Strg). Klick auf ein Modul schaltet es an/aus
 * (Leiste links grün = an), "Menü" daneben öffnet seine Einstellungen.
 */
public final class AdhdMenuScreen extends ThemedScreen {

    private static final int W = 230;
    private static final int ROW = 18;
    private static final int MENU_W = 46;
    private static final int MODULES = 7;

    public AdhdMenuScreen(Screen parent) {
        super(Text.literal("ADHD Client"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + MODULES * ROW + 6 + 12 + ROW + 6 + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;

        section(L.t("Module", "Modules"), x, y, inner);
        y += 12;
        y = row(x, y, inner, "Base Finder", () -> BaseFinderClient.enabled, v -> BaseFinderClient.enabled = v,
                () -> new com.adhdclient.basefinder.gui.ConfigScreen(this), L.t("Markiert auffällige Chunks, Spawner, Bienennester, Amethyst und Kelp", "Marks suspicious chunks, spawners, bee nests, amethyst and kelp"));
        y = row(x, y, inner, "Block ESP", () -> BlockESPClient.enabled, v -> BlockESPClient.enabled = v,
                () -> new com.adhdclient.blockesp.gui.BlockPickerScreen(this), L.t("Markiert ausgewählte Blöcke durch Wände", "Highlights selected blocks through walls"));
        y = row(x, y, inner, "ESP", () -> EspClient.enabled, v -> EspClient.enabled = v,
                () -> new com.adhdclient.esp.EspScreen(this), L.t("Zeigt ausgewählte Entities (z. B. Spieler) durch Wände", "Shows selected entities (e.g. players) through walls"));
        y = row(x, y, inner, "RTPQ Invsee", () -> RtpqInvseeClient.enabled, v -> RtpqInvseeClient.enabled = v,
                () -> new com.adhdclient.rtpq.RtpqScreen(this), L.t("Öffnet beim RTPQ-Gegner automatisch /invsee und warnt vor Elytra, Mace und Crystals", "Opens /invsee on your RTPQ opponent and warns about elytra, mace and crystals"));
        y = row(x, y, inner, "Free Cam", () -> FreeCamClient.enabled, v -> {
            FreeCamClient.set(v);
            if (v && client != null) client.setScreen(null);
        }, () -> new com.adhdclient.freecam.FreeCamScreen(this), L.t("Kamera vom Körper lösen und frei herumfliegen (Taste im Free-Cam-Menü)", "Detach the camera and fly around freely (key in the Free Cam menu)"));
        y = row(x, y, inner, "Free Look", () -> FreeLookClient.enabled, v -> FreeLookClient.enabled = v,
                () -> new com.adhdclient.freelook.FreeLookScreen(this), L.t("In der dritten Person umschauen, ohne dass sich der Spieler dreht (Taste im Free-Look-Menü)", "Look around in third person without turning your player (key in the Free Look menu)"));
        y = row(x, y, inner, "Full Bright", () -> FullBrightClient.enabled, v -> FullBrightClient.enabled = v,
                () -> new com.adhdclient.fullbright.FullBrightScreen(this), L.t("Alles hell wie mit Nachtsicht – keine Taste nötig", "Everything bright like night vision – no key needed"));
        y += 6;

        section("Client", x, y, inner);
        y += 12;
        int third = (inner - 8) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Design"), b -> client.setScreen(new ThemeScreen(this)))
                .dimensions(x, y, third, ROW - 2).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Alle aus", "All off")), b -> {
            BaseFinderClient.enabled = false;
            BlockESPClient.enabled = false;
            EspClient.enabled = false;
            RtpqInvseeClient.enabled = false;
            FreeCamClient.set(false);
            FreeLookClient.enabled = false;
            FullBrightClient.enabled = false;
            AdhdModuleState.save();
            updateStatus();
        }).dimensions(x + third + 4, y, third, ROW - 2).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Schließen", "Close")), b -> close())
                .dimensions(x + 2 * (third + 4), y, inner - 2 * (third + 4), ROW - 2).build());

        updateStatus();
    }

    private int row(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set,
                    Supplier<Screen> settings, String tooltip) {
        toggle(name, get, set, x, y, w - MENU_W - 4, ROW - 2, tooltip, () -> {
            AdhdModuleState.save();
            updateStatus();
        });
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Menü", "Menu")), b -> client.setScreen(settings.get()))
                .dimensions(x + w - MENU_W, y, MENU_W, ROW - 2).build());
        return y + ROW;
    }

    private void updateStatus() {
        int n = 0;
        if (BaseFinderClient.enabled) n++;
        if (BlockESPClient.enabled) n++;
        if (EspClient.enabled) n++;
        if (RtpqInvseeClient.enabled) n++;
        if (FreeCamClient.enabled) n++;
        if (FreeLookClient.enabled) n++;
        if (FullBrightClient.enabled) n++;
        status(n + "/" + MODULES + L.t(" Module an", " modules on"));
    }
}
