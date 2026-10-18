package com.adhdclient.basefinder.scan;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Heuristische Erkennung natürlich generierter Strukturen anhand typischer Blöcke im Chunk.
 *
 * Jede Struktur hat Merkmals-Blöcke mit Gewicht und Obergrenze (damit z. B. 500 Steinziegel
 * nicht allein reichen), einen Schwellwert und optional "Pflicht"-Merkmale, von denen
 * mindestens eines vorkommen muss. Ergebnis ist eine Stärke (Punkte / Schwellwert):
 * ab 1.0 gilt die Struktur als erkannt. Das ist bewusst eine Schätzung – Spielerbauten aus
 * denselben Blöcken können fälschlich als Struktur gelten und umgekehrt.
 */
public final class StructureSignatures {

    public static final class Feature {
        final String pattern;
        final double weight;
        final int cap;
        Set<Block> blocks = Set.of();

        Feature(String pattern, double weight, int cap) {
            this.pattern = pattern;
            this.weight = weight;
            this.cap = cap;
        }
    }

    public static final class Requirement {
        final String pattern;
        final int min;
        Set<Block> blocks = Set.of();

        Requirement(String pattern, int min) {
            this.pattern = pattern;
            this.min = min;
        }
    }

    public static final class Signature {
        public final String name;
        final double threshold;
        final List<Feature> features = new ArrayList<>();
        final List<Requirement> requireAny = new ArrayList<>();

        Signature(String name, double threshold) {
            this.name = name;
            this.threshold = threshold;
        }

        Signature f(String pattern, double weight, int cap) {
            features.add(new Feature(pattern, weight, cap));
            return this;
        }

        Signature req(String pattern, int min) {
            requireAny.add(new Requirement(pattern, min));
            return this;
        }

        /** Stärke >= 1 bedeutet "erkannt". */
        public double strength(Reference2IntMap<Block> counts) {
            if (!requireAny.isEmpty()) {
                boolean ok = false;
                for (Requirement r : requireAny) {
                    if (sum(counts, r.blocks) >= r.min) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) return 0;
            }
            double s = 0;
            for (Feature f : features) {
                int n = sum(counts, f.blocks);
                if (n > 0) s += Math.min(n, f.cap) * f.weight;
            }
            return s / threshold;
        }
    }

    public static final List<Signature> ALL = new ArrayList<>();

    static {
        ALL.add(new Signature("Mineshaft", 12)
                .f("cobweb", 2.5, 10).f("*rail", 0.6, 25)
                .f("oak_fence", 0.5, 20).f("dark_oak_fence", 0.5, 20)
                .f("oak_planks", 0.1, 40).f("dark_oak_planks", 0.1, 40).f("spawner", 3, 1)
                .req("*rail", 3).req("oak_fence", 4).req("dark_oak_fence", 4).req("cobweb", 3));
        ALL.add(new Signature("Festung", 15)
                .f("end_portal_frame", 20, 12).f("infested_*stone_bricks", 2, 20)
                .f("stone_bricks", 0.06, 250).f("mossy_stone_bricks", 0.2, 100).f("cracked_stone_bricks", 0.2, 100)
                .f("iron_bars", 1, 10).f("iron_door", 3, 2).f("bookshelf", 0.3, 40).f("stone_brick_stairs", 0.1, 40)
                .req("mossy_stone_bricks", 3).req("cracked_stone_bricks", 3).req("infested_*stone_bricks", 1).req("end_portal_frame", 1));
        ALL.add(new Signature("Dorf", 12)
                .f("bell", 20, 1).f("dirt_path", 0.5, 40).f("hay_block", 1, 10).f("composter", 2, 2)
                .f("lectern", 2, 2).f("smithing_table", 2, 2).f("fletching_table", 2, 2).f("cartography_table", 2, 2)
                .f("loom", 2, 2).f("stonecutter", 2, 2).f("grindstone", 2, 2).f("water_cauldron", 1, 2)
                .req("bell", 1).req("dirt_path", 8).req("hay_block", 3)
                .req("composter", 1).req("lectern", 1).req("fletching_table", 1).req("cartography_table", 1)
                .req("smithing_table", 1).req("loom", 1).req("stonecutter", 1).req("grindstone", 1));
        ALL.add(new Signature("Plünderer-Außenposten", 12)
                .f("dark_oak_log", 0.4, 40).f("birch_planks", 0.15, 120).f("cobblestone", 0.03, 120)
                .f("white_wall_banner", 5, 2).f("white_banner", 5, 2).f("dark_oak_fence", 0.3, 20)
                .req("dark_oak_log", 4));
        ALL.add(new Signature("Waldanwesen", 15)
                .f("dark_oak_planks", 0.04, 500).f("dark_oak_log", 0.1, 100).f("birch_planks", 0.03, 300)
                .f("cobblestone", 0.02, 200).f("glass_pane", 0.1, 100).f("*_carpet", 0.05, 100)
                .req("dark_oak_planks", 100));
        ALL.add(new Signature("Wüstentempel", 10)
                .f("blue_terracotta", 2, 10).f("orange_terracotta", 0.5, 40).f("chiseled_sandstone", 0.5, 20)
                .f("cut_sandstone", 0.03, 200).f("tnt", 2, 9).f("stone_pressure_plate", 2, 1).f("suspicious_sand", 1, 8)
                .req("blue_terracotta", 1).req("tnt", 4));
        ALL.add(new Signature("Dschungeltempel", 12)
                .f("mossy_cobblestone", 0.1, 150).f("chiseled_stone_bricks", 1, 10).f("tripwire_hook", 3, 4)
                .f("tripwire", 0.5, 10).f("sticky_piston", 2, 3).f("dispenser", 2, 2).f("lever", 1, 3)
                .f("redstone_wire", 0.2, 20)
                .req("tripwire_hook", 1).req("sticky_piston", 1));
        ALL.add(new Signature("Trial Chambers", 15)
                .f("trial_spawner", 10, 2).f("vault", 10, 2).f("tuff_bricks", 0.06, 300).f("chiseled_tuff*", 0.3, 50)
                .f("polished_tuff", 0.06, 300).f("waxed_*copper*", 0.08, 300).f("heavy_core", 5, 1)
                .req("trial_spawner", 1).req("vault", 1).req("tuff_bricks", 40).req("waxed_*copper*", 30));
        ALL.add(new Signature("Ancient City", 12)
                .f("reinforced_deepslate", 5, 20).f("sculk_shrieker", 3, 10).f("sculk_sensor", 0.5, 10)
                .f("deepslate_tiles", 0.05, 400).f("deepslate_bricks", 0.05, 400).f("cracked_deepslate_*", 0.1, 100)
                .f("gray_wool", 0.2, 30).f("soul_sand", 0.1, 30)
                .req("reinforced_deepslate", 1).req("deepslate_tiles", 40).req("deepslate_bricks", 40));
        ALL.add(new Signature("Bastion", 12)
                .f("gilded_blackstone", 3, 20).f("polished_blackstone_bricks", 0.05, 400)
                .f("cracked_polished_blackstone_bricks", 0.2, 100).f("chiseled_polished_blackstone", 0.5, 20)
                .f("blackstone", 0.02, 300).f("*chain", 0.5, 20).f("basalt", 0.02, 200)
                .req("gilded_blackstone", 1).req("polished_blackstone_bricks", 20));
        ALL.add(new Signature("Nether-Festung", 15)
                .f("nether_bricks", 0.05, 500).f("nether_brick_fence", 0.5, 50).f("nether_brick_stairs", 0.2, 50)
                .f("nether_wart", 0.5, 20).f("spawner", 5, 2)
                .req("nether_brick_fence", 2).req("nether_bricks", 60));
        ALL.add(new Signature("End-Stadt", 12)
                .f("purpur_block", 0.1, 300).f("purpur_pillar", 0.2, 100).f("purpur_stairs", 0.1, 100)
                .f("end_stone_bricks", 0.1, 300).f("magenta_stained_glass", 0.5, 20).f("dragon_*head", 10, 1)
                .req("purpur_block", 10).req("purpur_pillar", 5));
        ALL.add(new Signature("Ozeanmonument", 15)
                .f("prismarine", 0.05, 400).f("prismarine_bricks", 0.05, 400).f("dark_prismarine", 0.1, 200)
                .f("wet_sponge", 1, 10).f("gold_block", 0.2, 8)
                .req("prismarine_bricks", 20).req("dark_prismarine", 10));
        ALL.add(new Signature("Verlies", 10)
                .f("spawner", 8, 1).f("mossy_cobblestone", 0.3, 40).f("cobblestone", 0.03, 60).f("chest", 1, 2)
                .req("spawner", 1));
        ALL.add(new Signature("Iglu", 8)
                .f("snow_block", 0.05, 100).f("white_carpet", 1, 5).f("ice", 0.3, 5).f("red_bed", 2, 1)
                .f("brewing_stand", 2, 1).f("cauldron", 1, 1)
                .req("white_carpet", 3));
        ALL.add(new Signature("Hexenhütte", 6)
                .f("spruce_planks", 0.05, 100).f("cauldron", 2, 1).f("crafting_table", 1, 1)
                .f("potted_*", 2, 2).f("flower_pot", 2, 2)
                .req("potted_*", 1));
        ALL.add(new Signature("Trail Ruins", 8)
                .f("suspicious_gravel", 2, 10).f("mud_bricks", 0.1, 100).f("*_terracotta", 0.05, 200)
                .f("decorated_pot", 2, 3)
                .req("suspicious_gravel", 1));
    }

    private StructureSignatures() {
    }

    /** Muster neu auflösen (nach Welt-/Tag-Wechsel). */
    public static void resolve() {
        for (Signature s : ALL) {
            for (Feature f : s.features) f.blocks = Patterns.blocks(f.pattern);
            for (Requirement r : s.requireAny) r.blocks = Patterns.blocks(r.pattern);
        }
    }

    /** Alle Blöcke, die für die Erkennung gezählt werden müssen. */
    public static Set<Block> allBlocks() {
        Set<Block> out = new LinkedHashSet<>();
        for (Signature s : ALL) {
            for (Feature f : s.features) out.addAll(f.blocks);
            for (Requirement r : s.requireAny) out.addAll(r.blocks);
        }
        return out;
    }

    static int sum(Reference2IntMap<Block> counts, Set<Block> blocks) {
        int n = 0;
        for (Block b : blocks) n += counts.getInt(b);
        return n;
    }
}
