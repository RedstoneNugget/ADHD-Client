package com.adhdclient.casino.client.gui;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Löst Item-IDs aus der Spiellogik (reine Strings) in ItemStacks für die GUI auf. */
public final class Icons {

    private static final Map<String, ItemStack> CACHE = new HashMap<>();

    private Icons() {
    }

    public static ItemStack of(String itemId) {
        return CACHE.computeIfAbsent(itemId, id -> {
            Identifier ident = Identifier.tryParse(id);
            Item item = ident == null ? Items.BARRIER : Registries.ITEM.get(ident);
            return new ItemStack(item == Items.AIR ? Items.BARRIER : item);
        });
    }
}
