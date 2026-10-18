package com.adhdclient.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Taste, die im ADHD-Menü eingestellt wird (nicht in den Minecraft-Steuerungsoptionen).
 * Gespeichert wird nur der GLFW-Tastencode; -1 = keine Taste.
 */
public final class HotKey {

    public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;

    private boolean wasDown;

    /** Anzeigename, z. B. "F6" oder "Linke Alt-Taste". */
    public static String name(int code) {
        if (code == NONE) return L.t("Keine", "None");
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
    }

    /** Taste gerade gedrückt (unabhängig von offenen Menüs). */
    public static boolean isDown(int code) {
        if (code == NONE) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        return InputUtil.isKeyPressed(mc.getWindow(), code);
    }

    /** Gedrückt und kein Menü offen. */
    public static boolean isHeld(int code) {
        return MinecraftClient.getInstance().currentScreen == null && isDown(code);
    }

    /** True genau in dem Tick, in dem die Taste (ohne offenes Menü) gedrückt wird. */
    public boolean pressed(int code) {
        boolean down = isHeld(code);
        boolean edge = down && !wasDown;
        wasDown = down;
        return edge;
    }
}
