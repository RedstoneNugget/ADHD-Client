package com.adhdclient.packettools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Einstellungen von Packet Tools, gespeichert als config/adhd_client/packet_tools.json. */
public final class PacketToolsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Taste: gespeichertes Menü wieder öffnen (GLFW-Code oder Maustaste, -1 = keine). */
    public int restoreKey = org.lwjgl.glfw.GLFW.GLFW_KEY_V;
    /** Taste: alle Pakete anhalten / wieder senden (auch außerhalb von Menüs). */
    public int holdKey = com.adhdclient.gui.HotKey.NONE;
    /** Taste: angehaltene Pakete verwerfen statt senden. */
    public int discardKey = com.adhdclient.gui.HotKey.NONE;
    /** Taste: großes Paket-Log öffnen. */
    public int logKey = com.adhdclient.gui.HotKey.NONE;
    /** Sync-ID und Revision des offenen Menüs anzeigen. */
    public boolean showSyncInfo = true;
    /** Beim Anhalten Keep-Alive und Pong trotzdem senden – sonst trennt der Server nach etwa 30 Sekunden. */
    public boolean passKeepAlive = true;
    /** Beim Anhalten Sekunden und Paketanzahl über der Hotbar zeigen. */
    public boolean showHoldInfo = true;
    /** Oben rechts anzeigen, welche Pakete gerade gesendet werden. */
    public boolean showLog = false;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("packet_tools.json");
    }

    public static PacketToolsConfig load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                PacketToolsConfig c = GSON.fromJson(r, PacketToolsConfig.class);
                if (c != null) return c;
            } catch (IOException | JsonParseException e) {
                PacketToolsClient.LOGGER.warn("packet_tools.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        return new PacketToolsConfig();
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("packet_tools.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            PacketToolsClient.LOGGER.warn("packet_tools.json konnte nicht gespeichert werden", e);
        }
    }
}
