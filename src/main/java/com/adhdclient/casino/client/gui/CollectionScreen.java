package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.core.loot.Cosmetic;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Sammlung der kosmetischen Gegenstände: Titel und Rahmenfarben anlegen. */
public class CollectionScreen extends CasinoScreen {

    private static final int COLS = 4;
    private static final int ENTRY_H = 28;

    private record Entry(Cosmetic cosmetic, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public CollectionScreen(Screen parent) {
        super(Text.translatable("luckycasino.collection.title"), parent);
    }

    @Override
    protected void initContent() {
        entries.clear();
        int w = (pw - 20 - (COLS - 1) * 4) / COLS;
        int y = layout(Cosmetic.Type.TITLE, top + 40, w);
        layout(Cosmetic.Type.FRAME, y + 16, w);
    }

    private int layout(Cosmetic.Type type, int y0, int w) {
        int i = 0;
        for (Cosmetic c : Cosmetic.values()) {
            if (c.type() != type) {
                continue;
            }
            int x = left + 10 + (i % COLS) * (w + 4);
            int y = y0 + (i / COLS) * (ENTRY_H + 4);
            entries.add(new Entry(c, x, y, w, ENTRY_H));
            i++;
        }
        return y0 + ((i + COLS - 1) / COLS) * (ENTRY_H + 4);
    }

    private boolean isEquipped(Cosmetic c) {
        return c.type() == Cosmetic.Type.TITLE ? c.equals(casino.equippedTitle()) : c.equals(casino.equippedFrame());
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        for (Entry e : entries) {
            if (e.contains(click.x(), click.y())) {
                if (casino.equip(e.cosmetic())) {
                    CasinoSounds.play(net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_GOLD, 1f, 0.8f);
                } else {
                    CasinoSounds.error();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.drawText(textRenderer, Text.translatable("luckycasino.cosmetic.type.titles"), left + 10, top + 28, Ui.GOLD, true);
        Entry firstFrame = entries.stream().filter(e -> e.cosmetic().type() == Cosmetic.Type.FRAME).findFirst().orElse(null);
        if (firstFrame != null) {
            ctx.drawText(textRenderer, Text.translatable("luckycasino.cosmetic.type.frames"), left + 10, firstFrame.y() - 12, Ui.GOLD, true);
        }
        Ui.right(ctx, textRenderer, Text.translatable("luckycasino.collection.help"), left + pw - 10, top + 28, Ui.DARK_GRAY);

        Entry hovered = null;
        long now = now();
        for (Entry e : entries) {
            Cosmetic c = e.cosmetic();
            boolean owned = casino.owns(c);
            boolean equipped = isEquipped(c);
            boolean hover = e.contains(mouseX, mouseY);
            if (hover) {
                hovered = e;
            }
            int rarityColor = c.rarity() == null ? Cosmetic.FRAME_CLASSIC.color() : c.rarity().color();
            int border = equipped ? Ui.lerpColor(Ui.GOLD, Ui.WHITE, Ui.pulse(now, 800)) : owned ? Ui.darken(rarityColor, 0.2f) : 0xFF303030;
            ctx.fill(e.x(), e.y(), e.x() + e.w(), e.y() + e.h(), hover ? 0xD0283828 : 0xC0101010);
            ctx.drawStrokedRectangle(e.x(), e.y(), e.w(), e.h(), border);
            if (c.type() == Cosmetic.Type.FRAME) {
                int sw = owned ? Ui.frameColor(c, now) : 0xFF404040;
                ctx.fill(e.x() + 4, e.y() + 4, e.x() + 24, e.y() + 24, sw);
                ctx.fill(e.x() + 7, e.y() + 7, e.x() + 21, e.y() + 21, Ui.FELT);
            } else {
                Ui.item(ctx, Icons.of(CosmeticStyle.icon(c)), e.x() + 6, e.y() + 6, 1f);
                if (!owned) {
                    ctx.fill(e.x() + 6, e.y() + 6, e.x() + 22, e.y() + 22, 0xB0000000);
                }
            }
            Text name = owned ? Text.translatable(c.translationKey()) : Text.literal("???");
            int nameColor = owned ? (c.type() == Cosmetic.Type.TITLE ? c.color() : Ui.WHITE) : Ui.DARK_GRAY;
            float scale = Math.min(1f, (e.w() - 32) / (float) Math.max(1, textRenderer.getWidth(name)));
            Ui.scaled(ctx, textRenderer, name, e.x() + 28, e.y() + 5, scale, nameColor, false);
            Text sub = c.rarity() == null ? Text.translatable("luckycasino.collection.default")
                    : Text.translatable(c.rarity().translationKey());
            Ui.scaled(ctx, textRenderer, equipped ? Text.translatable("luckycasino.collection.equipped") : sub,
                    e.x() + 28, e.y() + 16, 0.75f, equipped ? Ui.GOLD : rarityColor, false);
        }
        if (hovered != null) {
            Cosmetic c = hovered.cosmetic();
            List<Text> lines = new ArrayList<>();
            lines.add(casino.owns(c) ? Text.translatable(c.translationKey()) : Text.literal("???"));
            lines.add(CosmeticStyle.typeName(c));
            lines.add(Text.translatable(casino.owns(c) ? "luckycasino.collection.click" : "luckycasino.collection.locked"));
            ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
        }
    }
}
