package com.adhdclient.inspect.ui;

import com.adhdclient.gui.L;
import com.adhdclient.inspect.InspectClient;
import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.Theme;
import com.adhdclient.inspect.config.InspectSettingsScreen;
import com.adhdclient.inspect.config.InspectConfig;
import com.adhdclient.inspect.data.Category;
import com.adhdclient.inspect.data.Coord;
import com.adhdclient.inspect.data.Entry;
import com.adhdclient.inspect.data.InspectData;
import com.adhdclient.inspect.data.Role;
import com.adhdclient.inspect.data.Section;
import com.adhdclient.inspect.export.Exporter;
import com.adhdclient.inspect.snbt.SnbtTree;
import com.adhdclient.inspect.snbt.SnbtTree.Seg;
import com.adhdclient.inspect.target.Target;
import com.adhdclient.inspect.world.WorldMarkers;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Das Info-Panel: dunkel, halbtransparent, mit Kopfbereich (großes Icon,
 * Name, Registry-ID, Mod, Position), Suchfeld, scrollbarem Inhalt
 * (einklappbare Abschnitte und SNBT-Bäume) und Knöpfen für Kopieren/Export.
 * Gezeichnet werden immer nur die sichtbaren Zeilen.
 */
public class InspectScreen extends Screen {

    private static final int PAD = 8;
    private static final int HEADER_H = 46;
    private static final int SEARCH_H = 20;

    private static final int LINE_H = 10;
    private static final int INDENT = 8;
    private static final int SCROLLBAR_W = 5;
    private static final int MIN_WIDTH = 340;

    // ------------------------------------------------------------ Modell

    private enum Kind { SECTION, STATIC, SNBT, INFO }

    /** Vorbereitete (umgebrochene) Zeile für Schlüssel/Wert, Hinweise, Zwischenüberschriften. */
    private record StaticRow(int indent, List<Seg> segs, @Nullable Coord coord, String lower) {
    }

    private static final class SectionModel {
        final Section section;
        final List<Object> parts = new ArrayList<>(); // List<StaticRow> | SnbtTree
        final int entryCount;

        SectionModel(Section section) {
            this.section = section;
            int n = 0;
            for (Entry e : section.entries) {
                if (!(e instanceof Entry.Sub)) {
                    n++;
                }
            }
            this.entryCount = n;
        }
    }

    private record Row(Kind kind, int section, @Nullable StaticRow row, @Nullable SnbtTree tree, int line) {
    }

    @Nullable
    private final Screen parent;
    private final Target target;
    private InspectData data;

    private final List<SectionModel> models = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> collapsedSections = new HashSet<>();
    private final Set<String> knownSections = new HashSet<>();
    private int hiddenCategories;
    private int searchHits;
    private String query = "";
    private boolean foldAll;

    private int px, py, pw, ph;
    private int footerH = 52;
    private int contentTop, contentBottom, contentLeft, contentRight;
    private double scroll;
    private int hscroll;
    private boolean draggingScrollbar;

    private TextFieldWidget search;
    private ButtonWidget giveButton;
    private ButtonWidget lockButton;
    private String status = "";
    private int statusColor;
    private long statusUntil;

    public InspectScreen(@Nullable Screen parent, Target target, InspectData data) {
        super(Text.literal("Inspect All"));
        this.parent = parent;
        this.target = target;
        this.data = data;
    }

    public InspectData getData() {
        return data;
    }

    public Target getTarget() {
        return target;
    }

    // ------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        InspectConfig cfg = InspectConfig.get();
        pw = MathHelper.clamp(width * cfg.panelWidthPercent / 100, Math.min(MIN_WIDTH, width - 16), width - 16);
        ph = height - 12 - Theme.TITLE_H;
        px = switch (cfg.panelPosition) {
            case LEFT -> 8;
            case RIGHT -> width - pw - 8;
            case CENTER -> (width - pw) / 2;
        };
        py = 6 + Theme.TITLE_H; // darüber die Titelleiste im ADHD-Design
        contentLeft = px + 1;
        contentRight = px + pw - 1;
        contentTop = py + HEADER_H + SEARCH_H;

        String oldQuery = search != null ? search.getText() : query;
        search = new TextFieldWidget(textRenderer, px + PAD, py + HEADER_H, pw - PAD * 2 - 80, 16, Text.literal(L.t("Suche", "Search")));
        search.setPlaceholder(Text.literal(L.t("Suchen … (Strg+F)", "Search … (Ctrl+F)")).withColor(0xFF808080));
        search.setMaxLength(200);
        search.setText(oldQuery);
        search.setChangedListener(text -> {
            query = text;
            scroll = 0;
            rebuildRows();
        });
        addDrawableChild(search);

        List<ButtonWidget> actions = List.of(
                ButtonWidget.builder(Text.literal(L.t("SNBT kopieren", "Copy SNBT")), b -> copySnbt()).build(),
                giveButton = ButtonWidget.builder(Text.literal(L.t("Als /give kopieren", "Copy as /give")), b -> copyGive()).build(),
                ButtonWidget.builder(Text.literal(L.t("Als JSON speichern", "Save as JSON")), b -> saveJson()).build());
        List<ButtonWidget> controls = List.of(
                lockButton = ButtonWidget.builder(lockLabel(), b -> toggleLock()).build(),
                ButtonWidget.builder(Text.literal(L.t("Auf/Zu", "Fold")), b -> toggleFoldAll()).build(),
                ButtonWidget.builder(Text.literal(L.t("Neu laden", "Reload")), b -> refresh()).build(),
                ButtonWidget.builder(Text.literal(L.t("Optionen", "Options")), b -> {
                    if (client != null) {
                        client.setScreen(new InspectSettingsScreen(this));
                    }
                }).build(),
                ButtonWidget.builder(Text.literal(L.t("Schließen", "Close")), b -> close()).build());

        // Breites Panel: alle Knöpfe in einer Zeile, sonst zwei Zeilen
        List<ButtonWidget> all = new ArrayList<>(actions);
        all.addAll(controls);
        int inner = pw - PAD * 2;
        boolean oneRow = preferredWidth(all) + 4 * (all.size() - 1) <= inner;
        footerH = oneRow ? 30 : 52;
        contentBottom = py + ph - footerH;
        int y1 = py + ph - footerH + 6;
        if (oneRow) {
            layoutRow(all, y1, inner);
        } else {
            layoutRow(actions, y1, inner);
            layoutRow(controls, y1 + 22, inner);
        }
        for (ButtonWidget b : all) {
            addDrawableChild(b);
        }
        rebuildModel();
    }

    private int preferredWidth(List<ButtonWidget> buttons) {
        int sum = 0;
        for (ButtonWidget b : buttons) {
            sum += textRenderer.getWidth(b.getMessage()) + 12;
        }
        return sum;
    }

    /** Verteilt die Knöpfe proportional zu ihrer Beschriftung über die Breite. */
    private void layoutRow(List<ButtonWidget> buttons, int y, int inner) {
        int gap = 4;
        int avail = inner - gap * (buttons.size() - 1);
        int pref = Math.max(1, preferredWidth(buttons));
        int x = px + PAD;
        for (int i = 0; i < buttons.size(); i++) {
            ButtonWidget b = buttons.get(i);
            int w = i == buttons.size() - 1 ? px + PAD + inner - x
                    : (int) ((textRenderer.getWidth(b.getMessage()) + 12) * (avail / (double) pref));
            b.setDimensionsAndPosition(w, 20, x, y);
            x += w + gap;
        }
    }

    /** " (K zum Entsperren)" – leer, wenn keine Taste eingestellt ist. */
    private static String keyHint(int code, String action) {
        return code == HotKey.NONE ? "" : " (" + HotKey.name(code) + action + ")";
    }

    private Text lockLabel() {
        return Text.literal(InspectClient.isLocked(target) ? L.t("Entsperren", "Unlock") : L.t("Sperren", "Lock"));
    }

    /** Ersetzt die Daten (z. B. wenn der integrierte Server geantwortet hat). */
    public void updateData(InspectData newData) {
        if (newData.iconEntity == null) {
            newData.iconEntity = data.iconEntity;
        }
        this.data = newData;
        if (InspectClient.isLocked(target)) {
            InspectClient.updateLockedData(newData);
        }
        rebuildModel();
    }

    private void rebuildModel() {
        models.clear();
        hiddenCategories = 0;
        InspectConfig cfg = InspectConfig.get();
        Set<Category> hidden = new HashSet<>();
        int wrap = contentRight - contentLeft - 14 - SCROLLBAR_W;
        int maxLines = cfg.maxSnbtLines;
        for (Section s : data.sections) {
            if (!cfg.isVisible(s.category)) {
                hidden.add(s.category);
                continue;
            }
            if (s.isEmpty()) {
                continue;
            }
            SectionModel m = new SectionModel(s);
            List<StaticRow> current = null;
            for (Entry e : s.entries) {
                if (e instanceof Entry.Snbt snbt) {
                    current = null;
                    try {
                        m.parts.add(new SnbtTree(snbt.nbt(), maxLines));
                    } catch (RuntimeException ex) {
                        InspectClient.LOGGER.warn("SNBT-Darstellung fehlgeschlagen", ex);
                        List<StaticRow> err = new ArrayList<>();
                        err.add(staticRow(0, List.of(new Seg(L.t("SNBT konnte nicht dargestellt werden.", "SNBT could not be shown."), Role.WARN)), null));
                        m.parts.add(err);
                    }
                    continue;
                }
                if (current == null) {
                    current = new ArrayList<>();
                    m.parts.add(current);
                }
                addStatic(current, e, wrap);
            }
            models.add(m);
            if (knownSections.add(s.title) && s.collapsedByDefault) {
                collapsedSections.add(s.title);
            }
        }
        hiddenCategories = hidden.size();
        if (foldAll) {
            applyFoldAll(true);
        }
        rebuildRows();
    }

    private void addStatic(List<StaticRow> out, Entry e, int wrap) {
        switch (e) {
            case Entry.Kv kv -> {
                List<Seg> first = new ArrayList<>();
                first.add(new Seg(kv.key(), Role.KEY));
                if (!kv.value().isEmpty()) {
                    first.add(new Seg(": ", Role.PUNCT));
                }
                int badge = kv.coord() != null ? 70 : 0;
                int keyW = textRenderer.getWidth(kv.key() + ": ");
                String value = kv.value();
                int avail = wrap - badge - keyW;
                if (value.isEmpty() || textRenderer.getWidth(value) <= avail || avail < 60) {
                    if (!value.isEmpty()) {
                        first.add(new Seg(value, kv.role()));
                    }
                    out.add(staticRow(0, first, kv.coord()));
                    return;
                }
                List<String> parts = wrapText(value, avail, wrap - badge - 12);
                first.add(new Seg(parts.get(0), kv.role()));
                out.add(staticRow(0, first, kv.coord()));
                for (int i = 1; i < parts.size(); i++) {
                    out.add(staticRow(12, List.of(new Seg(parts.get(i), kv.role())), null));
                }
            }
            case Entry.Note note -> {
                for (String line : wrapText(note.text(), wrap, wrap)) {
                    out.add(staticRow(0, List.of(new Seg(line, note.role())), null));
                }
            }
            case Entry.Sub sub -> out.add(staticRow(0, List.of(new Seg("› " + sub.title(), Role.ACCENT)), null));
            case Entry.Snbt ignored -> {
            }
        }
    }

    private static StaticRow staticRow(int indent, List<Seg> segs, @Nullable Coord coord) {
        StringBuilder sb = new StringBuilder();
        for (Seg s : segs) {
            sb.append(s.text());
        }
        return new StaticRow(indent, segs, coord, sb.toString().toLowerCase(Locale.ROOT));
    }

    /** Einfacher Wortumbruch; die erste Zeile hat {@code firstWidth}, die weiteren {@code restWidth}. */
    private List<String> wrapText(String text, int firstWidth, int restWidth) {
        List<String> out = new ArrayList<>();
        String rest = text;
        int width = Math.max(40, firstWidth);
        int guard = 0;
        while (!rest.isEmpty() && guard++ < 500) {
            if (textRenderer.getWidth(rest) <= width) {
                out.add(rest);
                break;
            }
            String cut = textRenderer.trimToWidth(rest, width);
            if (cut.isEmpty()) {
                cut = rest.substring(0, 1);
            }
            int brk = Math.max(cut.lastIndexOf(' '), Math.max(cut.lastIndexOf(','), cut.lastIndexOf('/')));
            if (brk > cut.length() / 2) {
                cut = cut.substring(0, brk + 1);
            }
            out.add(cut);
            rest = rest.substring(cut.length()).stripLeading();
            width = Math.max(40, restWidth);
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    private void rebuildRows() {
        rows.clear();
        searchHits = 0;
        String q = query.trim().toLowerCase(Locale.ROOT);
        boolean searching = !q.isEmpty();
        for (int index = 0; index < models.size(); index++) {
            final int i = index;
            SectionModel m = models.get(i);
            boolean titleMatch = searching && m.section.title.toLowerCase(Locale.ROOT).contains(q);
            if (!searching) {
                rows.add(new Row(Kind.SECTION, i, null, null, -1));
                if (collapsedSections.contains(m.section.title)) {
                    continue;
                }
                for (Object part : m.parts) {
                    if (part instanceof SnbtTree tree) {
                        tree.collectVisible(idx -> rows.add(new Row(Kind.SNBT, i, null, tree, idx)));
                    } else {
                        for (StaticRow r : asRows(part)) {
                            rows.add(new Row(Kind.STATIC, i, r, null, -1));
                        }
                    }
                }
                continue;
            }
            // Suche: nur Treffer (plus Eltern-Zeilen in SNBT) und ihre Abschnitte
            List<Row> found = new ArrayList<>();
            for (Object part : m.parts) {
                if (part instanceof SnbtTree tree) {
                    if (titleMatch) {
                        tree.collectVisible(idx -> found.add(new Row(Kind.SNBT, i, null, tree, idx)));
                    } else {
                        searchHits += tree.collectMatches(q, idx -> found.add(new Row(Kind.SNBT, i, null, tree, idx)));
                    }
                } else {
                    for (StaticRow r : asRows(part)) {
                        if (titleMatch || r.lower().contains(q)) {
                            found.add(new Row(Kind.STATIC, i, r, null, -1));
                            if (!titleMatch) {
                                searchHits++;
                            }
                        }
                    }
                }
            }
            if (titleMatch || !found.isEmpty()) {
                rows.add(new Row(Kind.SECTION, i, null, null, -1));
                rows.addAll(found);
            }
        }
        if (hiddenCategories > 0) {
            rows.add(new Row(Kind.INFO, -1, staticRow(0, List.of(new Seg(hiddenCategories
                    + L.t(" Kategorie(n) ausgeblendet – siehe Optionen", " categor(y/ies) hidden – see Options"), Role.DIM)), null), null, -1));
        }
        if (searching && rows.isEmpty()) {
            rows.add(new Row(Kind.INFO, -1, staticRow(0, List.of(new Seg(L.t("Keine Treffer für \"", "No hits for \"") + query.trim() + "\"", Role.WARN)), null), null, -1));
        }
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
    }

    @SuppressWarnings("unchecked")
    private static List<StaticRow> asRows(Object part) {
        return (List<StaticRow>) part;
    }

    // ------------------------------------------------------------ Aktionen

    private void copySnbt() {
        if (client == null) {
            return;
        }
        String text = Exporter.snbtText(data);
        client.keyboard.setClipboard(text);
        setStatus(data.rawNbt != null ? L.t("SNBT kopiert (", "SNBT copied (") + text.length() + L.t(" Zeichen)", " characters)") : L.t("Keine NBT – Werte als Text kopiert", "No NBT – values copied as text"), Role.ACCENT);
    }

    private void copyGive() {
        if (client == null) {
            return;
        }
        if (data.giveCommand == null) {
            setStatus(L.t("/give gibt es nur für Items", "/give only exists for items"), Role.WARN);
            return;
        }
        client.keyboard.setClipboard(data.giveCommand);
        setStatus(L.t("/give-Befehl kopiert (", "/give command copied (") + data.giveCommand.length() + L.t(" Zeichen)", " characters)"), Role.ACCENT);
    }

    private void saveJson() {
        try {
            Path file = Exporter.saveJson(data);
            Path gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
            String shown = file.startsWith(gameDir) ? gameDir.relativize(file).toString() : file.toString();
            setStatus(L.t("Gespeichert: ", "Saved: ") + shown.replace('\\', '/'), Role.ACCENT);
            InspectClient.LOGGER.info("JSON gespeichert: {}", file);
        } catch (Exception e) {
            InspectClient.LOGGER.warn("JSON-Export fehlgeschlagen", e);
            setStatus(L.t("Speichern fehlgeschlagen: ", "Saving failed: ") + e.getMessage(), Role.WARN);
        }
    }

    private void toggleLock() {
        boolean locked = InspectClient.toggleLock(target, data);
        lockButton.setMessage(lockLabel());
        setStatus(locked ? L.t("Ziel gesperrt – das Panel bleibt nach dem Schließen als Overlay offen", "Target locked – the panel stays open as an overlay after closing")
                + keyHint(InspectConfig.get().lockKey, L.t(" zum Entsperren", " to unlock"))
                : L.t("Ziel entsperrt", "Target unlocked"), Role.ACCENT);
    }

    private void toggleFoldAll() {
        foldAll = !foldAll;
        applyFoldAll(foldAll);
        rebuildRows();
    }

    private void applyFoldAll(boolean collapse) {
        for (SectionModel m : models) {
            for (Object part : m.parts) {
                if (part instanceof SnbtTree tree) {
                    tree.setAll(collapse);
                }
            }
        }
    }

    private void refresh() {
        if (client == null) {
            return;
        }
        InspectData fresh = InspectClient.inspectSafely(client, target);
        if (fresh == null) {
            setStatus(L.t("Ziel existiert nicht mehr (oder ist nicht geladen)", "Target no longer exists (or is not loaded)"), Role.WARN);
            return;
        }
        updateData(fresh);
        setStatus(L.t("Neu geladen", "Reloaded"), Role.ACCENT);
    }

    private void setStatus(String text, Role role) {
        status = text;
        statusColor = InspectConfig.get().color(role);
        statusUntil = System.currentTimeMillis() + 4000;
    }

    @Override
    public void tick() {
        // Antwort vom integrierten Server / der OP-Abfrage übernehmen
        if (data.pending != null && data.pending.isDone()) {
            InspectData result = null;
            try {
                result = data.pending.getNow(null);
            } catch (RuntimeException ignored) {
            }
            data.pending = null;
            if (result != null) {
                updateData(result);
            } else {
                data.pendingFailure = client != null && client.getServer() != null
                        ? L.t("Integrierter Server hat nicht geantwortet – zeige Client-Daten", "Integrated server did not answer – showing client data")
                        : L.t("Server-Abfrage ohne Antwort (keine OP-Rechte?) – zeige Client-Daten", "Server query got no answer (no OP rights?) – showing client data");
            }
        }
        if (giveButton != null) {
            giveButton.active = data.giveCommand != null;
        }
    }

    // ------------------------------------------------------------ Zeichnen

    private int contentHeight() {
        return contentBottom - contentTop;
    }

    private double maxScroll() {
        return Math.max(0, rows.size() * LINE_H + 6 - contentHeight());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        InspectConfig cfg = InspectConfig.get();
        int accent = cfg.accent();
        Theme.panel(context, px, py - Theme.TITLE_H, px + pw, py + ph, getTitle());
        context.fill(px + 1, contentTop - 1, px + pw - 1, contentTop, (accent & 0xFFFFFF) | 0x60000000);
        context.fill(px + 1, contentBottom, px + pw - 1, contentBottom + 1, (accent & 0xFFFFFF) | 0x60000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Knöpfe und Suchfeld im ADHD-Look überzeichnen (bleiben normale Widgets)
        for (var e : children()) {
            if (e instanceof ButtonWidget b && b.visible) {
                Theme.button(context, b, b == lockButton && InspectClient.isLocked(target) ? Theme.State.ON : Theme.State.NEUTRAL);
            }
        }
        Theme.field(context, search);
        InspectConfig cfg = InspectConfig.get();
        renderHeader(context, cfg, mouseX, mouseY);
        String hits = query.isBlank() ? rows.size() + L.t(" Zeilen", " lines") : searchHits + L.t(" Treffer", " hits");
        context.drawText(textRenderer, hits, px + pw - PAD - textRenderer.getWidth(hits), py + HEADER_H + 4, cfg.color(Role.DIM), false);
        renderRows(context, cfg, mouseX, mouseY);
        renderScrollbar(context, cfg);
        if (!status.isEmpty() && System.currentTimeMillis() < statusUntil) {
            int w = Math.min(pw - 16, textRenderer.getWidth(status) + 8);
            int sx = px + (pw - w) / 2;
            int sy = contentBottom - 14;
            context.fill(sx, sy, sx + w, sy + 12, 0xE0000000);
            context.drawStrokedRectangle(sx, sy, w, 12, statusColor);
            context.drawText(textRenderer, textRenderer.trimToWidth(status, w - 8), sx + 4, sy + 2, statusColor, false);
        }
    }

    private void renderHeader(DrawContext context, InspectConfig cfg, int mouseX, int mouseY) {
        int ix = px + PAD;
        int iy = py + 6;
        context.fill(ix - 1, iy - 1, ix + 33, iy + 33, 0x40000000);
        boolean drewEntity = false;
        if (data.iconEntity instanceof LivingEntity living && !living.isRemoved()) {
            try {
                float h = Math.max(living.getHeight(), living.getWidth());
                int size = MathHelper.clamp((int) (26 / Math.max(0.3f, h)), 4, 40);
                InventoryScreen.drawEntity(context, ix, iy, ix + 32, iy + 32, size, 0.0625f, mouseX, mouseY, living);
                drewEntity = true;
            } catch (RuntimeException e) {
                InspectClient.LOGGER.debug("Entity-Icon fehlgeschlagen", e);
            }
        }
        if (!drewEntity && !data.icon.isEmpty()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(ix, iy);
            context.getMatrices().scale(2f, 2f);
            context.drawItem(data.icon, 0, 0);
            context.getMatrices().popMatrix();
            if (data.icon.getCount() > 1) {
                String count = String.valueOf(data.icon.getCount());
                context.drawTextWithShadow(textRenderer, count, ix + 33 - textRenderer.getWidth(count), iy + 24, 0xFFFFFFFF);
            }
        }

        int tx = ix + 40;
        int maxW = px + pw - PAD - tx;
        String kind = data.kind.label.toUpperCase(Locale.ROOT) + (InspectClient.isLocked(target) ? "  🔒" : "");
        int kindW = textRenderer.getWidth(kind);
        context.drawTextWithShadow(textRenderer, kind, px + pw - PAD - kindW, py - Theme.TITLE_H + 4, Theme.text());

        String name = textRenderer.trimToWidth(data.name.getString(), maxW);
        context.drawTextWithShadow(textRenderer, name.isEmpty() ? "?" : name, tx, py + 7, cfg.accent());

        String line2 = data.registryId + "  ·  " + data.modName;
        context.drawText(textRenderer, textRenderer.trimToWidth(line2, maxW), tx, py + 19, cfg.color(Role.STRING), false);

        int y3 = py + 31;
        int x3 = tx;
        if (data.positionText != null && data.position != null) {
            String label = "Pos " + data.positionText;
            context.drawText(textRenderer, label, x3, y3, cfg.color(Role.COORD), false);
            x3 += textRenderer.getWidth(label) + 4;
            String dim = WorldMarkers.currentDimension();
            boolean marked = WorldMarkers.isMarked(data.position, dim);
            x3 += drawBadge(context, cfg, x3, y3, marked ? L.t("✔ Markiert", "✔ Marked") : L.t("Markieren", "Mark"), mouseX, mouseY) + 6;
        }
        String src;
        int srcColor;
        if (data.isPending()) {
            src = L.t("Lade vollständige Daten …", "Loading full data …");
            srcColor = cfg.color(Role.ACCENT);
        } else if (data.pendingFailure != null) {
            src = data.pendingFailure;
            srcColor = cfg.color(Role.WARN);
        } else {
            src = data.source;
            srcColor = data.multiplayer ? cfg.color(Role.WARN) : cfg.color(Role.DIM);
        }
        int left = px + pw - PAD - x3;
        if (left > 30) {
            context.drawText(textRenderer, textRenderer.trimToWidth(src, left), x3, y3, srcColor, false);
        }
    }

    /** Kleiner Knopf im Text (L.t("Markieren", "Mark")); liefert die Breite. */
    private int drawBadge(DrawContext context, InspectConfig cfg, int x, int y, String label, int mouseX, int mouseY) {
        int w = textRenderer.getWidth(label) + 6;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 9;
        int accent = cfg.accent();
        context.fill(x, y - 1, x + w, y + 9, hover ? (accent & 0xFFFFFF) | 0x70000000 : 0xC0000000);
        context.drawStrokedRectangle(x, y - 1, w, 10, (accent & 0xFFFFFF) | 0xC0000000);
        context.drawText(textRenderer, label, x + 3, y, hover ? 0xFFFFFFFF : cfg.color(Role.COORD), false);
        return w;
    }

    private int badgeWidth(Coord coord) {
        return textRenderer.getWidth(badgeLabel(coord)) + 6;
    }

    private String badgeLabel(Coord coord) {
        String cur = WorldMarkers.currentDimension();
        if (coord.dimension() != null && cur != null && !normalizeDim(coord.dimension()).equals(cur)) {
            return L.t("andere Dimension", "other dimension");
        }
        return WorldMarkers.isMarked(coord.pos(), coord.dimension()) ? L.t("✔ Markiert", "✔ Marked") : L.t("Markieren", "Mark");
    }

    private static String normalizeDim(String dim) {
        return dim.contains(":") ? dim : "minecraft:" + dim;
    }

    private void renderRows(DrawContext context, InspectConfig cfg, int mouseX, int mouseY) {
        int top = contentTop;
        int bottom = contentBottom;
        int textRight = contentRight - SCROLLBAR_W - 2;
        String q = query.trim().toLowerCase(Locale.ROOT);
        int first = Math.max(0, (int) (scroll / LINE_H) - 1);
        List<OrderedText> tooltip = null;

        context.enableScissor(contentLeft, top, contentRight - SCROLLBAR_W - 1, bottom);
        for (int i = first; i < rows.size(); i++) {
            int y = top + 3 + i * LINE_H - (int) scroll;
            if (y > bottom) {
                break;
            }
            if (y + LINE_H < top) {
                continue;
            }
            Row row = rows.get(i);
            boolean hoverRow = mouseY >= y - 1 && mouseY < y + LINE_H - 1 && mouseX >= contentLeft && mouseX < textRight
                    && mouseY >= top && mouseY < bottom;
            switch (row.kind()) {
                case SECTION -> {
                    SectionModel m = models.get(row.section());
                    int accent = cfg.accent();
                    context.fill(contentLeft, y - 1, textRight, y + LINE_H - 1, (accent & 0xFFFFFF) | (hoverRow ? 0x48000000 : 0x26000000));
                    boolean collapsed = collapsedSections.contains(m.section.title) && q.isEmpty();
                    String title = (collapsed ? "▸ " : "▾ ") + m.section.title;
                    context.drawText(textRenderer, title, contentLeft + 4, y, accent, false);
                    String count = m.entryCount + (m.entryCount == 1 ? L.t(" Eintrag", " entry") : L.t(" Einträge", " entries"));
                    context.drawText(textRenderer, count, textRight - textRenderer.getWidth(count) - 2, y, cfg.color(Role.DIM), false);
                }
                case STATIC, INFO -> {
                    StaticRow r = row.row();
                    int x = contentLeft + 6 + r.indent() - hscroll;
                    int end = drawSegs(context, cfg, r.segs(), x, y, q, r.lower());
                    Coord coord = r.coord();
                    int limit = textRight - (coord != null ? badgeWidth(coord) + 6 : 0);
                    if (coord != null) {
                        drawCoordBadge(context, cfg, coord, textRight, y, mouseX, mouseY);
                    }
                    if (hoverRow && end > limit && mouseX < limit) {
                        tooltip = textRenderer.wrapLines(Text.literal(plain(r.segs())), Math.max(200, pw - 40));
                    }
                }
                case SNBT -> {
                    SnbtTree tree = row.tree();
                    SnbtTree.Line line = tree.lines.get(row.line());
                    int x = contentLeft + 14 + line.depth * INDENT - hscroll;
                    boolean collapsed = line.isOpener() && tree.collapsed.get(row.line()) && q.isEmpty();
                    if (line.isOpener()) {
                        context.drawText(textRenderer, collapsed ? "▸" : "▾", x - 8, y, hoverRow ? cfg.accent() : cfg.color(Role.DIM), false);
                    }
                    int end = drawSegs(context, cfg, line.segs, x, y, q, line.lower());
                    if (collapsed) {
                        end = drawSeg(context, "…" + line.closer + (line.comma ? "," : ""), end, y, cfg.color(Role.PUNCT));
                        String info = "  " + line.children + (line.closer.equals("}") ? L.t(" Einträge", " entries") : L.t(" Elemente", " elements"));
                        end = drawSeg(context, info, end, y, cfg.color(Role.DIM));
                    }
                    int limit = textRight;
                    if (line.coord != null) {
                        drawCoordBadge(context, cfg, line.coord, textRight, y, mouseX, mouseY);
                        limit -= badgeWidth(line.coord) + 6;
                    }
                    if (hoverRow && end > limit && mouseX < limit) {
                        tooltip = textRenderer.wrapLines(Text.literal(line.plain()), Math.max(200, pw - 40));
                    }
                }
            }
        }
        context.disableScissor();
        if (tooltip != null) {
            context.drawOrderedTooltip(textRenderer, tooltip, mouseX, mouseY);
        }
    }

    private void drawCoordBadge(DrawContext context, InspectConfig cfg, Coord coord, int textRight, int y, int mouseX, int mouseY) {
        String label = badgeLabel(coord);
        int w = textRenderer.getWidth(label) + 6;
        int bx = textRight - w - 2;
        drawBadge(context, cfg, bx, y, label, mouseX, mouseY);
    }

    private int drawSegs(DrawContext context, InspectConfig cfg, List<Seg> segs, int x, int y, String q, String lower) {
        if (!q.isEmpty()) {
            int idx = lower.indexOf(q);
            if (idx >= 0) {
                String full = plain(segs);
                if (idx + q.length() <= full.length()) {
                    int hx = x + textRenderer.getWidth(full.substring(0, idx));
                    int hw = textRenderer.getWidth(full.substring(idx, idx + q.length()));
                    context.fill(hx - 1, y - 1, hx + hw + 1, y + 9, (cfg.accent() & 0xFFFFFF) | 0x66000000);
                }
            }
        }
        for (Seg s : segs) {
            x = drawSeg(context, s.text(), x, y, cfg.color(s.role()));
        }
        return x;
    }

    private int drawSeg(DrawContext context, String text, int x, int y, int color) {
        if (x > width) {
            return x + textRenderer.getWidth(text);
        }
        context.drawText(textRenderer, text, x, y, color, false);
        return x + textRenderer.getWidth(text);
    }

    private static String plain(List<Seg> segs) {
        StringBuilder sb = new StringBuilder();
        for (Seg s : segs) {
            sb.append(s.text());
        }
        return sb.toString();
    }

    private void renderScrollbar(DrawContext context, InspectConfig cfg) {
        double max = maxScroll();
        if (max <= 0) {
            return;
        }
        int h = contentHeight();
        int trackX = contentRight - SCROLLBAR_W;
        int barH = Math.max(14, (int) (h * (h / (double) (h + max))));
        int barY = contentTop + (int) ((h - barH) * (scroll / max));
        context.fill(trackX, contentTop, trackX + SCROLLBAR_W - 1, contentBottom, 0x40FFFFFF);
        context.fill(trackX, barY, trackX + SCROLLBAR_W - 1, barY + barH, (cfg.accent() & 0xFFFFFF) | 0xD0000000);
    }

    // ------------------------------------------------------------ Eingabe

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Modul-Tasten auf der Maus (z. B. Seitentaste) auch im Panel
        InspectConfig keys = InspectConfig.get();
        int mouseCode = HotKey.mouse(click.button());
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (mouseCode == keys.openKey) {
                close();
                return true;
            }
            if (mouseCode == keys.lockKey) {
                toggleLock();
                return true;
            }
        }
        double mx = click.x();
        double my = click.y();
        if (search.isFocused() && !search.isMouseOver(mx, my)) {
            search.setFocused(false);
            setFocused(null);
        }
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        // Scrollbalken
        if (mx >= contentRight - SCROLLBAR_W - 1 && mx < contentRight && my >= contentTop && my < contentBottom && maxScroll() > 0) {
            draggingScrollbar = true;
            scrollToMouse(my);
            return true;
        }
        // L.t("Markieren", "Mark") im Kopfbereich
        if (data.position != null && data.positionText != null && my >= py + 30 && my < py + 40) {
            int x3 = px + PAD + 40 + textRenderer.getWidth("Pos " + data.positionText) + 4;
            String dim = WorldMarkers.currentDimension();
            String label = WorldMarkers.isMarked(data.position, dim) ? L.t("✔ Markiert", "✔ Marked") : L.t("Markieren", "Mark");
            int w = textRenderer.getWidth(label) + 6;
            if (mx >= x3 && mx < x3 + w) {
                toggleMarker(new Coord(data.position, dim, data.registryId));
                return true;
            }
        }
        if (my < contentTop || my >= contentBottom || mx < contentLeft || mx >= contentRight) {
            return false;
        }
        int index = (int) Math.floor((my - contentTop - 2 + scroll) / LINE_H);
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        Row row = rows.get(index);
        int textRight = contentRight - SCROLLBAR_W - 2;
        Coord coord = row.kind() == Kind.SNBT ? row.tree().lines.get(row.line()).coord
                : row.row() != null ? row.row().coord() : null;
        if (coord != null) {
            int w = badgeWidth(coord);
            int bx = textRight - w - 2;
            if (mx >= bx && mx < bx + w) {
                toggleMarker(coord);
                return true;
            }
        }
        if (row.kind() == Kind.SECTION) {
            String title = models.get(row.section()).section.title;
            if (!collapsedSections.remove(title)) {
                collapsedSections.add(title);
            }
            rebuildRows();
            return true;
        }
        if (row.kind() == Kind.SNBT && query.isBlank()) {
            SnbtTree.Line line = row.tree().lines.get(row.line());
            if (line.isOpener()) {
                row.tree().toggle(row.line());
                rebuildRows();
                return true;
            }
        }
        return false;
    }

    private void toggleMarker(Coord coord) {
        String cur = WorldMarkers.currentDimension();
        if (coord.dimension() != null && cur != null && !normalizeDim(coord.dimension()).equals(cur)) {
            setStatus(L.t("Koordinate liegt in ", "Coordinate is in ") + coord.dimension() + L.t(" – Markierung nur in der aktuellen Dimension", " – markers only work in the current dimension"), Role.WARN);
            return;
        }
        boolean on = WorldMarkers.toggle(coord.pos(), coord.dimension(), coord.label());
        setStatus(on ? L.t("Markiert: ", "Marked: ") + coord.text() + keyHint(InspectConfig.get().clearKey, L.t(" löscht alle Markierungen", " removes all markers")) : L.t("Markierung entfernt: ", "Marker removed: ") + coord.text(), Role.ACCENT);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingScrollbar) {
            scrollToMouse(click.y());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingScrollbar = false;
        return super.mouseReleased(click);
    }

    private void scrollToMouse(double my) {
        double max = maxScroll();
        double t = (my - contentTop) / (double) Math.max(1, contentHeight());
        scroll = MathHelper.clamp(t * max, 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean shift = client != null && client.isShiftPressed();
        if (horizontalAmount != 0 || shift) {
            double amount = horizontalAmount != 0 ? horizontalAmount : verticalAmount;
            hscroll = MathHelper.clamp(hscroll - (int) (amount * 24), 0, 4000);
            return true;
        }
        scroll = MathHelper.clamp(scroll - verticalAmount * LINE_H * 3, 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (search.isFocused()) {
            if (input.isEscape()) {
                if (!search.getText().isEmpty()) {
                    search.setText("");
                } else {
                    search.setFocused(false);
                    setFocused(null);
                }
                return true;
            }
            if (input.isEnter()) {
                search.setFocused(false);
                setFocused(null);
                return true;
            }
            return super.keyPressed(input);
        }
        if (key == GLFW.GLFW_KEY_F && input.hasCtrlOrCmd()) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        InspectConfig keys = InspectConfig.get();
        if (key != HotKey.NONE && key == keys.openKey) {
            close();
            return true;
        }
        if (key != HotKey.NONE && key == keys.lockKey) {
            toggleLock();
            return true;
        }
        if (key != HotKey.NONE && key == keys.clearKey) {
            int n = WorldMarkers.clear();
            setStatus(n + L.t(" Markierung(en) gelöscht", " marker(s) removed"), Role.ACCENT);
            return true;
        }
        double page = Math.max(LINE_H, contentHeight() - LINE_H * 2);
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> scroll += LINE_H;
            case GLFW.GLFW_KEY_UP -> scroll -= LINE_H;
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll += page;
            case GLFW.GLFW_KEY_PAGE_UP -> scroll -= page;
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = maxScroll();
            case GLFW.GLFW_KEY_LEFT -> hscroll = Math.max(0, hscroll - 24);
            case GLFW.GLFW_KEY_RIGHT -> hscroll = Math.min(4000, hscroll + 24);
            default -> {
                return super.keyPressed(input);
            }
        }
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
        return true;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------ Für Tests

    /** Alle aktuell erzeugten Zeilen als Klartext (unabhängig vom Scrollen). */
    public List<String> getRowStrings() {
        List<String> out = new ArrayList<>();
        for (Row row : rows) {
            switch (row.kind()) {
                case SECTION -> out.add("## " + models.get(row.section()).section.title);
                case STATIC, INFO -> out.add(plain(row.row().segs()));
                case SNBT -> {
                    SnbtTree.Line line = row.tree().lines.get(row.line());
                    out.add("  ".repeat(line.depth) + line.plain());
                }
            }
        }
        return out;
    }

    public void setQueryForTest(String q) {
        search.setText(q);
    }

    /** Für Tests: erste Koordinate in den aktuellen Zeilen markieren, deren Text {@code contains} enthält. */
    public boolean markFirstCoord(String contains) {
        for (Row row : rows) {
            Coord c = row.kind() == Kind.SNBT ? row.tree().lines.get(row.line()).coord
                    : row.row() != null ? row.row().coord() : null;
            String text = row.kind() == Kind.SNBT ? row.tree().lines.get(row.line()).plain()
                    : row.row() != null ? plain(row.row().segs()) : "";
            if (c != null && text.contains(contains)) {
                if (!WorldMarkers.isMarked(c.pos(), c.dimension())) {
                    toggleMarker(c);
                }
                return true;
            }
        }
        return false;
    }

    public int coordRowCount() {
        int n = 0;
        for (Row row : rows) {
            Coord c = row.kind() == Kind.SNBT ? row.tree().lines.get(row.line()).coord
                    : row.row() != null ? row.row().coord() : null;
            if (c != null) {
                n++;
            }
        }
        return n;
    }

    public int totalSnbtLines() {
        int n = 0;
        for (SectionModel m : models) {
            for (Object part : m.parts) {
                if (part instanceof SnbtTree tree) {
                    n += tree.lines.size();
                }
            }
        }
        return n;
    }

    public void scrollForTest(double amount) {
        scroll = MathHelper.clamp(scroll + amount, 0, maxScroll());
    }

    public void collapseSectionForTest(String titlePrefix) {
        for (SectionModel m : models) {
            if (m.section.title.startsWith(titlePrefix)) {
                collapsedSections.add(m.section.title);
            }
        }
        rebuildRows();
    }

    public void toggleFoldAllForTest() {
        toggleFoldAll();
    }
}
