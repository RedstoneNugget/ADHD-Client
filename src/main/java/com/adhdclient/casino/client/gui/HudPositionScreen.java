package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoHud;
import com.adhdclient.casino.client.LuckyCasinoClient;
import com.adhdclient.casino.config.CasinoConfig;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** HUD-Anzeige per Maus verschieben. Position wird relativ zur Bildschirmgröße gespeichert. */
public class HudPositionScreen extends Screen {

    private final Screen parent;
    private final CasinoConfig cfg = LuckyCasinoClient.config();
    private boolean dragging;
    private double grabX;
    private double grabY;

    public HudPositionScreen(Screen parent) {
        super(Text.translatable("luckycasino.hudpos.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.hudpos.reset"), b -> {
            cfg.hudX = 0;
            cfg.hudY = 0;
            LuckyCasinoClient.markDirty();
        }).dimensions(width / 2 - 104, height - 28, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(width / 2 + 4, height - 28, 100, 20).build());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Bewusst ohne Unschärfe, damit man die Spielwelt sieht
        ctx.fillGradient(0, 0, width, height, 0x60101010, 0x80101010);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int[] b = CasinoHud.bounds(textRenderer, width, height);
        boolean hover = mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3];
        CasinoHud.draw(ctx, textRenderer, b[0], b[1]);
        ctx.drawStrokedRectangle(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, dragging || hover ? Ui.GOLD : 0x80FFFFFF);
        Ui.centered(ctx, textRenderer, title, width / 2, height / 2 - 20, Ui.GOLD);
        Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.hudpos.help"), width / 2, height / 2 - 6, Ui.WHITE);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        int[] b = CasinoHud.bounds(textRenderer, width, height);
        if (click.button() == 0 && click.x() >= b[0] && click.x() < b[0] + b[2] && click.y() >= b[1] && click.y() < b[1] + b[3]) {
            dragging = true;
            grabX = click.x() - b[0];
            grabY = click.y() - b[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging) {
            int[] size = CasinoHud.size(textRenderer);
            double freeW = Math.max(1, width - size[0] - 2 * CasinoHud.MARGIN);
            double freeH = Math.max(1, height - size[1] - 2 * CasinoHud.MARGIN);
            cfg.hudX = (float) Math.max(0, Math.min(1, (click.x() - grabX - CasinoHud.MARGIN) / freeW));
            cfg.hudY = (float) Math.max(0, Math.min(1, (click.y() - grabY - CasinoHud.MARGIN) / freeH));
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging) {
            dragging = false;
            LuckyCasinoClient.markDirty();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        LuckyCasinoClient.saveNow();
        client.setScreen(parent);
    }
}
