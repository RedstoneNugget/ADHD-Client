package com.adhdclient.test;

import com.adhdclient.butterfly.ButterflyClient;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/** Butterfly: Schwert in der Hand wird zum Messer; Bilder von Grundhaltung, Aufklappen, Inspect, Schlag und Spielen. */
final class ButterflyGameTest {

    private ButterflyGameTest() {
    }

    static void run(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            sp.getClientWorld().waitForChunksRender();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
            server.runCommand("item replace entity @a hotbar.1 with minecraft:stick");
            server.runCommand("tp @a 0.5 -60 0.5 0 10");
            context.waitTicks(20);
            context.runOnClient(c -> {
                c.getLanguageManager().setLanguage("en_us");
                ButterflyClient.enabled = true;
                ButterflyClient.config().idleFidget = false;
            });
            context.getInput().pressKey(GLFW.GLFW_KEY_2);
            context.waitTicks(5);
            context.getInput().pressKey(GLFW.GLFW_KEY_1);
            for (int i = 0; i < 10; i++) {
                context.waitTicks(2);
                context.takeScreenshot("70_butterfly_aufklappen_" + (char) ('a' + i));
            }
            context.waitTicks(20);
            context.takeScreenshot("71_butterfly_grundhaltung");
            for (int turn : new int[]{0, 35, 70}) {
                context.runOnClient(c -> {
                    com.adhdclient.butterfly.ButterflyRenderer.showcase = true;
                    com.adhdclient.butterfly.ButterflyRenderer.showcaseTurn = turn;
                });
                context.waitTicks(2);
                context.takeScreenshot("71_butterfly_vorschau_" + turn);
            }
            context.runOnClient(c -> com.adhdclient.butterfly.ButterflyRenderer.showcase = false);
            check(context.computeOnClient(c -> ButterflyClient.anim() == ButterflyClient.Anim.NONE), "Aufklappen ist fertig");

            context.getInput().pressKey(GLFW.GLFW_KEY_R);
            for (int i = 0; i < 24; i++) {
                context.waitTicks(3);
                context.takeScreenshot("72_butterfly_inspect_" + (char) ('a' + i));
            }
            context.waitTicks(20);
            for (int hit = 0; hit < 2; hit++) {
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                for (int i = 0; i < 5; i++) {
                    context.waitTicks(2);
                    context.takeScreenshot("73_butterfly_schlag" + hit + "_" + i);
                }
                context.waitTicks(15);
            }
            context.waitTicks(20);
            context.runOnClient(c -> ButterflyClient.start(ButterflyClient.Anim.IDLE));
            for (int i = 0; i < 3; i++) {
                context.waitTicks(5);
                context.takeScreenshot("74_butterfly_spielen_" + i);
            }
            context.waitTicks(20);
            context.runOnClient(c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            context.waitTicks(5);
            context.takeScreenshot("75_butterfly_dritte_person_vorn");
            context.runOnClient(c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("76_butterfly_dritte_person_hinten");
            // Seitenansicht: unsichtbarer Rüstungsständer rechts neben dem Spieler als Kamera
            server.runCommand("summon armor_stand -2.0 -60 0.5 {Invisible:1b,NoGravity:1b,Rotation:[-90f,0f],Tags:[\"adhd_cam\"]}");
            context.waitTicks(5);
            context.runOnClient(c -> {
                c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                for (net.minecraft.entity.Entity e : c.world.getEntities()) {
                    if (e instanceof net.minecraft.entity.decoration.ArmorStandEntity) c.setCameraEntity(e);
                }
            });
            context.waitTicks(3);
            context.takeScreenshot("77_butterfly_dritte_person_seite");
            context.runOnClient(c -> c.setCameraEntity(c.player));
            context.runOnClient(c -> {
                c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
                ButterflyClient.enabled = false;
            });
        }
        log("Butterfly: alle Prüfungen bestanden");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
