package com.adhdclient.casino.core.loot;

import com.adhdclient.casino.core.CasinoRng;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Ziehung des Kisteninhalts. */
public final class LootBox {

    /** Chance auf einen kosmetischen Gegenstand (sonst Chip-Bonus). */
    public static final double COSMETIC_CHANCE = 0.5;

    private LootBox() {
    }

    public static Rarity rollRarity(CasinoRng rng, LootBoxType type) {
        return Rarity.values()[rng.pickWeighted(type.rarityWeights())];
    }

    /**
     * @param owned Schlüssel ({@link Cosmetic#key()}) aller bereits besessenen Gegenstände
     */
    public static LootReward roll(CasinoRng rng, LootBoxType type, Set<String> owned) {
        Rarity rarity = rollRarity(rng, type);
        List<Cosmetic> pool = cosmeticsOf(rarity);
        if (!pool.isEmpty() && rng.chance(COSMETIC_CHANCE)) {
            Cosmetic c = rng.pick(pool);
            boolean duplicate = owned.contains(c.key());
            return new LootReward(rarity, c, duplicate ? rarity.duplicateReward() : 0, duplicate);
        }
        return new LootReward(rarity, null, rarity.chipReward(), false);
    }

    public static List<Cosmetic> cosmeticsOf(Rarity rarity) {
        List<Cosmetic> list = new ArrayList<>();
        for (Cosmetic c : Cosmetic.values()) {
            if (c.rarity() == rarity) {
                list.add(c);
            }
        }
        return list;
    }
}
