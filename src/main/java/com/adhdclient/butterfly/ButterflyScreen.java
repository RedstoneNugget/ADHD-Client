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
    private static final String TEMPLATE = "Butterfly-Vorlage";

    public ButterflyScreen(Screen parent) {
        super(Text.literal("Butterfly"), parent);
    }

    @Override
    protected void build() {
        int looks = ButterflyLook.values().length;
        int h = Theme.TITLE_H + 6 + 12 + ROW + looks * ROW + 6 + 12 + 2 * ROW + 6 + 12 + 2 * ROW + 6 + ROW + 16;
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

        section(L.t("Eigene Modelle (Blockbench)", "Own models (Blockbench)"), x, y, inner);
        y += 12;
        int half = (inner - 4) / 2;
        java.util.List<String> models = ButterflyCustomModel.names();
        String current = c.model.isEmpty() ? L.t("Butterfly Fade", "Butterfly Fade") : c.model;
        ButtonWidget model = ButtonWidget.builder(Text.literal(L.t("Modell: ", "Model: ") + current), b -> {
            // weiterschalten: eingebautes Butterfly → eigene Modelle → wieder eingebaut
            int i = models.indexOf(c.model);
            c.model = i + 1 < models.size() ? models.get(i + 1) : "";
            ButterflyClient.onConfigEdited();
            clearAndInit();
        }).dimensions(x, y, half, ROW - 2).build();
        model.setTooltip(Tooltip.of(Text.literal(c.model.isEmpty()
                ? L.t("Klicken wechselt zu deinen eigenen Modellen (", "Click to switch to your own models (") + models.size() + ")"
                : ButterflyCustomModel.status())));
        addDrawableChild(model);
        ButtonWidget folder = ButtonWidget.builder(Text.literal(L.t("Modelle-Ordner", "Models folder")), b -> {
            try {
                java.nio.file.Files.createDirectories(ButterflyCustomModel.folder());
            } catch (Exception ignored) {
            }
            net.minecraft.util.Util.getOperatingSystem().open(ButterflyCustomModel.folder());
        }).dimensions(x + half + 4, y, inner - half - 4, ROW - 2).build();
        folder.setTooltip(Tooltip.of(Text.literal(L.t("Hier .bbmodel-Dateien ablegen (oder .gltf/.glb/.zip zum Umwandeln)",
                "Put .bbmodel files here (or .gltf/.glb/.zip to convert)"))));
        addDrawableChild(folder);
        y += ROW;
        ButtonWidget make = ButtonWidget.builder(Text.literal(L.t("Vorlage erstellen", "Create template")), b -> {
            try {
                ButterflyBlockbench.write(ButterflyCustomModel.folder().resolve(TEMPLATE + ".bbmodel"));
                c.model = TEMPLATE;
                ButterflyClient.onConfigEdited();
                net.minecraft.util.Util.getOperatingSystem().open(ButterflyCustomModel.folder());
            } catch (Exception e) {
                ButterflyClient.LOGGER.warn("Butterfly: Vorlage konnte nicht geschrieben werden", e);
            }
            clearAndInit();
        }).dimensions(x, y, half, ROW - 2).build();
        make.setTooltip(Tooltip.of(Text.literal(L.t("Legt das Butterfly mit allen Animationen als " + TEMPLATE + ".bbmodel an – zum Bearbeiten in Blockbench (überschreibt die Vorlage)",
                "Writes the butterfly with all animations as " + TEMPLATE + ".bbmodel to edit in Blockbench (overwrites the template)"))));
        addDrawableChild(make);
        ButtonWidget gltf = ButtonWidget.builder(Text.literal(L.t("glTF umwandeln", "Convert glTF")), b -> {
            java.util.List<String> made = ButterflyGltfImport.convertAll();
            if (!made.isEmpty()) {
                c.model = made.get(0);
                ButterflyClient.onConfigEdited();
            }
            clearAndInit();
        }).dimensions(x + half + 4, y, inner - half - 4, ROW - 2).build();
        gltf.setTooltip(Tooltip.of(Text.literal(L.t("Wandelt .gltf/.glb/.zip im Modelle-Ordner in .bbmodel um",
                "Converts .gltf/.glb/.zip in the models folder into .bbmodel"))));
        addDrawableChild(gltf);
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
