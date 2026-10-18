package com.adhdclient.basefinder;

import com.adhdclient.basefinder.config.ModConfig;
import com.adhdclient.basefinder.render.MarkerRenderer;
import com.adhdclient.basefinder.scan.Patterns;
import com.adhdclient.basefinder.scan.ScanManager;
import com.adhdclient.gui.HotKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base-Finder-Modul innerhalb des ADHD Client: identische Scan-/Render-Logik wie die eigenständige
 * "Base Finder Donut SMP"-Mod, aber ohne eigene Tasten/Config-Screen-Aufruf – das übernimmt das
 * zentrale ADHD-Client-Menü. {@link #enabled} entscheidet, ob Scan und Markierungen aktiv sind.
 */
public final class BaseFinderClient {

    public static final String MOD_ID = "base_finder";
    public static final String MOD_NAME = "Base Finder";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    /** Haupt-Modulschalter, über das ADHD-Client-Menü umschaltbar. */
    public static boolean enabled = false;

    /**
     * Die eigenständige Base-Finder-Mod ist zusätzlich installiert. Dann bleibt dieses Modul aus, sonst wird
     * alles doppelt gescannt und gezeichnet.
     */
    public static final boolean STANDALONE_INSTALLED = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("base_finder");

    /** Hinweis, dass Base Finder doppelt installiert ist (für Chat und Menü). */
    public static String doubleInstalledHint() {
        return com.adhdclient.gui.L.t(
                "Base Finder ist doppelt installiert (eigene Mod + ADHD Client). Damit nichts doppelt angezeigt wird, bleibt das ADHD-Modul aus. Lösche die Base-Finder-Jar aus dem mods-Ordner, dann geht das ADHD-Modul.",
                "Base Finder is installed twice (own mod + ADHD Client). So nothing shows up twice, the ADHD module stays off. Delete the Base Finder jar from your mods folder to use the ADHD module.");
    }

    private static ModConfig config;
    private static long saveAt = -1;
    private static boolean hintShown;
    private static final HotKey MARKERS_KEY = new HotKey();
    private static final HotKey RESCAN_KEY = new HotKey();

    public static ModConfig config() {
        if (config == null) config = ModConfig.load();
        return config;
    }

    /** Nach jeder Änderung im Menü: sofort anwenden, Speichern gebündelt kurz danach. */
    public static void onConfigEdited() {
        config().sanitize();
        ScanManager.INSTANCE.configChanged();
        saveAt = System.currentTimeMillis() + 1000;
    }

    /** Config komplett ersetzen (z. B. für Tests mit Standardwerten). */
    public static void replaceConfig(ModConfig c) {
        config = c;
        onConfigEdited();
    }

    public static void saveNow() {
        saveAt = -1;
        config().save();
    }

    public void onInitializeClient() {
        config();

        ClientChunkEvents.CHUNK_LOAD.register(ScanManager.INSTANCE::onChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register(ScanManager.INSTANCE::onChunkUnload);
        // Block-Tags kommen vom Server; danach Muster mit "#tag" neu auflösen.
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client) {
                Patterns.clearCache();
                ScanManager.INSTANCE.configChanged();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(BaseFinderClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (saveAt >= 0) saveNow();
        });
        MarkerRenderer.register();
        if (STANDALONE_INSTALLED) {
            LOGGER.warn("Eigenständige Base-Finder-Mod gefunden – ADHD-Modul bleibt aus, damit nichts doppelt ist");
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
                if (hintShown || client.player == null) return;
                hintShown = true;
                client.player.sendMessage(net.minecraft.text.Text.literal("[ADHD Client] " + doubleInstalledHint()).formatted(net.minecraft.util.Formatting.YELLOW), false);
            }));
        }

        LOGGER.info("{} geladen (ADHD Client Modul)", MOD_NAME);
    }

    /** Nur Tasten geändert: speichern, aber nichts neu scannen. */
    public static void onKeysEdited() {
        saveAt = System.currentTimeMillis() + 1000;
    }

    /** Markierungen ein-/ausblenden (Anzeigen-Taste). */
    public static void toggleMarkers() {
        ModConfig.General g = config().general;
        g.markersVisible = !g.markersVisible;
        saveAt = System.currentTimeMillis() + 1000;
        ScanManager.INSTANCE.notifier.showNow(Text.literal(Lang.t("Markierungen: ", "Markers: ") + (g.markersVisible ? Lang.t("sichtbar", "visible") : Lang.t("ausgeblendet", "hidden")))
                .formatted(g.markersVisible ? Formatting.GREEN : Formatting.GRAY));
    }

    /** Alle Chunks neu scannen (Scannen-Taste und Knopf im Menü). */
    public static void rescan() {
        if (MinecraftClient.getInstance().world == null) return;
        int n = ScanManager.INSTANCE.forceFullRescan();
        ScanManager.INSTANCE.notifier.showNow(Text.literal(Lang.t("Vollscan: ", "Full scan: ") + n + Lang.t(" Chunks werden neu gescannt …", " chunks are being rescanned …")).formatted(Formatting.AQUA));
    }

    private static void tick(MinecraftClient mc) {
        ModConfig.General g = config().general;
        boolean markersPressed = MARKERS_KEY.pressed(g.markersKey);
        boolean rescanPressed = RESCAN_KEY.pressed(g.rescanKey);
        if (!enabled) return;
        if (markersPressed) toggleMarkers();
        if (rescanPressed) rescan();
        ScanManager.INSTANCE.tick(mc);
        if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
    }
}
