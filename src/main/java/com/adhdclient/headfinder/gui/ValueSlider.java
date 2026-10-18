package com.adhdclient.headfinder.gui;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** Schieberegler mit Grenzen, Schrittweite und Beschriftung "Name: Wert". */
public class ValueSlider extends SliderWidget {

    private final double min;
    private final double max;
    private final double step;
    private final String label;
    private final DoubleFunction<String> format;
    private final DoubleConsumer onChange;

    public ValueSlider(int x, int y, int w, int h, String label, double min, double max, double step, double value,
                       DoubleFunction<String> format, DoubleConsumer onChange) {
        super(x, y, w, h, Text.empty(), fraction(value, min, max));
        this.min = min;
        this.max = Math.max(min + step, max);
        this.step = step;
        this.label = label;
        this.format = format;
        this.onChange = onChange;
        updateMessage();
    }

    private static double fraction(double v, double min, double max) {
        return Math.max(0, Math.min(1, (v - min) / Math.max(1e-9, max - min)));
    }

    public double current() {
        double v = min + value * (max - min);
        v = Math.round(v / step) * step;
        return Math.max(min, Math.min(max, v));
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
