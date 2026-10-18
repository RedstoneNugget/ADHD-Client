package com.adhdclient.core;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.blockesp.BlockESPClient;
import com.adhdclient.esp.EspClient;
import com.adhdclient.freecam.FreeCamClient;
import com.adhdclient.freelook.FreeLookClient;
import com.adhdclient.fullbright.FullBrightClient;
import com.adhdclient.gui.L;
import com.adhdclient.rtpq.RtpqInvseeClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Alle Module des ADHD Client mit Kategorie – Grundlage für das ClickGUI. */
public final class Modules {

    public enum Category {
        RENDER("Render", "Render"), WORLD("Welt", "World"), CAMERA("Kamera", "Camera"), MISC("Sonstiges", "Misc");

        private final String de;
        private final String en;

        Category(String de, String en) {
            this.de = de;
            this.en = en;
        }

        public String title() {
            return L.t(de, en);
        }
    }

    /**
     * Ein Modul. {@code key} liefert den Namen der Modul-Taste oder null, wenn es keine hat.
     */
    public record Module(String name, Category category, Supplier<String> description, BooleanSupplier enabled,
                         Consumer<Boolean> set, Function<Screen, Screen> settings, Supplier<String> key) {

        public void toggle() {
            set.accept(!enabled.getAsBoolean());
            AdhdModuleState.save();
        }
    }

    public static final List<Module> ALL = List.of(
            new Module("Block ESP", Category.RENDER,
                    () -> L.t("Markiert ausgewählte Blöcke durch Wände", "Highlights selected blocks through walls"),
                    () -> BlockESPClient.enabled, v -> BlockESPClient.enabled = v,
                    com.adhdclient.blockesp.gui.BlockPickerScreen::new, () -> null),
            new Module("ESP", Category.RENDER,
                    () -> L.t("Zeigt ausgewählte Entities (z. B. Spieler) durch Wände", "Shows selected entities (e.g. players) through walls"),
                    () -> EspClient.enabled, v -> EspClient.enabled = v,
                    com.adhdclient.esp.EspScreen::new, () -> null),
            new Module("Full Bright", Category.RENDER,
                    () -> L.t("Alles hell wie mit Nachtsicht – keine Taste nötig", "Everything bright like night vision – no key needed"),
                    () -> FullBrightClient.enabled, v -> FullBrightClient.enabled = v,
                    com.adhdclient.fullbright.FullBrightScreen::new, () -> null),
            new Module("Base Finder", Category.WORLD,
                    () -> L.t("Markiert auffällige Chunks, Spawner, Bienennester, Amethyst und Kelp", "Marks suspicious chunks, spawners, bee nests, amethyst and kelp"),
                    () -> BaseFinderClient.enabled, v -> BaseFinderClient.enabled = v,
                    com.adhdclient.basefinder.gui.ConfigScreen::new, () -> null),
            new Module("Free Cam", Category.CAMERA,
                    () -> L.t("Kamera vom Körper lösen und frei herumfliegen", "Detach the camera and fly around freely"),
                    () -> FreeCamClient.enabled, v -> {
                        FreeCamClient.set(v);
                        if (v) MinecraftClient.getInstance().setScreen(null);
                    },
                    com.adhdclient.freecam.FreeCamScreen::new, FreeCamClient::keyName),
            new Module("Free Look", Category.CAMERA,
                    () -> L.t("In der dritten Person umschauen, ohne dass sich der Spieler dreht", "Look around in third person without turning your player"),
                    () -> FreeLookClient.enabled, v -> FreeLookClient.enabled = v,
                    com.adhdclient.freelook.FreeLookScreen::new, FreeLookClient::keyName),
            new Module("RTPQ Invsee", Category.MISC,
                    () -> L.t("Öffnet beim RTPQ-Gegner automatisch /invsee und warnt vor Elytra, Mace und Crystals", "Opens /invsee on your RTPQ opponent and warns about elytra, mace and crystals"),
                    () -> RtpqInvseeClient.enabled, v -> RtpqInvseeClient.enabled = v,
                    com.adhdclient.rtpq.RtpqScreen::new, () -> null)
    );

    private Modules() {
    }

    public static List<Module> in(Category c) {
        return ALL.stream().filter(m -> m.category() == c).toList();
    }

    public static void allOff() {
        for (Module m : ALL) {
            if (m.enabled().getAsBoolean()) m.set().accept(false);
        }
        AdhdModuleState.save();
    }
}
