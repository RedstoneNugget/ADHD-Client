package com.adhdclient.basefinder;

import com.adhdclient.basefinder.config.ModConfig;
import com.adhdclient.basefinder.render.MarkerRenderer;
import com.adhdclient.basefinder.scan.Patterns;
import com.adhdclient.basefinder.scan.ScanManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.minecraft.client.MinecraftClient;
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

    private static ModConfig config;
    private static long saveAt = -1;

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

        LOGGER.info("{} geladen (ADHD Client Modul)", MOD_NAME);
    }

    private static void tick(MinecraftClient mc) {
        if (!enabled) return;
        ScanManager.INSTANCE.tick(mc);
        if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
    }
}
