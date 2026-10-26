package com.adhdclient.safewalk;

import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;

/** Safe Walk: an Kanten stehen bleiben wie beim Schleichen, nur ohne zu schleichen. */
public final class SafeWalkClient {

    private SafeWalkClient() {
    }

    public static Extras.SafeWalk cfg() {
        return Extras.get().safeWalk;
    }

    /** Aus dem Mixin: soll dieser Spieler an Kanten hängen bleiben? */
    public static boolean active(PlayerEntity p) {
        Extras.SafeWalk c = cfg();
        if (!c.enabled || p != MinecraftClient.getInstance().player) return false;
        return !c.onlyBlocks || p.getMainHandStack().getItem() instanceof BlockItem || p.getOffHandStack().getItem() instanceof BlockItem;
    }

    public static Screen screen(Screen parent) {
        Extras.SafeWalk c = cfg();
        return OptionsScreen.builder("Safe Walk", c)
                .toggle(L.t("Nur mit Blöcken", "Only with blocks"), L.t("Nur wenn du Blöcke in der Hand hast (zum Brücken bauen)", "Only while holding blocks (for bridging)"),
                        () -> c.onlyBlocks, v -> c.onlyBlocks = v)
                .hint(() -> L.t("Du fällst nicht mehr von Kanten", "You no longer walk off edges"))
                .build(parent);
    }
}
