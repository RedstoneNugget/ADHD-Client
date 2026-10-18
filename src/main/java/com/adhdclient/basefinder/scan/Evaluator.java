package com.adhdclient.basefinder.scan;

import com.adhdclient.basefinder.Lang;
import com.adhdclient.basefinder.config.ModConfig;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleFunction;

/**
 * Wertet die gecachten Rohdaten mit der aktuellen Config aus: Schwellen, Kriterien, Gewichte,
 * Strukturfilter, Sortierung und Top-N. Läuft auf dem Client-Thread, ist billig (nur Zählwerte)
 * und wird nach jeder Änderung neu ausgeführt.
 */
public final class Evaluator {

    /** Ein Fund über der Schwelle (für Benachrichtigungen und Zusammenfassung). */
    public record Finding(String key, long chunkKey, String text, double value) {
    }

    public static final class Results {
        public final Map<Marker.Mode, List<Marker>> markers = new EnumMap<>(Marker.Mode.class);
        /** Alle Funde (nicht nur Top-N), sortiert wie die Markierungen – für die Fundliste. */
        public final Map<Marker.Mode, List<Marker>> all = new EnumMap<>(Marker.Mode.class);
        public final Map<Marker.Mode, List<Finding>> qualifying = new EnumMap<>(Marker.Mode.class);

        Results() {
            for (Marker.Mode m : Marker.Mode.values()) {
                markers.put(m, List.of());
                all.put(m, List.of());
                qualifying.put(m, List.of());
            }
        }

        public List<Marker> get(Marker.Mode m) {
            return markers.get(m);
        }

        public static final Results EMPTY = new Results();
    }

    private Evaluator() {
    }

    public static Results evaluate(Map<Long, ChunkResult> cache, ModConfig cfg, Compiled comp,
                                   RegistryKey<World> dim, Vec3d player) {
        return evaluate(cache, cfg, comp, dim, player, Set.of());
    }

    /** @param hidden vom Spieler ausgeblendete Funde (Schlüssel), tauchen weder als Markierung noch in der Liste auf */
    public static Results evaluate(Map<Long, ChunkResult> cache, ModConfig cfg, Compiled comp,
                                   RegistryKey<World> dim, Vec3d player, Set<String> hidden) {
        Results r = new Results();
        if (cfg.amethyst.enabled && comp.amethystOn) amethyst(r, cache, cfg, hidden, player);
        if (cfg.bees.enabled && comp.beesOn) bees(r, cache, cfg, hidden, dim, player);
        if (cfg.spawners.enabled && comp.spawnersOn) spawners(r, cache, cfg, hidden, comp, dim, player);
        if (cfg.kelp.enabled && comp.kelpOn) kelp(r, cache, cfg, hidden, player);
        if (cfg.anomaly.enabled && comp.anomalyOn) anomaly(r, cache, cfg, hidden, comp, dim, player);
        return r;
    }

    // ------------------------------------------------------------------ Amethyst

    private static void amethyst(Results r, Map<Long, ChunkResult> cache, ModConfig cfg, Set<String> hidden, Vec3d player) {
        ModConfig.Amethyst a = cfg.amethyst;
        boolean[] on = {a.countCluster, a.countLargeBud, a.countMediumBud, a.countSmallBud, a.countBudding};
        boolean onlyClusters = a.countCluster && !a.countLargeBud && !a.countMediumBud && !a.countSmallBud && !a.countBudding;
        record C(ChunkResult res, int count, int clusters, int minY, int maxY, Vec3d center, double dist) {
        }
        List<C> list = new ArrayList<>();
        for (ChunkResult res : cache.values()) {
            int count = 0;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int i = 0; i < on.length; i++) {
                if (!on[i]) continue;
                int n = res.count(Compiled.AMETHYST_TYPES[i]);
                if (n <= 0) continue;
                count += n;
                minY = Math.min(minY, res.amethystMinY[i]);
                maxY = Math.max(maxY, res.amethystMaxY[i]);
            }
            if (count < a.minCount || minY > maxY) continue;
            Vec3d center = new Vec3d((res.cx << 4) + 8, (minY + maxY + 1) / 2.0, (res.cz << 4) + 8);
            list.add(new C(res, count, res.count(Compiled.AMETHYST_TYPES[0]), minY, maxY, center, center.distanceTo(player)));
        }
        list.sort(Comparator.<C>comparingInt(c -> -c.count).thenComparingDouble(c -> c.dist));
        List<Finding> q = new ArrayList<>();
        List<Marker> m = new ArrayList<>();
        List<Marker> all = new ArrayList<>();
        int rank = 0;
        for (int idx = 0; idx < list.size(); idx++) {
            C c = list.get(idx);
            String what = onlyClusters ? c.count + Lang.t(" Cluster", " clusters") : c.count + Lang.t(" gezählt (", " counted (") + c.clusters + Lang.t(" Cluster)", " clusters)");
            String chunk = "Chunk (" + c.res.cx + ", " + c.res.cz + ")";
            String key = "A:" + c.res.key;
            if (hidden.contains(key)) continue; // vom Spieler ausgeblendet
            int i = rank++;
            q.add(new Finding(key, c.res.key, Lang.t("Amethyst: neuer ", "Amethyst: new ") + chunk + Lang.t(" mit ", " with ") + what, c.count));
            {
                boolean shown = i < a.topN;
                boolean best = i == 0;
                List<String> lines = new ArrayList<>();
                lines.add(best ? Lang.t("★ Bester Chunk", "★ Best chunk") : "Amethyst #" + (i + 1));
                lines.add(what);
                lines.add(chunk);
                addMarker(m, all, shown, new Marker(Marker.Mode.AMETHYST, true, chunkBox(c.res, c.minY, c.maxY), best, lines, 2,
                        c.center, key, "", c.count));
            }
        }
        r.markers.put(Marker.Mode.AMETHYST, m);
        r.all.put(Marker.Mode.AMETHYST, all);
        r.qualifying.put(Marker.Mode.AMETHYST, q);
    }

    // ------------------------------------------------------------------ Bienennester

    public enum BeeReason {
        DIMENSION_NETHER("Im Nether", "im Nether", "In the Nether", "in the Nether"),
        DIMENSION_END("Im End", "im End", "In the End", "in the End"),
        DIMENSION_OTHER("Fremde Dimension", "in fremder Dimension", "Foreign dimension", "in a foreign dimension"),
        UNDERGROUND("Unter der Erde", "unter der Erde", "Underground", "underground"),
        BIOME("Falsches Biom", "im falschen Biom", "Wrong biome", "in the wrong biome"),
        NO_TREE("Keine Baumstruktur", "ohne Baumstruktur", "No tree nearby", "without a tree"),
        BEEHIVE("Bienenstock (nie natürlich)", "nie natürlich", "Beehive (never natural)", "never natural");

        private final String textDe, phraseDe, textEn, phraseEn;

        BeeReason(String textDe, String phraseDe, String textEn, String phraseEn) {
            this.textDe = textDe;
            this.phraseDe = phraseDe;
            this.textEn = textEn;
            this.phraseEn = phraseEn;
        }

        public String text() {
            return Lang.t(textDe, textEn);
        }

        /** Kurzform für Benachrichtigungen ("Bienennest: unter der Erde bei ..."). */
        public String phrase() {
            return Lang.t(phraseDe, phraseEn);
        }
    }

    /** Gründe in Prioritätsreihenfolge; leer = natürlich. */
    public static List<BeeReason> beeReasons(ChunkResult.NestFact n, ModConfig.Bees b, RegistryKey<World> dim) {
        List<BeeReason> out = new ArrayList<>();
        if (b.critDimension && !World.OVERWORLD.equals(dim)) {
            out.add(World.NETHER.equals(dim) ? BeeReason.DIMENSION_NETHER : World.END.equals(dim) ? BeeReason.DIMENSION_END : BeeReason.DIMENSION_OTHER);
        }
        if (b.critUnderground && n.underground) out.add(BeeReason.UNDERGROUND);
        if (b.critBiome && !b.naturalBiomes.contains(n.biome)) out.add(BeeReason.BIOME);
        // logs < 0: Nachbarchunk nicht geladen -> Baumstruktur unbekannt, im Zweifel natürlich
        if (b.critNoTree && n.logs >= 0 && !(n.logs >= 1 && n.logs + n.leaves >= b.minTreeBlocks)) out.add(BeeReason.NO_TREE);
        if (n.beehive && b.beehiveAlwaysUnnatural) out.add(BeeReason.BEEHIVE);
        return out;
    }

    private static void bees(Results r, Map<Long, ChunkResult> cache, ModConfig cfg, Set<String> hidden, RegistryKey<World> dim, Vec3d player) {
        ModConfig.Bees b = cfg.bees;
        record C(ChunkResult.NestFact n, List<BeeReason> reasons, int bees, double dist) {
            double beeRank() {
                return bees < 0 ? 0.5 : bees; // unbekannt zwischen leer und bewohnt
            }
        }
        List<C> list = new ArrayList<>();
        for (ChunkResult res : cache.values()) {
            for (ChunkResult.NestFact n : res.nests) {
                if (n.beehive ? !b.includeBeehive : !b.includeBeeNest) continue;
                List<BeeReason> reasons = beeReasons(n, b, dim);
                if (reasons.isEmpty()) continue;
                int bees = BeeCounts.get(n.pos);
                if (bees < 0) bees = n.clientBees;
                if (b.hideEmpty && bees == 0) continue;
                list.add(new C(n, reasons, bees, Vec3d.ofCenter(n.pos).distanceTo(player)));
            }
        }
        list.sort(Comparator.<C>comparingDouble(c -> -c.beeRank())
                .thenComparingInt(c -> -c.n.honey)
                .thenComparingDouble(c -> c.dist));
        List<Finding> q = new ArrayList<>();
        List<Marker> m = new ArrayList<>();
        List<Marker> all = new ArrayList<>();
        int rank = 0;
        for (int idx = 0; idx < list.size(); idx++) {
            C c = list.get(idx);
            BlockPos p = c.n.pos;
            String pos = "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
            String main = c.reasons.get(0).text();
            String key = "B:" + p.asLong();
            if (hidden.contains(key)) continue; // vom Spieler ausgeblendet
            int i = rank++;
            String what = c.n.beehive ? Lang.t("Bienenstock", "Beehive") : Lang.t("Bienennest", "Bee nest");
            q.add(new Finding(key, ChunkPos.toLong(p.getX() >> 4, p.getZ() >> 4), what + ": " + c.reasons.get(0).phrase() + Lang.t(" bei ", " at ") + pos, Math.max(0, c.bees)));
            {
                boolean shown = i < b.topN;
                boolean best = i == 0;
                List<String> lines = new ArrayList<>();
                lines.add((best ? "★ " : "") + (c.n.beehive ? Lang.t("Unnatürlicher Bienenstock", "Unnatural beehive") : Lang.t("Unnatürliches Bienennest", "Unnatural bee nest")));
                lines.add(Lang.t("Grund: ", "Reason: ") + main + (c.reasons.size() > 1 ? " (+" + (c.reasons.size() - 1) + ")" : ""));
                lines.add(pos);
                String bees = c.bees < 0 ? "?" : Integer.toString(c.bees);
                String honey = c.n.honey < 0 ? "?" : c.n.honey + "/5";
                lines.add(Lang.t("Bienen: ", "Bees: ") + bees + Lang.t(" · Honig: ", " · Honey: ") + honey);
                addMarker(m, all, shown, new Marker(Marker.Mode.BEES, false, new Box(p).expand(0.03), best, lines, 2,
                        Vec3d.ofCenter(p), key, "", Math.max(0, c.bees)));
            }
        }
        r.markers.put(Marker.Mode.BEES, m);
        r.all.put(Marker.Mode.BEES, all);
        r.qualifying.put(Marker.Mode.BEES, q);
    }

    // ------------------------------------------------------------------ Spawner

    public enum SpawnerReason {
        WRONG_MOB("Mob nie aus natürlichen Spawnern", "mit falschem Mob", "Mob never in natural spawners", "with a wrong mob"),
        DIMENSION("Falsche Dimension", "in falscher Dimension", "Wrong dimension", "in the wrong dimension"),
        NO_STRUCTURE("Keine passende Struktur", "ohne passende Struktur", "No matching structure", "without a matching structure"),
        OPEN_SKY("Unter freiem Himmel", "unter freiem Himmel", "Under open sky", "under open sky"),
        EMPTY("Ohne Mob", "ohne Mob", "No mob", "without a mob");

        private final String textDe, phraseDe, textEn, phraseEn;

        SpawnerReason(String textDe, String phraseDe, String textEn, String phraseEn) {
            this.textDe = textDe;
            this.phraseDe = phraseDe;
            this.textEn = textEn;
            this.phraseEn = phraseEn;
        }

        public String text() {
            return Lang.t(textDe, textEn);
        }

        public String phrase() {
            return Lang.t(phraseDe, phraseEn);
        }
    }

    /** Gründe in Prioritätsreihenfolge (leer = natürlich) und die erwartete Struktur (oder null). */
    public record SpawnerVerdict(List<SpawnerReason> reasons, String expected) {
    }

    public static SpawnerVerdict spawnerVerdict(ChunkResult.SpawnerFact f, ModConfig.Spawners s, Compiled comp, RegistryKey<World> dim) {
        List<SpawnerReason> out = new ArrayList<>();
        String expected = null;
        if (f.trial()) {
            expected = "Trial Chambers";
        } else if (f.mob() == null) {
            if (s.critEmpty) out.add(SpawnerReason.EMPTY);
        } else if (!f.mob().equals("?")) {
            expected = s.naturalMobs.get(f.mob());
            if (expected == null && s.critWrongMob) out.add(SpawnerReason.WRONG_MOB);
        }
        if (expected != null) {
            for (int i = 0; i < comp.spawnerContexts.size(); i++) {
                Compiled.SpawnerCtx c = comp.spawnerContexts.get(i);
                if (!c.name().equalsIgnoreCase(expected)) continue;
                if (s.critDimension && !dim.getValue().toString().equals(Patterns.normalize(c.dimension()))) {
                    out.add(SpawnerReason.DIMENSION);
                }
                // contextCounts == null: Umgebung (noch) nicht vollständig geladen -> im Zweifel natürlich
                if (s.critNoStructure && f.contextCounts() != null && i < f.contextCounts().length
                        && f.contextCounts()[i] < c.minCount()) {
                    out.add(SpawnerReason.NO_STRUCTURE);
                }
                break;
            }
        }
        if (s.critOpenSky && f.openSky()) out.add(SpawnerReason.OPEN_SKY);
        out.sort(Comparator.naturalOrder());
        return new SpawnerVerdict(out, expected);
    }

    private static String mobName(String id) {
        if (id == null) return Lang.t("keiner", "none");
        if (id.equals("?")) return Lang.t("unbekannt", "unknown");
        Identifier ident = Identifier.tryParse(id);
        if (ident != null && Registries.ENTITY_TYPE.containsId(ident)) {
            return Registries.ENTITY_TYPE.get(ident).getName().getString();
        }
        return id;
    }

    private static void spawners(Results r, Map<Long, ChunkResult> cache, ModConfig cfg, Set<String> hidden, Compiled comp,
                                 RegistryKey<World> dim, Vec3d player) {
        ModConfig.Spawners s = cfg.spawners;
        record C(ChunkResult.SpawnerFact f, SpawnerVerdict v, double dist) {
        }
        List<C> list = new ArrayList<>();
        for (ChunkResult res : cache.values()) {
            for (ChunkResult.SpawnerFact f : res.spawners) {
                if (f.trial() && !s.includeTrialSpawners) continue;
                SpawnerVerdict v = spawnerVerdict(f, s, comp, dim);
                if (v.reasons().isEmpty() && !s.showAll) continue;
                list.add(new C(f, v, Vec3d.ofCenter(f.pos()).distanceTo(player)));
            }
        }
        // unnatürliche zuerst (schwerster Grund, Anzahl der Gründe), natürliche danach; jeweils nach Entfernung
        list.sort(Comparator.<C>comparingInt(c -> c.v.reasons().isEmpty() ? Integer.MAX_VALUE : c.v.reasons().get(0).ordinal())
                .thenComparingInt(c -> -c.v.reasons().size())
                .thenComparingDouble(c -> c.dist));
        List<Finding> q = new ArrayList<>();
        List<Marker> m = new ArrayList<>();
        List<Marker> all = new ArrayList<>();
        int rank = 0;
        for (int idx = 0; idx < list.size(); idx++) {
            C c = list.get(idx);
            BlockPos p = c.f.pos();
            String pos = "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
            SpawnerReason main = c.v.reasons().isEmpty() ? null : c.v.reasons().get(0);
            String mob = c.f.trial() ? Lang.t("Trial-Spawner", "Trial spawner") : mobName(c.f.mob());
            String key = "S:" + p.asLong();
            if (hidden.contains(key)) continue; // vom Spieler ausgeblendet
            int i = rank++;
            boolean natural = c.v.reasons().isEmpty();
            if (natural) {
                {
                boolean shown = i < s.topN;
                    List<String> lines = new ArrayList<>();
                    lines.add((i == 0 ? "★ " : "") + (c.f.trial() ? Lang.t("Trial-Spawner (natürlich)", "Trial spawner (natural)") : Lang.t("Spawner (natürlich)", "Spawner (natural)")));
                    if (!c.f.trial()) lines.add("Mob: " + mob);
                    lines.add(Lang.t("Struktur: ", "Structure: ") + (c.v.expected() != null ? Lang.name(c.v.expected()) : Lang.t("unbekannt", "unknown")));
                    lines.add(pos);
                    addMarker(m, all, shown, new Marker(Marker.Mode.SPAWNERS, false, new Box(p).expand(0.03), i == 0, lines, lines.size() - 1,
                            Vec3d.ofCenter(p), key, "", 0));
                }
                continue;
            }
            q.add(new Finding(key, ChunkPos.toLong(p.getX() >> 4, p.getZ() >> 4),
                    "Spawner (" + mob + "): " + main.phrase() + Lang.t(" bei ", " at ") + pos, c.v.reasons().size()));
            {
                boolean shown = i < s.topN;
                boolean best = i == 0;
                List<String> lines = new ArrayList<>();
                lines.add((best ? "★ " : "") + (c.f.trial() ? Lang.t("Unnatürlicher Trial-Spawner", "Unnatural trial spawner") : Lang.t("Unnatürlicher Spawner", "Unnatural spawner")));
                if (!c.f.trial()) lines.add("Mob: " + mob);
                String grund = Lang.t("Grund: ", "Reason: ") + main.text();
                if ((main == SpawnerReason.NO_STRUCTURE || main == SpawnerReason.DIMENSION) && c.v.expected() != null) {
                    grund += Lang.t(" (erwartet: ", " (expected: ") + Lang.name(c.v.expected()) + ")";
                }
                if (c.v.reasons().size() > 1) grund += " +" + (c.v.reasons().size() - 1);
                lines.add(grund);
                lines.add(pos);
                addMarker(m, all, shown, new Marker(Marker.Mode.SPAWNERS, false, new Box(p).expand(0.03), best, lines, lines.size() - 1,
                        Vec3d.ofCenter(p), key, "", c.v.reasons().size()));
            }
        }
        r.markers.put(Marker.Mode.SPAWNERS, m);
        r.all.put(Marker.Mode.SPAWNERS, all);
        r.qualifying.put(Marker.Mode.SPAWNERS, q);
    }

    // ------------------------------------------------------------------ Kelp

    private static void kelp(Results r, Map<Long, ChunkResult> cache, ModConfig cfg, Set<String> hidden, Vec3d player) {
        ModConfig.Kelp k = cfg.kelp;
        record C(ChunkResult.KelpColumn col, double dist) {
        }
        List<C> list = new ArrayList<>();
        for (ChunkResult res : cache.values()) {
            for (ChunkResult.KelpColumn col : res.kelp) {
                if (col.height() >= k.minHeight) list.add(new C(col, Vec3d.ofCenter(col.top()).distanceTo(player)));
            }
        }
        list.sort(Comparator.<C>comparingInt(c -> -c.col.height()).thenComparingDouble(c -> c.dist));
        List<Finding> q = new ArrayList<>();
        List<Marker> m = new ArrayList<>();
        List<Marker> all = new ArrayList<>();
        int rank = 0;
        for (int idx = 0; idx < list.size(); idx++) {
            C c = list.get(idx);
            BlockPos p = c.col.top();
            String pos = "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
            String key = "K:" + p.getX() + "," + p.getZ();
            if (hidden.contains(key)) continue; // vom Spieler ausgeblendet
            int i = rank++;
            q.add(new Finding(key, ChunkPos.toLong(p.getX() >> 4, p.getZ() >> 4), "Kelp: " + c.col.height() + Lang.t(" Blöcke hoch bei ", " blocks tall at ") + pos, c.col.height()));
            {
                boolean shown = i < k.topN;
                boolean best = i == 0;
                List<String> lines = new ArrayList<>();
                lines.add(best ? Lang.t("★ Höchster Kelp", "★ Tallest kelp") : Lang.t("Übergroßer Kelp", "Oversized kelp"));
                lines.add(Lang.t("Höhe: ", "Height: ") + c.col.height() + Lang.t(" Blöcke", " blocks"));
                lines.add(pos);
                addMarker(m, all, shown, new Marker(Marker.Mode.KELP, false, new Box(p).expand(0.03), best, lines, 2,
                        Vec3d.ofCenter(p), key, "", c.col.height()));
            }
        }
        r.markers.put(Marker.Mode.KELP, m);
        r.all.put(Marker.Mode.KELP, all);
        r.qualifying.put(Marker.Mode.KELP, q);
    }

    // ------------------------------------------------------------------ Anomalien

    /** Ergebnis der Anomalie-Bewertung eines Chunks. */
    public static final class AnomalyScore {
        public double raw;
        public double score;
        public String structure;       // erkannte Struktur oder null
        public boolean hiddenByStructure;
        public boolean base;
        public final List<Part> parts = new ArrayList<>();
        public long sectionMask;

        public String reason(int max) {
            List<Part> sorted = new ArrayList<>(parts);
            sorted.sort(Comparator.comparingDouble((Part p) -> -p.points));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(max, sorted.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(sorted.get(i).label);
            }
            return sb.toString();
        }
    }

    public record Part(String label, double points) {
    }

    public static AnomalyScore scoreChunk(ChunkResult res, Map<Long, ChunkResult> cache, ModConfig cfg, Compiled comp, RegistryKey<World> dim) {
        List<String> structures = cfg.anomaly.structureFilter ? detectStructures(cache, cfg).get(res.key) : null;
        return scoreChunk(res, structures, cfg, comp, dim);
    }

    public static AnomalyScore scoreChunk(ChunkResult res, List<String> structures, ModConfig cfg, Compiled comp, RegistryKey<World> dim) {
        if (structures == null) structures = List.of();
        String structure = structures.isEmpty() ? null : structures.get(0);
        ModConfig.Anomaly a = cfg.anomaly;
        AnomalyScore s = new AnomalyScore();
        Set<Block> natural = comp.naturalSetFor(dim);
        s.structure = structure;

        int lightTypes = 0;
        if (a.lightSources) {
            for (Compiled.Group g : comp.lightGroups) {
                if (!g.enabled || g.weight <= 0 || ignored(g, structures)) continue;
                int n = 0;
                for (Block b : g.blocks) {
                    if (natural.contains(b)) continue;
                    int c = res.count(b);
                    if (c > 0) {
                        n += c;
                        s.sectionMask |= res.sectionMask.getLong(b);
                    }
                }
                n += entities(res, g);
                if (n > 0) {
                    lightTypes++;
                    s.parts.add(new Part(n + " " + Lang.name(g.name), n * g.weight));
                }
            }
        }
        if (a.lightCheck && res.lightChecked) {
            if (res.litArtificialAir > 0 && a.lightCheckWeightPer10 > 0) {
                double pts = Math.min(a.lightCheckMax, res.litArtificialAir / 10.0 * a.lightCheckWeightPer10);
                s.parts.add(new Part(res.litArtificialAir + Lang.t(" künstl. beleuchtete Blöcke", " artificially lit blocks"), pts));
                s.sectionMask |= res.litSectionMask;
            }
            if (a.unknownLightWeight > 0) {
                int unknown = 0;
                for (Reference2IntMap.Entry<Block> e : res.emitterCounts.reference2IntEntrySet()) {
                    Block b = e.getKey();
                    if (natural.contains(b) || comp.listedBlocks.contains(b)) continue;
                    unknown += e.getIntValue();
                    s.sectionMask |= res.sectionMask.getLong(b);
                }
                if (unknown > 0) s.parts.add(new Part(unknown + Lang.t(" unbek. Lichtquellen", " unknown light sources"), unknown * a.unknownLightWeight));
            }
        }
        int otherTypes = 0;
        if (a.otherBlocks) {
            for (Compiled.Group g : comp.otherGroups) {
                if (!g.enabled || g.weight <= 0 || ignored(g, structures)) continue;
                int n = 0;
                for (Block b : g.blocks) {
                    int c = res.count(b);
                    if (c > 0) {
                        n += c;
                        s.sectionMask |= res.sectionMask.getLong(b);
                    }
                }
                n += entities(res, g);
                if (n > 0) {
                    otherTypes++;
                    s.parts.add(new Part(n + " " + Lang.name(g.name), n * g.weight));
                }
            }
        }
        for (Compiled.Group g : comp.customGroups) {
            if (g.weight == 0) continue;
            int n = 0;
            for (Block b : g.blocks) {
                int c = res.count(b);
                if (c > 0) {
                    n += c;
                    s.sectionMask |= res.sectionMask.getLong(b);
                }
            }
            n += entities(res, g);
            if (n > 0) {
                String name = g.blocks.size() == 1 ? g.blocks.iterator().next().getName().getString() : g.name;
                s.parts.add(new Part(n + "× " + name, n * g.weight));
            }
        }
        for (Part p : s.parts) s.raw += p.points;
        s.score = s.raw;
        s.base = a.markBase && structure == null && lightTypes >= 2 && otherTypes >= 2;
        if (structure != null) {
            // erste Struktur = die mit dem höchsten typischen Score (tolerantester Maßstab)
            double typical = a.structureTypicalScore.getOrDefault(structure, 10.0);
            if (s.raw < typical * a.structureToleranceFactor) {
                s.hiddenByStructure = true;
                s.score = 0;
            }
        }
        return s;
    }

    private static int entities(ChunkResult res, Compiled.Group g) {
        int n = 0;
        for (EntityType<?> t : g.entities) n += res.entityCounts.getInt(t);
        return n;
    }


    private static boolean ignored(Compiled.Group g, List<String> structures) {
        if (structures.isEmpty() || g.ignoreInStructure.isEmpty()) return false;
        if (g.ignoreInStructure.equals("*")) return true;
        for (String s : structures) {
            if (g.ignoreInStructure.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    /** Wichtigste erkannte Struktur eines einzelnen Chunks (berücksichtigt seine Nachbarn) oder null. */
    public static String detectStructure(ChunkResult res, Map<Long, ChunkResult> cache, ModConfig cfg) {
        List<String> l = detectStructures(cache, cfg).get(res.key);
        return l == null || l.isEmpty() ? null : l.get(0);
    }

    /**
     * Erkannte Strukturen aller gecachten Chunks. Ein Chunk kann mehrere haben (z. B. ein Dorf mit
     * einem Verlies darunter); sortiert nach typischem Score, der tolerantere Maßstab steht vorn.
     *  1. Eigene Stärke >= 1 -> erkannt.
     *  2. Zwei Ausbreitungsrunden, weil Strukturen über Chunkgrenzen ragen und nicht jeder Teil
     *     typische Blöcke enthält (z. B. ein Haus am Dorfrand): erkannt, wenn ein Nachbar dieselbe
     *     Struktur hat und der Chunk selbst schwache Hinweise (>= 0,25) zeigt, oder wenn mindestens
     *     zwei Nachbarn dieselbe Struktur haben.
     */
    public static Map<Long, List<String>> detectStructures(Map<Long, ChunkResult> cache, ModConfig cfg) {
        int n = StructureSignatures.ALL.size();
        Map<Long, Integer> det = new HashMap<>();
        for (ChunkResult res : cache.values()) {
            int mask = 0;
            double[] own = res.structureStrength;
            for (int i = 0; i < own.length && i < 32; i++) {
                if (own[i] >= 1.0) mask |= 1 << i;
            }
            if (mask != 0) det.put(res.key, mask);
        }
        for (int round = 0; round < 2; round++) {
            Map<Long, Integer> next = new HashMap<>(det);
            for (ChunkResult res : cache.values()) {
                int mask = det.getOrDefault(res.key, 0);
                int[] neighbours = new int[n];
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        int m = det.getOrDefault(ChunkPos.toLong(res.cx + dx, res.cz + dz), 0);
                        for (int i = 0; i < n && m != 0; i++) {
                            if ((m & (1 << i)) != 0) neighbours[i]++;
                        }
                    }
                }
                double[] own = res.structureStrength;
                int added = mask;
                for (int i = 0; i < n && i < 32; i++) {
                    if ((mask & (1 << i)) != 0 || neighbours[i] == 0) continue;
                    double v = i < own.length ? own[i] : 0;
                    if (v >= 0.25 || neighbours[i] >= 2) added |= 1 << i;
                }
                if (added != mask) next.put(res.key, added);
            }
            det = next;
        }
        Map<Long, List<String>> out = new HashMap<>();
        for (Map.Entry<Long, Integer> e : det.entrySet()) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < n && i < 32; i++) {
                if ((e.getValue() & (1 << i)) != 0) names.add(StructureSignatures.ALL.get(i).name);
            }
            names.sort(Comparator.comparingDouble((String s) -> -cfg.anomaly.structureTypicalScore.getOrDefault(s, 10.0)));
            out.put(e.getKey(), names);
        }
        return out;
    }

    private static void anomaly(Results r, Map<Long, ChunkResult> cache, ModConfig cfg, Set<String> hidden, Compiled comp,
                                RegistryKey<World> dim, Vec3d player) {
        ModConfig.Anomaly a = cfg.anomaly;
        record C(ChunkResult res, AnomalyScore s, Box box, Vec3d center, double dist) {
        }
        List<C> list = new ArrayList<>();
        Map<Long, List<String>> structures = a.structureFilter ? detectStructures(cache, cfg) : Map.of();
        for (ChunkResult res : cache.values()) {
            AnomalyScore s = scoreChunk(res, structures.get(res.key), cfg, comp, dim);
            if (s.score < a.minScore) continue;
            int minY;
            int maxY;
            if (s.sectionMask != 0) {
                int lo = Long.numberOfTrailingZeros(s.sectionMask);
                int hi = 63 - Long.numberOfLeadingZeros(s.sectionMask);
                minY = res.bottomY + lo * 16;
                maxY = res.bottomY + hi * 16 + 15;
            } else {
                minY = res.bottomY;
                maxY = res.bottomY + res.sectionCount * 16 - 1;
            }
            Box box = chunkBox(res, minY, maxY);
            Vec3d center = new Vec3d((res.cx << 4) + 8, (minY + maxY + 1) / 2.0, (res.cz << 4) + 8);
            list.add(new C(res, s, box, center, center.distanceTo(player)));
        }
        list.sort(Comparator.<C>comparingDouble(c -> -c.s.score).thenComparingDouble(c -> c.dist));
        List<Finding> q = new ArrayList<>();
        List<Marker> m = new ArrayList<>();
        List<Marker> all = new ArrayList<>();
        int rank = 0;
        for (int idx = 0; idx < list.size(); idx++) {
            C c = list.get(idx);
            String chunk = "Chunk (" + c.res.cx + ", " + c.res.cz + ")";
            int score = (int) Math.round(c.s.score);
            String reason = c.s.reason(3);
            String key = "N:" + c.res.key;
            if (hidden.contains(key)) continue; // vom Spieler ausgeblendet
            int i = rank++;
            q.add(new Finding(key, c.res.key, Lang.t("Anomalie: neuer ", "Anomaly: new ") + chunk + " Score " + score + " - " + c.s.reason(1), score));
            {
                boolean shown = i < a.topN;
                boolean best = i == 0;
                List<String> lines = new ArrayList<>();
                lines.add(best ? Lang.t("★ Auffälligster Chunk", "★ Most suspicious chunk") : Lang.t("Anomalie #", "Anomaly #") + (i + 1));
                lines.add("Score " + score + (c.s.base ? Lang.t(" · wahrscheinlich Basis", " · probably a base") : ""));
                lines.add(reason);
                if (c.s.structure != null) lines.add(Lang.t("trotz Struktur: ", "despite structure: ") + Lang.name(c.s.structure) + "?");
                lines.add(chunk);
                addMarker(m, all, shown, new Marker(Marker.Mode.ANOMALY, true, c.box, best, lines, lines.size() - 1,
                        c.center, key, "", score));
            }
        }
        r.markers.put(Marker.Mode.ANOMALY, m);
        r.all.put(Marker.Mode.ANOMALY, all);
        r.qualifying.put(Marker.Mode.ANOMALY, q);
    }

    private static void addMarker(List<Marker> shownList, List<Marker> all, boolean shown, Marker marker) {
        all.add(marker);
        if (shown) shownList.add(marker);
    }

    private static Box chunkBox(ChunkResult res, int minY, int maxY) {
        int x0 = res.cx << 4;
        int z0 = res.cz << 4;
        return new Box(x0, minY, z0, x0 + 16, maxY + 1, z0 + 16);
    }

    /** Zusammenfassung für die Actionbar nach einem Vollscan. */
    public static String summary(Results r, ModConfig cfg) {
        List<String> parts = new ArrayList<>();
        if (cfg.amethyst.enabled) {
            parts.add(best(r, Marker.Mode.AMETHYST, v -> Lang.t("Amethyst: bester Chunk ", "Amethyst: best chunk ") + (int) v + Lang.t(" Cluster", " clusters"), Lang.t("Amethyst: nichts", "Amethyst: none")));
        }
        if (cfg.bees.enabled) {
            parts.add(Lang.t("Unnatürliche Nester: ", "Unnatural nests: ") + r.qualifying.get(Marker.Mode.BEES).size());
        }
        if (cfg.spawners.enabled) {
            parts.add(Lang.t("Unnatürliche Spawner: ", "Unnatural spawners: ") + r.qualifying.get(Marker.Mode.SPAWNERS).size());
        }
        if (cfg.kelp.enabled) {
            parts.add(best(r, Marker.Mode.KELP, v -> Lang.t("Kelp: höchster ", "Kelp: tallest ") + (int) v, Lang.t("Kelp: nichts", "Kelp: none")));
        }
        if (cfg.anomaly.enabled) {
            parts.add(best(r, Marker.Mode.ANOMALY, v -> Lang.t("Anomalie: bester Chunk Score ", "Anomaly: best chunk score ") + (int) v, Lang.t("Anomalie: nichts", "Anomaly: none")));
        }
        return parts.isEmpty() ? Lang.t("Alle Suchmodi sind aus", "All search modes are off") : String.join(" | ", parts);
    }

    private static String best(Results r, Marker.Mode mode, DoubleFunction<String> fmt, String none) {
        List<Finding> q = r.qualifying.get(mode);
        if (q.isEmpty()) return none;
        double max = 0;
        for (Finding f : q) max = Math.max(max, f.value());
        return fmt.apply(max);
    }
}
