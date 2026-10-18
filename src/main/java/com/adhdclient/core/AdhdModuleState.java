package com.adhdclient.core;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.blockesp.BlockESPClient;
import com.adhdclient.esp.EspClient;
import com.adhdclient.freelook.FreeLookClient;
import com.adhdclient.fullbright.FullBrightClient;
import com.adhdclient.rtpq.RtpqInvseeClient;
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

/** Welche Module (Base Finder, Block ESP, ESP, RTPQ Invsee, …) an sind – config/adhd_client/modules.json. */
public final class AdhdModuleState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Data {
        boolean baseFinderEnabled = false;
        boolean blockEspEnabled = false;
        boolean espEnabled = false;
        boolean rtpqEnabled = false;
        boolean freeLookEnabled = false;
        boolean fullBrightEnabled = false;
        boolean freeCamEnabled = false;
        boolean blinkEnabled = false;
        boolean inspectEnabled = false;
        boolean packetToolsEnabled = false;
    }

    private AdhdModuleState() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("modules.json");
    }

    public static void load() {
        Path f = file();
        Data d = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                d = GSON.fromJson(r, Data.class);
            } catch (IOException | JsonParseException ignored) {
                // Standardwerte reichen
            }
        }
        if (d == null) d = new Data();
        BaseFinderClient.enabled = d.baseFinderEnabled && !BaseFinderClient.STANDALONE_INSTALLED;
        BlockESPClient.enabled = d.blockEspEnabled;
        EspClient.enabled = d.espEnabled;
        RtpqInvseeClient.enabled = d.rtpqEnabled;
        FreeLookClient.enabled = d.freeLookEnabled;
        FullBrightClient.enabled = d.fullBrightEnabled;
        com.adhdclient.freecam.FreeCamClient.enabled = d.freeCamEnabled;
        com.adhdclient.blink.BlinkClient.enabled = d.blinkEnabled;
        com.adhdclient.inspect.InspectClient.enabled = d.inspectEnabled;
        com.adhdclient.packettools.PacketToolsClient.enabled = d.packetToolsEnabled;
    }

    public static void save() {
        Data d = new Data();
        d.baseFinderEnabled = BaseFinderClient.enabled;
        d.blockEspEnabled = BlockESPClient.enabled;
        d.espEnabled = EspClient.enabled;
        d.rtpqEnabled = RtpqInvseeClient.enabled;
        d.freeLookEnabled = FreeLookClient.enabled;
        d.fullBrightEnabled = FullBrightClient.enabled;
        d.freeCamEnabled = com.adhdclient.freecam.FreeCamClient.enabled;
        d.blinkEnabled = com.adhdclient.blink.BlinkClient.enabled;
        d.inspectEnabled = com.adhdclient.inspect.InspectClient.enabled;
        d.packetToolsEnabled = com.adhdclient.packettools.PacketToolsClient.enabled;
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("modules.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(d, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            // nicht kritisch
        }
    }
}
