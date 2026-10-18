package com.adhdclient.test;

import com.adhdclient.packettools.FabricateScreen;
import com.adhdclient.packettools.PacketToolsClient;
import com.adhdclient.packettools.PacketToolsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

/**
 * In-Game-Test für Packet Tools in einer echten Welt: Truhe öffnen, dann auf dem Server prüfen, ob Klicks verworfen,
 * verzögert oder nachgereicht werden, ob Desync / ohne Paket schließen wirken und ob gespeicherte Menüs wiederkommen.
 */
final class PacketToolsGameTest {

    private static final BlockPos CHEST = new BlockPos(2, -60, 0);

    private PacketToolsGameTest() {
    }

    /** Letzte Chat-Nachricht, die der Server bekommen hat. */
    private static final String[] LAST_CHAT = {""};
    private static boolean listening;

    static void run(ClientGameTestContext context) {
        if (!listening) {
            listening = true;
            net.fabricmc.fabric.api.message.v1.ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> LAST_CHAT[0] = message.getContent().getString());
        }
        context.runOnClient(c -> c.getLanguageManager().setLanguage("en_us"));
        try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
            TestServerContext server = sp.getServer();
            sp.getClientWorld().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("setblock 2 -60 0 minecraft:chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5},{Slot:1b,id:\"minecraft:emerald\",count:3}]}");
            server.runCommand("tp @a 0.5 -60 0.5 -90 30");
            context.waitTicks(10);
            context.runOnClient(c -> PacketToolsClient.enabled = true);

            // --- Leiste erscheint ---
            openChest(context);
            context.takeScreenshot("30_packettools_panel_en");
            double sc0 = context.computeOnClient(c -> c.getWindow().getScaleFactor());
            context.getInput().setCursorPos(60 * sc0, 30 * sc0);
            context.waitTicks(25);
            context.takeScreenshot("30b_packettools_tooltip");

            // --- Pakete senden aus: Klick kommt nicht an ---
            context.runOnClient(c -> PacketToolsClient.sendPackets = false);
            click(context, 0, SlotActionType.PICKUP);
            check(serverCount(server, 0) == 5, "Senden aus: Server hat den Klick nicht bekommen");
            context.runOnClient(c -> PacketToolsClient.sendPackets = true);
            closeNormally(context);

            // --- Verzögern: erst gesammelt, beim Ausschalten gesendet ---
            openChest(context);
            context.runOnClient(c -> PacketToolsClient.setDelay(true));
            click(context, 0, SlotActionType.QUICK_MOVE);
            int queued = context.computeOnClient(c -> PacketToolsClient.queued());
            int during = serverCount(server, 0);
            context.takeScreenshot("31_packettools_delay");
            context.runOnClient(c -> PacketToolsClient.setDelay(false));
            context.waitTicks(5);
            int after = serverCount(server, 0);
            log("Verzögern: Warteschlange " + queued + ", Server währenddessen " + during + ", danach " + after);
            check(queued == 1 && during == 5 && after == 0, "Verzögern sammelt und sendet beim Ausschalten");

            // --- Desync: Server schließt, Client bleibt offen ---
            context.runOnClient(c -> PacketToolsClient.desync(c));
            context.waitTicks(5);
            boolean serverClosed = server.computeOnServer(s -> s.getPlayerManager().getPlayerList().get(0).currentScreenHandler instanceof PlayerScreenHandler);
            boolean clientOpen = context.computeOnClient(c -> c.currentScreen instanceof HandledScreen<?>);
            log("Desync: Server zu " + serverClosed + ", Client offen " + clientOpen);
            check(serverClosed && clientOpen, "Desync schließt nur auf dem Server");
            closeNormally(context);

            // --- Ohne Paket schließen + Menü speichern / wiederherstellen ---
            openChest(context);
            context.runOnClient(c -> PacketToolsClient.saveGui(c, c.currentScreen));
            context.runOnClient(c -> PacketToolsClient.closeWithoutPacket(c));
            context.waitTicks(5);
            boolean serverStillOpen = server.computeOnServer(s -> !(s.getPlayerManager().getPlayerList().get(0).currentScreenHandler instanceof PlayerScreenHandler));
            boolean clientClosed = context.computeOnClient(c -> c.currentScreen == null);
            log("Ohne Paket: Client zu " + clientClosed + ", Server noch offen " + serverStillOpen);
            check(clientClosed && serverStillOpen, "Ohne Paket schließen");
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_V);
            context.waitTicks(5);
            check(context.computeOnClient(c -> c.currentScreen instanceof HandledScreen<?>), "V öffnet das gespeicherte Menü wieder");
            context.takeScreenshot("32_packettools_restored");

            // --- Paket bauen: Shift-Klick auf Slot 1 ---
            context.runOnClient(c -> c.setScreen(new FabricateScreen(c.currentScreen)));
            context.waitTicks(3);
            context.takeScreenshot("33_packettools_build_packet");
            context.clickScreenButton("Action: PICKUP");
            context.waitTicks(1);
            context.runOnClient(c -> {
                for (var e : c.currentScreen.children()) {
                    if (e instanceof net.minecraft.client.gui.widget.TextFieldWidget f && f.getMessage().getString().equals("Slot")) f.setText("1");
                }
            });
            context.clickScreenButton("Send");
            context.waitTicks(5);
            context.takeScreenshot("34_packettools_packet_sent");
            int emeralds = serverCount(server, 1);
            log("Gebautes QUICK_MOVE-Paket: Smaragde in der Truhe " + emeralds);
            check(emeralds == 0, "Gebautes Paket wirkt auf dem Server");
            context.clickScreenButton("Back");
            context.waitTicks(3);
            check(context.computeOnClient(c -> c.currentScreen instanceof HandledScreen<?>), "Zurück öffnet das Menü wieder");
            closeNormally(context);

            // --- Chat im Menü: tippen (auch "e"), Enter, Menü bleibt offen, Server bekommt die Nachricht ---
            openChest(context);
            double[] fieldPos = context.computeOnClient(c -> {
                for (var e : c.currentScreen.children()) {
                    if (e instanceof net.minecraft.client.gui.widget.TextFieldWidget f && f.getMessage().getString().equals("Chat")) {
                        double sc = c.getWindow().getScaleFactor();
                        return new double[]{(f.getX() + 10) * sc, (f.getY() + 8) * sc};
                    }
                }
                throw new AssertionError("FEHLER: Chat-Feld fehlt");
            });
            context.getInput().setCursorPos(fieldPos[0], fieldPos[1]);
            context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.getInput().typeChars("hallo welt e");
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_E);
            context.waitTicks(2);
            context.takeScreenshot("37_packettools_chat_typing");
            context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
            context.waitTicks(10);
            boolean stillOpen = context.computeOnClient(c -> c.currentScreen instanceof HandledScreen<?>);
            log("Chat im Menü: Server bekam \"" + LAST_CHAT[0] + "\", Menü offen " + stillOpen);
            check(stillOpen && "hallo welt e".equals(LAST_CHAT[0]), "Chat aus dem Menü senden");
            context.takeScreenshot("38_packettools_chat_sent");
            closeNormally(context);

            // --- Alles anhalten: Laufen und Chat kommen erst beim Loslassen an; Verwerfen schickt nichts ---
            context.runOnClient(c -> { PacketToolsClient.config().showLog = true; PacketToolsClient.setHoldAll(true); });
            net.minecraft.util.math.Vec3d before = serverPos(server);
            LAST_CHAT[0] = "";
            context.runOnClient(c -> c.getNetworkHandler().sendChatMessage("angehalten"));
            context.getInput().holdKeyFor(o -> o.backKey, 15);
            context.waitTicks(5);
            net.minecraft.util.math.Vec3d posDuring = serverPos(server);
            int heldNow = context.computeOnClient(c -> PacketToolsClient.heldCount());
            String chatDuring = LAST_CHAT[0];
            context.takeScreenshot("39_packettools_hold_log");
            context.runOnClient(c -> PacketToolsClient.setHoldAll(false));
            context.waitTicks(10);
            net.minecraft.util.math.Vec3d posAfter = serverPos(server);
            log("Alles anhalten: " + heldNow + " Pakete, Server-Pos vorher " + before + " währenddessen " + posDuring + " danach " + posAfter
                    + ", Chat währenddessen \"" + chatDuring + "\" danach \"" + LAST_CHAT[0] + "\"");
            check(heldNow > 0 && posDuring.distanceTo(before) < 0.01 && chatDuring.isEmpty(), "Alles anhalten: Server sieht nichts");
            check(posAfter.distanceTo(before) > 1 && "angehalten".equals(LAST_CHAT[0]), "Loslassen sendet alles");
            LAST_CHAT[0] = "";
            context.runOnClient(c -> PacketToolsClient.setHoldAll(true));
            context.runOnClient(c -> c.getNetworkHandler().sendChatMessage("verworfen"));
            context.waitTicks(3);
            context.runOnClient(c -> PacketToolsClient.discardHeld());
            context.waitTicks(10);
            check(LAST_CHAT[0].isEmpty() && !context.computeOnClient(c -> PacketToolsClient.holdAll), "Verwerfen schickt nichts");
            context.runOnClient(c -> PacketToolsClient.config().showLog = false);
            server.runCommand("tp @a 0.5 -60 0.5 -90 30");
            context.waitTicks(10);
            context.setScreen(() -> new PacketToolsScreen(null));
            context.waitTicks(3);
            context.takeScreenshot("40_packettools_settings_en");
            context.setScreen(() -> null);
            context.waitTicks(3);

            // --- Modul aus: keine Leiste, V tut nichts ---
            context.runOnClient(c -> PacketToolsClient.enabled = false);
            openChest(context);
            boolean noPanel = context.computeOnClient(c -> c.currentScreen.children().stream().noneMatch(e ->
                    e instanceof net.minecraft.client.gui.widget.ButtonWidget b && b.getMessage().getString().contains("Close without packet")));
            check(noPanel, "Modul aus: keine Leiste");
            closeNormally(context);

            context.runOnClient(c -> c.getLanguageManager().setLanguage("de_de"));
            context.runOnClient(c -> PacketToolsClient.enabled = true);
            openChest(context);
            context.takeScreenshot("35_packettools_panel_de");
            closeNormally(context);
            context.setScreen(() -> new PacketToolsScreen(null));
            context.waitTicks(3);
            context.takeScreenshot("36_packettools_settings_de");
            context.setScreen(() -> null);
            context.runOnClient(c -> c.getLanguageManager().setLanguage("en_us"));
            context.runOnClient(c -> PacketToolsClient.enabled = false);
        }
        log("Packet Tools: alle Prüfungen bestanden");
    }

    private static void openChest(ClientGameTestContext context) {
        context.getInput().pressKey(o -> o.useKey);
        context.waitFor(c -> c.currentScreen instanceof HandledScreen<?>, 100);
        context.waitTicks(3);
    }

    private static void closeNormally(ClientGameTestContext context) {
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(c -> c.currentScreen == null, 100);
        context.waitTicks(2);
    }

    private static void click(ClientGameTestContext context, int slot, SlotActionType type) {
        context.runOnClient(c -> c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId, slot, 0, type, c.player));
        context.waitTicks(5);
    }

    private static net.minecraft.util.math.Vec3d serverPos(TestServerContext server) {
        return server.computeOnServer(s -> s.getPlayerManager().getPlayerList().get(0).getEntityPos());
    }

    private static int serverCount(TestServerContext server, int slot) {
        return server.computeOnServer(s -> {
            var be = s.getOverworld().getBlockEntity(CHEST);
            return be instanceof ChestBlockEntity chest ? chest.getStack(slot).getCount() : -1;
        });
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
