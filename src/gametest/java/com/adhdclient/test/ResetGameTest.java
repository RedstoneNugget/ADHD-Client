package com.adhdclient.test;

import com.adhdclient.airjump.AirJumpClient;
import com.adhdclient.fullbright.FullBrightClient;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Zurücksetzen-Knopf unten in den Modul-Fenstern: zweimal klicken setzt die Einstellungen auf Standard. */
final class ResetGameTest {

    private ResetGameTest() {
    }

    static void run(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            context.runOnClient(c -> {
                c.getLanguageManager().setLanguage("en_us");
                FullBrightClient.config().strength = 30;
                FullBrightClient.config().dayTime = false;
                c.setScreen(new com.adhdclient.fullbright.FullBrightScreen(null));
            });
            context.waitTicks(3);
            context.takeScreenshot("91_reset_knopf");
            context.clickScreenButton("Reset");
            context.waitTicks(2);
            context.clickScreenButton("Sure?");
            context.waitTicks(2);
            boolean fb = context.computeOnClient(c -> FullBrightClient.config().strength == 100 && FullBrightClient.config().dayTime);
            context.runOnClient(c -> {
                AirJumpClient.maxJumps = 7;
                AirJumpClient.strength = 0.9;
                c.setScreen(new com.adhdclient.airjump.AirJumpScreen(null));
            });
            context.waitTicks(3);
            context.clickScreenButton("Reset");
            context.waitTicks(2);
            context.clickScreenButton("Sure?");
            context.waitTicks(2);
            boolean aj = context.computeOnClient(c -> AirJumpClient.maxJumps == 0 && Math.abs(AirJumpClient.strength - 0.42) < 1e-6);
            log("Zurückgesetzt – Full Bright " + fb + ", Air Jump " + aj);
            check(fb && aj, "Zurücksetzen stellt die Standardwerte wieder her");
            context.runOnClient(c -> c.setScreen(null));
        }
        log("Zurücksetzen: alle Prüfungen bestanden");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
