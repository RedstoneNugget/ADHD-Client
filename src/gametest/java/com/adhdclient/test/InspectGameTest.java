package com.adhdclient.test;

import com.adhdclient.inspect.InspectClient;
import com.adhdclient.inspect.config.InspectConfig;
import com.adhdclient.inspect.config.InspectSettingsScreen;
import com.adhdclient.inspect.ui.InspectScreen;
import com.adhdclient.inspect.world.WorldMarkers;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * In-Game-Test für das Inspect-All-Modul (Szenen wie in der eigenständigen Mod): Bienennest, Truhe, Spawner,
 * Schild, Zombie, Schwert in der Hand und im Inventar – auf Englisch und Deutsch. Prüft außerdem, dass die Tasten
 * nur bei eingeschaltetem Modul etwas tun.
 */
final class InspectGameTest {

    private static final String BEE_A = "{entity_data:{id:\"minecraft:bee\",flower_pos:[I;20,-60,15],HasNectar:1b,Health:7.5f},ticks_in_hive:120,min_ticks_in_hive:600}";
    private static final String BEE_B = "{entity_data:{id:\"minecraft:bee\",flower_pos:[I;20,-60,15],HasNectar:0b},ticks_in_hive:5,min_ticks_in_hive:2400}";
    private static final String BEE_C = "{entity_data:{id:\"minecraft:bee\",FlowerPos:{X:-12,Y:-60,Z:8}},ticks_in_hive:0,min_ticks_in_hive:600}";
    private static final String NEST = "setblock 4 -60 0 minecraft:bee_nest[honey_level=3]{bees:[" + BEE_A + "," + BEE_B + "," + BEE_C + "]}";
    private static final String CHEST = "setblock 4 -60 4 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:12},"
            + "{Slot:5b,id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:enchantments\":{\"minecraft:sharpness\":5}}}]}";
    private static final String SPAWNER = "setblock 4 -60 8 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}},Delay:120s,SpawnCount:4s,RequiredPlayerRange:0s}";
    private static final String SIGN = "setblock 4 -60 12 minecraft:oak_sign[rotation=4]{front_text:{messages:[\"Zeile eins\",\"Zeile zwei\",\"\",\"\"],color:\"blue\",has_glowing_text:1b}}";
    private static final String ZOMBIE = "summon minecraft:zombie 0.5 -60 -6.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,CustomName:\"Testzombie\","
            + "Tags:[\"inspect_test\"],active_effects:[{id:\"minecraft:speed\",amplifier:1b,duration:6000}],"
            + "equipment:{head:{id:\"minecraft:iron_helmet\",count:1}},Rotation:[0f,0f]}";
    private static final String SWORD = "give @a minecraft:diamond_sword[minecraft:enchantments={\"minecraft:sharpness\":5,\"minecraft:unbreaking\":3},minecraft:custom_name=\"Testklinge\",minecraft:damage=17]";

    private InspectGameTest() {
    }

    static void run(ClientGameTestContext context) {
        context.runOnClient(c -> c.getLanguageManager().setLanguage("en_us"));
        try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
            TestServerContext server = sp.getServer();
            sp.getClientWorld().waitForChunksRender();
            buildScene(server);
            context.waitTicks(10);
            sp.getClientWorld().waitForChunksRender();

            // --- Modul aus: Taste tut nichts ---
            context.runOnClient(c -> InspectClient.enabled = false);
            lookAt(context, server, 0.5, -60, 0.5, 4.5, -59.5, 0.5);
            context.getInput().pressKey(GLFW.GLFW_KEY_I);
            context.waitTicks(5);
            check(context.computeOnClient(c -> c.currentScreen == null), "Modul aus: I öffnet nichts");
            context.runOnClient(c -> InspectClient.enabled = true);

            // --- Bienennest (Englisch) ---
            List<String> rows = openPanel(context, "Bee nest EN");
            expect(rows, "Bees: 3 / 3");
            expect(rows, "Honey: 3 / 5");
            expect(rows, "flower_pos: 20, -60, 15");
            expect(rows, "HasNectar: yes");
            expect(rows, "Integrated server");
            context.takeScreenshot("20_inspect_bee_nest_en");
            check(context.computeOnClient(c -> ((InspectScreen) c.currentScreen).markFirstCoord("20, -60, 15")), "Koordinate markieren");
            check(context.computeOnClient(c -> WorldMarkers.count()) == 1, "1 Markierung");
            closePanel(context);
            lookAt(context, server, -2, -60, -4, 20, -60, 15);
            context.waitTicks(5);
            context.takeScreenshot("21_inspect_marker");
            // Knopf im Panel löscht alle Markierungen
            lookAt(context, server, 0.5, -60, 0.5, 4.5, -59.5, 0.5);
            openPanel(context, "Bee nest again");
            context.clickScreenButton("Clear marks");
            context.waitTicks(2);
            check(context.computeOnClient(c -> WorldMarkers.count()) == 0, "Knopf \"Clear marks\" löscht alle Markierungen");
            context.takeScreenshot("21b_inspect_clear_marks");
            closePanel(context);

            // --- Truhe ---
            lookAt(context, server, 0.5, -60, 4.5, 4.5, -59.5, 4.5);
            rows = openPanel(context, "Chest EN");
            expect(rows, "Used slots: 2 / 27");
            expect(rows, "Slot 0: 12 ×");
            expect(rows, "(minecraft:diamond_sword) ✦");
            context.runOnClient(c -> ((InspectScreen) c.currentScreen).setQueryForTest("sharp"));
            context.waitTicks(2);
            context.takeScreenshot("22_inspect_chest_search");
            closePanel(context);

            // --- Spawner ---
            lookAt(context, server, 0.5, -60, 8.5, 4.5, -59.5, 8.5);
            rows = openPanel(context, "Spawner EN");
            expect(rows, "Entity: minecraft:zombie");
            expect(rows, "Next spawn in: 120 ticks");
            expect(rows, "Count per spawn: 4");
            closePanel(context);

            // --- Schild ---
            lookAt(context, server, 0.5, -60, 12.5, 4.5, -59.3, 12.5);
            rows = openPanel(context, "Sign EN");
            expect(rows, "Line 1: Zeile eins");
            expect(rows, "Color: blue");
            expect(rows, "Glowing ink: yes");
            closePanel(context);

            // --- Zombie + Sperren (K) ---
            lookAt(context, server, 0.5, -60, -2.5, 0.5, -59, -6.5);
            rows = openPanel(context, "Zombie EN");
            expect(rows, "Type: minecraft:zombie");
            expect(rows, "Health: 20 / 20");
            expect(rows, "Head: 1 ×");
            expect(rows, "minecraft:speed");
            context.takeScreenshot("23_inspect_zombie_en");
            closePanel(context);
            context.getInput().pressKey(GLFW.GLFW_KEY_K);
            context.waitTicks(25);
            check(context.computeOnClient(c -> InspectClient.hasLock()), "K sperrt das Ziel");
            lookAt(context, server, 0.5, -60, -2.5, 10, -60, -2.5);
            context.waitTicks(25);
            context.takeScreenshot("24_inspect_locked_hud");
            context.getInput().pressKey(GLFW.GLFW_KEY_K);
            context.waitTicks(2);
            check(!context.computeOnClient(c -> InspectClient.hasLock()), "K entsperrt");

            // --- Schwert in der Hand ---
            server.runCommand("clear @a");
            server.runCommand(SWORD);
            look(context, server, "tp @a 0.5 -60 0.5 0 -90");
            rows = openPanel(context, "Sword EN");
            expect(rows, "Custom name (custom_name): Testklinge");
            expect(rows, "Durability: 1544 / 1561");
            expect(rows, "minecraft:sharpness – level 5");
            context.clickScreenButton("Copy as /give");
            context.waitTicks(2);
            String clip = context.computeOnClient(c -> c.keyboard.getClipboard());
            check(clip.startsWith("/give @s minecraft:diamond_sword["), "/give in Zwischenablage");
            context.takeScreenshot("25_inspect_sword_en");
            closePanel(context);

            // --- Item im Inventar, Panel per Taste im Inventar ---
            server.runCommand("gamemode survival @a");
            context.waitTicks(5);
            context.getInput().pressKey(o -> o.inventoryKey);
            context.waitForScreen(InventoryScreen.class);
            hoverHotbarSlot0(context);
            context.waitTicks(3);
            context.takeScreenshot("26_inspect_inventory_tooltip");
            context.getInput().pressKey(GLFW.GLFW_KEY_I);
            context.waitForScreen(InspectScreen.class);
            rows = waitRows(context);
            expect(rows, "Origin: Slot");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(InventoryScreen.class);
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitFor(c -> c.currentScreen == null);
            server.runCommand("gamemode creative @a");

            // --- Deutsch ---
            context.runOnClient(c -> c.getLanguageManager().setLanguage("de_de"));
            lookAt(context, server, 0.5, -60, 0.5, 4.5, -59.5, 0.5);
            rows = openPanel(context, "Bienennest DE");
            expect(rows, "HasNectar: ja");
            expect(rows, "Integrierter Server");
            context.takeScreenshot("27_inspect_bee_nest_de");
            closePanel(context);
            lookAt(context, server, 0.5, -60, 4.5, 4.5, -59.5, 4.5);
            rows = openPanel(context, "Truhe DE");
            expect(rows, "Belegte Slots: 2 / 27");
            closePanel(context);

            // --- Einstellungen (alle drei Reiter, beide Sprachen) ---
            context.setScreen(() -> new InspectSettingsScreen(null));
            context.waitTicks(3);
            context.takeScreenshot("28_inspect_settings_de");
            context.runOnClient(c -> c.getLanguageManager().setLanguage("en_us"));
            for (String tab : List.of("Categories", "Colors", "General")) {
                context.setScreen(() -> new InspectSettingsScreen(null));
                context.waitTicks(3);
                context.clickScreenButton(tab);
                context.waitTicks(3);
                context.takeScreenshot("29_inspect_settings_" + tab.toLowerCase(java.util.Locale.ROOT));
            }
            // Markierungsfarbe per Palette: Feld 2 = Rot
            context.setScreen(() -> new InspectSettingsScreen(null));
            context.waitTicks(3);
            context.clickScreenButton("Colors");
            context.waitTicks(3);
            double[] sw = context.computeOnClient(c -> {
                int[] p = ((InspectSettingsScreen) c.currentScreen).markerSwatchCenter(1);
                double s = c.getWindow().getScaleFactor();
                return new double[]{p[0] * s, p[1] * s};
            });
            context.getInput().setCursorPos(sw[0], sw[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            String markerColor = context.computeOnClient(c -> InspectConfig.get().colorMarker);
            context.takeScreenshot("29b_inspect_marker_palette");
            log("Markierungsfarbe nach Klick: " + markerColor);
            check("#FF3B3B".equalsIgnoreCase(markerColor), "Palette setzt die Markierungsfarbe");
            context.setScreen(() -> null);
            context.waitTicks(3);
            context.runOnClient(c -> InspectConfig.get().colorMarker = "#FFC21A");
            context.setScreen(() -> null);
            context.waitTicks(3);
            context.runOnClient(c -> InspectClient.enabled = false);
        }
        log("Inspect All: alle Prüfungen bestanden");
    }

    private static void buildScene(TestServerContext server) {
        server.runCommand("time set noon");
        server.runCommand("gamerule doDaylightCycle false");
        server.runCommand("gamemode creative @a");
        server.runCommand(NEST);
        server.runCommand(CHEST);
        server.runCommand(SPAWNER);
        server.runCommand(SIGN);
        server.runCommand("setblock 20 -60 15 minecraft:poppy");
        server.runCommand("setblock -12 -60 8 minecraft:dandelion");
        server.runCommand("fill 10 -60 -5 10 -52 25 minecraft:stone");
        server.runCommand("kill @e[type=minecraft:zombie]");
        server.runCommand(ZOMBIE);
    }

    /** Teleportiert den Spieler und richtet die Augen exakt auf den Zielpunkt aus. */
    private static void lookAt(ClientGameTestContext context, TestServerContext server,
                               double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - (y + 1.62);
        double dz = tz - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        look(context, server, String.format(java.util.Locale.ROOT, "tp @a %s %s %s %.2f %.2f", x, y, z, yaw, pitch));
    }

    private static void look(ClientGameTestContext context, TestServerContext server, String tp) {
        server.runCommand(tp);
        context.waitTicks(8);
    }

    private static List<String> openPanel(ClientGameTestContext context, String title) {
        context.getInput().pressKey(GLFW.GLFW_KEY_I);
        context.waitForScreen(InspectScreen.class);
        List<String> rows = waitRows(context);
        dump(title, rows);
        return rows;
    }

    private static List<String> waitRows(ClientGameTestContext context) {
        context.waitFor(c -> c.currentScreen instanceof InspectScreen s && !s.getData().isPending(), 200);
        context.waitTicks(3);
        return context.computeOnClient(c -> ((InspectScreen) c.currentScreen).getRowStrings());
    }

    private static void closePanel(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(c -> c.currentScreen == null);
    }

    private static void hoverHotbarSlot0(ClientGameTestContext context) {
        double[] pos = context.computeOnClient(c -> {
            double scale = c.getWindow().getScaleFactor();
            int left = (c.getWindow().getScaledWidth() - 176) / 2;
            int top = (c.getWindow().getScaledHeight() - 166) / 2;
            return new double[]{(left + 8 + 8) * scale, (top + 142 + 8) * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
    }

    private static void expect(List<String> rows, String needle) {
        check(rows.stream().anyMatch(r -> r.contains(needle)), "enthält \"" + needle + "\"");
    }

    private static void dump(String title, List<String> rows) {
        log("---- Panel " + title + " (" + rows.size() + " Zeilen) ----");
        int n = 0;
        for (String row : rows) {
            log("   | " + row);
            if (++n >= 60) {
                log("   | …");
                break;
            }
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
