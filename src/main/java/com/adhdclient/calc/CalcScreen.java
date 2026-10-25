package com.adhdclient.calc;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Schnell-Rechner: Rechnung mit Minecraft-Einheiten, Items in Stacks/Shulker, Overworld ↔ Nether. */
public final class CalcScreen extends ThemedScreen {

    private static final int W = 300;
    private static final int ROW = 20;

    // bleibt zwischen dem Öffnen erhalten
    private static String expr = "";
    private static String items = "";
    private static String coordX = "", coordZ = "";
    private static boolean fromNether;

    private int exprY, itemsY, coordY, x0;

    public CalcScreen(Screen parent) {
        super(Text.literal(L.t("Rechner", "Calculator")), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + ROW + 3 * (12 + ROW + 12) + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        x0 = x;
        keyButton(L.t("Öffnen", "Open"), () -> CalcClient.key, v -> CalcClient.key = v, org.lwjgl.glfw.GLFW.GLFW_KEY_N, x, y, inner, ROW - 2,
                com.adhdclient.core.AdhdModuleState::save);
        y += ROW;

        section(L.t("Rechnung", "Math"), x, y, inner);
        y += 12;
        TextFieldWidget e = field(x, y, inner, expr, L.t("z. B. 3sh + 12st oder (64*27)/8", "e.g. 3sh + 12st or (64*27)/8"));
        e.setTooltip(Tooltip.of(Text.literal(L.t("+ − * / ^ ( )  ·  Einheiten: st = 64, sh = Shulker (1728), dk = Doppelkiste (3456), k = 1000",
                "+ − * / ^ ( )  ·  units: st = 64, sh = shulker (1728), dk = double chest (3456), k = 1000"))));
        e.setChangedListener(s -> expr = s);
        setInitialFocus(e);
        exprY = y + ROW;
        y += ROW + 12;

        section(L.t("Items → Stacks", "Items → stacks"), x, y, inner);
        y += 12;
        TextFieldWidget it = field(x, y, inner, items, L.t("Anzahl Items, z. B. 5000", "Number of items, e.g. 5000"));
        it.setChangedListener(s -> items = s);
        itemsY = y + ROW;
        y += ROW + 12;

        section(L.t("Koordinaten", "Coordinates"), x, y, inner);
        y += 12;
        int fw = (inner - 8 - 70 - 40) / 2;
        TextFieldWidget cx = field(x, y, fw, coordX, "X");
        cx.setChangedListener(s -> coordX = s);
        TextFieldWidget cz = field(x + fw + 4, y, fw, coordZ, "Z");
        cz.setChangedListener(s -> coordZ = s);
        addDrawableChild(ButtonWidget.builder(Text.literal(fromNether ? L.t("Nether →", "Nether →") : L.t("Welt →", "World →")), b -> {
            fromNether = !fromNether;
            clearAndInit();
        }).dimensions(x + 2 * fw + 8, y, 70, ROW - 2).build());
        ButtonWidget here = ButtonWidget.builder(Text.literal(L.t("Hier", "Here")), b -> {
            if (client.player == null) return;
            coordX = String.valueOf((int) Math.floor(client.player.getX()));
            coordZ = String.valueOf((int) Math.floor(client.player.getZ()));
            fromNether = client.player.getEntityWorld().getRegistryKey() == net.minecraft.world.World.NETHER;
            clearAndInit();
        }).dimensions(x + 2 * fw + 82, y, inner - 2 * fw - 82, ROW - 2).build();
        here.setTooltip(Tooltip.of(Text.literal(L.t("Deine aktuelle Position eintragen", "Fill in your current position"))));
        addDrawableChild(here);
        coordY = y + ROW;
        y += ROW + 12 + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close()).dimensions(x, y, inner, ROW - 2).build());
        status(L.t("Ergebnisse erscheinen sofort beim Tippen", "Results appear as you type"));
    }

    private TextFieldWidget field(int x, int y, int w, String text, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, ROW - 2, Text.literal(placeholder));
        f.setMaxLength(200);
        f.setText(text);
        f.setPlaceholder(Text.literal(placeholder).formatted(Formatting.DARK_GRAY));
        addDrawableChild(f);
        return f;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawTextWithShadow(textRenderer, mathResult(), x0, exprY, Theme.text());
        ctx.drawTextWithShadow(textRenderer, itemsResult(), x0, itemsY, Theme.text());
        ctx.drawTextWithShadow(textRenderer, coordResult(), x0, coordY, Theme.text());
    }

    private String mathResult() {
        if (expr.isBlank()) return "";
        try {
            return "= " + Calc.format(Calc.eval(expr));
        } catch (Exception e) {
            return "§7" + L.t("…", "…");
        }
    }

    /** z. B. "5000 = 2 Shulker + 24 Stacks + 8  (78 Stacks + 8)". */
    static String split(long n) {
        long sh = n / 1728, rest = n % 1728, st = rest / 64, single = rest % 64;
        StringBuilder sb = new StringBuilder(Calc.format(n)).append(" = ");
        if (sh > 0) sb.append(sh).append(L.t(" Shulker", " shulker")).append(st > 0 || single > 0 ? " + " : "");
        if (st > 0) sb.append(st).append(L.t(" Stacks", " stacks")).append(single > 0 ? " + " : "");
        if (single > 0 || n == 0) sb.append(single);
        sb.append("  §7(").append(n / 64).append(L.t(" St", " st")).append(n % 64 > 0 ? " + " + n % 64 : "").append(")");
        return sb.toString();
    }

    private String itemsResult() {
        if (items.isBlank()) return "";
        try {
            double v = Calc.eval(items);
            return v < 0 ? "§7…" : split((long) Math.floor(v));
        } catch (Exception e) {
            return "§7…";
        }
    }

    private String coordResult() {
        if (coordX.isBlank() || coordZ.isBlank()) return "";
        try {
            double x = Calc.eval(coordX), z = Calc.eval(coordZ);
            double f = fromNether ? 8 : 1.0 / 8;
            return (fromNether ? L.t("Welt: ", "World: ") : "Nether: ") + (int) Math.floor(x * f) + ", " + (int) Math.floor(z * f);
        } catch (Exception e) {
            return "§7…";
        }
    }
}
