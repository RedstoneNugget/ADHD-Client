package com.adhdclient.basefinder.gui;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.basefinder.Lang;
import com.adhdclient.basefinder.config.ModConfig;
import com.adhdclient.basefinder.render.MarkerRenderer;
import com.adhdclient.basefinder.scan.Evaluator;
import com.adhdclient.basefinder.scan.Marker;
import com.adhdclient.basefinder.scan.Patterns;
import com.adhdclient.basefinder.scan.ScanManager;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Menü "Base Finder", über das ADHD-Client-Menü erreichbar. Tabs oben, darunter ein scrollbarer Bereich.
 * Jede Änderung wirkt sofort (Caches werden bei Bedarf neu aufgebaut) und wird gespeichert.
 */
public class ConfigScreen extends ThemedScreen {

    private static String tr(String de) {
        return MenuText.tr(de);
    }

    private enum Tab {
        FINDS("Funde", null), AMETHYST("Amethyst", Marker.Mode.AMETHYST), BEES("Bienen", Marker.Mode.BEES),
        SPAWNERS("Spawner", Marker.Mode.SPAWNERS), KELP("Kelp", Marker.Mode.KELP), ANOMALY("Anomalien", Marker.Mode.ANOMALY),
        GENERAL("Allgemein", null), WEIGHTS("Gewichte", null);

        final String title;
        final Marker.Mode mode;

        Tab(String title, Marker.Mode mode) {
            this.title = title;
            this.mode = mode;
        }
    }

    private static final int ROW = 22;
    private static final int TOP = 28;
    /** Inhalt beginnt unter der Tasten-Zeile. */
    private static final int CONTENT_TOP = TOP + 22;
    private static final int SIDE_X = 10;
    private static final int SIDE_W = 124;
    /** Markiert Label als Abschnitts-Überschrift. */
    private static final int SECTION_MARK = 1;
    private static Tab lastTab = Tab.FINDS;

    private Tab tab = lastTab;
    private double scroll;
    private int contentHeight;

    private record Placed(ClickableWidget widget, int baseY) {
    }

    private record Label(String text, int x, int baseY, int color, boolean centered) {
    }

    private final List<Placed> placed = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<int[]> swatches = new ArrayList<>(); // x, baseY, size, colorIndex
    private final List<Supplier<String>> swatchColors = new ArrayList<>();

    // Layout-Cursor
    private int left;
    private int contentW;
    private int colW;
    private int cols = 2;
    private int cursorY;
    private int col;

    public ConfigScreen(Screen parent) {
        super(Text.literal(BaseFinderClient.MOD_NAME), parent);
    }

    private static ModConfig cfg() {
        return BaseFinderClient.config();
    }

    private static void changed() {
        BaseFinderClient.onConfigEdited();
    }

    @Override
    protected void build() {
        placed.clear();
        labels.clear();
        swatches.clear();
        swatchColors.clear();
        panel(4, 4, width - 4, height - 4);

        // Seitenleiste: Funde, die Suchmodi (mit Status), dann Allgemein und Gewichte
        section(tr("Ansicht"), SIDE_X, TOP - 2, SIDE_W);
        int sy = TOP + 10;
        int step = height < 260 ? 18 : 19; // auf kleinen Bildschirmen enger, damit nichts den Fertig-Knopf berührt
        for (Tab t : Tab.values()) {
            if (t == Tab.GENERAL) {
                sy += 4;
                section(tr("Einstellungen"), SIDE_X, sy, SIDE_W);
                sy += 12;
            }
            ButtonWidget b = ButtonWidget.builder(sideText(t), w -> switchTab(t)).dimensions(SIDE_X, sy, SIDE_W, 17).build();
            addDrawableChild(b);
            if (t == tab) markToggle(b, () -> true);
            sy += step;
        }

        // Inhalt rechts neben der Seitenleiste
        left = SIDE_X + SIDE_W + 14;
        contentW = Math.min(460, width - left - 18);
        // Bei schmalem Fenster eine Spalte, sonst zwei
        cols = contentW >= 330 ? 2 : 1;
        colW = cols == 2 ? (contentW - 8) / 2 : contentW;
        cursorY = 0;
        col = 0;
        switch (tab) {
            case FINDS -> buildFinds();
            case GENERAL -> buildGeneral();
            case AMETHYST -> buildAmethyst();
            case BEES -> buildBees();
            case SPAWNERS -> buildSpawners();
            case KELP -> buildKelp();
            case ANOMALY -> buildAnomaly();
            case WEIGHTS -> buildWeights();
        }
        newline();
        contentHeight = cursorY + 6;

        // Tasten ganz oben über dem Inhalt (gelten für alle Tabs)
        ModConfig.General keys = cfg().general;
        int keyW = (contentW - 8) / 3;
        keyButton(com.adhdclient.gui.L.t("Anzeigen", "Show"), () -> keys.markersKey, v -> keys.markersKey = v, new ModConfig.General().markersKey,
                left, TOP - 2, keyW, 17, BaseFinderClient::onKeysEdited);
        keyButton(com.adhdclient.gui.L.t("Scannen", "Scan"), () -> keys.rescanKey, v -> keys.rescanKey = v, new ModConfig.General().rescanKey,
                left + keyW + 4, TOP - 2, keyW, 17, BaseFinderClient::onKeysEdited);
        addDrawableChild(ButtonWidget.builder(Text.literal(tr("Neu scannen")), b -> BaseFinderClient.rescan())
                .dimensions(left + 2 * (keyW + 4), TOP - 2, contentW - 2 * (keyW + 4), 17)
                .tooltip(Tooltip.of(Text.literal(com.adhdclient.gui.L.t("Alle geladenen Chunks jetzt neu scannen", "Rescan all loaded chunks now")))).build());

        // Fußzeile unter der Seitenleiste
        int by = viewBottom() - 17;
        addDrawableChild(ButtonWidget.builder(Text.literal(tr("Fertig")), b -> close())
                .dimensions(SIDE_X, by, SIDE_W, 17).build());

        clampScroll();
        relayout();
    }

    /** Tab per Titel wählen (z. B. "Amethyst"). */
    public void selectTab(String title) {
        for (Tab t : Tab.values()) {
            if (t.title.equalsIgnoreCase(title)) switchTab(t);
        }
    }

    private void switchTab(Tab t) {
        tab = t;
        lastTab = t;
        scroll = 0;
        clearAndInit();
    }

    private void rebuildKeepScroll() {
        double s = scroll;
        clearAndInit();
        scroll = s;
        clampScroll();
        relayout();
    }

    @Override
    protected void onClose() {
        BaseFinderClient.saveNow();
    }

    // ------------------------------------------------------------------ Scrollen

    private int viewBottom() {
        return height - 4 - 16;
    }

    private void clampScroll() {
        int view = viewBottom() - CONTENT_TOP;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - view)));
    }

    private void relayout() {
        int bottom = viewBottom();
        for (Placed p : placed) {
            int y = CONTENT_TOP + p.baseY - (int) scroll;
            p.widget.setY(y);
            p.widget.visible = y >= CONTENT_TOP && y + p.widget.getHeight() <= bottom;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= CONTENT_TOP && mouseY <= viewBottom() && contentHeight > viewBottom() - CONTENT_TOP) {
            scroll -= vertical * 20;
            clampScroll();
            relayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ScanManager sm = ScanManager.INSTANCE;
        status(String.format(Locale.ROOT, Lang.t("Cache: %d Chunks · Warteschlange: %d · Radius: %d · letzter Tick: %.2f ms%s", "Cache: %d chunks · queue: %d · radius: %d · last tick: %.2f ms%s"),
                sm.cachedCount(), sm.pendingCount(), sm.radius(), sm.lastTickMs(),
                cfg().general.constantScanning ? "" : Lang.t(" · PAUSIERT", " · PAUSED")));
        super.render(ctx, mouseX, mouseY, delta);
        int bottom = viewBottom();
        for (Label l : labels) {
            int y = CONTENT_TOP + l.baseY - (int) scroll;
            if (y < CONTENT_TOP || y + 9 > bottom) continue;
            if (l.color == SECTION_MARK) {
                Theme.section(ctx, left, y, contentW, l.text);
                continue;
            }
            int color = themed(l.color);
            if (l.centered) ctx.drawCenteredTextWithShadow(textRenderer, l.text, l.x, y, color);
            else ctx.drawTextWithShadow(textRenderer, l.text, l.x, y, color);
        }
        for (int i = 0; i < swatches.size(); i++) {
            int[] s = swatches.get(i);
            int y = CONTENT_TOP + s[1] - (int) scroll;
            if (y < CONTENT_TOP || y + s[2] > bottom) continue;
            Integer c = ModConfig.parseColor(swatchColors.get(i).get());
            Theme.swatch(ctx, s[0], y, s[2], c == null ? 0x555555 : c);
        }
        // Scrollbalken
        int view = bottom - CONTENT_TOP;
        if (contentHeight > view) {
            int barH = Math.max(20, view * view / contentHeight);
            int barY = CONTENT_TOP + (int) ((view - barH) * (scroll / (contentHeight - view)));
            int x = left + contentW + 6;
            ctx.fill(x, CONTENT_TOP, x + 2, bottom, Theme.argb(255, Theme.mix(Theme.config().background, Theme.config().text, 0.18f)));
            ctx.fill(x, barY, x + 2, barY + barH, Theme.accent());
        }
    }

    /** Alte feste Textfarben auf die Farben aus dem Design-Menü umbiegen. */
    private static int themed(int color) {
        return switch (color) {
            case 0xFFE0E0E0, 0xFFFFFFFF -> Theme.text();
            case 0xFFA8B0C0, 0xFF9AA4B8 -> Theme.textDim();
            case 0xFFFFE066 -> Theme.accent();
            default -> color;
        };
    }

    // ------------------------------------------------------------------ Bausteine

    private void newline() {
        if (col != 0) {
            col = 0;
            cursorY += ROW;
        }
    }

    private int cellX() {
        return left + col * (colW + 8);
    }

    private void advance() {
        col++;
        if (col >= cols) {
            col = 0;
            cursorY += ROW;
        }
    }

    private <T extends ClickableWidget> T place(T w, int baseY) {
        addDrawableChild(w);
        placed.add(new Placed(w, baseY));
        return w;
    }

    private void header(String textDe) {
        String text = tr(textDe);
        newline();
        if (cursorY > 0) cursorY += 6;
        labels.add(new Label(text, left, cursorY + 2, SECTION_MARK, false));
        cursorY += 14;
    }

    private void note(String textDe) {
        String text = tr(textDe);
        newline();
        for (var line : textRenderer.wrapLines(Text.literal(text), contentW)) {
            StringBuilder sb = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });
            labels.add(new Label(sb.toString(), left, cursorY + 2, 0xFFA8B0C0, false));
            cursorY += 11;
        }
        cursorY += 2;
    }

    private void toggle(String labelDe, BooleanSupplier get, Consumer<Boolean> set, String tooltipDe) {
        String label = tr(labelDe);
        String tooltip = tr(tooltipDe);
        int y = cursorY;
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), w -> {
            set.accept(!get.getAsBoolean());
            changed();
        }).dimensions(cellX(), 0, colW, 20).build();
        if (tooltip != null) b.setTooltip(Tooltip.of(Text.literal(tooltip)));
        place(b, y);
        markToggle(b, get);
        advance();
    }

    private void slider(String labelDe, double min, double max, double step, DoubleSupplier get, DoubleConsumer set,
                        Function<Double, String> fmt, String tooltipDe) {
        String label = tr(labelDe);
        String tooltip = tr(tooltipDe);
        int y = cursorY;
        ValueSlider s = new ValueSlider(cellX(), 0, colW, 20, label, min, max, step, get.getAsDouble(), fmt::apply, v -> {
            set.accept(v);
            changed();
        });
        if (tooltip != null) s.setTooltip(Tooltip.of(Text.literal(tooltip)));
        place(s, y);
        advance();
    }

    private static String intFmt(double v) {
        return Integer.toString((int) Math.round(v));
    }

    /** Beschriftetes Textfeld über die volle Breite (Label links). */
    private TextFieldWidget field(String labelDe, int labelW, String value, int maxLen, Consumer<String> onChange, String tooltipDe) {
        String label = tr(labelDe);
        String tooltip = tr(tooltipDe);
        newline();
        int y = cursorY;
        labels.add(new Label(label, left, y + 6, 0xFFE0E0E0, false));
        TextFieldWidget tf = new TextFieldWidget(textRenderer, left + labelW, 0, contentW - labelW, 20, Text.literal(label));
        tf.setMaxLength(maxLen);
        tf.setText(value);
        tf.setCursorToStart(false);
        tf.setChangedListener(onChange);
        if (tooltip != null) tf.setTooltip(Tooltip.of(Text.literal(tooltip)));
        place(tf, y);
        cursorY += ROW;
        return tf;
    }

    private void colorField(String labelDe, Supplier<String> get, Consumer<String> set) {
        String label = tr(labelDe);
        int y = cursorY;
        int x = cellX();
        labels.add(new Label(label, x, y + 6, 0xFFE0E0E0, false));
        int fieldX = x + Math.min(70, colW / 2);
        int fieldW = colW - (fieldX - x) - 24;
        TextFieldWidget tf = new TextFieldWidget(textRenderer, fieldX, 0, fieldW, 20, Text.literal(label));
        tf.setMaxLength(7);
        tf.setText(get.get());
        tf.setChangedListener(s -> {
            boolean ok = ModConfig.parseColor(s) != null;
            tf.setEditableColor(ok ? 0xFFE0E0E0 : 0xFFFF5555);
            if (ok) {
                set.accept(s.trim().toUpperCase(Locale.ROOT).startsWith("#") ? s.trim().toUpperCase(Locale.ROOT) : "#" + s.trim().toUpperCase(Locale.ROOT));
                changed();
            }
        });
        tf.setTooltip(Tooltip.of(Text.literal(tr("Farbe als #RRGGBB, z. B. #B061FF"))));
        place(tf, y);
        swatches.add(new int[]{fieldX + fieldW + 6, y + 3, 14});
        swatchColors.add(get);
        advance();
    }

    private TextFieldWidget numberField(int x, int w, int y, double value, Consumer<Double> set, String tooltipDe) {
        String tooltip = tr(tooltipDe);
        TextFieldWidget tf = new TextFieldWidget(textRenderer, x, 0, w, 20, Text.literal("Gewicht"));
        tf.setMaxLength(10);
        tf.setText(trim(value));
        tf.setChangedListener(s -> {
            try {
                double v = Double.parseDouble(s.trim().replace(',', '.'));
                tf.setEditableColor(0xFFE0E0E0);
                set.accept(v);
                changed();
            } catch (NumberFormatException e) {
                tf.setEditableColor(0xFFFF5555);
            }
        });
        if (tooltip != null) tf.setTooltip(Tooltip.of(Text.literal(tooltip)));
        place(tf, y);
        return tf;
    }

    private static String trim(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        return String.format(Locale.ROOT, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static List<String> splitList(String s) {
        List<String> out = new ArrayList<>();
        for (String p : s.split("[,;\\s]+")) {
            if (!p.isBlank()) out.add(p.trim());
        }
        return out;
    }

    // ------------------------------------------------------------------ Tabs

    // ------------------------------------------------------------------ Seitenleiste

    private Evaluator.Results shownResults;
    private int refreshTicks;
    private TextFieldWidget searchField;

    // Filter der Fundliste bleiben erhalten, solange das Spiel läuft
    private static Marker.Mode filterMode;
    private static boolean sortByDistance = true;
    private static String search = "";

    private static Text sideText(Tab t) {
        if (t.mode == null) {
            String icon = switch (t) {
                case FINDS -> "☰ ";
                case GENERAL -> "⚙ ";
                default -> "≡ ";
            };
            Text base = Text.literal(icon + tr(t.title));
            if (t == Tab.FINDS) {
                int n = 0;
                for (Marker.Mode m : Marker.Mode.values()) n += ScanManager.INSTANCE.results().all.get(m).size();
                return base.copy().append(Text.literal("  " + n).formatted(Formatting.YELLOW));
            }
            return base;
        }
        boolean on = modeEnabled(t.mode);
        int count = ScanManager.INSTANCE.results().all.get(t.mode).size();
        return Text.literal("■ ").styled(s -> s.withColor(MarkerRenderer.colorOf(t.mode, cfg())))
                .append(Text.literal(tr(t.title) + "  ").formatted(on ? Formatting.WHITE : Formatting.GRAY))
                .append(on ? Text.literal(Integer.toString(count)).formatted(Formatting.GREEN) : Text.literal(tr("aus")).formatted(Formatting.DARK_GRAY));
    }

    private static boolean modeEnabled(Marker.Mode m) {
        ModConfig c = cfg();
        return switch (m) {
            case AMETHYST -> c.amethyst.enabled;
            case BEES -> c.bees.enabled;
            case SPAWNERS -> c.spawners.enabled;
            case KELP -> c.kelp.enabled;
            case ANOMALY -> c.anomaly.enabled;
        };
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        // Trennlinie zwischen Seitenleiste und Inhalt
        int x = SIDE_X + SIDE_W + 6;
        ctx.fill(x, TOP - 2, x + 1, viewBottom(), Theme.argb(255, Theme.mix(Theme.config().background, Theme.config().text, 0.18f)));
    }

    @Override
    public void tick() {
        super.tick();
        // Fundliste und Zähler regelmäßig auffrischen (nicht, während in der Suche getippt wird)
        if (++refreshTicks < 20) return;
        refreshTicks = 0;
        if (ScanManager.INSTANCE.results() != shownResults && (searchField == null || !searchField.isFocused())) {
            rebuildKeepScroll();
        }
    }

    // ------------------------------------------------------------------ Fundliste

    private void buildFinds() {
        shownResults = ScanManager.INSTANCE.results();
        searchField = null;
        ScanManager sm = ScanManager.INSTANCE;

        // Bedienzeile: Modus-Filter, Sortierung, Suche
        int y = cursorY;
        int bw = Math.min(118, (contentW - 8) / 3);
        place(ButtonWidget.builder(Text.literal(Lang.t("Modus: ", "Mode: ") + (filterMode == null ? Lang.t("alle", "all") : filterMode.title())), b -> {
            Marker.Mode[] modes = Marker.Mode.values();
            filterMode = filterMode == null ? modes[0] : (filterMode.ordinal() + 1 < modes.length ? modes[filterMode.ordinal() + 1] : null);
            scroll = 0;
            rebuildKeepScroll();
        }).dimensions(left, 0, bw, 20).build(), y);
        place(ButtonWidget.builder(Text.literal(tr(sortByDistance ? "↕ Entfernung" : "↕ Rang")), b -> {
            sortByDistance = !sortByDistance;
            rebuildKeepScroll();
        }).dimensions(left + bw + 4, 0, bw, 20).tooltip(Tooltip.of(Text.literal(tr("Sortierung umschalten")))).build(), y);
        TextFieldWidget tf = new TextFieldWidget(textRenderer, left + 2 * bw + 8, 0, contentW - 2 * bw - 8, 20, Text.literal("Suche"));
        tf.setMaxLength(60);
        tf.setText(search);
        tf.setPlaceholder(Text.literal(tr("Suchen …")).formatted(Formatting.DARK_GRAY));
        tf.setChangedListener(s -> {
            search = s;
            scroll = 0;
            rebuildKeepScroll();
            if (searchField != null) {
                setFocused(searchField);
                searchField.setFocused(true);
            }
        });
        place(tf, y);
        searchField = tf;
        cursorY += ROW;

        if (sm.focusKey() != null || sm.hiddenCount() > 0) {
            int x = left;
            if (sm.focusKey() != null) {
                place(ButtonWidget.builder(Text.literal(tr("◎ Fokus aufheben")).formatted(Formatting.YELLOW), b -> {
                    sm.setFocus(null);
                    rebuildKeepScroll();
                }).dimensions(x, 0, 130, 20).build(), cursorY);
                x += 134;
            }
            if (sm.hiddenCount() > 0) {
                place(ButtonWidget.builder(Text.literal(Lang.t("Ausgeblendete zeigen (", "Show hidden (") + sm.hiddenCount() + ")"), b -> {
                    sm.unhideAll();
                    rebuildKeepScroll();
                }).dimensions(x, 0, 170, 20).build(), cursorY);
            }
            cursorY += ROW;
        }

        // Einträge sammeln
        Vec3d eye = client != null && client.player != null ? client.player.getEyePos() : Vec3d.ZERO;
        record Row(Marker m, String title, String detail, String coords, double dist) {
        }
        List<Row> rows = new ArrayList<>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        for (Marker.Mode mode : Marker.Mode.values()) {
            if (filterMode != null && filterMode != mode) continue;
            for (Marker m : shownResults.all.get(mode)) {
                String title = m.lines.get(0).replace("★ ", "").replaceFirst("^(Unnatürliche[rs]?|Unnatural) ", "");
                if (!title.isEmpty()) title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
                List<String> rest = new ArrayList<>();
                for (int i = 1; i < m.lines.size(); i++) {
                    if (i != m.distanceLine) rest.add(m.lines.get(i));
                }
                String detail = String.join(" · ", rest);
                String coords = m.distanceLine >= 0 ? m.lines.get(m.distanceLine) : "";
                String all = (title + " " + detail + " " + coords + " " + mode.title()).toLowerCase(Locale.ROOT);
                if (!q.isEmpty() && !all.contains(q)) continue;
                rows.add(new Row(m, title, detail, coords, m.target.distanceTo(eye)));
            }
        }
        if (sortByDistance) rows.sort((a, b) -> Double.compare(a.dist, b.dist));

        if (rows.isEmpty()) {
            note(search.isBlank() ? "Noch keine Funde in den geladenen Chunks. Die Liste füllt sich, sobald gescannt wurde."
                    : "Keine Funde passen zur Suche.");
            return;
        }
        int shown = Math.min(rows.size(), 150);
        labels.add(new Label(rows.size() + Lang.t(" Funde", " finds") + (shown < rows.size() ? Lang.t(" (die ersten ", " (showing the first ") + shown + Lang.t(" angezeigt)", ")") : ""),
                left, cursorY + 2, 0xFF9AA4B8, false));
        cursorY += 14;
        int btnW = 80;
        int textW = contentW - btnW - 18;
        for (int i = 0; i < shown; i++) {
            Row r = rows.get(i);
            Marker m = r.m;
            int ry = cursorY;
            boolean focused = m.key.equals(sm.focusKey());
            labels.add(new Label("■", left, ry + 2, 0xFF000000 | MarkerRenderer.colorOf(m.mode, cfg()), false));
            labels.add(new Label(fit(r.title + (r.detail.isEmpty() ? "" : " · " + r.detail), textW),
                    left + 12, ry + 2, focused ? 0xFFFFE066 : 0xFFFFFFFF, false));
            labels.add(new Label(fit(r.coords + " · " + Math.round(r.dist) + " m", textW), left + 12, ry + 13, 0xFF9AA4B8, false));
            int bx = left + contentW - btnW;
            place(ButtonWidget.builder(Text.literal(focused ? "◎" : "○"), b -> {
                sm.setFocus(focused ? null : m.key);
                rebuildKeepScroll();
            }).dimensions(bx, 0, 22, 20).tooltip(Tooltip.of(Text.literal(focused ? "Fokus aufheben" : "Nur diesen Fund in der Welt zeigen"))).build(), ry);
            place(ButtonWidget.builder(Text.literal(tr("XYZ")), b -> {
                Vec3d t = m.target;
                String c = (int) Math.floor(t.x) + " " + (int) Math.floor(t.y) + " " + (int) Math.floor(t.z);
                if (client != null) client.keyboard.setClipboard(c);
                ScanManager.INSTANCE.notifier.showNow(Text.literal(Lang.t("Koordinaten kopiert: ", "Coordinates copied: ") + c).formatted(Formatting.GREEN));
            }).dimensions(bx + 24, 0, 32, 20).tooltip(Tooltip.of(Text.literal(tr("Koordinaten kopieren (z. B. für /tp)")))).build(), ry);
            place(ButtonWidget.builder(Text.literal(tr("✕")), b -> {
                sm.hide(m.key);
                rebuildKeepScroll();
            }).dimensions(bx + 58, 0, 22, 20).tooltip(Tooltip.of(Text.literal(tr("Fund ausblenden (bis zum Verlassen der Welt)")))).build(), ry);
            cursorY += ROW;
        }
    }

    private void buildGeneral() {
        ModConfig.General g = cfg().general;
        header("Scannen");
        toggle("Konstantes Scannen", () -> g.constantScanning, v -> g.constantScanning = v,
                "Scannt dauerhaft im Hintergrund, sobald du in einer Welt bist.");
        toggle("Markierungen sichtbar", () -> g.markersVisible, v -> g.markersVisible = v,
                "Alle Markierungen ein-/ausblenden. Das Scannen läuft weiter.");
        slider("Scan-Radius", 1, 32, 1, () -> g.scanRadius, v -> g.scanRadius = (int) Math.round(v),
                v -> intFmt(v) + Lang.t(" Chunks", " chunks"), "Höchstens bis zur eingestellten Render-Distanz.");
        slider("Scan-Budget", 0.2, 10, 0.1, () -> g.scanBudgetMs, v -> g.scanBudgetMs = v,
                v -> String.format(Locale.ROOT, Lang.t("%.1f ms/Tick", "%.1f ms/tick"), v), "Maximale Scan-Arbeit pro Tick. Kleiner = flüssiger, aber langsamer.");
        slider("Hintergrund-Durchlauf", 0, 300, 5, () -> g.backgroundIntervalSec, v -> g.backgroundIntervalSec = (int) Math.round(v),
                v -> v < 1 ? Lang.t("aus", "off") : Lang.t("alle ", "every ") + intFmt(v) + " s", "Prüft alle gecachten Chunks regelmäßig neu, falls Updates verpasst wurden.");
        slider("Verzögerung nach Änderung", 200, 5000, 100, () -> g.dirtyDelayMs, v -> g.dirtyDelayMs = (int) Math.round(v),
                v -> String.format(Locale.ROOT, "%.1f s", v / 1000), "Geänderte Chunks werden gebündelt nach dieser Zeit neu gescannt.");

        header("Suchmodi");
        ModConfig c = cfg();
        toggle("Amethyst", () -> c.amethyst.enabled, v -> c.amethyst.enabled = v, "Chunks mit den meisten ausgewachsenen Amethystclustern.");
        toggle("Unnatürliche Bienennester", () -> c.bees.enabled, v -> c.bees.enabled = v, "Nester/Stöcke, die dort nicht natürlich entstehen können.");
        toggle("Unnatürliche Spawner", () -> c.spawners.enabled, v -> c.spawners.enabled = v, "Spawner, die dort nicht natürlich entstehen können (falscher Mob, falsche Umgebung …).");
        toggle("Übergroßer Kelp", () -> c.kelp.enabled, v -> c.kelp.enabled = v, "Die höchsten zusammenhängenden Kelp-Säulen.");
        toggle("Anomalien", () -> c.anomaly.enabled, v -> c.anomaly.enabled = v, "Chunks, die nicht wie frisch generierte Welt aussehen (Licht, Bauten).");

        header("Anzeige & Meldungen");
        toggle("Benachrichtigungs-Ton", () -> g.notificationSound, v -> g.notificationSound = v, "Kurzer Ton bei neuen Funden.");
        slider("Label-Größe", 0.25, 4, 0.05, () -> g.labelScale, v -> g.labelScale = v,
                v -> String.format(Locale.ROOT, "%.2fx", v), null);
        modeDisplay("Amethyst", () -> c.amethyst.notify, v -> c.amethyst.notify = v,
                () -> c.amethyst.color, v -> c.amethyst.color = v);
        modeDisplay("Bienennester", () -> c.bees.notify, v -> c.bees.notify = v,
                () -> c.bees.color, v -> c.bees.color = v);
        modeDisplay("Spawner", () -> c.spawners.notify, v -> c.spawners.notify = v,
                () -> c.spawners.color, v -> c.spawners.color = v);
        modeDisplay("Kelp", () -> c.kelp.notify, v -> c.kelp.notify = v,
                () -> c.kelp.color, v -> c.kelp.color = v);
        modeDisplay("Anomalien", () -> c.anomaly.notify, v -> c.anomaly.notify = v,
                () -> c.anomaly.color, v -> c.anomaly.color = v);
        note(tr("Config-Datei: config/") + ModConfig.FILE_NAME + tr(" – alle Werte, Gewichte und Listen lassen sich dort auch ohne Menü ändern."));
    }

    private void modeDisplay(String name, BooleanSupplier notify, Consumer<Boolean> setNotify,
                             Supplier<String> color, Consumer<String> setColor) {
        newline();
        labels.add(new Label(tr(name), left, cursorY + 2, 0xFFFFFFFF, false));
        cursorY += 13;
        toggle("Meldung", notify, setNotify, "Actionbar-Meldung bei neuen Funden in diesem Modus.");
        colorField("Farbe", color, setColor);
        newline();
    }

    private void buildAmethyst() {
        ModConfig.Amethyst a = cfg().amethyst;
        header("Amethyst");
        toggle("Modus aktiv", () -> a.enabled, v -> a.enabled = v, null);
        newline();
        header("Was wird gezählt");
        toggle("Ausgewachsene Cluster", () -> a.countCluster, v -> a.countCluster = v, "minecraft:amethyst_cluster");
        toggle("Große Knospen", () -> a.countLargeBud, v -> a.countLargeBud = v, "minecraft:large_amethyst_bud");
        toggle("Mittlere Knospen", () -> a.countMediumBud, v -> a.countMediumBud = v, "minecraft:medium_amethyst_bud");
        toggle("Kleine Knospen", () -> a.countSmallBud, v -> a.countSmallBud = v, "minecraft:small_amethyst_bud");
        toggle("Knospender Amethyst", () -> a.countBudding, v -> a.countBudding = v, "minecraft:budding_amethyst");
        newline();
        header("Anzeige");
        slider("Mindestanzahl", 1, 200, 1, () -> a.minCount, v -> a.minCount = (int) Math.round(v), ConfigScreen::intFmt, "Ein Chunk wird erst ab so vielen gezählten Blöcken markiert.");
        slider("Anzahl Markierungen (N)", 1, 10, 1, () -> a.topN, v -> a.topN = (int) Math.round(v), ConfigScreen::intFmt, "Nur die N besten Chunks werden angezeigt.");
        note("Markiert wird der ganze Chunk (Rahmen vom tiefsten bis zum höchsten Fund). Der beste Chunk ist dicker/heller und heißt \"Bester Chunk\".");
    }

    private void buildBees() {
        ModConfig.Bees b = cfg().bees;
        header("Unnatürliche Bienennester");
        toggle("Modus aktiv", () -> b.enabled, v -> b.enabled = v, null);
        newline();
        toggle("Bienennester (bee_nest)", () -> b.includeBeeNest, v -> b.includeBeeNest = v, null);
        toggle("Bienenstöcke (beehive)", () -> b.includeBeehive, v -> b.includeBeehive = v, null);
        header("Kriterien (eins reicht für \"unnatürlich\")");
        toggle("Unterirdisch", () -> b.critUnderground, v -> b.critUnderground = v, "Unter der Oberfläche ohne Himmelszugang (Höhle, in Stein eingebaut).");
        toggle("Falsches Biom", () -> b.critBiome, v -> b.critBiome = v, "Biom, in dem keine Nester generieren (Liste unten).");
        toggle("Dimension", () -> b.critDimension, v -> b.critDimension = v, "Nether oder End.");
        toggle("Keine Baumstruktur", () -> b.critNoTree, v -> b.critNoTree = v, "Keine Stämme/Blätter in der Nähe.");
        toggle("Beehives immer unnatürlich", () -> b.beehiveAlwaysUnnatural, v -> b.beehiveAlwaysUnnatural = v,
                "Bienenstöcke generieren nie natürlich – mit diesem Schalter immer melden.");
        toggle("Leere Nester ausblenden", () -> b.hideEmpty, v -> b.hideEmpty = v,
                "Nester mit bekannt 0 Bienen ignorieren. Auf Servern ist die Anzahl meist unbekannt (?), solche Nester bleiben sichtbar.");
        newline();
        header("Anzeige & Feineinstellung");
        slider("Anzahl Markierungen (N)", 1, 10, 1, () -> b.topN, v -> b.topN = (int) Math.round(v), ConfigScreen::intFmt, null);
        slider("Baum-Suchradius", 1, 6, 1, () -> b.treeRadius, v -> b.treeRadius = (int) Math.round(v), v -> intFmt(v) + Lang.t(" Blöcke", " blocks"), null);
        slider("Mind. Baumblöcke", 1, 30, 1, () -> b.minTreeBlocks, v -> b.minTreeBlocks = (int) Math.round(v), ConfigScreen::intFmt,
                "So viele Stamm-/Laubblöcke (davon min. 1 Stamm) müssen im Radius sein.");
        newline();
        field("Natürliche Biome", 100, String.join(", ", b.naturalBiomes), 4000, s -> {
            b.naturalBiomes = splitList(s);
            changed();
        }, "Komma-getrennte Biom-IDs, in denen Nester natürlich generieren.");
        note("Sortierung: Bienenanzahl, dann Honigstufe, dann Entfernung. Die Bienenanzahl kennt der Client nur im Einzelspieler; auf Servern steht dort \"?\".");
    }

    private void buildSpawners() {
        ModConfig.Spawners s = cfg().spawners;
        header("Unnatürliche Spawner");
        toggle("Modus aktiv", () -> s.enabled, v -> s.enabled = v, null);
        toggle("Trial-Spawner", () -> s.includeTrialSpawners, v -> s.includeTrialSpawners = v,
                "Trial-Spawner gehören in Trial Chambers (Tuffziegel, Kupfer).");
        toggle("Alle anzeigen", () -> s.showAll, v -> s.showAll = v,
                "Auch natürliche Spawner markieren (Label „natürlich“ + Struktur). Unnatürliche stehen immer zuerst.");
        header("Kriterien (eins reicht für \"unnatürlich\")");
        toggle("Falscher Mob", () -> s.critWrongMob, v -> s.critWrongMob = v,
                "Mob, der nie aus natürlichen Spawnern kommt (z. B. Schwein, Creeper, Eisengolem).");
        toggle("Falsche Dimension", () -> s.critDimension, v -> s.critDimension = v,
                "Z. B. Lohe in der Oberwelt oder Zombie im Nether.");
        toggle("Keine passende Struktur", () -> s.critNoStructure, v -> s.critNoStructure = v,
                "Um den Spawner fehlen die typischen Blöcke (Verlies: Bruchstein, Mineshaft: Spinnweben/Holz …). Findet auch umgebaute Mob-Farmen.");
        toggle("Unter freiem Himmel", () -> s.critOpenSky, v -> s.critOpenSky = v,
                "Natürliche Spawner liegen nie offen unter dem Himmel.");
        toggle("Ohne Mob", () -> s.critEmpty, v -> s.critEmpty = v,
                "Leerer Spawner (z. B. im Kreativmodus gesetzt).");
        newline();
        header("Anzeige");
        slider("Anzahl Markierungen (N)", 1, 30, 1, () -> s.topN, v -> s.topN = (int) Math.round(v), ConfigScreen::intFmt, null);
        newline();
        StringBuilder mobs = new StringBuilder();
        for (Map.Entry<String, String> e : s.naturalMobs.entrySet()) {
            if (!mobs.isEmpty()) mobs.append(", ");
            mobs.append(e.getKey()).append('=').append(e.getValue());
        }
        field("Natürliche Mobs", 100, mobs.toString(), 4000, text -> {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            for (String part : text.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2 && !kv[0].isBlank() && !kv[1].isBlank()) m.put(Patterns.normalize(kv[0]), kv[1].trim());
            }
            s.naturalMobs = m;
            changed();
        }, "Mob-ID=Struktur, durch Komma getrennt. Die Strukturen (Verlies, Mineshaft, Festung, Nether-Festung, Bastion, Trial Chambers) und ihre typischen Blöcke stehen in der Config-Datei.");
        note("Natürlich: Zombie/Skelett/Spinne im Verlies (Bruchstein), Höhlenspinne im Mineshaft, Silberfischchen in der Festung, Lohe in der Nether-Festung, Magmawürfel in der Bastion. Spawner lassen sich in Vanilla nicht abbauen und umsetzen – ein Fund deutet auf Kreativmodus, Befehle, ein Server-Plugin oder eine umgebaute Farm hin.");
    }

    private void buildKelp() {
        ModConfig.Kelp k = cfg().kelp;
        header("Übergroßer Kelp");
        toggle("Modus aktiv", () -> k.enabled, v -> k.enabled = v, null);
        newline();
        slider("Mindesthöhe", 2, 64, 1, () -> k.minHeight, v -> k.minHeight = (int) Math.round(v), v -> intFmt(v) + Lang.t(" Blöcke", " blocks"),
                "Zusammenhängende Kelp-Säulen ab dieser Höhe werden berücksichtigt.");
        slider("Anzahl Markierungen (N)", 1, 10, 1, () -> k.topN, v -> k.topN = (int) Math.round(v), ConfigScreen::intFmt, null);
        note("Markiert wird jeweils die Spitze der Säule, mit der Höhe im Label.");
    }

    private void buildAnomaly() {
        ModConfig.Anomaly a = cfg().anomaly;
        header("Unnatürliche Chunks (Anomalien)");
        toggle("Modus aktiv", () -> a.enabled, v -> a.enabled = v, null);
        newline();
        header("Kategorien");
        toggle("Lichtquellen", () -> a.lightSources, v -> a.lightSources = v, "Fackeln, Laternen, Leuchtstein … gewichtet gezählt.");
        toggle("Licht-Check unter Oberfläche", () -> a.lightCheck, v -> a.lightCheck = v,
                "Blocklicht in Höhlen/Räumen, das keine natürliche Quelle (Lava, Leuchtflechten …) erklärt.");
        toggle("Andere künstliche Blöcke", () -> a.otherBlocks, v -> a.otherBlocks = v, "Werkbank, Ofen, Truhen, Betten, Glas, Beton … (niedrige Gewichte).");
        toggle("Strukturfilter", () -> a.structureFilter, v -> a.structureFilter = v,
                "Erkennt natürliche Strukturen (Dorf, Mineshaft, Festung …) und blendet sie aus, außer der Score liegt weit darüber.");
        toggle("\"Wahrscheinlich Basis\" markieren", () -> a.markBase, v -> a.markBase = v,
                "Mehrere Lichtquellen-Sorten UND mehrere andere künstliche Blöcke zusammen.");
        newline();
        header("Schwellen");
        slider("Mindestscore", 1, 200, 1, () -> a.minScore, v -> a.minScore = (int) Math.round(v), ConfigScreen::intFmt, null);
        slider("Anzahl Markierungen (N)", 1, 10, 1, () -> a.topN, v -> a.topN = (int) Math.round(v), ConfigScreen::intFmt, null);
        slider("Struktur-Toleranz", 1, 20, 0.5, () -> a.structureToleranceFactor, v -> a.structureToleranceFactor = v,
                v -> String.format(Locale.ROOT, Lang.t("%.1fx typisch", "%.1fx typical"), v), "Strukturchunks werden nur gemeldet, wenn ihr Score so viel höher als typisch ist.");
        slider("Licht-Check Punkte/10 Blöcke", 0, 10, 0.1, () -> a.lightCheckWeightPer10, v -> a.lightCheckWeightPer10 = v,
                v -> String.format(Locale.ROOT, "%.1f", v), null);
        slider("Licht-Check Maximum", 0, 100, 1, () -> a.lightCheckMax, v -> a.lightCheckMax = v, ConfigScreen::intFmt, null);
        slider("Unbekannte Lichtquelle", 0, 10, 0.1, () -> a.unknownLightWeight, v -> a.unknownLightWeight = v,
                v -> String.format(Locale.ROOT, Lang.t("%.1f Pkt.", "%.1f pts"), v), "Leuchtende Blöcke, die weder natürlich noch in einer Liste sind (z. B. Lichtblock).");
        note("Gewichte der einzelnen Blocksorten, eigene Blöcke und die Listen natürlicher Lichtquellen stehen im Tab \"Gewichte\".");
    }

    private void buildWeights() {
        ModConfig.Anomaly a = cfg().anomaly;
        header("Lichtquellen (Gewicht pro Block)");
        groupRows(a.lightGroups);
        header("Andere künstliche Blöcke");
        groupRows(a.otherGroups);
        note("Schienen zählen nicht in erkannten Mineshafts, Treppen/Stufen nicht in erkannten Strukturen.");

        header("Eigene Blöcke");
        note("Beliebige Block-ID mit Gewicht, z. B. minecraft:tnt. Auch Platzhalter (minecraft:*_shulker_box), Tags (#minecraft:beds) und Objekte (entity:minecraft:armor_stand).");
        for (int i = 0; i < a.customBlocks.size(); i++) {
            ModConfig.CustomBlock cb = a.customBlocks.get(i);
            newline();
            int y = cursorY;
            int idW = contentW - 70 - 26;
            TextFieldWidget id = new TextFieldWidget(textRenderer, left, 0, idW, 20, Text.literal("Block"));
            id.setMaxLength(200);
            id.setText(cb.block);
            id.setEditableColor(Patterns.matchesSomething(cb.block) ? 0xFFE0E0E0 : 0xFFFF5555);
            id.setChangedListener(s -> {
                cb.block = s.trim();
                id.setEditableColor(Patterns.matchesSomething(cb.block) ? 0xFFE0E0E0 : 0xFFFF5555);
                changed();
            });
            place(id, y);
            numberField(left + idW + 4, 62, y, cb.weight, v -> cb.weight = v, "Gewicht pro Block");
            final int index = i;
            place(ButtonWidget.builder(Text.literal(tr("✕")), b -> {
                a.customBlocks.remove(index);
                changed();
                rebuildKeepScroll();
            }).dimensions(left + contentW - 20, 0, 20, 20).tooltip(Tooltip.of(Text.literal(tr("Entfernen")))).build(), y);
            cursorY += ROW;
        }
        newline();
        place(ButtonWidget.builder(Text.literal(tr("+ Eigenen Block hinzufügen")), b -> {
            a.customBlocks.add(new ModConfig.CustomBlock("minecraft:tnt", 3.0));
            changed();
            rebuildKeepScroll();
        }).dimensions(left, 0, colW, 20).build(), cursorY);
        cursorY += ROW;

        header("Natürliche Lichtquellen (pro Dimension)");
        field("Oberwelt", 70, String.join(", ", a.naturalLightOverworld), 8000, s -> {
            a.naturalLightOverworld = splitList(s);
            changed();
        }, "Diese Blöcke gelten in der Oberwelt als natürliche Lichtquellen.");
        field("Nether", 70, String.join(", ", a.naturalLightNether), 8000, s -> {
            a.naturalLightNether = splitList(s);
            changed();
        }, "Diese Blöcke gelten im Nether als natürliche Lichtquellen.");
        field("End", 70, String.join(", ", a.naturalLightEnd), 8000, s -> {
            a.naturalLightEnd = splitList(s);
            changed();
        }, "Diese Blöcke gelten im End als natürliche Lichtquellen.");

        header("Typischer Score natürlicher Strukturen");
        for (Map.Entry<String, Double> e : a.structureTypicalScore.entrySet()) {
            int y = cursorY;
            int x = cellX();
            labels.add(new Label(Lang.name(e.getKey()), x, y + 6, 0xFFE0E0E0, false));
            numberField(x + colW - 60, 60, y, e.getValue(), v -> e.setValue(Math.max(0, v)), "Typischer Score dieser Struktur");
            advance();
        }
        newline();
        place(ButtonWidget.builder(Text.literal(tr("Gewichte auf Standard zurücksetzen")), b -> {
            a.lightGroups = ModConfig.defaultLightGroups();
            a.otherGroups = ModConfig.defaultOtherGroups();
            a.structureTypicalScore = ModConfig.defaultTypicalScores();
            ModConfig.Anomaly def = new ModConfig.Anomaly();
            a.naturalLightOverworld = def.naturalLightOverworld;
            a.naturalLightNether = def.naturalLightNether;
            a.naturalLightEnd = def.naturalLightEnd;
            changed();
            rebuildKeepScroll();
        }).dimensions(left, 0, contentW, 20).build(), cursorY);
        cursorY += ROW;
    }

    private void groupRows(List<ModConfig.WeightedGroup> groups) {
        for (ModConfig.WeightedGroup g : groups) {
            int y = cursorY;
            int x = cellX();
            String tip = String.join(", ", g.blocks)
                    + (g.ignoreInStructure.isEmpty() ? "" : g.ignoreInStructure.equals("*") ? "\n(nicht in erkannten Strukturen)" : "\n(nicht in: " + g.ignoreInStructure + ")");
            ButtonWidget b = ButtonWidget.builder(groupText(g), w -> {
                g.enabled = !g.enabled;
                w.setMessage(groupText(g));
                changed();
            }).dimensions(x, 0, colW - 50, 20).tooltip(Tooltip.of(Text.literal(tip))).build();
            place(b, y);
            numberField(x + colW - 46, 46, y, g.weight, v -> g.weight = Math.max(0, v), "Gewicht pro Block");
            advance();
        }
        newline();
    }

    private static Text groupText(ModConfig.WeightedGroup g) {
        return g.enabled ? Text.literal("✔ " + Lang.name(g.name)).formatted(Formatting.WHITE)
                : Text.literal("✘ " + Lang.name(g.name)).formatted(Formatting.DARK_GRAY, Formatting.STRIKETHROUGH);
    }
}
