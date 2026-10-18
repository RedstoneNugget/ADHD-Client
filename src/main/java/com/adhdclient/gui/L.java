package com.adhdclient.gui;

import net.minecraft.client.MinecraftClient;

/**
 * Sprache des ADHD Client: Deutsch oder Englisch. "Automatisch" folgt der Spielsprache
 * (Deutsch bei de_de, de_at, de_ch …, sonst Englisch). Einstellbar im Design-Menü.
 */
public final class L {

    public enum Mode { AUTO, DE, EN }

    private L() {
    }

    public static boolean german() {
        Mode m = Theme.config().language;
        if (m == Mode.DE) return true;
        if (m == Mode.EN) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getLanguageManager() == null) return false;
        String code = mc.getLanguageManager().getLanguage();
        return code != null && code.startsWith("de");
    }

    /** Text in der eingestellten Sprache. */
    public static String t(String de, String en) {
        return german() ? de : en;
    }
}
