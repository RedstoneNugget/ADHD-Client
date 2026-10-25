package com.adhdclient.emoji;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Chat-Emojis: „:skull:“, „:heart:“ … werden im Chat zu Symbolen, die die Minecraft-Schrift darstellen kann.
 * Standard nur bei dir (Anzeige); optional auch beim Senden.
 */
public final class ChatEmojiClient {

    public static boolean enabled = false;
    /** Auch eigene Nachrichten vor dem Senden umwandeln (dann sehen es alle). */
    public static boolean onSend = false;

    public static final Map<String, String> EMOJIS = new LinkedHashMap<>();

    static {
        e("skull", "☠");
        e("heart", "❤");
        e("star", "★");
        e("smile", "☺");
        e("sad", "☹");
        e("check", "✔");
        e("x", "✘");
        e("fire", "♨");
        e("zap", "⚡");
        e("sun", "☀");
        e("cloud", "☁");
        e("snow", "❄");
        e("music", "♪");
        e("sword", "⚔");
        e("pick", "⛏");
        e("warn", "⚠");
        e("peace", "☮");
        e("crown", "♛");
        e("flower", "✿");
        e("arrow", "➜");
        e("up", "▲");
        e("down", "▼");
        e("shrug", "¯\\_(ツ)_/¯");
        e("tableflip", "(╯°□°)╯︵ ┻━┻");
        e("lenny", "( ͡° ͜ʖ ͡°)");
    }

    private static void e(String name, String symbol) {
        EMOJIS.put(":" + name + ":", symbol);
    }

    private ChatEmojiClient() {
    }

    /** Alle :namen: in einem Text ersetzen. */
    public static String replace(String s) {
        if (s.indexOf(':') < 0) return s;
        for (Map.Entry<String, String> e : EMOJIS.entrySet()) s = s.replace(e.getKey(), e.getValue());
        return s;
    }

    /** Chat-Text mit allen Farben und Stilen behalten, nur die Emoji-Namen ersetzen. */
    public static Text replace(Text text) {
        if (!text.getString().contains(":")) return text;
        MutableText out = Text.empty();
        text.visit((Style style, String part) -> {
            out.append(Text.literal(replace(part)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }
}
