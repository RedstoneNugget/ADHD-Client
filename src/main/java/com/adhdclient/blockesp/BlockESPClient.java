package com.adhdclient.blockesp;

import com.adhdclient.blockesp.render.BlockESPRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block ESP: markiert alle Blöcke, die im Block-Menü ausgewählt sind, durch Wände.
 * {@link #enabled} wird im ADHD-Client-Menü umgeschaltet.
 */
public final class BlockESPClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("Block ESP");

    public static boolean enabled = false;

    private static BlockESPConfig config;
    private static long saveAt = -1;
    private static final BlockESPScanner SCANNER = new BlockESPScanner();

    public static BlockESPConfig config() {
        if (config == null) config = BlockESPConfig.load();
        return config;
    }

    public static BlockESPScanner scanner() {
        return SCANNER;
    }

    /** Nach jeder Änderung im Menü: sofort anwenden, Speichern gebündelt kurz danach. */
    public static void onConfigEdited(boolean selectionChanged) {
        config().sanitize();
        if (selectionChanged) SCANNER.setSelection(config.selectedBlocks);
        saveAt = System.currentTimeMillis() + 1000;
    }

    public static void saveNow() {
        saveAt = -1;
        config().save();
    }

    public void onInitializeClient() {
        SCANNER.setSelection(config().selectedBlocks);

        ClientChunkEvents.CHUNK_LOAD.register(SCANNER::onChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register(SCANNER::onChunkUnload);
        ClientTickEvents.END_CLIENT_TICK.register(BlockESPClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (saveAt >= 0) saveNow();
        });
        BlockESPRenderer.register();

        LOGGER.info("Block ESP geladen (ADHD Client Modul)");
    }

    private static void tick(MinecraftClient mc) {
        if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
        if (!enabled) return;
        SCANNER.tick(mc);
    }
}
