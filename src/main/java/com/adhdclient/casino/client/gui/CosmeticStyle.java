package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.core.loot.Cosmetic;
import com.adhdclient.casino.core.loot.LootReward;
import com.adhdclient.casino.core.loot.Rarity;
import net.minecraft.text.Text;

/** Darstellung von Kosmetik und Lootbox-Inhalten (Symbole, Namen). */
public final class CosmeticStyle {

    private CosmeticStyle() {
    }

    public static String icon(Cosmetic c) {
        if (c.type() == Cosmetic.Type.TITLE) {
            return "minecraft:name_tag";
        }
        return switch (c) {
            case FRAME_OAK -> "minecraft:oak_planks";
            case FRAME_STONE -> "minecraft:stone";
            case FRAME_COPPER -> "minecraft:copper_block";
            case FRAME_EMERALD -> "minecraft:emerald_block";
            case FRAME_LAPIS -> "minecraft:lapis_block";
            case FRAME_AMETHYST -> "minecraft:amethyst_block";
            case FRAME_GOLD -> "minecraft:gold_block";
            case FRAME_RAINBOW -> "minecraft:beacon";
            default -> "minecraft:item_frame";
        };
    }

    public static String chipIcon(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> "minecraft:gold_nugget";
            case UNCOMMON -> "minecraft:gold_ingot";
            case RARE -> "minecraft:emerald";
            case EPIC -> "minecraft:diamond";
            case LEGENDARY -> "minecraft:gold_block";
        };
    }

    public static String icon(LootReward reward) {
        return reward.cosmetic() != null ? icon(reward.cosmetic()) : chipIcon(reward.rarity());
    }

    public static Text name(LootReward reward) {
        if (reward.cosmetic() != null) {
            return Text.translatable(reward.cosmetic().translationKey());
        }
        return Text.translatable("luckycasino.loot.chips", Fmt.chips(reward.chips()));
    }

    /** "Titel" oder "Rahmen". */
    public static Text typeName(Cosmetic c) {
        return Text.translatable(c.type() == Cosmetic.Type.TITLE ? "luckycasino.cosmetic.type.title" : "luckycasino.cosmetic.type.frame");
    }
}
