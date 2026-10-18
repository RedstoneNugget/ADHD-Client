package com.adhdclient.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Basis für alle Menüs des ADHD Client: ein Panel mit Titelleiste und X, Abschnitts-Überschriften und
 * Knöpfe im eigenen Look. Die Widgets bleiben normale Minecraft-Widgets (Klicks, Tastatur, Tooltips),
 * nur ihr Aussehen wird nach dem Zeichnen überdeckt.
 */
public abstract class ThemedScreen extends Screen {

    private record Section(String text, int x, int y, int width) {
    }

    private record Line(String text, int x, int y, int color) {
    }

    protected final Screen parent;
    private final Map<ClickableWidget, BooleanSupplier> toggles = new IdentityHashMap<>();
    private final List<Section> sections = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    protected int panelX1;
    protected int panelY1;
    protected int panelX2;
    protected int panelY2;
    private String status = "";

    protected ThemedScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** Baut den Inhalt; vorher werden Panel-Daten zurückgesetzt. */
    protected abstract void build();

    @Override
    protected final void init() {
        toggles.clear();
        sections.clear();
        lines.clear();
        status = "";
        listening = null;
        build();
    }

    // ---------------------------------------------------------------- Tasten einstellen

    private record KeyTarget(ButtonWidget button, String label, java.util.function.IntConsumer set, Runnable changed) {
    }

    private KeyTarget listening;

    /** Knopf "Label: Taste"; Klick, dann neue Taste drücken (Esc = abbrechen, Entf/Rücktaste = keine Taste). */
    protected ButtonWidget keyButton(String label, java.util.function.IntSupplier get, java.util.function.IntConsumer set,
                                     int x, int y, int w, int h, Runnable changed) {
        ButtonWidget[] ref = new ButtonWidget[1];
        ButtonWidget b = ButtonWidget.builder(Text.literal(label + ": " + HotKey.name(get.getAsInt())), btn -> {
            listening = new KeyTarget(ref[0], label, set, changed);
            btn.setMessage(Text.literal(label + L.t(": > Taste drücken <", ": > press a key <")));
        }).dimensions(x, y, w, h).build();
        b.setTooltip(Tooltip.of(Text.literal(L.t("Klicken, dann die neue Taste drücken. Esc = abbrechen, Entf = keine Taste.", "Click, then press the new key. Esc = cancel, Delete = no key."))));
        ref[0] = b;
        toggles.put(b, () -> listening != null && listening.button == ref[0]);
        return addDrawableChild(b);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (listening != null) {
            KeyTarget t = listening;
            listening = null;
            int code = input.key();
            if (code != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE || code == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) code = HotKey.NONE;
                t.set.accept(code);
                if (t.changed != null) t.changed.run();
                t.button.setMessage(Text.literal(t.label + ": " + HotKey.name(code)));
            } else {
                clearAndInit();
            }
            return true;
        }
        return super.keyPressed(input);
    }

    /** Panel-Größe festlegen; legt auch das X oben rechts an. */
    protected void panel(int x1, int y1, int x2, int y2) {
        panelX1 = x1;
        panelY1 = y1;
        panelX2 = x2;
        panelY2 = y2;
        addDrawableChild(ButtonWidget.builder(Text.literal("×"), b -> close()).dimensions(x2 - 15, y1 + 2, 12, 12).build());
    }

    /** Panel zentriert mit der gegebenen Größe; gibt die y-Koordinate unter der Titelleiste zurück. */
    protected int centeredPanel(int w, int h) {
        int x1 = (width - w) / 2;
        int y1 = Math.max(4, (height - h) / 2);
        panel(x1, y1, x1 + w, Math.min(height - 4, y1 + h));
        return y1 + Theme.TITLE_H + 6;
    }

    protected void section(String text, int x, int y, int w) {
        sections.add(new Section(text, x, y, w));
    }

    protected void line(String text, int x, int y, int color) {
        lines.add(new Line(text, x, y, color));
    }

    protected void status(String text) {
        status = text;
    }

    /** Schalter: grüner Rand = an, roter Rand = aus. */
    protected ButtonWidget toggle(String label, BooleanSupplier get, Consumer<Boolean> set, int x, int y, int w, int h,
                                  String tooltip, Runnable changed) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), btn -> {
            set.accept(!get.getAsBoolean());
            if (changed != null) changed.run();
        }).dimensions(x, y, w, h).build();
        if (tooltip != null) b.setTooltip(Tooltip.of(Text.literal(tooltip)));
        toggles.put(b, get);
        return addDrawableChild(b);
    }

    /** Für Widgets, die nachträglich (z. B. beim Scrollen) als Schalter registriert werden. */
    protected void markToggle(ClickableWidget w, BooleanSupplier get) {
        toggles.put(w, get);
    }

    protected void unmark(ClickableWidget w) {
        toggles.remove(w);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        if (panelX2 > panelX1) Theme.panel(ctx, panelX1, panelY1, panelX2, panelY2, title);
        for (Section s : sections) Theme.section(ctx, s.x, s.y, s.width, s.text);
        for (Line l : lines) ctx.drawTextWithShadow(textRenderer, l.text, l.x, l.y, l.color);
        if (!status.isEmpty() && panelX2 > panelX1) {
            int dim = Theme.argb(255, Theme.mix(Theme.config().background, Theme.config().text, 0.18f));
            ctx.fill(panelX1 + 2, panelY2 - 13, panelX2 - 1, panelY2 - 12, dim);
            ctx.drawTextWithShadow(textRenderer, status, panelX1 + 7, panelY2 - 10, Theme.textDim());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (Element e : children()) {
            if (!(e instanceof ClickableWidget w) || !w.visible) continue;
            if (w instanceof ValueSlider s) Theme.slider(ctx, s);
            else if (w instanceof TextFieldWidget f) Theme.field(ctx, f);
            else if (w instanceof ButtonWidget) {
                BooleanSupplier on = toggles.get(w);
                Theme.button(ctx, w, on == null ? Theme.State.NEUTRAL : (on.getAsBoolean() ? Theme.State.ON : Theme.State.OFF));
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        onClose();
        if (client != null) client.setScreen(parent);
    }

    /** Vor dem Schließen (z. B. Speichern). */
    protected void onClose() {
    }
}
