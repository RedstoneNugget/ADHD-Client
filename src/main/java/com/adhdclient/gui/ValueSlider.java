package com.adhdclient.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * Schieberegler mit Grenzen, Schrittweite und Beschriftung "Name: Wert". Bei sehr großen Bereichen verläuft er
 * quadratisch (vorne fein, hinten grob); das Mausrad ändert um einen Schritt, mit Shift um zehn.
 */
public class ValueSlider extends SliderWidget {

    private final double min;
    private final double max;
    private final double step;
    private final boolean curved;
    private final String label;
    private final DoubleFunction<String> format;
    private final DoubleConsumer onChange;

    public ValueSlider(int x, int y, int w, int h, String label, double min, double max, double step, double value,
                       DoubleFunction<String> format, DoubleConsumer onChange) {
        super(x, y, w, h, Text.empty(), 0);
        this.min = min;
        this.max = Math.max(min + step, max);
        this.step = step;
        this.curved = (this.max - min) / step > 200;
        this.label = label;
        this.format = format;
        this.onChange = onChange;
        this.value = fraction(value);
        updateMessage();
    }

    private double fraction(double v) {
        double f = Math.max(0, Math.min(1, (v - min) / Math.max(1e-9, max - min)));
        return curved ? Math.sqrt(f) : f;
    }

    /** Wert von außen setzen, ohne den Änderungs-Callback auszulösen. */
    public void setCurrent(double v) {
        value = fraction(v);
        updateMessage();
    }

    public double fraction() {
        return value;
    }

    public double current() {
        double f = curved ? value * value : value;
        double v = min + f * (max - min);
        v = Math.round(v / step) * step;
        return Math.max(min, Math.min(max, v));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0) return false;
        double n = MinecraftClient.getInstance().isShiftPressed() ? 10 : 1;
        value = fraction(Math.max(min, Math.min(max, current() + Math.signum(vertical) * step * n)));
        updateMessage();
        applyValue();
        return true;
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal(label + ": " + format.apply(current())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(current());
    }
}
