package com.adhdclient.casino.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lädt und speichert {@link SaveData} als JSON – ausschließlich lokal, kein Netzwerk.
 *
 * <p>Robustheit:</p>
 * <ul>
 *   <li>Speichern schreibt zuerst in eine .tmp-Datei und ersetzt dann atomar die Hauptdatei.
 *       Die vorherige Version bleibt als {@code .bak} erhalten.</li>
 *   <li>Ist die Datei beschädigt, wird sie als {@code .corrupt-<Zeitstempel>} gesichert,
 *       dann wird die {@code .bak} versucht und notfalls mit einem frischen Spielstand begonnen.</li>
 *   <li>Fehler beim Laden/Speichern werfen nie bis ins Spiel durch.</li>
 * </ul>
 */
public final class SaveManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("LuckyCasino");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public enum LoadStatus {
        /** Normal geladen. */
        LOADED,
        /** Keine Datei vorhanden – neuer Spielstand. */
        CREATED,
        /** Hauptdatei beschädigt, letzte gute Sicherung (.bak) geladen. */
        RESTORED_FROM_BACKUP,
        /** Hauptdatei beschädigt, keine brauchbare Sicherung – neuer Spielstand. */
        RESET_AFTER_CORRUPTION
    }

    /**
     * @param corruptCopy Pfad der Sicherung der beschädigten Datei (oder null)
     */
    public record LoadResult(SaveData data, LoadStatus status, Path corruptCopy) {
    }

    private final Path file;
    private final Path backup;
    private final Path temp;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public SaveManager(Path file) {
        this.file = file;
        this.backup = file.resolveSibling(file.getFileName() + ".bak");
        this.temp = file.resolveSibling(file.getFileName() + ".tmp");
    }

    public Path file() {
        return file;
    }

    public LoadResult load() {
        if (!Files.exists(file)) {
            SaveData fromBackup = tryRead(backup);
            if (fromBackup != null) {
                LOGGER.warn("[LuckyCasino] Spielstand fehlt, Sicherung {} wird verwendet", backup);
                save(fromBackup);
                return new LoadResult(fromBackup, LoadStatus.RESTORED_FROM_BACKUP, null);
            }
            SaveData fresh = new SaveData();
            fresh.sanitize();
            save(fresh);
            return new LoadResult(fresh, LoadStatus.CREATED, null);
        }

        SaveData data = tryRead(file);
        if (data != null) {
            return new LoadResult(data, LoadStatus.LOADED, null);
        }

        // Beschädigt: Original sichern, dann Backup versuchen
        Path corruptCopy = file.resolveSibling(file.getFileName() + ".corrupt-" + LocalDateTime.now().format(STAMP));
        try {
            Files.copy(file, corruptCopy, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.warn("[LuckyCasino] Spielstand beschädigt – Kopie gesichert unter {}", corruptCopy);
        } catch (IOException e) {
            LOGGER.error("[LuckyCasino] Konnte beschädigten Spielstand nicht sichern", e);
            corruptCopy = null;
        }
        SaveData fromBackup = tryRead(backup);
        if (fromBackup != null) {
            save(fromBackup);
            return new LoadResult(fromBackup, LoadStatus.RESTORED_FROM_BACKUP, corruptCopy);
        }
        SaveData fresh = new SaveData();
        fresh.sanitize();
        // Hauptdatei direkt ersetzen, damit die defekte Datei nicht als .bak weiterlebt
        writeAtomically(fresh, false);
        return new LoadResult(fresh, LoadStatus.RESET_AFTER_CORRUPTION, corruptCopy);
    }

    /** Liest und prüft eine Datei; null bei Fehler (fehlend, kein JSON, falsche Typen …). */
    private SaveData tryRead(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(text);
            if (root == null || !root.isJsonObject()) {
                return null;
            }
            SaveData data = gson.fromJson(root, SaveData.class);
            if (data == null) {
                return null;
            }
            data.sanitize();
            return data;
        } catch (Exception e) { // IOException, JsonParseException, NumberFormatException, IllegalStateException …
            LOGGER.warn("[LuckyCasino] Datei {} ist nicht lesbar: {}", path, e.toString());
            return null;
        }
    }

    /** Speichert; liefert false bei Fehler (wird geloggt, nie geworfen). */
    public boolean save(SaveData data) {
        return writeAtomically(data, true);
    }

    private boolean writeAtomically(SaveData data, boolean keepBackup) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Files.writeString(temp, gson.toJson(data), StandardCharsets.UTF_8);
            if (keepBackup && Files.isRegularFile(file)) {
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("[LuckyCasino] Spielstand konnte nicht gespeichert werden", e);
            return false;
        }
    }
}
