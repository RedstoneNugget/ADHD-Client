package com.adhdclient.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Taste, die im ADHD-Menü eingestellt wird (nicht in den Minecraft-Steuerungsoptionen).
 * Gespeichert wird nur ein Code: GLFW-Tastencode, oder {@link #MOUSE} + Maustaste; -1 = keine Taste.
 * Drücken wird über die echten Tasten-Ereignisse gezählt (HotKeyInputMixin), damit auch ein sehr kurzes
 * Antippen zwischen zwei Ticks nicht verloren geht.
 */
public final class HotKey {

    public static final int NONE = GLFW.GLFW_KEY_UNKNOWN;
    /** Ab hier sind es Maustasten: MOUSE + 0 = links, + 1 = rechts, + 2 = Mausrad, + 3/+ 4 = Seitentasten … */
    public static final int MOUSE = 1000;

    /** Wie oft jede Taste ohne offenes Menü gedrückt wurde. */
    private static final Map<Integer, Integer> PRESSES = new HashMap<>();
    private static final boolean[] MOUSE_DOWN = new boolean[16];

    private int lastCode = NONE;
    private int lastCount;

    public static int mouse(int button) {
        return MOUSE + button;
    }

    public static boolean isMouse(int code) {
        return code >= MOUSE;
    }

    /** Aus dem Mixin: Taste/Maustaste gedrückt (GLFW_PRESS) oder losgelassen (GLFW_RELEASE). */
    public static void onInput(int code, int action) {
        if (isMouse(code) && code - MOUSE < MOUSE_DOWN.length) {
            if (action == GLFW.GLFW_PRESS) MOUSE_DOWN[code - MOUSE] = true;
            else if (action == GLFW.GLFW_RELEASE) MOUSE_DOWN[code - MOUSE] = false;
        }
        if (action == GLFW.GLFW_PRESS && MinecraftClient.getInstance().currentScreen == null) {
            PRESSES.merge(code, 1, Integer::sum);
        }
    }

    /** Anzeigename, z. B. "F6", "Linke Alt-Taste" oder "Maustaste 4". */
    public static String name(int code) {
        if (code == NONE) return L.t("Keine", "None");
        if (isMouse(code)) return InputUtil.Type.MOUSE.createFromCode(code - MOUSE).getLocalizedText().getString();
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
    }

    /** Taste gerade gedrückt (unabhängig von offenen Menüs). */
    public static boolean isDown(int code) {
        if (code == NONE) return false;
        if (isMouse(code)) return code - MOUSE < MOUSE_DOWN.length && MOUSE_DOWN[code - MOUSE];
        return InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), code);
    }

    /** Gedrückt und kein Menü offen. */
    public static boolean isHeld(int code) {
        return MinecraftClient.getInstance().currentScreen == null && isDown(code);
    }

    /** True, wenn die Taste seit dem letzten Aufruf (ohne offenes Menü) gedrückt wurde. */
    public boolean pressed(int code) {
        if (code == NONE) return false;
        int count = PRESSES.getOrDefault(code, 0);
        if (code != lastCode) {
            lastCode = code;
            lastCount = count;
            return false;
        }
        boolean edge = count != lastCount;
        lastCount = count;
        return edge;
    }
}
