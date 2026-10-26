package com.adhdclient.extras;

import com.adhdclient.gui.Defaults;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Einstellungsfenster für die kleineren Module: Tasten oben, dann Schalter (zwei pro Zeile), Regler und Farben,
 * unten „Fertig“ und der Zurücksetzen-Knopf. Wird mit {@link #builder} beschrieben.
 */
public final class OptionsScreen extends ThemedScreen {

    private static final int W = 270;
    private static final int ROW = 20;
    /** Farben zum Durchklicken. */
    private static final int[] PALETTE = {0xFF3030, 0xFF9020, 0xFFD040, 0x30FF60, 0x55FFFF, 0x4080FF, 0xC060FF, 0xFFFFFF};

    private sealed interface Opt permits Toggle, Slider, Key, Color, Action {
    }

    private record Toggle(String label, String tip, BooleanSupplier get, Consumer<Boolean> set) implements Opt {
    }

    private record Slider(String label, double min, double max, double step, DoubleSupplier get, DoubleConsumer set,
                          DoubleFunction<String> fmt) implements Opt {
    }

    private record Key(String label, IntSupplier get, IntConsumer set, int def) implements Opt {
    }

    private record Color(String label, IntSupplier get, IntConsumer set) implements Opt {
    }

    private record Action(String label, String tip, Runnable run) implements Opt {
    }

    public static final class Builder {
        private final String title;
        private final Extras.Settings settings;
        private final List<Opt> opts = new ArrayList<>();
        private Supplier<String> hint = () -> "";

        private Builder(String title, Extras.Settings settings) {
            this.title = title;
            this.settings = settings;
        }

        public Builder toggle(String label, String tip, BooleanSupplier get, Consumer<Boolean> set) {
            opts.add(new Toggle(label, tip, get, set));
            return this;
        }

        public Builder slider(String label, double min, double max, double step, DoubleSupplier get, DoubleConsumer set, DoubleFunction<String> fmt) {
            opts.add(new Slider(label, min, max, step, get, set, fmt));
            return this;
        }

        public Builder intSlider(String label, int min, int max, IntSupplier get, IntConsumer set, String unit) {
            return slider(label, min, max, 1, get::getAsInt, v -> set.accept((int) Math.round(v)), v -> (int) Math.round(v) + unit);
        }

        public Builder key(String label, IntSupplier get, IntConsumer set, int def) {
            opts.add(new Key(label, get, set, def));
            return this;
        }

        public Builder color(String label, IntSupplier get, IntConsumer set) {
            opts.add(new Color(label, get, set));
            return this;
        }

        public Builder action(String label, String tip, Runnable run) {
            opts.add(new Action(label, tip, run));
            return this;
        }

        /** Hinweis unten in der Statuszeile, solange das Modul an ist. */
        public Builder hint(Supplier<String> hint) {
            this.hint = hint;
            return this;
        }

        public OptionsScreen build(Screen parent) {
            return new OptionsScreen(this, parent);
        }
    }

    public static Builder builder(String title, Extras.Settings settings) {
        return new Builder(title, settings);
    }

    private final Builder spec;

    private OptionsScreen(Builder spec, Screen parent) {
        super(Text.literal(spec.title), parent);
        this.spec = spec;
    }

    @Override
    protected void build() {
        int inner = W - 16;
        int half = (inner - 4) / 2;
        int rows = 0;
        int pendingToggles = 0;
        for (Opt o : spec.opts) {
            if (o instanceof Toggle) {
                if (pendingToggles++ % 2 == 0) rows++;
            } else {
                pendingToggles = 0;
                rows++;
            }
        }
        int h = Theme.TITLE_H + 6 + rows * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int col = 0;
        for (Opt o : spec.opts) {
            if (!(o instanceof Toggle) && col == 1) {
                col = 0;
                y += ROW;
            }
            switch (o) {
                case Toggle t -> {
                    toggle(t.label(), t.get(), t.set(), x + col * (half + 4), y, half, ROW - 2, t.tip(), Extras::save);
                    if (++col == 2) {
                        col = 0;
                        y += ROW;
                    }
                }
                case Slider s -> {
                    addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, s.label(), s.min(), s.max(), s.step(), s.get().getAsDouble(),
                            s.fmt(), v -> {
                        s.set().accept(v);
                        Extras.save();
                    }));
                    y += ROW;
                }
                case Key k -> {
                    keyButton(k.label(), k.get(), k.set(), k.def(), x, y, inner, ROW - 2, Extras::save);
                    y += ROW;
                }
                case Color c -> {
                    ButtonWidget[] ref = new ButtonWidget[1];
                    ref[0] = addDrawableChild(ButtonWidget.builder(colorText(c), b -> {
                        int i = 0;
                        while (i < PALETTE.length && PALETTE[i] != c.get().getAsInt()) i++;
                        c.set().accept(PALETTE[(i + 1) % PALETTE.length]);
                        Extras.save();
                        ref[0].setMessage(colorText(c));
                    }).dimensions(x, y, inner, ROW - 2).build());
                    ref[0].setTooltip(Tooltip.of(Text.literal(L.t("Klicken für die nächste Farbe", "Click for the next color"))));
                    y += ROW;
                }
                case Action a -> {
                    ButtonWidget b = addDrawableChild(ButtonWidget.builder(Text.literal(a.label()), btn -> a.run().run())
                            .dimensions(x, y, inner, ROW - 2).build());
                    if (a.tip() != null) b.setTooltip(Tooltip.of(Text.literal(a.tip())));
                    y += ROW;
                }
            }
        }
        if (col == 1) y += ROW;
        y += 6;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(spec.settings.enabled ? spec.hint.get() : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    private static Text colorText(Color c) {
        return Text.literal(c.label() + ": ").append(Text.literal("■■■").styled(s -> s.withColor(c.get().getAsInt())));
    }

    @Override
    protected void onClose() {
        Extras.save();
    }

    @Override
    protected Runnable resetAction() {
        return () -> {
            boolean on = spec.settings.enabled;
            Defaults.reset(spec.settings);
            spec.settings.enabled = on;
            Extras.save();
        };
    }
}
