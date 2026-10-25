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
            new Module("Enemy Totems", Category.RENDER,
                    () -> L.t("Zeigt hinter dem Namen, ob ein Spieler ein Totem hält und wie oft es geplatzt ist", "Shows after the name whether a player holds a totem and how often it popped"),
                    () -> com.adhdclient.totemview.TotemViewClient.enabled, v -> com.adhdclient.totemview.TotemViewClient.enabled = v,
                    com.adhdclient.totemview.TotemViewScreen::new, () -> null),
            new Module("Butterfly", Category.RENDER,
                    () -> L.t("Waffen in der Hand werden zum Butterfly-Messer – mit Flips, Inspect und Animationen (nur bei dir)", "Weapons in your hand become a butterfly knife – with flips, inspect and animations (only for you)"),
                    () -> com.adhdclient.butterfly.ButterflyClient.enabled, v -> com.adhdclient.butterfly.ButterflyClient.enabled = v,
                    com.adhdclient.butterfly.ButterflyScreen::new, com.adhdclient.butterfly.ButterflyClient::keyName),
            new Module("Stronghold Finder", Category.WORLD,
                    () -> L.t("Zwei Ender-Augen von verschiedenen Orten werfen – zeigt, wo das End-Portal ist", "Throw two eyes of ender from different spots – shows where the end portal is"),
                    () -> com.adhdclient.stronghold.StrongholdClient.enabled, v -> com.adhdclient.stronghold.StrongholdClient.enabled = v,
                    com.adhdclient.stronghold.StrongholdScreen::new, () -> null),
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
            new Module("Chat Emojis", Category.MISC,
                    () -> L.t(":skull:, :heart: und mehr werden im Chat zu Symbolen", ":skull:, :heart: and more become symbols in chat"),
                    () -> com.adhdclient.emoji.ChatEmojiClient.enabled, v -> com.adhdclient.emoji.ChatEmojiClient.enabled = v,
                    com.adhdclient.emoji.ChatEmojiScreen::new, () -> null),
            new Module("Client Intro", Category.MISC,
                    () -> L.t("Kurze ADHD-Client-Animation beim Start im Hauptmenü", "Short ADHD Client animation on the title screen at start"),
                    () -> com.adhdclient.intro.IntroClient.enabled, v -> com.adhdclient.intro.IntroClient.enabled = v,
                    com.adhdclient.intro.IntroScreen::new, () -> null),
            new Module("Calculator", Category.MISC,
                    () -> L.t("Schnell-Rechner: Rechnungen mit Stacks und Shulkern, Items zählen, Overworld ↔ Nether", "Quick calculator: math with stacks and shulkers, count items, overworld ↔ nether"),
                    () -> com.adhdclient.calc.CalcClient.enabled, v -> com.adhdclient.calc.CalcClient.enabled = v,
                    com.adhdclient.calc.CalcClient::open, com.adhdclient.calc.CalcClient::keyName),
            new Module("Auto Reconnect", Category.MISC,
                    () -> L.t("Verbindet nach einem Rauswurf automatisch neu – mit Countdown und Abbrechen-Knopf", "Reconnects automatically after a disconnect – with countdown and cancel button"),
                    () -> com.adhdclient.reconnect.AutoReconnectClient.enabled, v -> com.adhdclient.reconnect.AutoReconnectClient.enabled = v,
                    com.adhdclient.reconnect.AutoReconnectScreen::new, () -> null),
            new Module("Macros", Category.MISC,
                    () -> L.t("Eine Taste schickt einen Befehl oder eine Chat-Nachricht", "A key sends a command or a chat message"),
                    () -> com.adhdclient.macros.MacrosClient.enabled, v -> com.adhdclient.macros.MacrosClient.enabled = v,
                    com.adhdclient.macros.MacrosScreen::new, () -> null),
            new Module("Auto Totem", Category.MISC,
                    () -> L.t("Fehlt das Totem in der Offhand, kommt eins aus der Hotbar nach", "If the offhand totem is missing, one from the hotbar is moved in"),
                    () -> com.adhdclient.autototem.AutoTotemClient.enabled, v -> com.adhdclient.autototem.AutoTotemClient.enabled = v,
                    com.adhdclient.autototem.AutoTotemScreen::new, com.adhdclient.autototem.AutoTotemClient::keyName),
            new Module("Anti Crystal Death", Category.MISC,
                    () -> L.t("End-Kristall in der Nähe: Inventar öffnen und Totems in Main- und Offhand legen", "End crystal nearby: open the inventory and put totems in main hand and offhand"),
                    () -> com.adhdclient.anticrystal.AntiCrystalClient.enabled, v -> com.adhdclient.anticrystal.AntiCrystalClient.enabled = v,
                    com.adhdclient.anticrystal.AntiCrystalScreen::new, com.adhdclient.anticrystal.AntiCrystalClient::keyName),
            new Module("Lunge Macro", Category.MISC,
                    () -> L.t("Taste: Speer mit Lunge nehmen, zustechen und zurück aufs vorherige Item (auf Servern oft verboten)", "Key: take the lunge spear, jab and switch back to your item (often not allowed on servers)"),
                    () -> com.adhdclient.lunge.LungeMacroClient.enabled, v -> com.adhdclient.lunge.LungeMacroClient.enabled = v,
                    com.adhdclient.lunge.LungeMacroScreen::new, com.adhdclient.lunge.LungeMacroClient::keyName),
            new Module("Aim Lock", Category.MISC,
                    () -> L.t("Taste drücken: der Blick bleibt an der Hitbox des Ziels, bis es zu weit weg ist (auf Servern meist verboten)", "Press the key: your view sticks to the target's hitbox until it is too far away (usually not allowed on servers)"),
                    () -> com.adhdclient.aimlock.AimLockClient.enabled, v -> com.adhdclient.aimlock.AimLockClient.enabled = v,
                    com.adhdclient.aimlock.AimLockScreen::new, com.adhdclient.aimlock.AimLockClient::keyName),
            new Module("Air Jump", Category.MOVEMENT,
                    () -> L.t("In der Luft nochmal springen (auf Servern meist erkannt)", "Jump again in mid-air (usually detected on servers)"),
                    () -> com.adhdclient.airjump.AirJumpClient.enabled, v -> com.adhdclient.airjump.AirJumpClient.enabled = v,
                    com.adhdclient.airjump.AirJumpScreen::new, () -> null),
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
            new Module("Ghost Items", Category.MISC,
                    () -> L.t("Legt Items nur bei dir ins Inventar (Client-seitig) – der Server weiß davon nichts", "Puts items into your inventory only on your side (client-side) – the server knows nothing"),
                    () -> com.adhdclient.ghost.GhostItemsClient.enabled, v -> com.adhdclient.ghost.GhostItemsClient.enabled = v,
                    com.adhdclient.ghost.GhostItemsScreen::new, () -> null),
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
