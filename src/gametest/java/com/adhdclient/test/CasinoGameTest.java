package com.adhdclient.test;

import com.adhdclient.casino.CasinoModuleScreen;
import com.adhdclient.casino.client.LuckyCasinoClient;
import com.adhdclient.casino.client.gui.CasinoMenuScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/** In-Game-Test für Lucky Casino im ADHD Client: Taste O öffnet das Casino nur bei eingeschaltetem Modul. */
final class CasinoGameTest {

    private CasinoGameTest() {
    }

    static void run(ClientGameTestContext context) {
        context.runOnClient(c -> c.getLanguageManager().setLanguage("en_us"));
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            context.waitTicks(10);

            // Modul aus: O tut nichts
            context.runOnClient(c -> LuckyCasinoClient.enabled = false);
            context.getInput().pressKey(GLFW.GLFW_KEY_O);
            context.waitTicks(5);
            check(context.computeOnClient(c -> c.currentScreen == null), "Modul aus: O öffnet kein Casino");

            // Modul an: O öffnet das Casino
            context.runOnClient(c -> LuckyCasinoClient.enabled = true);
            context.getInput().pressKey(GLFW.GLFW_KEY_O);
            context.waitTicks(5);
            check(context.computeOnClient(c -> c.currentScreen instanceof CasinoMenuScreen), "O öffnet das Casino");
            long balance = context.computeOnClient(c -> LuckyCasinoClient.casino().balance());
            log("Casino offen, Guthaben " + balance);
            context.takeScreenshot("50_casino_menu_en");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(3);

            context.runOnClient(c -> c.getLanguageManager().setLanguage("de_de"));
            context.setScreen(() -> new CasinoMenuScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("51_casino_menu_de");
            context.setScreen(() -> new CasinoModuleScreen(null));
            context.waitTicks(3);
            context.takeScreenshot("52_casino_module_de");
            context.setScreen(() -> null);
            context.runOnClient(c -> c.getLanguageManager().setLanguage("en_us"));
            context.runOnClient(c -> LuckyCasinoClient.enabled = false);
        }
        log("Lucky Casino: alle Prüfungen bestanden");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
