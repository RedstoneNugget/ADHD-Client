package com.adhdclient.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Auswahl-Liste mit Suche (für Blöcke und Entities): Klick wählt einen Eintrag aus bzw. ab, die Leiste
 * links zeigt den Zustand. Nur die sichtbaren Zeilen bekommen Knöpfe, damit über tausend Einträge flüssig bleiben.
 */
public abstract class PickerScreen extends ThemedScreen {

    public record Entry(String id, String name, ItemStack icon) {
    }

    private static final int ROW_H = 20;
    private static final int COLS = 2;

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();
    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    private final List<int[]> iconPos = new ArrayList<>();
    private final List<ItemStack> iconStacks = new ArrayList<>();

    private TextFieldWidget search;
    private String query = "";
    private boolean onlySelected;
    private int scrollRow;
    private int left;
    private int inner;
    private int colW;
    private int listTop;
    private int listBottom;

    protected PickerScreen(Text title, Screen parent, List<Entry> entries) {
        super(title, parent);
        all.addAll(entries);
        all.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
    }

    /** Aktuelle Auswahl (IDs), wird direkt verändert. */
    protected abstract List<String> selection();

    /** Nach jeder Änderung der Auswahl. */
    protected abstract void selectionChanged();

    /** Zusätzliche Einstellungen über der Liste; gibt die benutzte Höhe zurück. */
    protected int buildExtras(int x, int y, int w) {
        return 0;
    }

    protected abstract String noun();

    @Override
    protected final void build() {
        int w = Math.min(480, width - 16);
        int h = height - 16;
        int y = centeredPanel(w, h);
        left = panelX1 + 8;
        inner = w - 20;
        colW = (inner - 4) / COLS;

        search = new TextFieldWidget(textRenderer, left, y, inner - 128, ROW_H - 2, Text.literal("Suche"));
        search.setPlaceholder(Text.literal(L.t(noun() + " suchen …", "Search " + noun().toLowerCase(java.util.Locale.ROOT) + " …")).formatted(Formatting.DARK_GRAY));
        search.setText(query);
        search.setChangedListener(s -> {
            query = s;
            scrollRow = 0;
            refilter();
        });
        addDrawableChild(search);
        setInitialFocus(search);
        toggle(L.t("Nur ausgewählte", "Only selected"), () -> onlySelected, v -> {
            onlySelected = v;
            scrollRow = 0;
            refilter();
        }, left + inner - 124, y, 124, ROW_H - 2, null, null);
        y += ROW_H;

        y += buildExtras(left, y, inner);
        y += 2;
        section(L.t("Liste", "List"), left, y, inner);
        listTop = y + 12;
        listBottom = panelY2 - 16 - ROW_H - 4;

        int half = (inner - 4) / 2;
        int by = panelY2 - 16 - ROW_H + 2;
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Alle abwählen", "Deselect all")), b -> {
            selection().clear();
            selectionChanged();
            refilter();
        }).dimensions(left, by, half, ROW_H - 2).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Fertig", "Done")), b -> close())
                .dimensions(left + half + 4, by, inner - half - 4, ROW_H - 2).build());

        rowButtons.clear();
        refilter();
    }

    private void refilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> sel = selection();
        filtered.clear();
        for (Entry e : all) {
            if (onlySelected && !sel.contains(e.id())) continue;
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.id().contains(q)) continue;
            filtered.add(e);
        }
        clampScroll();
        rebuildRows();
        status(statusText(sel.size()));
    }

    private String statusText(int selected) {
        return selected + L.t(" ausgewählt  ·  ", " selected  ·  ") + filtered.size() + L.t(" von ", " of ") + all.size() + " " + noun()
                + L.t("  ·  Mausrad zum Scrollen", "  ·  scroll with the mouse wheel");
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private int totalRows() {
        return (filtered.size() + COLS - 1) / COLS;
    }

    private void clampScroll() {
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, totalRows() - visibleRows())));
    }

    private void rebuildRows() {
        for (ButtonWidget b : rowButtons) {
            remove(b);
            unmark(b);
        }
        rowButtons.clear();
        iconPos.clear();
        iconStacks.clear();

        int rows = visibleRows();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= filtered.size()) break;
                Entry e = filtered.get(idx);
                int x = left + c * (colW + 4);
                int y = listTop + r * ROW_H;
                String name = textRenderer.trimToWidth(e.name(), colW - 34);
                ButtonWidget b = ButtonWidget.builder(Text.literal(name), w -> toggle(e))
                        .dimensions(x + 18, y, colW - 18, ROW_H - 2).build();
                addDrawableChild(b);
                markToggle(b, () -> selection().contains(e.id()));
                rowButtons.add(b);
                iconPos.add(new int[]{x, y + 1});
                iconStacks.add(e.icon());
            }
        }
    }

    private void toggle(Entry e) {
        List<String> sel = selection();
        if (!sel.remove(e.id())) sel.add(e.id());
        selectionChanged();
        if (onlySelected) refilter();
        else status(statusText(sel.size()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= listTop && mouseY <= listBottom) {
            int before = scrollRow;
            scrollRow -= (int) Math.signum(vertical) * 2;
            clampScroll();
            if (scrollRow != before) rebuildRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (int i = 0; i < iconStacks.size(); i++) {
            int[] p = iconPos.get(i);
            ItemStack s = iconStacks.get(i);
            if (!s.isEmpty()) ctx.drawItem(s, p[0], p[1]);
        }
        int rows = visibleRows();
        int total = totalRows();
        if (total > rows) {
            int x = left + inner + 3;
            int view = listBottom - listTop;
            int barH = Math.max(16, view * rows / total);
            int barY = listTop + (int) ((view - barH) * (scrollRow / (double) (total - rows)));
            ctx.fill(x, listTop, x + 2, listBottom, Theme.argb(255, Theme.mix(Theme.config().background, Theme.config().text, 0.18f)));
            ctx.fill(x, barY, x + 2, barY + barH, Theme.accent());
        }
    }
}
