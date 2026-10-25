package com.adhdclient.totemview;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gegner-Totem-Anzeige: hinter dem Namen anderer Spieler steht „⚑ Totem“, wenn sie ein Totem in einer Hand halten,
 * dazu wie oft ihr Totem schon geplatzt ist (seit du sie siehst).
 */
public final class TotemViewClient {

    public static boolean enabled = false;
    /** Zahl der Totem-Pops anzeigen. */
    public static boolean showPops = true;

    private static final Map<UUID, Integer> POPS = new HashMap<>();

    private TotemViewClient() {
    }

    /** Aus dem Mixin: ein Totem einer Entity ist geplatzt (Status 35). */
    public static void onPop(Entity e) {
        if (e instanceof PlayerEntity) POPS.merge(e.getUuid(), 1, Integer::sum);
    }

    public static int pops(UUID id) {
        return POPS.getOrDefault(id, 0);
    }

    /** Namensschild eines Spielers um Totem-Hinweis und Pops ergänzen. */
    public static Text decorate(Entity entity, Text name) {
        if (!enabled || name == null || !(entity instanceof PlayerEntity p)) return name;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (p == mc.player) return name;
        boolean totem = p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        int pops = pops(p.getUuid());
        if (!totem && !(showPops && pops > 0)) return name;
        MutableText out = name.copy();
        if (totem) out.append(Text.literal(" ⚑ Totem").formatted(Formatting.GOLD));
        if (showPops && pops > 0) out.append(Text.literal(" ×" + pops).formatted(Formatting.RED));
        return out;
    }
}
