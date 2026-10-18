package com.adhdclient.compat;

import com.adhdclient.gui.ClickGuiScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Optionale Mod-Menu-Anbindung: Mod Menu lädt diese Klasse nur über den Entrypoint "modmenu".
 * Ohne Mod Menu wird sie nie angefasst – keine harte Abhängigkeit.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClickGuiScreen::new;
    }
}
