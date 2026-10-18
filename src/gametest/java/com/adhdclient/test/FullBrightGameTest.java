package com.adhdclient.test;

import com.adhdclient.fullbright.FullBrightClient;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Full Bright im Dunkeln: geschlossener Steinraum ohne Licht, nachts. Misst die mittlere Bildhelligkeit
 * mit Full Bright aus und an (und mit Dunkelheits-Effekt).
 */
final class FullBrightGameTest {

    private FullBrightGameTest() {
    }

    static void run(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            TestServerContext server = sp.getServer();
            sp.getClientWorld().waitForChunksRender();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set midnight");
            server.runCommand("gamerule doDaylightCycle false");
            // geschlossener Raum aus Stein, innen 7×4×7, mit ein paar Erzen an der Wand zum Erkennen
            server.runCommand("fill 20 -61 20 28 -55 28 minecraft:stone");
            server.runCommand("fill 21 -60 21 27 -57 27 minecraft:air");
            server.runCommand("setblock 27 -59 24 minecraft:diamond_ore");
            server.runCommand("setblock 27 -58 23 minecraft:gold_ore");
            server.runCommand("tp @a 22.5 -60 24.5 -90 0");
            context.waitTicks(40);
            sp.getClientWorld().waitForChunksRender();

            context.runOnClient(c -> FullBrightClient.enabled = false);
            context.waitTicks(10);
            double off = brightness(context.takeScreenshot("60_fullbright_aus"));
            context.runOnClient(c -> FullBrightClient.enabled = true);
            context.waitTicks(10);
            double on = brightness(context.takeScreenshot("61_fullbright_an"));
            server.runCommand("effect give @a minecraft:darkness 30 0");
            context.waitTicks(40);
            double darkness = brightness(context.takeScreenshot("62_fullbright_an_dunkelheit"));
            server.runCommand("effect clear @a");
            context.runOnClient(c -> FullBrightClient.enabled = false);
            // draußen um Mitternacht: mit Full Bright wie am Tag
            server.runCommand("fill 20 -61 20 28 -55 28 minecraft:air");
            server.runCommand("fill 18 -60 30 30 -58 30 minecraft:oak_log");
            server.runCommand("tp @a 24.5 -60 22.5 0 -10");
            context.waitTicks(30);
            double nightOff = brightness(context.takeScreenshot("63_nacht_draussen_aus"));
            context.runOnClient(c -> FullBrightClient.enabled = true);
            context.waitTicks(10);
            double nightOn = brightness(context.takeScreenshot("64_nacht_draussen_an"));
            long realTime = server.computeOnServer(s -> s.getOverworld().getTimeOfDay() % 24000L);
            context.runOnClient(c -> FullBrightClient.enabled = false);
            context.waitTicks(30);
            long clientAfter = context.computeOnClient(c -> c.world.getTimeOfDay() % 24000L);
            log(String.format(java.util.Locale.ROOT, "Nachts draußen: aus %.1f, an %.1f · Server-Zeit %d, Client-Zeit danach %d", nightOff, nightOn, realTime, clientAfter));
            check(nightOn > nightOff + 60, "Full Bright draußen nachts wie am Tag");
            check(realTime == 18000, "Server-Zeit bleibt Mitternacht");
            check(Math.abs(clientAfter - 18000) < 100, "Nach dem Ausschalten ist es beim Client wieder Nacht");
            log(String.format(java.util.Locale.ROOT, "Full Bright Helligkeit: aus %.1f, an %.1f, an + Dunkelheit %.1f", off, on, darkness));
            check(on > off + 40, "Full Bright macht den dunklen Raum deutlich heller");
            check(darkness > on * 0.7, "Dunkelheits-Effekt wird mit Full Bright ignoriert");
        }
        log("Full Bright: alle Prüfungen bestanden");
    }

    /** Mittlere Helligkeit (0–255) des mittleren Bildbereichs. */
    private static double brightness(Path file) {
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            int w = img.getWidth();
            int h = img.getHeight();
            long sum = 0;
            int n = 0;
            for (int y = h / 4; y < h * 3 / 4; y += 2) {
                for (int x = w / 4; x < w * 3 / 4; x += 2) {
                    int rgb = img.getRGB(x, y);
                    sum += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                    n += 3;
                }
            }
            return sum / (double) n;
        } catch (Exception e) {
            throw new AssertionError("Screenshot nicht lesbar: " + e);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
