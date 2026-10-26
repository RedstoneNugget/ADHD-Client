package com.adhdclient.nofall;

import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.Locale;

/**
 * No Fall: während du fällst, meldet der Client dem Server „am Boden“ – so zählt der Server keine Fallhöhe und es
 * gibt keinen Fallschaden. Auf Servern meist verboten und leicht zu erkennen.
 */
public final class NoFallClient {

    private NoFallClient() {
    }

    public static Extras.NoFall cfg() {
        return Extras.get().noFall;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NoFallClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!cfg().enabled || p == null || p.networkHandler == null) return;
        if (p.getAbilities().creativeMode || p.getAbilities().flying || p.isGliding() || p.isOnGround()) return;
        if (p.fallDistance < cfg().minFall) return;
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, p.horizontalCollision));
        p.fallDistance = 0;
    }

    public static Screen screen(Screen parent) {
        Extras.NoFall c = cfg();
        return OptionsScreen.builder("No Fall", c)
                .slider(L.t("Ab Fallhöhe", "From fall height"), 0.5, 100, 0.5, () -> c.minFall, v -> c.minFall = v,
                        v -> String.format(Locale.ROOT, "%.1f", v) + L.t(" Blöcke", " blocks"))
                .hint(() -> L.t("Kein Fallschaden – auf Servern meist verboten", "No fall damage – usually not allowed on servers"))
                .build(parent);
    }
}
