package com.adhdclient.headfinder.detect;

import com.adhdclient.basefinder.scan.Patterns;
import com.adhdclient.headfinder.config.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prüft, ob ein Block (als platzierter Block, Drop-Item oder Item-Frame-Inhalt) zu einem der
 * konfigurierten Suchprofile passt. Die Muster (exakte ID, "*"-Platzhalter, "#tag") werden mit
 * derselben Logik aufgelöst wie bei Base Finder ({@link Patterns}).
 */
public final class BlockESP {

    public record Match(String profileName, String blockId) {
    }

    private Set<Block> targets = Set.of();
    private Map<Block, String> nameByBlock = Map.of();

    /** Nach jeder Config-Änderung aufrufen. */
    public void rebuild(ModConfig cfg) {
        Set<Block> out = new LinkedHashSet<>();
        Map<Block, String> names = new LinkedHashMap<>();
        for (ModConfig.SearchProfile p : cfg.profiles) {
            if (!p.enabled || p.pattern.isBlank()) continue;
            Set<Block> resolved = Patterns.blocks(p.pattern);
            out.addAll(resolved);
            String label = p.name.isBlank() ? p.pattern : p.name;
            for (Block b : resolved) names.putIfAbsent(b, label);
        }
        targets = out;
        nameByBlock = names;
    }

    public boolean hasCriteria() {
        return !targets.isEmpty();
    }

    /** Für die Palette-Vorprüfung beim Scannen einer Chunk-Sektion. */
    public Set<Block> targets() {
        return targets;
    }

    /** Platzierter Block an einer Position – Treffer, wenn er in einem der Muster vorkommt. */
    public Match match(BlockState state) {
        if (state == null) return null;
        Block b = state.getBlock();
        if (!targets.contains(b)) return null;
        return new Match(nameByBlock.getOrDefault(b, idOf(b)), idOf(b));
    }

    /** Als Item (Drop am Boden oder in einem Item-Frame) – nur Blöcke mit eigenem Item (BlockItem). */
    public Match match(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return null;
        return match(bi.getBlock().getDefaultState());
    }

    private static String idOf(Block b) {
        return Registries.BLOCK.getId(b).toString();
    }

    /** Für Befehle: lesbare Beschreibung der aktiven Muster. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Block b : targets) out.add(nameByBlock.getOrDefault(b, idOf(b)) + " (" + idOf(b) + ")");
        return out;
    }
}
