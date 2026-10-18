package com.adhdclient.rtpq;

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

/** Einstellungen von RTPQ Invsee, gespeichert als config/adhd_client/rtpq_invsee.json. */
public final class RtpqConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Nur auf Servern aktiv, deren Adresse diesen Text enthält (leer = überall). */
    public String serverFilter = "";
    /** Text in der Chat-Zeile direkt vor dem Gegner-Namen, z. B. "Gegner gefunden:". */
    public String opponentText = "Gegner gefunden:";
    /** Optional: Enthält eine Chat-Zeile diesen Text, wird das Inventar sofort geschlossen (leer = aus). */
    public String endText = "";
    /** Countdown, falls der Server keine "Teleport in X"-Zeile schickt. */
    public int countdownSeconds = 3;
    /** Abstand zwischen den beiden /rtpqueue bei "Nein" (Ticks). */
    public int rtpqueueDelayTicks = 20;
    public boolean warnElytra = true;
    public boolean warnMace = true;
    public boolean warnCrystals = true;
    /** Ab so vielen End-Kristallen wird gewarnt. */
    public int crystalThreshold = 7;
    public boolean sounds = true;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("rtpq_invsee.json");
    }

    public static RtpqConfig load() {
        Path f = file();
        RtpqConfig cfg = null;
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, RtpqConfig.class);
            } catch (IOException | JsonParseException e) {
                RtpqInvseeClient.LOGGER.warn("rtpq_invsee.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        if (cfg == null) cfg = new RtpqConfig();
        cfg.sanitize();
        return cfg;
    }

    public void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("rtpq_invsee.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            RtpqInvseeClient.LOGGER.warn("rtpq_invsee.json konnte nicht gespeichert werden", e);
        }
    }

    public void sanitize() {
        if (serverFilter == null) serverFilter = "";
        if (opponentText == null || opponentText.isBlank()) opponentText = new RtpqConfig().opponentText;
        if (endText == null) endText = "";
        opponentText = opponentText.trim();
        endText = endText.trim();
        countdownSeconds = Math.max(1, Math.min(30, countdownSeconds));
        rtpqueueDelayTicks = Math.max(1, Math.min(200, rtpqueueDelayTicks));
        crystalThreshold = Math.max(1, Math.min(64, crystalThreshold));
    }

}
