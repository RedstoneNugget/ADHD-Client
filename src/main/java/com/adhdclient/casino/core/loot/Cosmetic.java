package com.adhdclient.casino.core.loot;

/**
 * Kosmetische Belohnungen: Titel (werden im Menü/HUD angezeigt) und
 * Rahmenfarben (färben den Rahmen aller Casino-Fenster).
 */
public enum Cosmetic {
    // --- Titel ---
    TITLE_ROOKIE(Type.TITLE, "rookie", Rarity.COMMON, 0xFFB0B0B0),
    TITLE_GAMBLER(Type.TITLE, "gambler", Rarity.COMMON, 0xFFB0B0B0),
    TITLE_LUCKY(Type.TITLE, "lucky", Rarity.UNCOMMON, 0xFF55D655),
    TITLE_CARD_SHARK(Type.TITLE, "card_shark", Rarity.UNCOMMON, 0xFF55D655),
    TITLE_HIGH_ROLLER(Type.TITLE, "high_roller", Rarity.RARE, 0xFF4A9BFF),
    TITLE_DAREDEVIL(Type.TITLE, "daredevil", Rarity.RARE, 0xFF4A9BFF),
    TITLE_DIAMOND_HANDS(Type.TITLE, "diamond_hands", Rarity.EPIC, 0xFF6FE8F0),
    TITLE_CASINO_KING(Type.TITLE, "casino_king", Rarity.LEGENDARY, 0xFFFFAA00),
    // --- Rahmenfarben ---
    FRAME_CLASSIC(Type.FRAME, "classic", null, 0xFFC9A227),
    FRAME_OAK(Type.FRAME, "oak", Rarity.COMMON, 0xFF9C7A48),
    FRAME_STONE(Type.FRAME, "stone", Rarity.COMMON, 0xFF8A8A8A),
    FRAME_COPPER(Type.FRAME, "copper", Rarity.UNCOMMON, 0xFFD9774A),
    FRAME_EMERALD(Type.FRAME, "emerald", Rarity.RARE, 0xFF17C45A),
    FRAME_LAPIS(Type.FRAME, "lapis", Rarity.RARE, 0xFF2F5FD0),
    FRAME_AMETHYST(Type.FRAME, "amethyst", Rarity.EPIC, 0xFF9A5CC6),
    FRAME_GOLD(Type.FRAME, "gold", Rarity.EPIC, 0xFFFFD83A),
    FRAME_RAINBOW(Type.FRAME, "rainbow", Rarity.LEGENDARY, 0xFFFFFFFF);

    public enum Type { TITLE, FRAME }

    private final Type type;
    private final String id;
    private final Rarity rarity;
    private final int color;

    Cosmetic(Type type, String id, Rarity rarity, int color) {
        this.type = type;
        this.id = id;
        this.rarity = rarity;
        this.color = color;
    }

    public Type type() {
        return type;
    }

    /** Speicher-ID, z. B. "title.lucky" oder "frame.gold". */
    public String key() {
        return type.name().toLowerCase() + "." + id;
    }

    /** Seltenheit; null = Standard (nicht aus Lootboxen). */
    public Rarity rarity() {
        return rarity;
    }

    public boolean isDefault() {
        return rarity == null;
    }

    /** Farbe (Titeltext bzw. Rahmen). Regenbogen wird in der GUI animiert. */
    public int color() {
        return color;
    }

    public boolean isRainbow() {
        return this == FRAME_RAINBOW;
    }

    public String translationKey() {
        return "luckycasino.cosmetic." + key();
    }

    public static Cosmetic byKey(String key) {
        for (Cosmetic c : values()) {
            if (c.key().equals(key)) {
                return c;
            }
        }
        return null;
    }
}
