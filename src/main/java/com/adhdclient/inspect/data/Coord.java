package com.adhdclient.inspect.data;

import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Eine erkannte Koordinate (z. B. flower_pos) – im Panel mit "Markieren"-Knopf.
 *
 * @param dimension Dimension, falls die Daten eine angeben (z. B. lodestone_tracker), sonst null = aktuelle
 * @param label     kurze Beschriftung für die Markierung in der Welt (z. B. "flower_pos")
 */
public record Coord(BlockPos pos, @Nullable String dimension, String label) {

    public String text() {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
