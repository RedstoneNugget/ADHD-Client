package com.adhdclient.esp;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Einstellungen des ESP: welche Entities, welche Anzeige-Funktionen, Farbe und Reichweite. */
public final class EspScreen extends ThemedScreen {

    private static final int W = 300;
    private static final int ROW = 20;

    private int x;
    private int colW;
    private int y;
    private int col;
    private static final int SWATCH = 17;
    private static final int SWATCH_STEP = 20;
    private static final String[] PALETTE = {"#FF3B3B", "#FF8C1A", "#FFE53B", "#7CFF3B", "#2ECC71",
            "#3BE8FF", "#3B7BFF", "#A23BFF", "#FF3BC4", "#FFFFFF"};

    private int paletteX;
    private int paletteY;

    public EspScreen(Screen parent) {
        super(Text.literal("ESP"), parent);
    }

    private static EspConfig cfg() {
        return EspClient.config();
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + 12 + 3 * ROW + 6 + 12 + 3 * ROW + 16;
        y = centeredPanel(W, h);
        x = panelX1 + 8;
        int inner = W - 16;
        colW = (inner - 8) / 3;
        col = 0;
        EspConfig c = cfg();

        section(L.t("Ziele", "Targets"), x, y, inner);
        y += 12;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Entities auswählen … (", "Select entities … (") + c.entities.size() + ")"),
                b -> client.setScreen(new EntityPickerScreen(this))).dimensions(x, y, inner, ROW - 2).build());
        y += ROW + 6;

        section(L.t("Anzeige", "Display"), x, y, inner);
        y += 12;
        flag("Box", () -> c.box, v -> c.box = v, L.t("3D-Box um jede ausgewählte Entity", "3D box around every selected entity"));
        flag(L.t("Füllung", "Fill"), () -> c.fill, v -> c.fill = v, L.t("Box halbdurchsichtig füllen", "Fill the box semi-transparently"));
        flag("Glow", () -> c.glow, v -> c.glow = v, L.t("Leuchtender Umriss, wie der Glowing-Effekt", "Glowing outline, like the Glowing effect"));
        flag("Tracer", () -> c.tracers, v -> c.tracers = v, L.t("Linie vom Fadenkreuz zu jeder Entity", "Line from your crosshair to every entity"));
        flag(L.t("Namen", "Names"), () -> c.names, v -> c.names = v, L.t("Namensschild über der Entity, auch durch Wände", "Name tag above the entity, also through walls"));
        flag(L.t("Entfernung", "Distance"), () -> c.distance, v -> c.distance = v, L.t("Entfernung im Namensschild", "Distance in the name tag"));
        flag(L.t("Leben", "Health"), () -> c.health, v -> c.health = v, L.t("Herzen im Namensschild (manche Server schicken falsche Werte)", "Hearts in the name tag (some servers send fake values)"));
        flag(L.t("Unsichtbare", "Invisible"), () -> c.showInvisible, v -> c.showInvisible = v, L.t("Auch unsichtbare Entities anzeigen", "Also show invisible entities"));
        if (col != 0) {
            col = 0;
            y += ROW;
        }
        y += 6;

        section(L.t("Farbe und Reichweite", "Color and range"), x, y, inner);
        y += 12;
        int third = (inner - 8) / 3;
        modeButton(EspConfig.ColorMode.DISTANCE, x, y, third,
                L.t("Nah = rot, mittel = gelb, weit weg = grün", "Near = red, medium = yellow, far away = green"));
        modeButton(EspConfig.ColorMode.TEAM, x + third + 4, y, third,
                L.t("Teamfarbe vom Server (z. B. bei Minigames)", "Team color from the server (e.g. in minigames)"));
        modeButton(EspConfig.ColorMode.FIXED, x + 2 * (third + 4), y, inner - 2 * (third + 4),
                L.t("Immer die Farbe, die du unten auswählst", "Always the color you pick below"));
        y += ROW;

        // Farbpalette: ein Klick wählt die Farbe (und schaltet auf "Feste Farbe"); rechts eigene Farbe als #RRGGBB
        paletteX = x;
        paletteY = y + 1;
        int fieldX = x + PALETTE.length * SWATCH_STEP;
        TextFieldWidget color = new TextFieldWidget(textRenderer, fieldX, y, x + inner - fieldX, ROW - 2, Text.literal("#RRGGBB"));
        color.setMaxLength(7);
        color.setText(c.fixedColor);
        color.setChangedListener(s -> {
            boolean ok = EspConfig.isValidColor(s);
            color.setEditableColor(ok ? 0xFFE0E0E0 : 0xFFFF5555);
            if (ok && !s.equalsIgnoreCase(c.fixedColor)) {
                c.fixedColor = s.startsWith("#") ? s.toUpperCase(java.util.Locale.ROOT) : "#" + s.toUpperCase(java.util.Locale.ROOT);
                c.colorMode = EspConfig.ColorMode.FIXED;
                EspClient.onConfigEdited();
            }
        });
        color.setTooltip(Tooltip.of(Text.literal(L.t("Eigene Farbe als #RRGGBB, z. B. #FF8800", "Your own color as #RRGGBB, e.g. #FF8800"))));
        addDrawableChild(color);
        y += ROW;

        int doneW = 70;
        addDrawableChild(new ValueSlider(x, y, inner - doneW - 4, ROW - 2, L.t("Reichweite", "Range"), 16, 1024, 16, c.range,
                v -> (int) Math.round(v) + " m", v -> {
            c.range = (int) Math.round(v);
            EspClient.onConfigEdited();
        }));
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close())
                .dimensions(x + inner - doneW, y, doneW, ROW - 2).build());
        status(c.entities.size() + L.t(" Entity-Typen ausgewählt", " entity types selected"));
    }

    private void flag(String label, BooleanSupplier get, Consumer<Boolean> set, String tooltip) {
        toggle(label, get, set, x + col * (colW + 4), y, colW, ROW - 2, tooltip, EspClient::onConfigEdited);
        if (++col >= 3) {
            col = 0;
            y += ROW;
        }
    }

    /** Ein Knopf pro Farbmodus; der aktive leuchtet wie ein eingeschalteter Schalter. */
    private void modeButton(EspConfig.ColorMode mode, int bx, int by, int w, String tooltip) {
        toggle(mode.title(), () -> cfg().colorMode == mode, v -> cfg().colorMode = mode, bx, by, w, ROW - 2, tooltip,
                EspClient::onConfigEdited);
    }

    private int swatchAt(double mx, double my) {
        if (my < paletteY || my >= paletteY + SWATCH || mx < paletteX) return -1;
        int i = (int) ((mx - paletteX) / SWATCH_STEP);
        if (i >= PALETTE.length || mx - paletteX - i * SWATCH_STEP >= SWATCH) return -1;
        return i;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        EspConfig c = cfg();
        boolean fixed = c.colorMode == EspConfig.ColorMode.FIXED;
        int hovered = swatchAt(mouseX, mouseY);
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = paletteX + i * SWATCH_STEP;
            int rgb = EspConfig.rgb(PALETTE[i], 0x555555);
            // ohne "Feste Farbe" etwas abgedunkelt, damit klar ist, dass die Palette gerade nicht benutzt wird
            Theme.swatch(ctx, sx, paletteY, SWATCH, fixed || i == hovered ? rgb : Theme.mix(rgb, Theme.config().background, 0.55f));
            boolean selected = fixed && PALETTE[i].equalsIgnoreCase(c.fixedColor);
            if (selected || i == hovered) {
                Theme.border(ctx, sx - 1, paletteY - 1, sx + SWATCH + 1, paletteY + SWATCH + 1, selected ? 0xFFFFFFFF : Theme.textDim());
            }
        }
        if (hovered >= 0) {
            ctx.drawTooltip(textRenderer, Text.literal(L.t("Klicken: diese Farbe benutzen", "Click: use this color")), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int i = click.button() == 0 ? swatchAt(click.x(), click.y()) : -1;
        if (i >= 0) {
            EspConfig c = cfg();
            c.fixedColor = PALETTE[i];
            c.colorMode = EspConfig.ColorMode.FIXED;
            EspClient.onConfigEdited();
            clearAndInit();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    protected void onClose() {
        EspClient.saveNow();
    }
}
