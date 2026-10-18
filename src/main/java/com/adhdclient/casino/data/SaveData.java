package com.adhdclient.casino.data;

import com.adhdclient.casino.config.CasinoConfig;
import com.adhdclient.casino.core.progress.PlayerProfile;

/** Inhalt der Datei {@code config/luckycasino.json}. */
public class SaveData {
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    public CasinoConfig settings = new CasinoConfig();
    public PlayerProfile profile = new PlayerProfile();

    public void sanitize() {
        if (settings == null) {
            settings = new CasinoConfig();
        }
        settings.sanitize();
        if (profile == null) {
            profile = new PlayerProfile();
        }
        profile.sanitize();
        version = CURRENT_VERSION;
    }
}
