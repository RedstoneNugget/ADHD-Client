package com.adhdclient.test;

import com.adhdclient.emoji.ChatEmojiClient;
import com.adhdclient.intro.IntroClient;
import com.adhdclient.totemview.TotemViewClient;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.text.Text;

/** Client-Intro im Hauptmenü, Chat-Emojis im Chat, Totem-Hinweis lässt den eigenen Namen in Ruhe. */
final class FunGameTest {

    private FunGameTest() {
    }

    static void run(ClientGameTestContext context) {
        context.runOnClient(c -> {
            IntroClient.enabled = true;
            IntroClient.replay();
            c.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
        });
        context.waitTicks(16);
        context.takeScreenshot("92_intro");
        check(context.computeOnClient(c -> IntroClient.running()), "Intro läuft im Hauptmenü");
        context.getInput().setCursorPos(10, 10);
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        check(context.computeOnClient(c -> !IntroClient.running()), "Klick überspringt das Intro");

        check("☠ und ❤".equals(ChatEmojiClient.replace(":skull: und :heart:")), "Kürzel werden ersetzt");
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            context.runOnClient(c -> {
                ChatEmojiClient.enabled = true;
                c.inGameHud.getChatHud().addMessage(Text.literal("Gut gemacht :star: :skull: :heart:"));
                TotemViewClient.enabled = true;
            });
            context.waitTicks(3);
            context.takeScreenshot("93_chat_emojis");
            boolean own = context.computeOnClient(c -> TotemViewClient.decorate(c.player, Text.literal("Ich")).getString().equals("Ich"));
            check(own, "Eigener Name bekommt keinen Totem-Hinweis");
            context.runOnClient(c -> {
                ChatEmojiClient.enabled = false;
                TotemViewClient.enabled = false;
            });
        }
        log("Intro, Emojis, Totems: alle Prüfungen bestanden");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("FEHLER: " + message);
    }

    private static void log(String s) {
        System.out.println("[ADHD-TEST] " + s);
    }
}
