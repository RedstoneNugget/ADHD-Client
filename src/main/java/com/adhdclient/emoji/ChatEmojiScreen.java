package com.adhdclient.emoji;

import com.adhdclient.core.AdhdModuleState;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Chat-Emojis: Option „beim Senden“ und eine Liste aller Kürzel. */
public final class ChatEmojiScreen extends ThemedScreen {

    private static final int W = 320;
    private static final int ROW = 20;
    private static final int COLS = 3;

    public ChatEmojiScreen(Screen parent) {
        super(Text.literal(L.t("Chat-Emojis", "Chat emojis")), parent);
    }

    @Override
    protected void build() {
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, String> e : ChatEmojiClient.EMOJIS.entrySet()) {
            if (e.getValue().length() <= 2) items.add(e.getKey() + " " + e.getValue());
        }
        int rows = (items.size() + COLS - 1) / COLS;
        int h = Theme.TITLE_H + 6 + ROW + 6 + 12 + rows * 10 + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        toggle(L.t("Auch beim Senden umwandeln", "Also convert when sending"), () -> ChatEmojiClient.onSend, v -> ChatEmojiClient.onSend = v, x, y, inner, ROW - 2,
                L.t("Aus = nur du siehst die Symbole; an = deine Nachrichten werden mit Symbolen gesendet", "Off = only you see the symbols; on = your messages are sent with symbols"), AdhdModuleState::save);
        y += ROW + 6;
        section(L.t("Kürzel", "Shortcodes"), x, y, inner);
        y += 12;
        int colW = inner / COLS;
        for (int i = 0; i < items.size(); i++) line(items.get(i), x + (i % COLS) * colW, y + (i / COLS) * 10, Theme.text());
        y += rows * 10 + 6;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(ChatEmojiClient.enabled ? L.t("Dazu noch :shrug: :tableflip: :lenny:", "Also :shrug: :tableflip: :lenny:")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        AdhdModuleState.save();
    }

    @Override
    protected Runnable resetAction() {
        return () -> {
            ChatEmojiClient.onSend = false;
            AdhdModuleState.save();
        };
    }
}
