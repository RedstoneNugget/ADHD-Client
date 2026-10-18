package com.adhdclient.casino.core.loot;

/**
 * Inhalt einer geöffneten Kiste.
 *
 * @param rarity    Seltenheit
 * @param cosmetic  kosmetischer Gegenstand oder null (dann reiner Chip-Bonus)
 * @param chips     gutgeschriebene Chips (Bonus oder Duplikat-Entschädigung)
 * @param duplicate true, wenn der Gegenstand schon im Besitz war
 */
public record LootReward(Rarity rarity, Cosmetic cosmetic, long chips, boolean duplicate) {

    public boolean isCosmetic() {
        return cosmetic != null;
    }

    public boolean isNewCosmetic() {
        return cosmetic != null && !duplicate;
    }
}
