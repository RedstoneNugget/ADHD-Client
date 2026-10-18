package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.core.progress.Achievement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Übersicht aller Erfolge (2 Spalten, mit dem Mausrad scrollbar). */
public class AchievementsScreen extends CasinoScreen {

    private static final int ROW_H = 21;
    private static final int ROW_GAP = 2;
    private static final int SCROLLBAR_W = 4;

    private double scroll;

    public AchievementsScreen(Screen parent) {
        super(Text.translatable("luckycasino.menu.achievements"), parent);
    }

    @Override
    protected void initContent() {
        scroll = Math.min(scroll, maxScroll());
    }

    private int viewTop() {
        return top + HEADER_H + 14;
    }

    private int viewHeight() {
        return ph - HEADER_H - 20;
    }

    private int contentHeight() {
        int rows = (Achievement.values().length + 1) / 2;
        return rows * (ROW_H + ROW_GAP) - ROW_GAP;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - viewHeight());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (maxScroll() > 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - vertical * (ROW_H + ROW_GAP)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Achievement[] all = Achievement.values();
        int colW = (pw - 24 - SCROLLBAR_W - 4) / 2;
        int y0 = viewTop();
        int viewH = viewHeight();
        Ui.right(ctx, textRenderer, Text.translatable("luckycasino.achievements.progress", casino.unlockedCount(), all.length),
                left + pw - 10, top + HEADER_H + 3, Ui.GRAY);

        boolean mouseInView = mouseY >= y0 && mouseY < y0 + viewH;
        Achievement hovered = null;
        ctx.enableScissor(left + 4, y0, left + pw - 4, y0 + viewH);
        for (int i = 0; i < all.length; i++) {
            Achievement a = all[i];
            int x = left + 8 + (i % 2) * (colW + 8);
            int y = y0 + (i / 2) * (ROW_H + ROW_GAP) - (int) Math.round(scroll);
            if (y + ROW_H < y0 || y > y0 + viewH) {
                continue;
            }
            boolean unlocked = casino.isUnlocked(a);
            if (mouseInView && mouseX >= x && mouseX < x + colW && mouseY >= y && mouseY < y + ROW_H) {
                hovered = a;
            }
            ctx.fill(x, y, x + colW, y + ROW_H, unlocked ? 0xC0201A08 : 0xA0080808);
            ctx.drawStrokedRectangle(x, y, colW, ROW_H, unlocked ? 0xFFB08A20 : 0xFF303030);
            ctx.drawItem(Icons.of(a.iconItem()), x + 3, y + 3);
            if (!unlocked) {
                ctx.fill(x + 3, y + 3, x + 19, y + 19, 0xB0000000);
            }
            ctx.drawText(textRenderer, Text.translatable(a.translationKey()), x + 23, y + 3, unlocked ? Ui.GOLD : Ui.GRAY, true);
            Text desc = Text.translatable(a.descriptionKey());
            float scale = Math.min(0.8f, (colW - 26) / (float) Math.max(1, textRenderer.getWidth(desc)));
            Ui.scaled(ctx, textRenderer, desc, x + 23, y + 13, scale, unlocked ? 0xFFD8D8D8 : Ui.DARK_GRAY, false);
            if (unlocked) {
                Ui.right(ctx, textRenderer, Text.literal("✔"), x + colW - 4, y + 3, Ui.GREEN);
            }
        }
        ctx.disableScissor();

        // Scrollbalken
        double max = maxScroll();
        if (max > 0) {
            int sx = left + pw - 8 - SCROLLBAR_W;
            ctx.fill(sx, y0, sx + SCROLLBAR_W, y0 + viewH, 0x60000000);
            int thumbH = Math.max(16, (int) (viewH * (viewH / (double) contentHeight())));
            int thumbY = y0 + (int) ((viewH - thumbH) * (scroll / max));
            ctx.fill(sx, thumbY, sx + SCROLLBAR_W, thumbY + thumbH, 0xFFB08A20);
        }

        if (hovered != null) {
            long at = casino.unlockedAt(hovered);
            ctx.drawTooltip(textRenderer, List.of(
                    Text.translatable(hovered.translationKey()),
                    Text.translatable(hovered.descriptionKey()),
                    at > 0 ? Text.translatable("luckycasino.achievements.unlocked_at", Fmt.date(at))
                            : Text.translatable("luckycasino.achievements.locked")), mouseX, mouseY);
        }
    }
}
