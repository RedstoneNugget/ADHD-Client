package com.adhdclient.rtpq;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Einstellungen von RTPQ Invsee – nur normale Texte, keine Regex. */
public final class RtpqScreen extends ThemedScreen {

    private static final int W = 330;
    private static final int ROW = 20;
    private static final int LABEL_W = 110;

    private int x;
    private int inner;
    private int colW;
    private int y;
    private int col;

    public RtpqScreen(Screen parent) {
        super(Text.literal("RTPQ Invsee"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + 3 * (ROW + 11) + 6 + 12 + 2 * ROW + 6 + 12 + 2 * ROW + 6 + ROW + 16;
        y = centeredPanel(W, h);
        x = panelX1 + 8;
        inner = W - 16;
        colW = (inner - 4) / 2;
        col = 0;
        RtpqConfig c = RtpqInvseeClient.config();

        section(L.t("Erkennung", "Detection"), x, y, inner);
        y += 12;
        field(L.t("Server", "Server"), c.serverFilter, s -> c.serverFilter = s,
                L.t("z. B. donutsmp – leer = alle Server", "e.g. donutsmp – empty = every server"),
                L.t("Nur auf Servern, deren Adresse diesen Text enthält.", "Only on servers whose address contains this text."));
        field(L.t("Text vor Gegner", "Text before name"), c.opponentText, s -> c.opponentText = s,
                L.t("z. B. Gegner gefunden:", "e.g. Opponent found:"),
                L.t("Der Text in der Chat-Nachricht direkt vor dem Namen des Gegners. Groß-/Kleinschreibung egal.",
                        "The text in the chat message right before the opponent's name. Not case sensitive."));
        field(L.t("Inventar zu bei", "Close inventory on"), c.endText, s -> c.endText = s,
                L.t("optional, z. B. Teleportiert", "optional, e.g. Teleported"),
                L.t("Kommt dieser Text im Chat, wird das Inventar sofort geschlossen. Leer = nur nach dem Countdown.",
                        "When this text appears in chat, the inventory closes right away. Empty = only after the countdown."));
        y += 6;

        section(L.t("Warnungen", "Warnings"), x, y, inner);
        y += 12;
        flag("Elytra", () -> c.warnElytra, v -> c.warnElytra = v);
        flag("Mace", () -> c.warnMace, v -> c.warnMace = v);
        flag("Crystals", () -> c.warnCrystals, v -> c.warnCrystals = v);
        flag(L.t("Töne", "Sounds"), () -> c.sounds, v -> c.sounds = v);
        y += 6;

        section(L.t("Zeiten", "Timing"), x, y, inner);
        y += 12;
        slider(L.t("Crystals ab", "Crystals from"), 1, 64, c.crystalThreshold, v -> String.valueOf((int) Math.round(v)), v -> c.crystalThreshold = (int) Math.round(v));
        slider("Countdown", 1, 30, c.countdownSeconds, v -> (int) Math.round(v) + " s", v -> c.countdownSeconds = (int) Math.round(v));
        slider(L.t("/rtpqueue-Abstand", "/rtpqueue delay"), 1, 100, c.rtpqueueDelayTicks, v -> (int) Math.round(v) + " Ticks", v -> c.rtpqueueDelayTicks = (int) Math.round(v));
        if (col != 0) {
            col = 0;
            y += ROW;
        }
        y += 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(c.serverFilter.isBlank() ? L.t("Aktiv auf allen Servern", "Active on every server")
                : L.t("Nur auf Servern mit \"", "Only on servers with \"") + c.serverFilter + "\"");
    }

    private void field(String label, String value, Consumer<String> set, String placeholder, String tooltip) {
        line(label, x, y + 5, Theme.text());
        TextFieldWidget tf = new TextFieldWidget(textRenderer, x + LABEL_W, y, inner - LABEL_W, ROW - 2, Text.literal(label));
        tf.setMaxLength(128);
        tf.setText(value);
        tf.setCursorToStart(false);
        tf.setPlaceholder(Text.literal(placeholder).formatted(Formatting.DARK_GRAY));
        tf.setChangedListener(s -> {
            set.accept(s);
            RtpqInvseeClient.onConfigEdited();
        });
        tf.setTooltip(Tooltip.of(Text.literal(tooltip)));
        addDrawableChild(tf);
        y += ROW;
        line(placeholder, x + LABEL_W + 2, y, Theme.textDim());
        y += 11;
    }

    private void flag(String label, BooleanSupplier get, Consumer<Boolean> set) {
        toggle(label, get, set, x + col * (colW + 4), y, colW, ROW - 2, null, RtpqInvseeClient::onConfigEdited);
        advance();
    }

    private void slider(String label, double min, double max, double value, DoubleFunction<String> fmt, DoubleConsumer set) {
        addDrawableChild(new ValueSlider(x + col * (colW + 4), y, colW, ROW - 2, label, min, max, 1, value, fmt, v -> {
            set.accept(v);
            RtpqInvseeClient.onConfigEdited();
        }));
        advance();
    }

    private void advance() {
        if (++col >= 2) {
            col = 0;
            y += ROW;
        }
    }

    @Override
    protected void onClose() {
        RtpqInvseeClient.saveNow();
    }
}
