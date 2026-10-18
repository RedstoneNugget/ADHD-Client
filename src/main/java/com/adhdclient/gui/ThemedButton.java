package com.adhdclient.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

import java.util.function.Supplier;

/**
 * Knopf, der sich selbst im ADHD-Look zeichnet (für Menüs, die nicht von {@link ThemedScreen} kommen, z. B. Truhen).
 * Beschriftung und Zustand werden jedes Bild neu abgefragt; Tooltips liegen dadurch normal darüber.
 */
public class ThemedButton extends ButtonWidget {

    private final Supplier<net.minecraft.text.Text> label;
    private final Supplier<Theme.State> state;

    public ThemedButton(int x, int y, int width, int height, Supplier<net.minecraft.text.Text> label, Supplier<Theme.State> state, PressAction onPress) {
        super(x, y, width, height, label.get(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.label = label;
        this.state = state;
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        setMessage(label.get());
        Theme.button(context, this, state.get());
    }
}
