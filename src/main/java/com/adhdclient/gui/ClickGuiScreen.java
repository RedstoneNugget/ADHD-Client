package com.adhdclient.gui;

import com.adhdclient.core.Modules;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Hauptmenü des ADHD Client im ClickGUI-Stil: ein Fenster pro Kategorie, nebeneinander und frei verschiebbar.
 * Linksklick schaltet ein Modul an/aus, Rechtsklick öffnet seine Einstellungen, Rechtsklick auf die Titelleiste
 * klappt ein Fenster ein. Positionen werden gespeichert.
 */
public final class ClickGuiScreen extends Screen {

    private static final int PANEL_W = 116;
    private static final int HEADER_H = 16;
    private static final int ROW_H = 15;

    /** Eine Zeile im Fenster: Modul oder Client-Aktion. */
    private record Row(String name, Modules.Module module, Runnable action) {
    }

    private static final class Panel {
        final String id;
        final String title;
        final List<Row> rows;
        int x;
        int y;
        boolean collapsed;

        Panel(String id, String title, List<Row> rows) {
            this.id = id;
            this.title = title;
            this.rows = rows;
        }

        int height() {
            return HEADER_H + (collapsed ? 0 : rows.size() * ROW_H + 2);
        }
    }

    private final Screen parent;
    private final List<Panel> panels = new ArrayList<>();
    private Panel dragging;
    private double dragOffX;
    private double dragOffY;

    public ClickGuiScreen(Screen parent) {
        super(Text.literal("ADHD Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panels.clear();
        for (Modules.Category c : Modules.Category.values()) {
            List<Row> rows = new ArrayList<>();
            for (Modules.Module m : Modules.in(c)) rows.add(new Row(m.name(), m, null));
            panels.add(new Panel(c.name(), c.title(), rows));
        }
        List<Row> client = new ArrayList<>();
        client.add(new Row("Design", null, () -> this.client.setScreen(new ThemeScreen(this))));
        client.add(new Row(L.t("Alle aus", "All off"), null, Modules::allOff));
        panels.add(new Panel("CLIENT", "Client", client));

        // gespeicherte Positionen, sonst nebeneinander von links (bei Platzmangel in die nächste Reihe)
        int x = 10;
        int y = 22;
        int rowBottom = y;
        for (Panel p : panels) {
            int[] saved = Theme.config().panels.get(p.id);
            if (saved != null && saved.length >= 3) {
                p.x = saved[0];
                p.y = saved[1];
                p.collapsed = saved[2] != 0;
            } else {
                if (x + PANEL_W > width - 10) {
                    x = 10;
                    y = rowBottom + 10;
                }
                p.x = x;
                p.y = y;
                x += PANEL_W + 10;
                rowBottom = Math.max(rowBottom, y + p.height());
            }
            clamp(p);
        }
    }

    private void clamp(Panel p) {
        p.x = Math.max(0, Math.min(width - PANEL_W, p.x));
        p.y = Math.max(0, Math.min(height - HEADER_H, p.y));
    }

    private void save() {
        for (Panel p : panels) Theme.config().panels.put(p.id, new int[]{p.x, p.y, p.collapsed ? 1 : 0});
        Theme.config().save();
    }

    // ---------------------------------------------------------------- Zeichnen

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ThemeConfig c = Theme.config();
        Row hovered = null;
        Panel top = topAt(mouseX, mouseY);

        for (Panel p : panels) {
            int x1 = p.x;
            int x2 = p.x + PANEL_W;
            int y = p.y;
            // Titelleiste
            ctx.fillGradient(x1, y, x2, y + HEADER_H, Theme.accent(), Theme.argb(255, Theme.mix(c.accent, c.background, 0.55f)));
            ctx.drawTextWithShadow(textRenderer, p.title, x1 + 6, y + 4, Theme.text());
            String right = (p.rows.get(0).module() != null ? countOn(p) + "/" + p.rows.size() + "  " : "") + (p.collapsed ? "+" : "–");
            ctx.drawTextWithShadow(textRenderer, right, x2 - 5 - textRenderer.getWidth(right), y + 4, Theme.text());
            if (p.collapsed) {
                Theme.border(ctx, x1, y, x2, y + HEADER_H, Theme.argb(255, Theme.mix(c.accent, c.background, 0.5f)));
                continue;
            }
            // Zeilen
            int bodyTop = y + HEADER_H;
            int bodyBottom = p.y + p.height();
            ctx.fill(x1, bodyTop, x2, bodyBottom, Theme.argb(Math.round(c.opacity * 2.55f), c.background));
            for (int i = 0; i < p.rows.size(); i++) {
                Row r = p.rows.get(i);
                int ry = bodyTop + 1 + i * ROW_H;
                boolean hover = p == top && mouseX >= x1 && mouseX < x2 && mouseY >= ry && mouseY < ry + ROW_H;
                boolean on = r.module() != null && r.module().enabled().getAsBoolean();
                int bg = c.background;
                if (on) bg = Theme.mix(bg, c.on, 0.16f);
                if (hover) bg = Theme.mix(bg, c.text, 0.10f);
                if (on || hover) ctx.fill(x1 + 1, ry, x2 - 1, ry + ROW_H, Theme.argb(255, bg));
                if (on) ctx.fill(x1 + 1, ry, x1 + 3, ry + ROW_H, Theme.argb(255, c.on));
                int textColor = r.module() == null || on ? Theme.text() : Theme.textDim();
                ctx.drawTextWithShadow(textRenderer, r.name(), x1 + 7, ry + 4, textColor);
                String key = r.module() == null ? null : r.module().key().get();
                String hint = key != null && !key.equals(L.t("Keine", "None")) ? key : (r.module() != null ? "›" : "");
                int hw = textRenderer.getWidth(hint);
                ctx.drawTextWithShadow(textRenderer, hint, x2 - 6 - hw, ry + 4, Theme.textDim());
                if (hover) hovered = r;
            }
            Theme.border(ctx, x1, y, x2, bodyBottom, Theme.argb(255, Theme.mix(c.accent, c.background, 0.5f)));
        }

        // Hinweisleiste unten
        String help = L.t("Linksklick: an/aus  ·  Rechtsklick: Einstellungen  ·  Titel ziehen: verschieben  ·  Rechtsklick Titel: einklappen",
                "Left click: on/off  ·  Right click: settings  ·  Drag title: move  ·  Right click title: collapse");
        int hw = textRenderer.getWidth(help);
        if (hw > width - 8) help = L.t("Links: an/aus  ·  Rechts: Einstellungen", "Left: on/off  ·  Right: settings");
        hw = textRenderer.getWidth(help);
        ctx.fill(width / 2 - hw / 2 - 6, height - 16, width / 2 + hw / 2 + 6, height - 2, Theme.argb(170, c.background));
        ctx.drawTextWithShadow(textRenderer, help, width / 2 - hw / 2, height - 13, Theme.textDim());

        if (hovered != null && hovered.module() != null && dragging == null) {
            List<Text> tip = new ArrayList<>();
            tip.add(Text.literal(hovered.module().description().get()));
            ctx.drawTooltip(textRenderer, tip, mouseX, mouseY);
        }
    }

    private static int countOn(Panel p) {
        int n = 0;
        for (Row r : p.rows) if (r.module() != null && r.module().enabled().getAsBoolean()) n++;
        return n;
    }

    // ---------------------------------------------------------------- Maus

    /** Oberstes Fenster unter der Maus (zuletzt gezeichnet = oben). */
    private Panel topAt(double mx, double my) {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (mx >= p.x && mx < p.x + PANEL_W && my >= p.y && my < p.y + p.height()) return p;
        }
        return null;
    }

    private void bringToFront(Panel p) {
        panels.remove(p);
        panels.add(p);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        Panel p = topAt(mx, my);
        if (p == null) return super.mouseClicked(click, doubled);
        bringToFront(p);
        int button = click.button();

        if (my < p.y + HEADER_H) {
            if (button == 0) {
                dragging = p;
                dragOffX = mx - p.x;
                dragOffY = my - p.y;
            } else if (button == 1) {
                p.collapsed = !p.collapsed;
                save();
            }
            return true;
        }
        int index = (int) ((my - p.y - HEADER_H - 1) / ROW_H);
        if (index < 0 || index >= p.rows.size()) return true;
        Row r = p.rows.get(index);
        if (r.action() != null) {
            if (button == 0) r.action().run();
            return true;
        }
        if (button == 0) {
            r.module().toggle();
        } else if (button == 1) {
            save();
            client.setScreen(r.module().settings().apply(this));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging != null) {
            dragging.x = (int) Math.round(click.x() - dragOffX);
            dragging.y = (int) Math.round(click.y() - dragOffY);
            clamp(dragging);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) {
            dragging = null;
            save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        save();
        if (client != null) client.setScreen(parent);
    }
}
