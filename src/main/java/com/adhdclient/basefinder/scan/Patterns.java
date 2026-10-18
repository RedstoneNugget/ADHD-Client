package com.adhdclient.basefinder.scan;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Löst Block-Muster aus der Config auf: exakte IDs, Platzhalter (*), Block-Tags (#...) und
 * Objekte ("entity:..."). Unbekannte IDs werden still ignoriert, damit eine Config auch mit
 * anderen Versionen/Mods weiter funktioniert.
 *
 * Ergebnisse werden pro Muster gecacht (Registries ändern sich zur Laufzeit nicht); nur Tags
 * können sich ändern, deshalb leert {@link #clearCache()} den Cache nach einem Tag-Reload.
 */
public final class Patterns {

    public static final String ENTITY_PREFIX = "entity:";

    private static final Map<String, Set<Block>> BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Set<EntityType<?>>> ENTITY_CACHE = new ConcurrentHashMap<>();
    private static volatile String[] blockIds;
    private static volatile Block[] blockArr;

    private Patterns() {
    }

    public static void clearCache() {
        BLOCK_CACHE.clear();
        ENTITY_CACHE.clear();
    }

    public static Set<Block> blocks(Collection<String> patterns) {
        Set<Block> out = new LinkedHashSet<>();
        if (patterns == null) return out;
        for (String raw : patterns) out.addAll(blocks(raw));
        return out;
    }

    public static Set<Block> blocks(String raw) {
        if (raw == null) return Set.of();
        String p = raw.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty() || p.startsWith(ENTITY_PREFIX)) return Set.of();
        Set<Block> cached = BLOCK_CACHE.get(p);
        if (cached != null) return cached;
        Set<Block> out = Collections.unmodifiableSet(resolveBlocks(p));
        BLOCK_CACHE.put(p, out);
        return out;
    }

    public static Set<EntityType<?>> entities(Collection<String> patterns) {
        Set<EntityType<?>> out = new LinkedHashSet<>();
        if (patterns == null) return out;
        for (String raw : patterns) {
            if (raw == null) continue;
            String p = raw.trim().toLowerCase(Locale.ROOT);
            if (!p.startsWith(ENTITY_PREFIX)) continue;
            out.addAll(ENTITY_CACHE.computeIfAbsent(p, Patterns::resolveEntities));
        }
        return out;
    }

    private static Set<EntityType<?>> resolveEntities(String p) {
        Set<EntityType<?>> out = new LinkedHashSet<>();
        String id = normalize(p.substring(ENTITY_PREFIX.length()));
        if (id.contains("*")) {
            String[] parts = id.split("\\*", -1);
            for (EntityType<?> t : Registries.ENTITY_TYPE) {
                if (globMatch(parts, Registries.ENTITY_TYPE.getId(t).toString())) out.add(t);
            }
        } else {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null && Registries.ENTITY_TYPE.containsId(ident)) out.add(Registries.ENTITY_TYPE.get(ident));
        }
        return Collections.unmodifiableSet(out);
    }

    private static Set<Block> resolveBlocks(String p) {
        Set<Block> out = new LinkedHashSet<>();
        if (p.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(normalize(p.substring(1)));
            if (tagId == null) return out;
            TagKey<Block> tag = TagKey.of(RegistryKeys.BLOCK, tagId);
            // Tags kommen vom Server; fehlen sie (noch), ist die Menge einfach leer.
            for (Block b : Registries.BLOCK) {
                if (b.getDefaultState().isIn(tag)) out.add(b);
            }
            return out;
        }
        String id = normalize(p);
        if (id.contains("*")) {
            ensureIds();
            String[] parts = id.split("\\*", -1);
            String[] ids = blockIds;
            Block[] arr = blockArr;
            for (int i = 0; i < ids.length; i++) {
                if (globMatch(parts, ids[i])) out.add(arr[i]);
            }
            return out;
        }
        Identifier ident = Identifier.tryParse(id);
        if (ident != null && Registries.BLOCK.containsId(ident)) out.add(Registries.BLOCK.get(ident));
        return out;
    }

    private static void ensureIds() {
        if (blockIds != null && blockIds.length == Registries.BLOCK.size()) return;
        int n = Registries.BLOCK.size();
        String[] ids = new String[n];
        Block[] arr = new Block[n];
        int i = 0;
        for (Block b : Registries.BLOCK) {
            if (i >= n) break;
            ids[i] = Registries.BLOCK.getId(b).toString();
            arr[i] = b;
            i++;
        }
        blockArr = arr;
        blockIds = ids;
    }

    /** Platzhalter-Vergleich ohne Regex: Teile zwischen den * müssen der Reihe nach vorkommen. */
    static boolean globMatch(String[] parts, String s) {
        if (parts.length == 1) return s.equals(parts[0]);
        String first = parts[0];
        String last = parts[parts.length - 1];
        if (!s.startsWith(first) || !s.endsWith(last) || s.length() < first.length() + last.length()) return false;
        int pos = first.length();
        int end = s.length() - last.length();
        for (int i = 1; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            int idx = s.indexOf(part, pos);
            if (idx < 0 || idx + part.length() > end) return false;
            pos = idx + part.length();
        }
        return true;
    }

    /** Ohne Namespace wird "minecraft:" angenommen. */
    public static String normalize(String id) {
        String t = id.trim().toLowerCase(Locale.ROOT);
        return t.contains(":") ? t : "minecraft:" + t;
    }

    /** Prüft, ob ein Text ein gültiges Muster ist, das mindestens einen Block oder ein Objekt trifft. */
    public static boolean matchesSomething(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String p = raw.trim().toLowerCase(Locale.ROOT);
        if (p.startsWith(ENTITY_PREFIX)) return !entities(List.of(p)).isEmpty();
        return !blocks(p).isEmpty();
    }
}
