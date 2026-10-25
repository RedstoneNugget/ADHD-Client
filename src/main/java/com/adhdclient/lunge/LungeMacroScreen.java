package com.adhdclient.lunge;

import com.adhdclient.core.AdhdModuleState;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Lunge Macro: Taste, Halten-Wiederholen und Abstand. */
public final class LungeMacroScreen extends ThemedScreen {

    private static final int W = 260;
    private static final int ROW = 20;

    public LungeMacroScreen(Screen parent) {
        super(Text.literal("Lunge Macro"), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + 12 + 2 * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;

        section(L.t("Taste", "Key"), x, y, inner);
        y += 12;
        keyButton("Lunge", () -> LungeMacroClient.key, v -> LungeMacroClient.key = v, GLFW.GLFW_KEY_V, x, y, inner, ROW - 2, AdhdModuleState::save);
        y += ROW + 6;

        section(L.t("Halten", "Hold"), x, y, inner);
        y += 12;
        toggle(L.t("Halten = wiederholen", "Hold = repeat"), () -> LungeMacroClient.repeat, v -> LungeMacroClient.repeat = v, x, y, inner, ROW - 2,
                L.t("Solange die Taste gedrückt ist, wird immer wieder gelunged", "While the key is held, it keeps lunging"), AdhdModuleState::save);
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Abstand", "Interval"), 1, 40, 1, LungeMacroClient.delay,
                v -> (int) Math.round(v) + L.t(" Ticks", " ticks"), v -> LungeMacroClient.delay = (int) Math.round(v)));
        y += ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(LungeMacroClient.enabled ? L.t("Speer mit Lunge in die Hotbar legen, dann Taste drücken", "Put a lunge spear in your hotbar, then press the key")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        AdhdModuleState.save();
    }

    @Override
    protected Runnable resetAction() {
        return () -> {
            LungeMacroClient.key = org.lwjgl.glfw.GLFW.GLFW_KEY_V;
            LungeMacroClient.repeat = true;
            LungeMacroClient.delay = 10;
            com.adhdclient.core.AdhdModuleState.save();
        };
    }
}
