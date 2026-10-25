package com.adhdclient.macros;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** Makros bearbeiten: je Zeile Taste, Text („/befehl“ oder Nachricht) und Löschen; unten Hinzufügen. */
public final class MacrosScreen extends ThemedScreen {

    private static final int W = 320;
    private static final int ROW = 20;

    public MacrosScreen(Screen parent) {
        super(Text.literal(L.t("Makros", "Macros")), parent);
    }

    @Override
    protected void build() {
        List<MacrosClient.Macro> macros = MacrosClient.config().macros;
        int h = Theme.TITLE_H + 6 + 12 + Math.max(1, macros.size()) * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        int keyW = 96, delW = 18;

        section(L.t("Taste → Befehl oder Nachricht", "Key → command or message"), x, y, inner);
        y += 12;
        if (macros.isEmpty()) {
            line(L.t("Noch keine Makros – unten hinzufügen", "No macros yet – add one below"), x, y + 5, Theme.textDim());
            y += ROW;
        }
        for (int i = 0; i < macros.size(); i++) {
            MacrosClient.Macro m = macros.get(i);
            keyButton(String.valueOf(i + 1), () -> m.key, v -> m.key = v, com.adhdclient.gui.HotKey.NONE,
                    x, y, keyW, ROW - 2, MacrosClient::save);
            TextFieldWidget field = new TextFieldWidget(textRenderer, x + keyW + 4, y, inner - keyW - delW - 8, ROW - 2, Text.literal("Text"));
            field.setMaxLength(512);
            field.setText(m.text);
            field.setPlaceholder(Text.literal(L.t("/befehl oder Nachricht", "/command or message")).formatted(Formatting.DARK_GRAY));
            field.setTooltip(Tooltip.of(Text.literal(L.t("Mit / am Anfang = Befehl, sonst Chat-Nachricht. Mehrere mit ;; trennen, z. B. /home;;Bin da!",
                    "Starting with / = command, otherwise chat message. Separate several with ;; e.g. /home;;I'm here!"))));
            field.setChangedListener(s -> m.text = s);
            addDrawableChild(field);
            int idx = i;
            ButtonWidget del = ButtonWidget.builder(Text.literal("✕"), b -> {
                macros.remove(idx);
                MacrosClient.save();
                clearAndInit();
            }).dimensions(x + inner - delW, y, delW, ROW - 2).build();
            del.setTooltip(Tooltip.of(Text.literal(L.t("Makro löschen", "Delete macro"))));
            addDrawableChild(del);
            y += ROW;
        }
        y += 6;
        int half = (inner - 4) / 2;
        ButtonWidget add = ButtonWidget.builder(Text.literal(L.t("+ Makro", "+ Macro")), b -> {
            macros.add(new MacrosClient.Macro());
            MacrosClient.save();
            clearAndInit();
        }).dimensions(x, y, half, ROW - 2).build();
        add.active = macros.size() < MacrosClient.MAX;
        addDrawableChild(add);
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x + half + 4, y, inner - half - 4, ROW - 2).build());
        status(MacrosClient.enabled ? (MacrosClient.lastSent.isEmpty() ? L.t("Tasten wirken nur ohne offenes Menü", "Keys only work with no menu open")
                : L.t("Zuletzt: ", "Last: ") + MacrosClient.lastSent)
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        MacrosClient.save();
    }
}
