package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.core.CasinoRng;
import com.adhdclient.casino.core.LuckMode;
import com.adhdclient.casino.core.loot.Cosmetic;
import com.adhdclient.casino.core.loot.LootBox;
import com.adhdclient.casino.core.loot.LootBoxType;
import com.adhdclient.casino.core.loot.LootReward;
import com.adhdclient.casino.core.loot.Rarity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Lootboxen: Kiste kaufen, Streifen rollt aus, Belohnung wird enthüllt. */
public class LootBoxScreen extends CasinoScreen {

    private static final int CARD = 44;
    private static final int STEP = 48;
    private static final int STRIP_LEN = 52;
    private static final int WIN_INDEX = 45;
    private static final Random VISUAL = new Random();
    /** Eigener Zufall nur für Füllkarten (beeinflusst nicht die echte Ziehung). */
    private static final CasinoRng FILLER = new CasinoRng(LuckMode.FAIR);

    private final ButtonWidget[] openButtons = new ButtonWidget[LootBoxType.values().length];
    private ButtonWidget equipButton;

    private final List<LootReward> strip = new ArrayList<>();
    private LootReward reward;
    private boolean rolling;
    private long rollStart;
    private float endOffset;
    private int lastTickIndex = -1;
    private long revealedAt;

    public LootBoxScreen(Screen parent) {
        super(Text.translatable("luckycasino.menu.loot"), parent);
    }

    private int stripX() {
        return left + 14;
    }

    private int stripW() {
        return pw - 28;
    }

    private int stripY() {
        return top + 104;
    }

    @Override
    protected void initContent() {
        LootBoxType[] types = LootBoxType.values();
        for (int i = 0; i < types.length; i++) {
            LootBoxType type = types[i];
            int bx = left + 14 + i * (pw - 28) / 2;
            openButtons[i] = addDrawableChild(ButtonWidget.builder(
                    Text.translatable("luckycasino.loot.open", Fmt.chips(type.price())), b -> open(type))
                    .dimensions(bx + 108, top + 52, 80, 18).build());
        }
        equipButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.loot.equip"), b -> {
            if (reward != null && reward.cosmetic() != null && casino.equip(reward.cosmetic())) {
                CasinoSounds.play(net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_GOLD, 1f, 0.8f);
            }
        }).dimensions(left + pw / 2 + 60, top + 172, 80, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.loot.collection"),
                b -> client.setScreen(new CollectionScreen(this))).dimensions(left + 14, top + ph - 24, 110, 18).build());
        update();
    }

    private void update() {
        LootBoxType[] types = LootBoxType.values();
        for (int i = 0; i < types.length; i++) {
            openButtons[i].active = !rolling && casino.canAfford(types[i].price());
        }
        equipButton.visible = !rolling && reward != null && reward.cosmetic() != null && !reward.duplicate();
        if (equipButton.visible) {
            boolean equipped = reward.cosmetic().type() == Cosmetic.Type.TITLE
                    ? reward.cosmetic().equals(casino.equippedTitle())
                    : reward.cosmetic().equals(casino.equippedFrame());
            equipButton.active = !equipped;
            equipButton.setMessage(Text.translatable(equipped ? "luckycasino.loot.equipped" : "luckycasino.loot.equip"));
        }
    }

    private void open(LootBoxType type) {
        if (rolling) {
            return;
        }
        long before = casino.balance();
        LootReward r = casino.openLootBox(type);
        if (r == null) {
            CasinoSounds.error();
            return;
        }
        reward = r;
        freezeBalance(before - type.price());
        strip.clear();
        for (int i = 0; i < STRIP_LEN; i++) {
            strip.add(i == WIN_INDEX ? r : LootBox.roll(FILLER, type, new HashSet<>()));
        }
        endOffset = WIN_INDEX * STEP + CARD / 2f - stripW() / 2f + (VISUAL.nextFloat() - 0.5f) * (CARD - 10);
        rollStart = now();
        rolling = true;
        lastTickIndex = -1;
        CasinoSounds.chestOpen();
        update();
    }

    private long rollDuration() {
        return dur(5_200);
    }

    @Override
    public void tick() {
        if (rolling && now() - rollStart >= rollDuration()) {
            finishRoll();
        }
        update();
    }

    private void finishRoll() {
        rolling = false;
        revealedAt = now();
        unfreezeBalance();
        switch (reward.rarity()) {
            case LEGENDARY -> CasinoSounds.legendary();
            case EPIC, RARE -> CasinoSounds.bigWin();
            default -> CasinoSounds.win();
        }
        update();
    }

    @Override
    public void finishPendingRounds() {
        if (rolling) {
            finishRoll();
        }
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        drawBoxes(ctx);
        drawStrip(ctx);
        drawReward(ctx);
        ctx.drawText(textRenderer, Text.translatable("luckycasino.loot.opened", casino.profile().stats.lootBoxesOpened,
                casino.ownedCosmeticCount(), Cosmetic.values().length - 1), left + 132, top + ph - 19, Ui.GRAY, true);
    }

    private void drawBoxes(DrawContext ctx) {
        LootBoxType[] types = LootBoxType.values();
        for (int i = 0; i < types.length; i++) {
            LootBoxType type = types[i];
            int bx = left + 14 + i * (pw - 28) / 2;
            int by = top + 28;
            int w = (pw - 28) / 2 - 6;
            Ui.box(ctx, bx, by, w, 70, 0xB0000000, i == 0 ? 0xFF9C7A48 : 0xFF6FE8F0);
            Ui.item(ctx, Icons.of(type.itemId()), bx + 8, by + 10, 2f);
            ctx.drawText(textRenderer, Text.translatable(type.translationKey()), bx + 46, by + 8, Ui.GOLD, true);
            ctx.drawText(textRenderer, Text.translatable("luckycasino.loot.price", Fmt.chips(type.price())), bx + 46, by + 20, Ui.WHITE, true);
            // Seltenheits-Chancen als farbiger Balken
            int[] weights = type.rarityWeights();
            int total = 0;
            for (int wgt : weights) {
                total += wgt;
            }
            int barX = bx + 8;
            int barW = w - 16;
            int acc = 0;
            for (int r = 0; r < weights.length; r++) {
                int x0 = barX + barW * acc / total;
                acc += weights[r];
                int x1 = barX + barW * acc / total;
                ctx.fill(x0, by + 46, x1, by + 52, Rarity.values()[r].color());
            }
            StringBuilder sb = new StringBuilder();
            for (int r = 0; r < weights.length; r++) {
                if (r > 0) {
                    sb.append(" / ");
                }
                sb.append(weights[r]);
            }
            ctx.drawText(textRenderer, Text.translatable("luckycasino.loot.odds", sb.toString()), barX, by + 56, Ui.GRAY, false);
        }
    }

    private float currentOffset() {
        if (rolling) {
            float t = Ui.clamp01((now() - rollStart) / (float) rollDuration());
            return endOffset * Ui.easeOutCubic(t);
        }
        return strip.isEmpty() ? 0 : endOffset;
    }

    private void drawStrip(DrawContext ctx) {
        int x = stripX();
        int y = stripY();
        int w = stripW();
        ctx.fill(x - 2, y - 2, x + w + 2, y + CARD + 10, 0xFF101010);
        ctx.fill(x, y, x + w, y + CARD + 8, 0xFF1C1C1C);
        if (strip.isEmpty()) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.loot.hint"), x + w / 2, y + CARD / 2, Ui.GRAY);
            return;
        }
        float offset = currentOffset();
        int center = x + w / 2;
        int under = (int) Math.floor((offset + w / 2f) / STEP);
        if (rolling && under != lastTickIndex) {
            lastTickIndex = under;
            CasinoSounds.tick(1.5f);
        }
        ctx.enableScissor(x, y, x + w, y + CARD + 8);
        for (int i = 0; i < strip.size(); i++) {
            float cx = x + i * STEP - offset + 4;
            if (cx + CARD < x || cx > x + w) {
                continue;
            }
            LootReward r = strip.get(i);
            boolean highlight = !rolling && i == WIN_INDEX;
            drawCard(ctx, r, cx, y + 4, highlight);
        }
        ctx.disableScissor();
        // Abdunklung an den Seiten
        for (int i = 0; i < 30; i++) {
            int a = (int) (160 * (1 - i / 30f));
            ctx.fill(x + i, y, x + i + 1, y + CARD + 8, a << 24);
            ctx.fill(x + w - i - 1, y, x + w - i, y + CARD + 8, a << 24);
        }
        // Markierung in der Mitte
        ctx.fill(center - 1, y - 6, center + 1, y + CARD + 14, 0xFFFFD34D);
        for (int k = 0; k < 5; k++) {
            ctx.fill(center - 5 + k, y - 6 + k, center + 5 - k, y - 5 + k, 0xFFFFD34D);
            ctx.fill(center - 5 + k, y + CARD + 13 - k, center + 5 - k, y + CARD + 14 - k, 0xFFFFD34D);
        }
    }

    private void drawCard(DrawContext ctx, LootReward r, float x, int y, boolean highlight) {
        int ix = Math.round(x);
        int color = r.rarity().color();
        ctx.fill(ix, y, ix + CARD, y + CARD, 0xFF2A2A2A);
        ctx.fillGradient(ix + 1, y + 1, ix + CARD - 1, y + CARD - 1, 0xFF303030, Ui.darken(color, 0.55f));
        ctx.fill(ix, y + CARD - 4, ix + CARD, y + CARD, color);
        ctx.drawStrokedRectangle(ix, y, CARD, CARD, highlight ? Ui.lerpColor(color, 0xFFFFFFFF, Ui.pulse(now(), 400)) : Ui.darken(color, 0.2f));
        Ui.item(ctx, Icons.of(CosmeticStyle.icon(r)), ix + 6, y + 6, 2f);
    }

    private void drawReward(DrawContext ctx) {
        if (rolling || reward == null) {
            return;
        }
        float appear = Ui.clamp01((now() - revealedAt) / 300f);
        int y = top + 164;
        int color = reward.rarity().color();
        if (reward.rarity() == Rarity.LEGENDARY) {
            color = Ui.lerpColor(color, 0xFFFFFFFF, 0.3f * Ui.pulse(now(), 500));
        }
        Text rarity = Text.translatable(reward.rarity().translationKey());
        Text name = CosmeticStyle.name(reward);
        int nameX = left + 30;
        Ui.scaled(ctx, textRenderer, name, nameX, y, 1f + 0.25f * Ui.easeOutBack(appear), color, false);
        Text info = reward.cosmetic() != null
                ? Text.translatable("luckycasino.loot.info", rarity, CosmeticStyle.typeName(reward.cosmetic()))
                : Text.translatable("luckycasino.loot.info_chips", rarity);
        ctx.drawText(textRenderer, info, nameX, y + 15, Ui.GRAY, true);
        if (reward.duplicate()) {
            ctx.drawText(textRenderer, Text.translatable("luckycasino.loot.duplicate", Fmt.chips(reward.chips())), nameX, y + 27, Ui.GOLD, true);
        } else if (reward.cosmetic() != null) {
            ctx.drawText(textRenderer, Text.translatable("luckycasino.loot.new"), nameX, y + 27, Ui.GREEN, true);
        }
    }
}
