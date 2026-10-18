package com.adhdclient.gui;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.core.AdhdModuleState;
import com.adhdclient.headfinder.HeadFinderClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Zentrales Menü des ADHD Client (Taste: Rechte Strg): jedes Modul einzeln an/aus,
 * daneben ein Knopf zu den eigenen Einstellungen des Moduls.
 */
public final class AdhdMenuScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int TOGGLE_W = 64;

    private final Screen parent;

    public AdhdMenuScreen(Screen parent) {
        super(Text.literal("ADHD Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 260;
        int x = width / 2 - w / 2;
        int y = height / 2 - 50;

        y = row(x, y, w, "Base Finder", () -> BaseFinderClient.enabled, v -> BaseFinderClient.enabled = v,
                () -> client.setScreen(new com.adhdclient.basefinder.gui.ConfigScreen(this)));
        y = row(x, y, w, "Block Finder", () -> HeadFinderClient.enabled, v -> HeadFinderClient.enabled = v,
                () -> client.setScreen(new com.adhdclient.headfinder.gui.HeadListScreen(this)));
        y += 10;

        addDrawableChild(ButtonWidget.builder(Text.literal("Fertig"), b -> close())
                .dimensions(x, y, w, 20).build());
    }

    private int row(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set, Runnable open) {
        ButtonWidget toggle = ButtonWidget.builder(toggleText(get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            AdhdModuleState.save();
            b.setMessage(toggleText(get.getAsBoolean()));
        }).dimensions(x, y, TOGGLE_W, 20).build();
        addDrawableChild(toggle);

        addDrawableChild(ButtonWidget.builder(Text.literal(name), b -> open.run())
                .dimensions(x + TOGGLE_W + 6, y, w - TOGGLE_W - 6, 20).build());
        return y + ROW_H;
    }

    private static Text toggleText(boolean on) {
        return on ? Text.literal("An").formatted(Formatting.GREEN) : Text.literal("Aus").formatted(Formatting.RED);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 70, 0xFFFFE066);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
