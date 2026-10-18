package com.adhdclient.casino.client;

import com.adhdclient.casino.client.gui.Icons;
import com.adhdclient.casino.core.progress.Achievement;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/** Toast oben rechts: Erfolg freigeschaltet, Level-Up, Mission erfüllt. */
public final class CasinoToast implements Toast {

    private static final long DURATION_MS = 5_000;

    private final ItemStack icon;
    private final Text title;
    private final Text line;
    private final int color;
    private long startTime = -1;
    private Visibility visibility = Visibility.SHOW;

    public CasinoToast(Achievement achievement) {
        this(Icons.of(achievement.iconItem()), Text.translatable("luckycasino.toast.achievement"),
                Text.translatable(achievement.translationKey()), 0xFFFFC832);
    }

    public CasinoToast(ItemStack icon, Text title, Text line, int color) {
        this.icon = icon;
        this.title = title;
        this.line = line;
        this.color = color;
    }

    @Override
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (startTime < 0) {
            startTime = time;
        }
        visibility = time - startTime < DURATION_MS * manager.getNotificationDisplayTimeMultiplier()
                ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public SoundEvent getSoundEvent() {
        return LuckyCasinoClient.config().soundsEnabled ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : null;
    }

    @Override
    public void draw(DrawContext ctx, TextRenderer tr, long startTime) {
        int w = getWidth();
        int h = getHeight();
        ctx.fill(0, 0, w, h, 0xF0101010);
        ctx.fillGradient(1, 1, w - 1, h - 1, 0xF0302410, 0xF0181208);
        ctx.drawStrokedRectangle(0, 0, w, h, color);
        ctx.drawItem(icon, 8, 8);
        ctx.drawText(tr, title, 30, 7, color, false);
        ctx.drawText(tr, line, 30, 18, 0xFFFFFFFF, false);
    }
}
