package com.adhdclient.test;

import com.adhdclient.airjump.AirJumpClient;
import com.adhdclient.autototem.AutoTotemClient;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

/** Auto Totem legt ein Hotbar-Totem in die Offhand (auch auf dem Server); Air Jump springt in der Luft erneut. */
final class TotemJumpGameTest {

    private TotemJumpGameTest() {
    }

    static void run(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            sp.getClientWorld().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.4 with minecraft:totem_of_undying");
            context.waitTicks(10);
            context.runOnClient(c -> {
                c.player.getInventory().setSelectedSlot(0);
                AutoTotemClient.swaps = 0;
                AutoTotemClient.delay = 2;
                AutoTotemClient.enabled = true;
            });
            int maxSel = 0;
            for (int i = 0; i < 40; i++) {
                context.waitTick();
                maxSel = Math.max(maxSel, context.computeOnClient(c -> c.player.getInventory().getSelectedSlot()));
                if (i == 5) context.takeScreenshot("89_auto_totem_unterwegs");
            }
            boolean client = context.computeOnClient(c -> c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
            boolean server2 = server.computeOnServer(s -> s.getPlayerManager().getPlayerList().get(0).getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
            int back = context.computeOnClient(c -> c.player.getInventory().getSelectedSlot());
            log("Totem in der Offhand – Client " + client + ", Server " + server2 + "; weitester Slot " + maxSel + ", zurück auf " + back);
            check(client && server2, "Auto Totem legt das Totem in die Offhand");
            check(maxSel == 4 && back == 0, "Auswahl läuft durch die Hotbar zum Totem und zurück");
            context.runOnClient(c -> AutoTotemClient.enabled = false);

            // Anti Crystal Death: zwei Totems im Inventar, Kristall neben dir → Totems in beide Hände
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a inventory.0 with minecraft:totem_of_undying");
            server.runCommand("item replace entity @a inventory.1 with minecraft:totem_of_undying");
            server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
            context.waitTicks(5);
            context.runOnClient(c -> {
                c.player.getInventory().setSelectedSlot(0);
                com.adhdclient.anticrystal.AntiCrystalClient.enabled = true;
            });
            server.runCommand("execute at @p run summon end_crystal ~2 ~ ~ {ShowBottom:0b}");
            context.waitTicks(3);
            context.takeScreenshot("90_anti_crystal_inventar");
            context.waitTicks(15);
            boolean both = context.computeOnClient(c -> c.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) && c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
            boolean closed = context.computeOnClient(c -> c.currentScreen == null);
            log("Anti Crystal: Totems in beiden Händen " + both + ", Inventar wieder zu " + closed);
            check(both && closed, "Anti Crystal Death legt Totems in beide Hände");
            context.runOnClient(c -> com.adhdclient.anticrystal.AntiCrystalClient.enabled = false);
            server.runCommand("kill @e[type=end_crystal]");

            // Tempo „sofort“: beides in höchstens 2 Ticks
            server.runCommand("clear @a");
            server.runCommand("item replace entity @a hotbar.0 with minecraft:diamond_sword");
            server.runCommand("item replace entity @a hotbar.6 with minecraft:totem_of_undying");
            context.waitTicks(5);
            context.runOnClient(c -> {
                c.player.getInventory().setSelectedSlot(0);
                AutoTotemClient.delay = 0;
                AutoTotemClient.enabled = true;
            });
            context.waitTicks(2);
            boolean fastTotem = server.computeOnServer(s -> s.getPlayerManager().getPlayerList().get(0).getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
            int fastBack = context.computeOnClient(c -> c.player.getInventory().getSelectedSlot());
            context.runOnClient(c -> AutoTotemClient.enabled = false);
            server.runCommand("item replace entity @a inventory.3 with minecraft:totem_of_undying");
            server.runCommand("item replace entity @a inventory.4 with minecraft:totem_of_undying");
            server.runCommand("item replace entity @a weapon.offhand with air");
            context.waitTicks(5);
            context.runOnClient(c -> {
                com.adhdclient.anticrystal.AntiCrystalClient.delay = 0;
                com.adhdclient.anticrystal.AntiCrystalClient.enabled = true;
            });
            server.runCommand("execute at @p run summon end_crystal ~2 ~ ~ {ShowBottom:0b}");
            context.waitTicks(2);
            boolean fastBoth = context.computeOnClient(c -> c.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) && c.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING));
            log("Sofort: Auto Totem Offhand " + fastTotem + " (zurück auf " + fastBack + "), Anti Crystal beide Hände " + fastBoth);
            check(fastTotem && fastBack == 0 && fastBoth, "Tempo sofort funktioniert bei beiden");
            context.runOnClient(c -> {
                com.adhdclient.anticrystal.AntiCrystalClient.enabled = false;
                com.adhdclient.anticrystal.AntiCrystalClient.delay = 1;
                AutoTotemClient.delay = 2;
            });
            server.runCommand("kill @e[type=end_crystal]");

            // Air Jump: springen, oben nochmal springen → höher als ein normaler Sprung (≈ 1,25)
            context.runOnClient(c -> {
                AirJumpClient.enabled = true;
                AirJumpClient.maxJumps = 1;
            });
            double y0 = context.computeOnClient(c -> c.player.getY());
            context.getInput().holdKeyFor(GLFW.GLFW_KEY_SPACE, 2);
            context.waitTicks(5);
            context.getInput().holdKeyFor(GLFW.GLFW_KEY_SPACE, 2);
            double top = y0;
            for (int i = 0; i < 20; i++) {
                context.waitTick();
                top = Math.max(top, context.computeOnClient(c -> c.player.getY()));
            }
            log(String.format("Höchster Punkt über Boden: %.2f", top - y0));
            check(top - y0 > 1.6, "Air Jump springt in der Luft erneut");
            context.runOnClient(c -> AirJumpClient.enabled = false);

            // Umschalt-Tasten: J schaltet Auto Totem an, U Anti Crystal Death
            context.runOnClient(c -> {
                AutoTotemClient.enabled = false;
                AutoTotemClient.toggleKey = GLFW.GLFW_KEY_J;
                com.adhdclient.anticrystal.AntiCrystalClient.enabled = false;
                com.adhdclient.anticrystal.AntiCrystalClient.toggleKey = GLFW.GLFW_KEY_U;
            });
            context.waitTicks(3);
            context.getInput().pressKey(GLFW.GLFW_KEY_J);
            context.getInput().pressKey(GLFW.GLFW_KEY_U);
            context.waitTicks(3);
            boolean on = context.computeOnClient(c -> AutoTotemClient.enabled && com.adhdclient.anticrystal.AntiCrystalClient.enabled);
            context.getInput().pressKey(GLFW.GLFW_KEY_J);
            context.waitTicks(3);
            boolean offAgain = context.computeOnClient(c -> !AutoTotemClient.enabled);
            log("Tasten schalten an: " + on + ", wieder aus: " + offAgain);
            check(on && offAgain, "Hotkeys schalten Auto Totem und Anti Crystal Death um");
            context.runOnClient(c -> {
                AutoTotemClient.toggleKey = -1;
                com.adhdclient.anticrystal.AntiCrystalClient.toggleKey = -1;
                com.adhdclient.anticrystal.AntiCrystalClient.enabled = false;
            });
        }
        log("Auto Totem und Air Jump: alle Prüfungen bestanden");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
