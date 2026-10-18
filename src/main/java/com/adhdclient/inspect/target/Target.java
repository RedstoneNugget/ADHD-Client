package com.adhdclient.inspect.target;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Das, was gerade untersucht wird (oder gesperrt ist). */
public sealed interface Target {

    record BlockTarget(BlockPos pos, @Nullable Direction side, String dimension) implements Target {
    }

    record EntityTarget(int networkId, UUID uuid) implements Target {
    }

    /** Item-Kopie; {@code origin} beschreibt die Herkunft ("Haupthand", "Inventar-Slot 3" …). */
    record ItemTarget(ItemStack stack, String origin) implements Target {
    }
}
