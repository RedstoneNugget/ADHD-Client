package com.adhdclient.test;

import com.adhdclient.extras.Extras;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Saubere Bilder für das GitHub-README (ohne Chat-Meldungen): gradlew runClientGameTest -PonlyShowcase */
final class ShowcaseGameTest {

    private ShowcaseGameTest() {
    }

    static void run(ClientGameTestContext context) {
        Extras x = Extras.get();
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            sp.getClientWorld().waitForChunksRender();
            server.runCommand("gamerule sendCommandFeedback false");
            server.runCommand("gamerule announceAdvancements false");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("time set 6000");
            server.runCommand("gamemode creative @a");
            context.runOnClient(c -> c.getLanguageManager().setLanguage("en_us"));

            // Menü
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            context.waitTicks(20);
            shot(context, "showcase_menu", () -> context.runOnClient(c -> {
                c.options.getGuiScale().setValue(1);
                c.onResolutionChanged();
                c.setScreen(new com.adhdclient.gui.ClickGuiScreen(null));
            }));
            context.runOnClient(c -> {
                c.setScreen(null);
                c.options.getGuiScale().setValue(0);
                c.onResolutionChanged();
            });

            // Render-Module
            server.runCommand("setblock 3 -60 6 chest");
            server.runCommand("setblock 4 -60 6 trapped_chest");
            server.runCommand("setblock 6 -60 7 barrel");
            server.runCommand("setblock -4 -60 7 purple_shulker_box");
            server.runCommand("setblock -6 -60 9 ender_chest");
            server.runCommand("summon item 1 -60 4 {Item:{id:\"minecraft:diamond\",count:12},PickupDelay:32767}");
            server.runCommand("summon item -2 -60 5 {Item:{id:\"minecraft:golden_apple\",count:3},PickupDelay:32767}");
            server.runCommand("summon zombie -1 -60 10 {NoAI:1b,equipment:{mainhand:{id:\"minecraft:iron_sword\",count:1},head:{id:\"minecraft:iron_helmet\",count:1}}}");
            server.runCommand("summon pillager 2 -60 12 {NoAI:1b}");
            server.runCommand("summon villager 5 -60 11 {NoAI:1b}");
            server.runCommand("fill -8 -60 14 8 -56 14 stone");
            server.runCommand("summon cow 0 -60 17 {NoAI:1b}");
            context.runOnClient(c -> {
                x.storageEsp.enabled = true;
                x.itemEsp.enabled = true;
                x.nametags.enabled = true;
                x.nametags.playersOnly = false;
                x.radar.enabled = true;
                com.adhdclient.esp.EspClient.enabled = true;
            });
            server.runCommand("tp @a 0.5 -59 -1.5 0 12");
            context.waitTicks(30);
            shot(context, "showcase_esp", () -> { });
            context.runOnClient(c -> {
                x.storageEsp.enabled = false;
                x.itemEsp.enabled = false;
                x.nametags.enabled = false;
                x.radar.enabled = false;
                com.adhdclient.esp.EspClient.enabled = false;
            });

            // Trajectories (dritte Person, damit man den Bogen sieht)
            server.runCommand("item replace entity @a weapon.mainhand with ender_pearl 16");
            server.runCommand("tp @a 0.5 -60 -10.5 0 -25");
            context.runOnClient(c -> {
                x.trajectories.enabled = true;
                com.adhdclient.freecam.FreeCamClient.enabled = true;
                com.adhdclient.freecam.FreeCamClient.set(true);
            });
            context.runOnClient(c -> place(new Vec3d(42, -50, 10), 90f, 8f));
            context.waitTicks(70);
            shot(context, "showcase_trajectories", () -> { });
            context.runOnClient(c -> {
                x.trajectories.enabled = false;
                com.adhdclient.freecam.FreeCamClient.set(false);
                com.adhdclient.freecam.FreeCamClient.enabled = false;
            });

            // Butterfly
            server.runCommand("item replace entity @a weapon.mainhand with diamond_sword");
            server.runCommand("item replace entity @a hotbar.1 with stick");
            server.runCommand("tp @a 0.5 -60 0.5 0 10");
            context.runOnClient(c -> {
                com.adhdclient.butterfly.ButterflyClient.enabled = true;
                com.adhdclient.butterfly.ButterflyClient.config().idleFidget = false;
            });
            context.getInput().pressKey(GLFW.GLFW_KEY_2);
            context.waitTicks(5);
            context.getInput().pressKey(GLFW.GLFW_KEY_1);
            context.waitTicks(40);
            shot(context, "showcase_butterfly", () -> { });
            context.runOnClient(c -> com.adhdclient.butterfly.ButterflyClient.enabled = false);

            // Kiste mit Chest Stealer + Sortieren
            server.runCommand("setblock 0 -60 2 chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:20},{Slot:4b,id:\"minecraft:iron_ingot\",count:33},"
                    + "{Slot:9b,id:\"minecraft:golden_apple\",count:4},{Slot:13b,id:\"minecraft:diamond\",count:7},{Slot:20b,id:\"minecraft:ender_pearl\",count:16},"
                    + "{Slot:22b,id:\"minecraft:totem_of_undying\",count:1},{Slot:26b,id:\"minecraft:iron_ingot\",count:12}]}");
            context.runOnClient(c -> {
                x.chestSteal.enabled = true;
                x.sorter.enabled = true;
            });
            context.waitTicks(5);
            context.runOnClient(c -> c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND,
                    new BlockHitResult(new Vec3d(0.5, -59.5, 2.5), Direction.UP, new BlockPos(0, -60, 2), false)));
            context.waitTicks(10);
            shot(context, "showcase_chest", () -> { });
            context.runOnClient(c -> {
                c.player.closeHandledScreen();
                x.chestSteal.enabled = false;
                x.sorter.enabled = false;
            });
        }
    }

    /** Free-Cam-Kamera an eine feste Stelle setzen (nur für die Bilder). */
    private static void place(Vec3d pos, float yaw, float pitch) {
        try {
            Class<?> fc = com.adhdclient.freecam.FreeCamClient.class;
            for (String n : new String[]{"pos", "lastPos"}) {
                var f = fc.getDeclaredField(n);
                f.setAccessible(true);
                f.set(null, pos);
            }
            var y = fc.getDeclaredField("yaw");
            y.setAccessible(true);
            y.setFloat(null, yaw);
            var p = fc.getDeclaredField("pitch");
            p.setAccessible(true);
            p.setFloat(null, pitch);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void shot(ClientGameTestContext context, String name, Runnable setup) {
        setup.run();
        context.runOnClient(c -> {
            c.inGameHud.getChatHud().clear(false);
            c.getToastManager().clear();
        });
        context.waitTicks(3);
        context.takeScreenshot(name);
    }
}
