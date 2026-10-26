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
        boolean casinoEnabled = false;
        boolean butterflyEnabled = false;
        boolean ghostItemsEnabled = false;
        boolean aimLockEnabled = false;
        boolean autoReconnectEnabled = false;
        boolean macrosEnabled = false;
        boolean strongholdEnabled = false;
        boolean calcEnabled = false;
        int calcKey = org.lwjgl.glfw.GLFW.GLFW_KEY_N;
        boolean lungeEnabled = false;
        int lungeKey = org.lwjgl.glfw.GLFW.GLFW_KEY_V;
        boolean lungeRepeat = true;
        int lungeDelay = 10;
        boolean airJumpEnabled = false;
        int airJumps = 0;
        double airJumpStrength = 0.42;
        boolean autoTotemEnabled = false;
        boolean autoTotemInventory = false;
        double autoTotemDelay = 0.5;
        boolean antiCrystalEnabled = false;
        double antiCrystalRadius = 6;
        int autoTotemKey = -1;
        int antiCrystalKey = -1;
        int antiCrystalDelay = 1;
        boolean emojiEnabled = false;
        boolean emojiOnSend = false;
        boolean totemViewEnabled = false;
        boolean totemViewPops = true;
        boolean introEnabled = true;
        boolean stackDropEnabled = false;
        int stackDropKey = com.adhdclient.gui.HotKey.NONE;
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
        com.adhdclient.casino.client.LuckyCasinoClient.enabled = d.casinoEnabled && !com.adhdclient.casino.client.LuckyCasinoClient.STANDALONE_INSTALLED;
        com.adhdclient.butterfly.ButterflyClient.enabled = d.butterflyEnabled;
        com.adhdclient.ghost.GhostItemsClient.enabled = d.ghostItemsEnabled;
        com.adhdclient.aimlock.AimLockClient.enabled = d.aimLockEnabled;
        com.adhdclient.reconnect.AutoReconnectClient.enabled = d.autoReconnectEnabled;
        com.adhdclient.macros.MacrosClient.enabled = d.macrosEnabled;
        com.adhdclient.stronghold.StrongholdClient.enabled = d.strongholdEnabled;
        com.adhdclient.calc.CalcClient.enabled = d.calcEnabled;
        com.adhdclient.calc.CalcClient.key = d.calcKey;
        com.adhdclient.lunge.LungeMacroClient.enabled = d.lungeEnabled;
        com.adhdclient.lunge.LungeMacroClient.key = d.lungeKey;
        com.adhdclient.lunge.LungeMacroClient.repeat = d.lungeRepeat;
        com.adhdclient.lunge.LungeMacroClient.delay = d.lungeDelay;
        com.adhdclient.airjump.AirJumpClient.enabled = d.airJumpEnabled;
        com.adhdclient.airjump.AirJumpClient.maxJumps = d.airJumps;
        com.adhdclient.airjump.AirJumpClient.strength = d.airJumpStrength;
        com.adhdclient.autototem.AutoTotemClient.enabled = d.autoTotemEnabled;
        com.adhdclient.autototem.AutoTotemClient.wholeInventory = d.autoTotemInventory;
        com.adhdclient.autototem.AutoTotemClient.delay = d.autoTotemDelay;
        com.adhdclient.anticrystal.AntiCrystalClient.enabled = d.antiCrystalEnabled;
        com.adhdclient.anticrystal.AntiCrystalClient.radius = d.antiCrystalRadius;
        com.adhdclient.autototem.AutoTotemClient.toggleKey = d.autoTotemKey;
        com.adhdclient.anticrystal.AntiCrystalClient.toggleKey = d.antiCrystalKey;
        com.adhdclient.anticrystal.AntiCrystalClient.delay = d.antiCrystalDelay;
        com.adhdclient.emoji.ChatEmojiClient.enabled = d.emojiEnabled;
        com.adhdclient.emoji.ChatEmojiClient.onSend = d.emojiOnSend;
        com.adhdclient.totemview.TotemViewClient.enabled = d.totemViewEnabled;
        com.adhdclient.totemview.TotemViewClient.showPops = d.totemViewPops;
        com.adhdclient.intro.IntroClient.enabled = d.introEnabled;
        com.adhdclient.stackdrop.StackDropClient.enabled = d.stackDropEnabled;
        com.adhdclient.stackdrop.StackDropClient.key = d.stackDropKey;
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
        d.casinoEnabled = com.adhdclient.casino.client.LuckyCasinoClient.enabled;
        d.butterflyEnabled = com.adhdclient.butterfly.ButterflyClient.enabled;
        d.ghostItemsEnabled = com.adhdclient.ghost.GhostItemsClient.enabled;
        d.aimLockEnabled = com.adhdclient.aimlock.AimLockClient.enabled;
        d.autoReconnectEnabled = com.adhdclient.reconnect.AutoReconnectClient.enabled;
        d.macrosEnabled = com.adhdclient.macros.MacrosClient.enabled;
        d.strongholdEnabled = com.adhdclient.stronghold.StrongholdClient.enabled;
        d.calcEnabled = com.adhdclient.calc.CalcClient.enabled;
        d.calcKey = com.adhdclient.calc.CalcClient.key;
        d.lungeEnabled = com.adhdclient.lunge.LungeMacroClient.enabled;
        d.lungeKey = com.adhdclient.lunge.LungeMacroClient.key;
        d.lungeRepeat = com.adhdclient.lunge.LungeMacroClient.repeat;
        d.lungeDelay = com.adhdclient.lunge.LungeMacroClient.delay;
        d.airJumpEnabled = com.adhdclient.airjump.AirJumpClient.enabled;
        d.airJumps = com.adhdclient.airjump.AirJumpClient.maxJumps;
        d.airJumpStrength = com.adhdclient.airjump.AirJumpClient.strength;
        d.autoTotemEnabled = com.adhdclient.autototem.AutoTotemClient.enabled;
        d.autoTotemInventory = com.adhdclient.autototem.AutoTotemClient.wholeInventory;
        d.autoTotemDelay = com.adhdclient.autototem.AutoTotemClient.delay;
        d.antiCrystalEnabled = com.adhdclient.anticrystal.AntiCrystalClient.enabled;
        d.antiCrystalRadius = com.adhdclient.anticrystal.AntiCrystalClient.radius;
        d.autoTotemKey = com.adhdclient.autototem.AutoTotemClient.toggleKey;
        d.antiCrystalKey = com.adhdclient.anticrystal.AntiCrystalClient.toggleKey;
        d.antiCrystalDelay = com.adhdclient.anticrystal.AntiCrystalClient.delay;
        d.emojiEnabled = com.adhdclient.emoji.ChatEmojiClient.enabled;
        d.emojiOnSend = com.adhdclient.emoji.ChatEmojiClient.onSend;
        d.totemViewEnabled = com.adhdclient.totemview.TotemViewClient.enabled;
        d.totemViewPops = com.adhdclient.totemview.TotemViewClient.showPops;
        d.introEnabled = com.adhdclient.intro.IntroClient.enabled;
        d.stackDropEnabled = com.adhdclient.stackdrop.StackDropClient.enabled;
        d.stackDropKey = com.adhdclient.stackdrop.StackDropClient.key;
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
