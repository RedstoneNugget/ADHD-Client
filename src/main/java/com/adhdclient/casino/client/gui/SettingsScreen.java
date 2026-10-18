package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.ClientSession;
import com.adhdclient.casino.client.LuckyCasinoClient;
import com.adhdclient.casino.config.CasinoConfig;
import com.adhdclient.casino.core.GameType;
import com.adhdclient.casino.core.LuckMode;
import com.adhdclient.casino.core.Odds;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Einstellungen (auch über Mod Menu erreichbar). */
public class SettingsScreen extends CasinoScreen {

    private final CasinoConfig cfg = LuckyCasinoClient.config();

    public SettingsScreen(Screen parent) {
        super(Text.translatable("luckycasino.menu.settings"), parent);
    }

    @Override
    protected void initContent() {
        int x = left + 12;
        int w = 186;
        int y = top + HEADER_H + 10;

        addDrawableChild(ButtonWidget.builder(modeText(), b -> {
            LuckMode next = casino.mode().next();
            casino.setMode(next);
            cfg.luckMode = next;
            // Laufende Blackjack-Hände behalten ihre Regeln, neue Hände nutzen den neuen Modus
            LuckyCasinoClient.markDirty();
            b.setMessage(modeText());
        }).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.translatable("luckycasino.settings.mode.tooltip"))).build());
        y += 26;

        addDrawableChild(ButtonWidget.builder(toggle("luckycasino.settings.hud", cfg.hudEnabled), b -> {
            cfg.hudEnabled = !cfg.hudEnabled;
            LuckyCasinoClient.markDirty();
            b.setMessage(toggle("luckycasino.settings.hud", cfg.hudEnabled));
        }).dimensions(x, y, w, 20).build());
        y += 22;
        addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.settings.hud_move"),
                b -> client.setScreen(new HudPositionScreen(this))).dimensions(x, y, w, 20).build());
        y += 22;
        addDrawableChild(ButtonWidget.builder(toggle("luckycasino.settings.hud_title", cfg.hudShowTitle), b -> {
            cfg.hudShowTitle = !cfg.hudShowTitle;
            LuckyCasinoClient.markDirty();
            b.setMessage(toggle("luckycasino.settings.hud_title", cfg.hudShowTitle));
        }).dimensions(x, y, w, 20).build());
        y += 26;

        addDrawableChild(ButtonWidget.builder(toggle("luckycasino.settings.sounds", cfg.soundsEnabled), b -> {
            cfg.soundsEnabled = !cfg.soundsEnabled;
            LuckyCasinoClient.markDirty();
            b.setMessage(toggle("luckycasino.settings.sounds", cfg.soundsEnabled));
        }).dimensions(x, y, w, 20).build());
        y += 22;
        addDrawableChild(new VolumeSlider(x, y, w, 20, cfg));
        y += 22;
        addDrawableChild(ButtonWidget.builder(animText(), b -> {
            cfg.fastAnimations = !cfg.fastAnimations;
            LuckyCasinoClient.markDirty();
            b.setMessage(animText());
        }).dimensions(x, y, w, 20).build());
        y += 22;
        addDrawableChild(ButtonWidget.builder(reminderText(), b -> {
            int[] steps = {0, 30, 60, 120};
            int idx = 0;
            for (int i = 0; i < steps.length; i++) {
                if (steps[i] == cfg.reminderMinutes) {
                    idx = i;
                }
            }
            cfg.reminderMinutes = steps[(idx + 1) % steps.length];
            LuckyCasinoClient.markDirty();
            b.setMessage(reminderText());
        }).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.translatable("luckycasino.settings.reminder.tooltip"))).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.settings.reset").withColor(0xFFFF7070), b ->
                client.setScreen(new ConfirmScreen(confirmed -> {
                    if (confirmed) {
                        ClientSession.clear();
                        casino.reset();
                        LuckyCasinoClient.saveNow();
                    }
                    client.setScreen(this);
                }, Text.translatable("luckycasino.settings.reset.title"), Text.translatable("luckycasino.settings.reset.text"))))
                .dimensions(left + pw - 12 - 186, top + ph - 26, 186, 20).build());
    }

    private Text modeText() {
        return Text.translatable("luckycasino.settings.mode", Text.translatable(casino.mode().translationKey()));
    }

    private Text animText() {
        return Text.translatable("luckycasino.settings.animations",
                Text.translatable(cfg.fastAnimations ? "luckycasino.settings.animations.fast" : "luckycasino.settings.animations.normal"));
    }

    private Text reminderText() {
        return Text.translatable("luckycasino.settings.reminder", cfg.reminderMinutes == 0
                ? ScreenTexts.OFF : Text.translatable("luckycasino.settings.reminder.minutes", cfg.reminderMinutes));
    }

    private static Text toggle(String key, boolean on) {
        return Text.translatable(key, on ? ScreenTexts.ON : ScreenTexts.OFF);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = left + pw - 12 - 186;
        int y = top + HEADER_H + 10;
        int w = 186;
        Ui.box(ctx, x - 4, y - 2, w + 8, 176, 0xB0000000, 0xFF2E4A36);
        LuckMode mode = casino.mode();
        ctx.drawText(textRenderer, Text.translatable(mode.translationKey()), x, y + 2, Ui.GOLD, true);
        int used = Ui.wrapped(ctx, textRenderer, Text.translatable(mode.descriptionKey()), x, y + 14, w, Ui.WHITE);
        int ty = y + 20 + used;
        ctx.drawText(textRenderer, Text.translatable("luckycasino.settings.edge_title"), x, ty, Ui.GRAY, true);
        ty += 12;
        for (GameType g : GameType.values()) {
            double edge = Odds.houseEdge(g, mode);
            ctx.drawText(textRenderer, Text.translatable(g.translationKey()), x + 4, ty, Ui.WHITE, true);
            String prefix = g == GameType.BLACKJACK ? "~" : "";
            Ui.right(ctx, textRenderer, Text.literal(prefix + Fmt.percent(Math.max(0, edge))), x + w - 4, ty,
                    edge < 0.005 ? Ui.GREEN : edge < 0.06 ? Ui.GOLD : Ui.RED);
            ty += 11;
        }
        ctx.drawText(textRenderer, Text.translatable("luckycasino.settings.config_file"), left + 12, top + ph - 11, Ui.DARK_GRAY, true);
    }

    /** Lautstärkeregler (0–100 %). */
    private static final class VolumeSlider extends SliderWidget {
        private final CasinoConfig cfg;

        VolumeSlider(int x, int y, int w, int h, CasinoConfig cfg) {
            super(x, y, w, h, Text.empty(), cfg.soundVolume);
            this.cfg = cfg;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("luckycasino.settings.volume", Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            cfg.soundVolume = (float) value;
            LuckyCasinoClient.markDirty();
        }
    }
}
