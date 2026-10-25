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
            context.runOnClient(c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));

            // Eigenes Modell: Vorlage als .bbmodel anlegen, auswählen, laden und Inspect daraus abspielen
            java.nio.file.Path models = context.computeOnClient(c -> com.adhdclient.butterfly.ButterflyCustomModel.folder());
            context.runOnClient(c -> {
                try {
                    com.adhdclient.butterfly.ButterflyBlockbench.write(models.resolve("Butterfly-Vorlage.bbmodel"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                ButterflyClient.config().model = "Butterfly-Vorlage";
            });
            String st = context.computeOnClient(c -> com.adhdclient.butterfly.ButterflyCustomModel.status());
            log("Eigenes Modell: " + st);
            check(st.contains("loaded") && st.contains("inspect") && st.contains("draw"), "Vorlage wird als eigenes Modell mit Animationen geladen");
            context.waitTicks(10);
            context.getInput().pressKey(GLFW.GLFW_KEY_R);
            for (int i = 0; i < 8; i++) {
                context.waitTicks(6);
                context.takeScreenshot("78_eigenes_modell_inspect_" + (char) ('a' + i));
            }
            context.waitTicks(30);

            // glTF-Umwandlung: kleiner Würfel als .gltf, umwandeln, als Modell laden
            context.runOnClient(c -> {
                try {
                    java.nio.file.Files.writeString(models.resolve("Testwuerfel.gltf"), cubeGltf());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            java.util.List<String> made = context.computeOnClient(c -> com.adhdclient.butterfly.ButterflyGltfImport.convertAll());
            log("glTF umgewandelt: " + made);
            check(made.contains("Testwuerfel"), "glTF wird in .bbmodel umgewandelt");
            context.runOnClient(c -> ButterflyClient.config().model = "Testwuerfel");
            String st2 = context.computeOnClient(c -> com.adhdclient.butterfly.ButterflyCustomModel.status());
            log("Würfel: " + st2);
            check(st2.contains("loaded"), "umgewandeltes Modell wird geladen");
            context.waitTicks(5);
            context.takeScreenshot("78_testwuerfel");

            context.runOnClient(c -> c.setScreen(new com.adhdclient.butterfly.ButterflyScreen(null)));
            context.waitTicks(5);
            context.takeScreenshot("79_butterfly_menue");
            context.runOnClient(c -> {
                c.setScreen(null);
                ButterflyClient.config().model = "";
                ButterflyClient.enabled = false;
                for (String f : new String[]{"Butterfly-Vorlage.bbmodel", "Testwuerfel.gltf", "Testwuerfel.bbmodel"}) {
                    try {
                        java.nio.file.Files.deleteIfExists(models.resolve(f));
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        log("Butterfly: alle Prüfungen bestanden");
    }

    /** Ein Würfel (2 × 2 × 2) als glTF mit eingebetteten Daten und rotem Material. */
    private static String cubeGltf() {
        float[] p = {-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1};
        short[] idx = {0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 3, 6, 2, 3, 7, 6, 0, 4, 7, 0, 7, 3, 1, 2, 6, 1, 6, 5};
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(p.length * 4 + idx.length * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (float f : p) b.putFloat(f);
        for (short s : idx) b.putShort(s);
        String data = java.util.Base64.getEncoder().encodeToString(b.array());
        return "{\"asset\":{\"version\":\"2.0\"},\"scene\":0,\"scenes\":[{\"nodes\":[0]}],\"nodes\":[{\"name\":\"Wuerfel\",\"mesh\":0}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"indices\":1,\"material\":0}]}],"
                + "\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorFactor\":[0.9,0.1,0.1,1]}}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":8,\"type\":\"VEC3\",\"min\":[-1,-1,-1],\"max\":[1,1,1]},"
                + "{\"bufferView\":1,\"componentType\":5123,\"count\":36,\"type\":\"SCALAR\"}],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":96},{\"buffer\":0,\"byteOffset\":96,\"byteLength\":72}],"
                + "\"buffers\":[{\"byteLength\":168,\"uri\":\"data:application/octet-stream;base64," + data + "\"}]}";
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
