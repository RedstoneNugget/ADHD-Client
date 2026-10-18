package com.adhdclient.esp;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * ESP: zeigt ausgewählte Entities (Spieler, Mobs, Items …) durch Wände – als Box, leuchtender Umriss,
 * Tracer und/oder Namensschild. {@link #enabled} wird im ADHD-Client-Menü umgeschaltet.
 */
public final class EspClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("ESP");

    public static boolean enabled = false;

    private static EspConfig config;
    private static long saveAt = -1;
    private static Set<EntityType<?>> types = Set.of();

    public static EspConfig config() {
        if (config == null) {
            config = EspConfig.load();
            rebuildTypes();
        }
        return config;
    }

    public static void onConfigEdited() {
        config().sanitize();
        rebuildTypes();
        saveAt = System.currentTimeMillis() + 1000;
    }

    private static void rebuildTypes() {
        Set<EntityType<?>> out = new HashSet<>();
        for (String id : config.entities) {
            Identifier ident = Identifier.tryParse(id);
            if (ident != null && Registries.ENTITY_TYPE.containsId(ident)) out.add(Registries.ENTITY_TYPE.get(ident));
        }
        types = out;
    }

    public static void saveNow() {
        saveAt = -1;
        config().save();
    }

    public void onInitializeClient() {
        config();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (saveAt >= 0) saveNow();
        });
        EspRenderer.register();
        LOGGER.info("ESP geladen (ADHD Client Modul)");
    }

    /** Ausgewählte Entity der Client-Welt (nicht man selbst) innerhalb der Reichweite. */
    public static boolean isTarget(Entity e) {
        if (!enabled) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || e == mc.player || e.getEntityWorld() != mc.world) return false;
        EspConfig cfg = config();
        if (!types.contains(e.getType())) return false;
        if (e.isInvisible() && !cfg.showInvisible) return false;
        return e.squaredDistanceTo(mc.player) <= (double) cfg.range * cfg.range;
    }

    /** Farbe einer Entity als 0xRRGGBB. */
    public static int colorFor(Entity e, double distance) {
        EspConfig cfg = config();
        return switch (cfg.colorMode) {
            case TEAM -> e.getTeamColorValue() & 0xFFFFFF;
            case FIXED -> EspConfig.rgb(cfg.fixedColor, 0xFF3B3B);
            case DISTANCE -> distanceColor(distance);
        };
    }

    /** Nah = rot, ~32 m = gelb, ab 64 m = grün. */
    private static int distanceColor(double d) {
        float t = MathHelper.clamp((float) (d / 64.0), 0f, 1f);
        int r;
        int g;
        if (t < 0.5f) {
            r = 255;
            g = (int) (59 + (230 - 59) * (t / 0.5f));
        } else {
            r = (int) (255 - (255 - 59) * ((t - 0.5f) / 0.5f));
            g = 230;
        }
        return (r << 16) | (g << 8) | 59;
    }
}
