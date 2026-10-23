package com.adhdclient.butterfly;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** Einstellungen von Butterfly: Inspect-Taste, welche Items welcher Look werden, Tempo, Animationen. */
public final class ButterflyScreen extends ThemedScreen {

    private static final int W = 300;
    private static final int ROW = 20;

    public ButterflyScreen(Screen parent) {
        super(Text.literal("Butterfly"), parent);
    }

    @Override
    protected void build() {
        int looks = ButterflyLook.values().length;
        int h = Theme.TITLE_H + 6 + 12 + ROW + looks * ROW + 6 + 12 + 2 * ROW + 6 + 12 + ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        int third = (inner - 8) / 3;
        ButterflyConfig c = ButterflyClient.config();

        section(L.t("Taste und Items", "Key and items"), x, y, inner);
        y += 12;
        keyButton("Inspect", () -> c.inspectKey, v -> c.inspectKey = v, new ButterflyConfig().inspectKey, x, y, inner, ROW - 2, ButterflyClient::onConfigEdited);
        y += ROW;
        for (ButterflyLook look : ButterflyLook.values()) {
            int n = c.looks.getOrDefault(look.id, List.of()).size();
            ButtonWidget pick = ButtonWidget.builder(Text.literal(look.title() + ": " + n + L.t(" Items wählen …", " items – choose …")),
                    b -> client.setScreen(new ItemLookPickerScreen(this, look))).dimensions(x, y, inner, ROW - 2).build();
            pick.setTooltip(Tooltip.of(Text.literal(L.t("Diese Items sehen in deiner Hand aus wie ein ", "These items look like a ")
                    + look.title() + L.t(" (nur bei dir)", " in your hand (only for you)"))));
            addDrawableChild(pick);
            y += ROW;
        }
        y += 6;

        section(L.t("Bewegung", "Movement"), x, y, inner);
        y += 12;
        toggle(L.t("Aufklappen", "Open flip"), () -> c.equipFlip, v -> c.equipFlip = v, x, y, third, ROW - 2,
                L.t("Beim Ziehen klappt das Messer mit einem Flip auf", "The knife flips open when you pull it out"), ButterflyClient::onConfigEdited);
        toggle(L.t("Flip beim Schlag", "Flip on hit"), () -> c.attackFlip, v -> c.attackFlip = v, x + third + 4, y, third, ROW - 2,
                L.t("Beim Angriff ein schneller Schnitt", "A quick slash when you attack"), ButterflyClient::onConfigEdited);
        toggle(L.t("Spielen", "Fidget"), () -> c.idleFidget, v -> c.idleFidget = v, x + 2 * (third + 4), y, inner - 2 * (third + 4), ROW - 2,
                L.t("Im Stand spielt die Hand ab und zu mit dem Messer", "Standing still, your hand plays with the knife now and then"), ButterflyClient::onConfigEdited);
        y += ROW;
        addDrawableChild(new ValueSlider(x, y, inner, ROW - 2, L.t("Tempo", "Speed"), 0.5, 2.0, 0.1, c.speed,
                v -> String.format(Locale.ROOT, "×%.1f", v), v -> {
            c.speed = v;
            ButterflyClient.onConfigEdited();
        }));
        y += ROW + 6;

        section(L.t("Eigene Animationen (Blockbench)", "Own animations (Blockbench)"), x, y, inner);
        y += 12;
        int half = (inner - 4) / 2;
        boolean exists = java.nio.file.Files.exists(ButterflyAnimations.file());
        ButtonWidget make = ButtonWidget.builder(Text.literal(exists ? L.t("Vorlage neu erstellen", "Recreate template") : L.t("Vorlage erstellen", "Create template")), b -> {
            try {
                ButterflyBlockbench.write();
                net.minecraft.util.Util.getOperatingSystem().open(ButterflyAnimations.file().getParent());
            } catch (Exception e) {
                ButterflyClient.LOGGER.warn("Butterfly: Vorlage konnte nicht geschrieben werden", e);
            }
            clearAndInit();
        }).dimensions(x, y, half, ROW - 2).build();
        make.setTooltip(Tooltip.of(Text.literal(exists
                ? L.t("Überschreibt deine Datei mit dem Modell und den eingebauten Animationen", "Overwrites your file with the model and the built-in animations")
                : L.t("Legt butterfly.bbmodel mit Modell und Animationen zum Bearbeiten in Blockbench an", "Creates butterfly.bbmodel with the model and animations to edit in Blockbench"))));
        addDrawableChild(make);
        ButtonWidget folder = ButtonWidget.builder(Text.literal(L.t("Ordner öffnen", "Open folder")), b -> {
            try {
                java.nio.file.Files.createDirectories(ButterflyAnimations.file().getParent());
            } catch (Exception ignored) {
            }
            net.minecraft.util.Util.getOperatingSystem().open(ButterflyAnimations.file().getParent());
        }).dimensions(x + half + 4, y, inner - half - 4, ROW - 2).build();
        folder.setTooltip(Tooltip.of(Text.literal(ButterflyAnimations.status())));
        addDrawableChild(folder);
        y += ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(ButterflyClient.enabled ? L.t("Inspect mit ", "Inspect with ") + ButterflyClient.keyName() + L.t(" – nur du siehst das Messer", " – only you see the knife")
                : L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
    }

    @Override
    protected void onClose() {
        ButterflyClient.saveNow();
    }
}
