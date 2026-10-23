package com.adhdclient.core;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.blink.BlinkClient;
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
        RENDER("Render", "Render"), WORLD("Welt", "World"), CAMERA("Kamera", "Camera"), MOVEMENT("Bewegung", "Movement"), MISC("Sonstiges", "Misc");

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
                    () -> L.t("Alles hell wie am Tag, auch nachts und in Höhlen – keine Taste nötig", "Everything bright like daytime, even at night and in caves – no key needed"),
                    () -> FullBrightClient.enabled, v -> FullBrightClient.enabled = v,
                    com.adhdclient.fullbright.FullBrightScreen::new, () -> null),
            new Module("Butterfly", Category.RENDER,
                    () -> L.t("Waffen in der Hand werden zum Butterfly-Messer – mit Flips, Inspect und Animationen (nur bei dir)", "Weapons in your hand become a butterfly knife – with flips, inspect and animations (only for you)"),
                    () -> com.adhdclient.butterfly.ButterflyClient.enabled, v -> com.adhdclient.butterfly.ButterflyClient.enabled = v,
                    com.adhdclient.butterfly.ButterflyScreen::new, com.adhdclient.butterfly.ButterflyClient::keyName),
            new Module("Base Finder", Category.WORLD,
                    () -> BaseFinderClient.STANDALONE_INSTALLED ? BaseFinderClient.doubleInstalledHint()
                            : L.t("Markiert auffällige Chunks, Spawner, Bienennester, Amethyst und Kelp", "Marks suspicious chunks, spawners, bee nests, amethyst and kelp"),
                    () -> BaseFinderClient.enabled, v -> {
                        // Mit der eigenständigen Mod zusätzlich bliebe alles doppelt – dann nur den Hinweis zeigen
                        if (v && BaseFinderClient.STANDALONE_INSTALLED) {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal("[ADHD Client] " + BaseFinderClient.doubleInstalledHint())
                                    .formatted(net.minecraft.util.Formatting.YELLOW), false);
                            return;
                        }
                        BaseFinderClient.enabled = v;
                    },
                    com.adhdclient.basefinder.gui.ConfigScreen::new, () -> null),
            new Module("Free Cam", Category.CAMERA,
                    () -> L.t("Kamera vom Körper lösen und frei herumfliegen", "Detach the camera and fly around freely"),
                    () -> FreeCamClient.enabled, v -> {
                        FreeCamClient.enabled = v;
                        if (!v) FreeCamClient.set(false);
                    },
                    com.adhdclient.freecam.FreeCamScreen::new, FreeCamClient::keyName),
            new Module("Free Look", Category.CAMERA,
                    () -> L.t("In der dritten Person umschauen, ohne dass sich der Spieler dreht", "Look around in third person without turning your player"),
                    () -> FreeLookClient.enabled, v -> FreeLookClient.enabled = v,
                    com.adhdclient.freelook.FreeLookScreen::new, FreeLookClient::keyName),
            new Module("Blink", Category.MOVEMENT,
                    () -> L.t("Hält deine Bewegung zurück und sendet sie beim Ausschalten auf einmal", "Holds back your movement and sends it all at once when turned off"),
                    () -> BlinkClient.enabled, v -> {
                        BlinkClient.enabled = v;
                        if (!v) BlinkClient.set(false);
                    },
                    com.adhdclient.blink.BlinkScreen::new, BlinkClient::keyName),
            new Module("RTPQ Invsee", Category.MISC,
                    () -> L.t("Öffnet beim RTPQ-Gegner automatisch /invsee und warnt vor Elytra, Mace und Crystals", "Opens /invsee on your RTPQ opponent and warns about elytra, mace and crystals"),
                    () -> RtpqInvseeClient.enabled, v -> RtpqInvseeClient.enabled = v,
                    com.adhdclient.rtpq.RtpqScreen::new, () -> null),
            new Module("Inspect All", Category.MISC,
                    () -> L.t("Zeigt alle Daten zu Block, Item oder Entity: Blockstate, Komponenten, NBT, Markierungen", "Shows all data of a block, item or entity: block state, components, NBT, markers"),
                    () -> com.adhdclient.inspect.InspectClient.enabled, v -> com.adhdclient.inspect.InspectClient.enabled = v,
                    com.adhdclient.inspect.config.InspectSettingsScreen::new,
                    () -> com.adhdclient.gui.HotKey.name(com.adhdclient.inspect.config.InspectConfig.get().openKey)),
            new Module("Packet Tools", Category.MISC,
                    () -> L.t("Werkzeuge für Menüs: ohne Paket schließen, Desync, Klicks verzögern, Menü speichern, Pakete bauen", "Tools for menus: close without packet, desync, delay clicks, save menus, build packets"),
                    () -> com.adhdclient.packettools.PacketToolsClient.enabled, v -> com.adhdclient.packettools.PacketToolsClient.enabled = v,
                    com.adhdclient.packettools.PacketToolsScreen::new, com.adhdclient.packettools.PacketToolsClient::keyName),
            new Module("Lucky Casino", Category.MISC,
                    () -> com.adhdclient.casino.client.LuckyCasinoClient.STANDALONE_INSTALLED ? com.adhdclient.casino.client.LuckyCasinoClient.doubleInstalledHint()
                            : L.t("Casino mit Spielgeld: Slots, Roulette, Blackjack, Crash, Plinko, Minen und mehr", "Casino with play money: slots, roulette, blackjack, crash, Plinko, mines and more"),
                    () -> com.adhdclient.casino.client.LuckyCasinoClient.enabled, v -> {
                        if (v && com.adhdclient.casino.client.LuckyCasinoClient.STANDALONE_INSTALLED) return;
                        com.adhdclient.casino.client.LuckyCasinoClient.enabled = v;
                    },
                    com.adhdclient.casino.CasinoModuleScreen::new,
                    () -> com.adhdclient.gui.HotKey.name(com.adhdclient.casino.client.LuckyCasinoClient.config().adhdOpenKey))
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
