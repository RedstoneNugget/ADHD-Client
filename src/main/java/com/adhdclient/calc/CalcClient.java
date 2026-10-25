package com.adhdclient.calc;

import com.adhdclient.gui.HotKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** Schnell-Rechner: eine Taste öffnet das Rechner-Fenster (nur wenn das Modul an ist und kein Menü offen). */
public final class CalcClient {

    public static boolean enabled = false;
    /** Taste zum Öffnen (im Rechner-Modul einstellbar über das ADHD-Menü). */
    public static int key = GLFW.GLFW_KEY_N;
    private static final HotKey KEY = new HotKey();

    private CalcClient() {
    }

    public static String keyName() {
        return HotKey.name(key);
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean pressed = KEY.pressed(key);
            if (pressed && enabled && mc.currentScreen == null && mc.player != null) mc.setScreen(new CalcScreen(null));
        });
    }

    /** Öffnen aus dem ADHD-Menü (Einstellungen-Knopf des Moduls). */
    public static net.minecraft.client.gui.screen.Screen open(net.minecraft.client.gui.screen.Screen parent) {
        return new CalcScreen(parent);
    }

    static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
