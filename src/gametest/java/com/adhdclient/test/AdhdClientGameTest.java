package com.adhdclient.test;

import com.adhdclient.blink.BlinkClient;
import com.adhdclient.blink.BlinkScreen;
import com.adhdclient.esp.EspScreen;
import com.adhdclient.gui.ClickGuiScreen;
import com.adhdclient.gui.HotKey;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.math.Vec3d;

/**
 * Automatischer In-Game-Test: gradlew runClientGameTest
 * Prüft Blink in einer echten Einzelspieler-Welt und macht Bilder der Menüs
 * (build/run/clientGameTest/screenshots).
 */
public class AdhdClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("adhd.onlyFullBright")) {
            FullBrightGameTest.run(context);
            return;
        }
        if (Boolean.getBoolean("adhd.onlyButterfly")) {
            ButterflyGameTest.run(context);
            return;
        }
        context.runOnClient(c -> c.options.getSoundVolumeOption(net.minecraft.sound.SoundCategory.MASTER).setValue(0.0));
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            sp.getClientWorld().waitForChunksRender();
            server.runCommand("gamemode creative @a");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("time set noon");
            context.waitTicks(20);
            context.runOnClient(c -> BlinkClient.enabled = true);

            menus(context);
            keyReset(context);
            mouseKey(context);
            baseFinderDouble(context);
            blinkSend(context, server);
            blinkCancel(context, server);
        }
        InspectGameTest.run(context);
        PacketToolsGameTest.run(context);
        CasinoGameTest.run(context);
        FullBrightGameTest.run(context);
        ButterflyGameTest.run(context);
        log("ALLE TESTS BESTANDEN");
    }

    private void menus(ClientGameTestContext context) {
        context.setScreen(() -> new ClickGuiScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("01_clickgui");
        // rotes Design
        context.runOnClient(c -> com.adhdclient.gui.Theme.config().apply(com.adhdclient.gui.ThemeConfig.PRESETS.get("Rot")));
        context.setScreen(() -> new ClickGuiScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("01b_clickgui_rot");
        context.setScreen(() -> new com.adhdclient.gui.ThemeScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("01c_design_rot");
        context.runOnClient(c -> com.adhdclient.gui.Theme.config().apply(com.adhdclient.gui.ThemeConfig.PRESETS.get("Lila")));
        context.setScreen(() -> new EspScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("02_esp");
        context.setScreen(() -> new BlinkScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("03_blink");
        context.runOnClient(c -> c.getLanguageManager().setLanguage("de_de"));
        context.setScreen(() -> new EspScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("02b_esp_de");
        context.setScreen(() -> new BlinkScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("03b_blink_de");
        context.runOnClient(c -> c.getLanguageManager().setLanguage("en_us"));
        context.setScreen(() -> null);
        context.waitTicks(5);
    }

    /** Base Finder: mit eigenständiger Mod bleibt das ADHD-Modul aus (sonst alles doppelt), ohne lässt es sich schalten. */
    private void baseFinderDouble(ClientGameTestContext context) {
        boolean standalone = context.computeOnClient(c -> com.adhdclient.basefinder.BaseFinderClient.STANDALONE_INSTALLED);
        com.adhdclient.core.Modules.Module bf = com.adhdclient.core.Modules.ALL.stream().filter(m -> m.name().equals("Base Finder")).findFirst().orElseThrow();
        context.runOnClient(c -> bf.set().accept(true));
        context.waitTicks(2);
        boolean on = context.computeOnClient(c -> com.adhdclient.basefinder.BaseFinderClient.enabled);
        log("Base Finder: eigene Mod installiert " + standalone + ", ADHD-Modul an " + on);
        if (standalone) {
            check(!on, "ADHD-Base-Finder ging trotz eigenständiger Mod an (doppelt)");
            context.setScreen(() -> new ClickGuiScreen(null));
            context.waitTicks(3);
            double s = context.computeOnClient(c -> c.getWindow().getScaleFactor());
            context.getInput().setCursorPos(190 * s, 45 * s);
            context.waitTicks(3);
            context.takeScreenshot("07_base_finder_doppelt");
            context.setScreen(() -> null);
            context.waitTicks(3);
            context.takeScreenshot("08_base_finder_chat");
        } else {
            check(on, "Base Finder liess sich nicht einschalten");
            boolean before = context.computeOnClient(c -> com.adhdclient.basefinder.BaseFinderClient.config().general.markersVisible);
            context.getInput().pressKey(GLFW.GLFW_KEY_H);
            context.waitTicks(3);
            boolean after = context.computeOnClient(c -> com.adhdclient.basefinder.BaseFinderClient.config().general.markersVisible);
            context.takeScreenshot("09_base_finder_h");
            log("Base Finder H: Markierungen " + before + " -> " + after);
            check(before != after, "H hat die Markierungen nicht umgeschaltet");
            context.getInput().pressKey(GLFW.GLFW_KEY_H);
            context.getInput().pressKey(GLFW.GLFW_KEY_J);
            context.waitTicks(3);
            context.takeScreenshot("10_base_finder_j");
            context.setScreen(() -> new com.adhdclient.basefinder.gui.ConfigScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("11_base_finder_menu");
            context.runOnClient(c -> c.getLanguageManager().setLanguage("de_de"));
            context.setScreen(() -> new com.adhdclient.basefinder.gui.ConfigScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("11b_base_finder_menu_de");
            context.runOnClient(c -> c.getLanguageManager().setLanguage("en_us"));
            context.setScreen(() -> null);
            context.waitTicks(3);
        }
        context.runOnClient(c -> bf.set().accept(false));
    }

    /** Esc beim Tastenwählen = keine Taste, ↺ = zurück auf den Standard (Free Cam: F6). */
    private void keyReset(ClientGameTestContext context) {
        context.setScreen(() -> new com.adhdclient.freecam.FreeCamScreen(null));
        context.waitTicks(3);
        context.clickScreenButton("On/Off: F6");
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitTicks(2);
        int afterEsc = context.computeOnClient(c -> com.adhdclient.freecam.FreeCamClient.config().key);
        boolean stillOpen = context.computeOnClient(c -> c.currentScreen instanceof com.adhdclient.freecam.FreeCamScreen);
        context.takeScreenshot("06b_taste_esc_keine");
        context.runOnClient(c -> c.currentScreen.children().stream().filter(e -> e instanceof com.adhdclient.gui.ResetButton).findFirst().ifPresent(e -> ((com.adhdclient.gui.ResetButton) e).onPress(null)));
        context.waitTicks(2);
        int afterReset = context.computeOnClient(c -> com.adhdclient.freecam.FreeCamClient.config().key);
        context.takeScreenshot("06c_taste_zurueckgesetzt");
        log("Taste: nach Esc " + afterEsc + " (Menü offen " + stillOpen + "), nach ↺ " + afterReset + " (F6 = " + GLFW.GLFW_KEY_F6 + ")");
        check(afterEsc == HotKey.NONE && stillOpen, "Esc beim Tastenwählen = keine Taste, Menü bleibt offen");
        check(afterReset == GLFW.GLFW_KEY_F6, "↺ setzt auf F6 zurück");
        context.setScreen(() -> null);
        context.waitTicks(3);
    }

    /** Taste per Maus setzen: Knopf anklicken, dann Seitentaste 4 drücken. */
    private void mouseKey(ClientGameTestContext context) {
        context.runOnClient(c -> BlinkClient.config().key = HotKey.NONE);
        context.setScreen(() -> new BlinkScreen(null));
        context.waitTicks(3);
        double[] pos = context.computeOnClient(c -> {
            for (var e : c.currentScreen.children()) {
                if (e instanceof ButtonWidget b && b.getMessage().getString().startsWith("On/Off")) {
                    double s = c.getWindow().getScaleFactor();
                    return new double[]{(b.getX() + b.getWidth() / 2.0) * s, (b.getY() + b.getHeight() / 2.0) * s};
                }
            }
            throw new AssertionError("FEHLER: Tasten-Knopf nicht gefunden");
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        context.takeScreenshot("05_taste_warten");
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_4);
        context.waitTicks(2);
        context.takeScreenshot("06_maustaste_gesetzt");
        int key = context.computeOnClient(c -> BlinkClient.config().key);
        log("Blink-Taste per Maus: " + context.computeOnClient(c -> HotKey.name(BlinkClient.config().key)));
        check(key == HotKey.mouse(GLFW.GLFW_MOUSE_BUTTON_4), "Maustaste wurde nicht als Taste gespeichert");
        context.setScreen(() -> null);
        context.waitTicks(3);
        // im Spiel: Seitentaste schaltet Blink an und wieder aus
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_4);
        context.waitTicks(3);
        boolean onByMouse = context.computeOnClient(c -> BlinkClient.active);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_4);
        context.waitTicks(3);
        boolean offByMouse = !context.computeOnClient(c -> BlinkClient.active);
        log("Blink per Seitentaste: an " + onByMouse + ", wieder aus " + offByMouse);
        check(onByMouse && offByMouse, "Seitentaste hat Blink nicht geschaltet");
        context.runOnClient(c -> BlinkClient.config().key = HotKey.NONE);
    }

    private void blinkSend(ClientGameTestContext context, TestServerContext server) {
        Vec3d serverStart = serverPos(server);
        context.runOnClient(c -> BlinkClient.set(true));
        context.getInput().holdKeyFor(o -> o.forwardKey, 30);
        context.waitTicks(5);
        Vec3d client = context.computeOnClient(c -> c.player.getEntityPos());
        Vec3d serverNow = serverPos(server);
        int held = context.computeOnClient(c -> BlinkClient.heldCount());
        boolean fake = context.computeOnClient(c -> c.world.getEntityById(-424242) != null);
        context.takeScreenshot("04_blink_an");
        log("Blink an: Client " + fmt(client) + ", Server " + fmt(serverNow) + ", Pakete " + held + ", Kopie " + fake);
        check(client.distanceTo(serverStart) > 2, "Client hat sich nicht bewegt");
        check(serverNow.distanceTo(serverStart) < 0.1, "Server hat Bewegung trotz Blink gesehen");
        check(held > 0, "keine Pakete zurückgehalten");
        check(fake, "keine Kopie zu sehen");

        context.runOnClient(c -> BlinkClient.set(false));
        context.waitTicks(10);
        Vec3d serverAfter = serverPos(server);
        boolean fakeGone = context.computeOnClient(c -> c.world.getEntityById(-424242) == null);
        log("Blink aus: Server " + fmt(serverAfter));
        check(serverAfter.distanceTo(client) < 0.5, "Server ist nicht zur neuen Position gesprungen");
        check(fakeGone, "Kopie wurde nicht entfernt");
    }

    private void blinkCancel(ClientGameTestContext context, TestServerContext server) {
        Vec3d serverStart = serverPos(server);
        context.runOnClient(c -> BlinkClient.set(true));
        context.getInput().holdKeyFor(o -> o.forwardKey, 30);
        context.waitTicks(5);
        context.runOnClient(c -> BlinkClient.cancel());
        context.waitTicks(10);
        Vec3d client = context.computeOnClient(c -> c.player.getEntityPos());
        Vec3d serverNow = serverPos(server);
        log("Blink abgebrochen: Client " + fmt(client) + ", Server " + fmt(serverNow));
        check(client.distanceTo(serverStart) < 0.5, "Client wurde nicht zum Start zurückgesetzt");
        check(serverNow.distanceTo(serverStart) < 0.5, "Server hat abgebrochene Bewegung gesehen");
    }

    private static Vec3d serverPos(TestServerContext server) {
        return server.computeOnServer(s -> s.getPlayerManager().getPlayerList().get(0).getEntityPos());
    }

    private static String fmt(Vec3d v) {
        return String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f", v.x, v.y, v.z);
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
