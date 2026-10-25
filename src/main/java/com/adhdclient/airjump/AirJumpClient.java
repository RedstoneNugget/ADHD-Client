package com.adhdclient.airjump;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Air Jump: in der Luft erneut Springen drücken = noch ein Sprung. Wie oft (0 = unbegrenzt) und wie hoch ist
 * einstellbar. Am Boden, im Wasser oder beim Fliegen wird nichts verändert.
 */
public final class AirJumpClient {

    public static boolean enabled = false;
    /** Luftsprünge bis zur nächsten Landung (0 = unbegrenzt). */
    public static int maxJumps = 1;
    /** Sprungkraft (0,42 = normaler Sprung). */
    public static double strength = 0.42;

    private static boolean wasDown;
    private static int used;
    private static int airTicks;

    private AirJumpClient() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AirJumpClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        boolean down = mc.options.jumpKey.isPressed();
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (p == null) return;
        if (p.isOnGround() || p.isTouchingWater() || p.isInLava() || p.isClimbing() || p.getAbilities().flying) {
            used = 0;
            airTicks = 0;
            return;
        }
        airTicks++;
        // der Tastendruck, mit dem man vom Boden abspringt, zählt nicht als Luftsprung
        if (!enabled || !pressed || airTicks < 2 || mc.currentScreen != null || p.isGliding() || p.hasVehicle()) return;
        if (maxJumps > 0 && used >= maxJumps) return;
        used++;
        Vec3d v = p.getVelocity();
        p.setVelocity(v.x, strength, v.z);
        p.fallDistance = 0;
    }
}
