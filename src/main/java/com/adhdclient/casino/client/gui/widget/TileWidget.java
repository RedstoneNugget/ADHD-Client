package com.adhdclient.casino.client.gui.widget;

import com.adhdclient.casino.client.gui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/** Kachel im Hauptmenü: Item-Symbol, Name und Untertitel. */
public class TileWidget extends ClickableWidget {

    private final ItemStack icon;
    private final Supplier<Text> subtitle;
    private final Supplier<Integer> subtitleColor;
    private final int accent;
    private final Runnable action;

    public TileWidget(int x, int y, int w, int h, Text name, ItemStack icon, Supplier<Text> subtitle,
                      Supplier<Integer> subtitleColor, int accent, Runnable action) {
        super(x, y, w, h, name);
        this.icon = icon;
        this.subtitle = subtitle;
        this.subtitleColor = subtitleColor;
        this.accent = accent;
        this.action = action;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int x = getX();
        int y = getY();
        boolean hl = isHovered() || isFocused();
        ctx.fill(x, y, x + width, y + height, hl ? 0xE0283828 : 0xC0101810);
        ctx.fill(x, y, x + 2, y + height, hl ? accent : Ui.darken(accent, 0.45f));
        ctx.drawStrokedRectangle(x, y, width, height, hl ? accent : 0xFF2E4A36);
        Ui.item(ctx, icon, x + 5, y + (height - 20) / 2f, 1.25f);
        int room = width - 30;
        fitText(ctx, getMessage(), x + 28, y + height / 2 - 9, room, hl ? Ui.GOLD : Ui.WHITE);
        Text sub = subtitle.get();
        if (sub != null) {
            fitText(ctx, sub, x + 28, y + height / 2 + 2, room, subtitleColor.get());
        }
    }

    /** Text, der bei Platzmangel verkleinert wird. */
    private static void fitText(DrawContext ctx, Text text, int x, int y, int room, int color) {
        var tr = MinecraftClient.getInstance().textRenderer;
        float scale = Math.min(1f, room / (float) Math.max(1, tr.getWidth(text)));
        Ui.scaled(ctx, tr, text, x, y + (1 - scale) * 4, scale, color, false);
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        action.run();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (isFocused() && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_SPACE
                || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            action.run();
            return true;
        }
        return false;
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
