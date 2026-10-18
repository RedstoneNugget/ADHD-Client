package com.adhdclient.esp;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
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
    private int swatchX;
    private int swatchY;

    public EspScreen(Screen parent) {
        super(Text.literal("ESP"), parent);
    }

    private static EspConfig cfg() {
        return EspClient.config();
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + 12 + 4 * ROW + 6 + 12 + 2 * ROW + 6 + ROW + 16;
        y = centeredPanel(W, h);
        x = panelX1 + 8;
        int inner = W - 16;
        colW = (inner - 4) / 2;
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
        y += 6;

        section(L.t("Farbe und Reichweite", "Color and range"), x, y, inner);
        y += 12;
        ButtonWidget mode = ButtonWidget.builder(modeText(), b -> {
            c.colorMode = c.colorMode.next();
            b.setMessage(modeText());
            EspClient.onConfigEdited();
        }).dimensions(x, y, colW, ROW - 2).build();
        mode.setTooltip(Tooltip.of(Text.literal(L.t("Entfernung: nah rot, weit grün · Team: Teamfarbe vom Server · Feste Farbe: rechts", "Distance: near red, far green · Team: team color from the server · Fixed color: on the right"))));
        addDrawableChild(mode);

        TextFieldWidget color = new TextFieldWidget(textRenderer, x + colW + 4, y, colW - 22, ROW - 2, Text.literal("Farbe"));
        color.setMaxLength(7);
        color.setText(c.fixedColor);
        color.setChangedListener(s -> {
            boolean ok = EspConfig.isValidColor(s);
            color.setEditableColor(ok ? 0xFFE0E0E0 : 0xFFFF5555);
            if (ok) {
                c.fixedColor = s;
                EspClient.onConfigEdited();
            }
        });
        color.setTooltip(Tooltip.of(Text.literal(L.t("Feste Farbe als #RRGGBB", "Fixed color as #RRGGBB"))));
        addDrawableChild(color);
        swatchX = x + inner - 16;
        swatchY = y + 1;
        y += ROW;

        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Reichweite", "Range"), 16, 1024, 16, c.range,
                v -> (int) Math.round(v) + " m", v -> {
            c.range = (int) Math.round(v);
            EspClient.onConfigEdited();
        }));
        y += ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close())
                .dimensions(x, y, inner, ROW - 2).build());
        status(c.entities.size() + L.t(" Entity-Typen ausgewählt", " entity types selected"));
    }

    private void flag(String label, BooleanSupplier get, Consumer<Boolean> set, String tooltip) {
        toggle(label, get, set, x + col * (colW + 4), y, colW, ROW - 2, tooltip, EspClient::onConfigEdited);
        if (++col >= 2) {
            col = 0;
            y += ROW;
        }
    }

    private static Text modeText() {
        return Text.literal(L.t("Farbe: ", "Color: ") + cfg().colorMode.title());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        Theme.swatch(ctx, swatchX, swatchY, 16, EspConfig.rgb(cfg().fixedColor, 0x555555));
    }

    @Override
    protected void onClose() {
        EspClient.saveNow();
    }
}
