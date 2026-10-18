package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.core.Casino;
import com.adhdclient.casino.core.progress.Missions;
import com.adhdclient.casino.core.progress.PlayerProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Tägliche Missionen und Casino-Level. */
public class MissionsScreen extends CasinoScreen {

    private static final int CARD_H = 40;

    private final ButtonWidget[] claimButtons = new ButtonWidget[Missions.PER_DAY];

    public MissionsScreen(Screen parent) {
        super(Text.translatable("luckycasino.menu.missions"), parent);
    }

    private int cardY(int i) {
        return top + HEADER_H + 20 + i * (CARD_H + 6);
    }

    @Override
    protected void initContent() {
        for (int i = 0; i < Missions.PER_DAY; i++) {
            int idx = i;
            claimButtons[i] = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.missions.claim"), b -> claim(idx))
                    .dimensions(left + pw - 88, cardY(i) + 11, 76, 18).build());
        }
        update();
    }

    private void claim(int i) {
        List<Missions.Progress> list = casino.missions();
        if (i < list.size() && casino.claimMission(list.get(i).id)) {
            CasinoSounds.cashOut();
        }
        update();
    }

    private void update() {
        List<Missions.Progress> list = casino.missions();
        for (int i = 0; i < claimButtons.length; i++) {
            Missions.Progress mp = i < list.size() ? list.get(i) : null;
            Missions.Def def = mp == null ? null : Missions.byId(mp.id);
            boolean done = def != null && mp.progress >= def.target();
            claimButtons[i].visible = def != null && done;
            claimButtons[i].active = done && !mp.claimed;
            claimButtons[i].setMessage(Text.translatable(mp != null && mp.claimed ? "luckycasino.missions.claimed" : "luckycasino.missions.claim"));
        }
    }

    @Override
    public void tick() {
        update();
    }

    /** Beschreibung einer Mission, z. B. "Gewinne 3 Runden Blackjack". */
    public static Text describe(Missions.Def def) {
        Text game = def.game() == null ? Text.empty() : Text.translatable(def.game().translationKey());
        return Text.translatable(def.translationKey(), Fmt.chips(def.target()), game);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawText(textRenderer, Text.translatable("luckycasino.missions.today"), left + 12, top + HEADER_H + 6, Ui.GOLD, true);
        Ui.right(ctx, textRenderer, Text.translatable("luckycasino.missions.new_in", Fmt.duration(CasinoMenuScreen.untilMidnightMs())),
                left + pw - 12, top + HEADER_H + 6, Ui.GRAY);
        List<Missions.Progress> list = casino.missions();
        for (int i = 0; i < list.size(); i++) {
            Missions.Progress mp = list.get(i);
            Missions.Def def = Missions.byId(mp.id);
            if (def == null) {
                continue;
            }
            int x = left + 10;
            int y = cardY(i);
            int w = pw - 20;
            boolean done = mp.progress >= def.target();
            int border = mp.claimed ? 0xFF3A5A3A : done ? Ui.lerpColor(Ui.GREEN, Ui.WHITE, Ui.pulse(now(), 900) * 0.4f) : 0xFF2E4A36;
            Ui.box(ctx, x, y, w, CARD_H, mp.claimed ? 0x90101010 : 0xC0101810, border);
            String icon = def.game() != null ? def.game().iconItem() : "minecraft:writable_book";
            Ui.item(ctx, Icons.of(icon), x + 6, y + 12, 1f);
            Text desc = describe(def);
            ctx.drawText(textRenderer, desc, x + 28, y + 6, mp.claimed ? Ui.DARK_GRAY : Ui.WHITE, true);
            // Fortschrittsbalken
            int bx = x + 28;
            int bw = w - 28 - 100;
            ctx.fill(bx, y + 20, bx + bw, y + 28, 0xFF202020);
            int fill = (int) Math.round(bw * Math.min(1.0, mp.progress / (double) def.target()));
            ctx.fill(bx, y + 20, bx + fill, y + 28, done ? 0xFF3FBF5A : 0xFF2F6FD0);
            Ui.centered(ctx, textRenderer, Text.literal(Fmt.chips(mp.progress) + " / " + Fmt.chips(def.target())),
                    bx + bw / 2, y + 20, Ui.WHITE);
            if (!done) {
                Ui.right(ctx, textRenderer, Text.translatable("luckycasino.missions.reward", Fmt.chips(def.reward())),
                        x + w - 10, y + 16, Ui.GOLD);
            }
        }
        drawLevel(ctx);
    }

    private void drawLevel(DrawContext ctx) {
        PlayerProfile p = casino.profile();
        int x = left + 10;
        int y = cardY(Missions.PER_DAY) + 4;
        int w = pw - 20;
        Ui.box(ctx, x, y, w, 44, 0xB0000000, 0xFF2E4A36);
        Ui.item(ctx, Icons.of("minecraft:experience_bottle"), x + 6, y + 6, 1.25f);
        Ui.scaled(ctx, textRenderer, Text.translatable("luckycasino.level", p.level), x + 30, y + 6, 1.5f, 0xFF7CFC6A, false);
        long need = Casino.xpForNextLevel(p.level);
        int bx = x + 30;
        int bw = w - 40;
        ctx.fill(bx, y + 22, bx + bw, y + 28, 0xFF202020);
        ctx.fill(bx, y + 22, bx + (int) (bw * Math.min(1.0, p.xp / (double) need)), y + 28, 0xFF7CFC6A);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.level.xp", Fmt.chips(p.xp), Fmt.chips(need),
                Fmt.chips(Casino.levelReward(p.level + 1))), bx, y + 31, Ui.GRAY, true);
        Ui.right(ctx, textRenderer, Text.translatable("luckycasino.missions.total", p.missionsCompleted), x + w - 8, y + 8, Ui.GRAY);
    }
}
