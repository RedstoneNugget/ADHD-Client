package com.adhdclient.headfinder;

import com.adhdclient.headfinder.command.HeadCommands;
import com.adhdclient.headfinder.config.ModConfig;
import com.adhdclient.headfinder.data.HeadStore;
import com.adhdclient.headfinder.detect.BlockESP;
import com.adhdclient.headfinder.hud.HeadHud;
import com.adhdclient.headfinder.render.HeadRenderer;
import com.adhdclient.headfinder.scan.HeadScanner;
import com.adhdclient.headfinder.scan.InteractionTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block-Finder-Modul innerhalb des ADHD Client (ursprünglich "Head Finder" – findet inzwischen
 * beliebige Blöcke anhand konfigurierter Muster, nicht mehr nur Spieler-Köpfe anhand ihrer Textur).
 * Ohne eigene Tasten/Config-Screen-Aufruf – das übernimmt das zentrale ADHD-Client-Menü.
 * {@link #enabled} entscheidet, ob Scan, Rendering und HUD aktiv sind.
 */
public final class HeadFinderClient {

    public static final String MOD_ID = "head_finder";
    public static final Logger LOGGER = LoggerFactory.getLogger("Block Finder");

    /** Haupt-Modulschalter, über das ADHD-Client-Menü umschaltbar. */
    public static boolean enabled = true;

    private static ModConfig config;
    private static long configSaveAt = -1;
    private static final BlockESP MATCHER = new BlockESP();
    private static final HeadStore STORE = new HeadStore();
    private static final HeadScanner SCANNER = new HeadScanner(MATCHER, STORE);

    public static ModConfig config() {
        if (config == null) {
            config = ModConfig.load();
            MATCHER.rebuild(config);
        }
        return config;
    }

    public static HeadStore store() {
        return STORE;
    }

    public static BlockESP matcher() {
        return MATCHER;
    }

    public static HeadScanner scanner() {
        return SCANNER;
    }

    /** Nach jeder Änderung (Menü, Befehl): sofort anwenden, Speichern gebündelt kurz danach. */
    public static void onConfigEdited() {
        config().sanitize();
        MATCHER.rebuild(config);
        SCANNER.requestRescan();
        configSaveAt = System.currentTimeMillis() + 1000;
    }

    /** Config-Datei neu einlesen (z. B. nach Bearbeiten von Hand). */
    public static void reloadConfig() {
        config = ModConfig.load();
        MATCHER.rebuild(config);
        SCANNER.requestRescan();
    }

    /** Config komplett ersetzen (für Tests). */
    public static void replaceConfig(ModConfig c) {
        config = c;
        onConfigEdited();
    }

    public static void saveConfigNow() {
        configSaveAt = -1;
        config().save();
    }

    public void onInitializeClient() {
        config();

        ClientChunkEvents.CHUNK_LOAD.register(SCANNER::onChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register(SCANNER::onChunkUnload);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> STORE.open(serverKey(client)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STORE.close());
        ClientTickEvents.END_CLIENT_TICK.register(HeadFinderClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (configSaveAt >= 0) saveConfigNow();
            STORE.close();
        });

        new InteractionTracker(MATCHER, STORE).register();
        HeadRenderer.register();
        HeadHud.register();
        HeadCommands.register();

        LOGGER.info("Block Finder geladen (ADHD Client Modul)");
    }

    /**
     * Schlüssel für die Speicherdatei: Einzelspieler-Weltname bzw. Serveradresse.
     * Dimensionen werden innerhalb der Datei getrennt.
     */
    public static String serverKey(MinecraftClient mc) {
        if (mc.isIntegratedServerRunning() && mc.getServer() != null) {
            return "sp_" + mc.getServer().getSaveProperties().getLevelName();
        }
        ServerInfo info = mc.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) return "mp_" + info.address;
        return "unknown";
    }

    private static void tick(MinecraftClient mc) {
        if (!enabled) return;
        // Falls JOIN verpasst wurde (z. B. Mod-Reload): Speicher trotzdem öffnen
        if (mc.world != null && !STORE.isOpen()) STORE.open(serverKey(mc));

        SCANNER.tick(mc);
        STORE.tick();
        if (configSaveAt >= 0 && System.currentTimeMillis() >= configSaveAt) saveConfigNow();
    }
}
