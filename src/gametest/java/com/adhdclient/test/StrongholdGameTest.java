package com.adhdclient.test;

import com.adhdclient.stronghold.StrongholdClient;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Stronghold Finder: zwei „Würfe“ (Ender-Auge spawnen und ein Stück Richtung Ziel bewegen) von verschiedenen Orten,
 * beide zeigen auf (100, 200) – der Schnittpunkt muss dort liegen.
 */
final class StrongholdGameTest {

    private StrongholdGameTest() {
    }

    static void run(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            sp.getClientWorld().waitForChunksRender();
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0.5 -60 0.5");
            context.waitTicks(10);
            context.runOnClient(c -> {
                c.getLanguageManager().setLanguage("en_us");
                StrongholdClient.enabled = true;
                StrongholdClient.reset();
            });
            fakeThrow(context, server, 0, 0, 100, 200, "a");
            check(context.computeOnClient(c -> StrongholdClient.throwsMade().size()) == 1, "Erster Wurf wird erkannt");
            context.takeScreenshot("86_stronghold_wurf1");
            fakeThrow(context, server, 200, 0, 100, 200, "b");
            double[] r = context.computeOnClient(c -> StrongholdClient.result());
            log("Ergebnis: " + (r == null ? "keins" : (int) r[0] + ", " + (int) r[1]));
            check(r != null && Math.abs(r[0] - 100) < 4 && Math.abs(r[1] - 200) < 4, "Schnittpunkt liegt beim Stronghold");
            context.takeScreenshot("87_stronghold_ergebnis");
            context.runOnClient(c -> StrongholdClient.enabled = false);
        }
        log("Stronghold Finder: alle Prüfungen bestanden");
    }

    /** Auge bei (x, z) spawnen und nach 2 Ticks 4 Blöcke Richtung (tx, tz) setzen – wie ein fliegendes Auge. */
    private static void fakeThrow(ClientGameTestContext context, TestServerContext server, double x, double z, double tx, double tz, String tag) {
        double dx = tx - x, dz = tz - z, len = Math.hypot(dx, dz);
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f -60 %.2f", x, z));
        context.waitTicks(20);

        server.runCommand(String.format(java.util.Locale.ROOT, "summon eye_of_ender %.2f -58 %.2f {NoGravity:1b,Tags:[\"%s\"]}", x, z, tag));
        context.waitTicks(2);
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @e[tag=%s] %.3f -58 %.3f", tag, x + dx / len * 4, z + dz / len * 4));
        context.waitTicks(10);
        server.runCommand("kill @e[tag=" + tag + "]");
        context.waitTicks(2);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
