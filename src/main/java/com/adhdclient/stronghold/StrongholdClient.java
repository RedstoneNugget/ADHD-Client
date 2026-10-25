package com.adhdclient.stronghold;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EyeOfEnderEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stronghold Finder: erkennt geworfene Ender-Augen, misst ihre Flugrichtung und rechnet aus zwei Würfen von
 * verschiedenen Orten den Schnittpunkt aus – dort liegt der Stronghold mit dem End-Portal (Triangulation wie bei
 * Speedrunnern). Zeigt Overworld- und Nether-Koordinaten, Entfernung und wohin du dich drehen musst.
 */
public final class StrongholdClient {

    public static boolean enabled = false;

    /** Ein Wurf: Startpunkt (x, z) und Richtung (normiert, x/z). */
    public record Throw(double x, double z, double dx, double dz) {
        double yaw() {
            return Math.toDegrees(Math.atan2(-dx, dz));
        }
    }

    /** Augen, die gerade fliegen: Netzwerk-ID → erster gesehener Punkt und Tick. */
    private static final Map<Integer, double[]> FLYING = new HashMap<>();
    private static final List<Throw> THROWS = new ArrayList<>();
    @Nullable
    private static double[] result;   // x, z
    @Nullable
    private static World lastWorld;

    private StrongholdClient() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(StrongholdClient::tick);
        HudElementRegistry.addLast(Identifier.of("adhd_client", "stronghold"), StrongholdClient::hud);
    }

    public static List<Throw> throwsMade() {
        return List.copyOf(THROWS);
    }

    @Nullable
    public static double[] result() {
        return result;
    }

    public static void reset() {
        THROWS.clear();
        FLYING.clear();
        result = null;
    }

    /** Für Tests: einen Wurf direkt eintragen. */
    public static void addThrow(Throw t) {
        if (THROWS.size() >= 2) THROWS.remove(0);
        THROWS.add(t);
        result = THROWS.size() == 2 ? intersect(THROWS.get(0), THROWS.get(1)) : null;
    }

    private static void tick(MinecraftClient mc) {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            reset();
        }
        if (!enabled || mc.world == null) return;
        long now = mc.world.getTime();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EyeOfEnderEntity)) continue;
            double[] start = FLYING.get(e.getId());
            if (start == null) {
                FLYING.put(e.getId(), new double[]{e.getX(), e.getZ(), now, 0});
                continue;
            }
            // nach ein paar Ticks Flug die Richtung ablesen (danach bleibt das Auge stehen und fällt)
            if (start[3] == 0 && now - start[2] >= 6) {
                double dx = e.getX() - start[0], dz = e.getZ() - start[1], len = Math.hypot(dx, dz);
                start[3] = 1;
                if (len > 0.5) addThrow(new Throw(start[0], start[1], dx / len, dz / len));
            }
        }
        FLYING.keySet().removeIf(id -> mc.world.getEntityById(id) == null);
    }

    /** Schnittpunkt zweier Würfe oder null, wenn sie fast parallel sind oder hinter dem Werfer liegen. */
    @Nullable
    static double[] intersect(Throw a, Throw b) {
        double cross = a.dx * b.dz - a.dz * b.dx;
        if (Math.abs(cross) < Math.sin(Math.toRadians(0.8))) return null;
        double t = ((b.x - a.x) * b.dz - (b.z - a.z) * b.dx) / cross;
        double s = ((b.x - a.x) * a.dz - (b.z - a.z) * a.dx) / cross;
        if (t < 0 || s < 0) return null;
        return new double[]{a.x + a.dx * t, a.z + a.dz * t};
    }

    /** Winkel zwischen den beiden Würfen in Grad (zu klein = ungenau). */
    static double angleBetween(Throw a, Throw b) {
        return Math.toDegrees(Math.acos(MathHelper.clamp(a.dx * b.dx + a.dz * b.dz, -1, 1)));
    }

    // ------------------------------------------------------------ Anzeige

    private static void hud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.options.hudHidden || THROWS.isEmpty()) return;
        TextRenderer tr = mc.textRenderer;
        List<String> lines = new ArrayList<>();
        lines.add("§d" + L.t("Stronghold Finder", "Stronghold Finder"));
        Throw first = THROWS.get(0);
        lines.add(String.format(L.t("Wurf 1: %d, %d  Richtung %.1f°", "Throw 1: %d, %d  heading %.1f°"),
                (int) first.x, (int) first.z, first.yaw()));
        if (THROWS.size() == 1) {
            // Tipp: quer zur Flugrichtung weggehen
            double side = MathHelper.wrapDegrees(first.yaw() + 90);
            lines.add("§7" + String.format(L.t("Geh 100–300 Blöcke quer (Richtung %.0f° oder %.0f°)", "Walk 100–300 blocks sideways (heading %.0f° or %.0f°)"),
                    side, MathHelper.wrapDegrees(side + 180)));
            lines.add("§7" + L.t("und wirf noch ein Auge", "and throw another eye"));
        } else {
            Throw second = THROWS.get(1);
            lines.add(String.format(L.t("Wurf 2: %d, %d  Richtung %.1f°", "Throw 2: %d, %d  heading %.1f°"),
                    (int) second.x, (int) second.z, second.yaw()));
            double angle = angleBetween(first, second);
            if (result == null) {
                lines.add("§c" + L.t("Würfe zu ähnlich – weiter seitlich gehen und neu werfen", "Throws too similar – walk further sideways and throw again"));
            } else {
                int x = (int) Math.floor(result[0]), z = (int) Math.floor(result[1]);
                double dist = Math.hypot(result[0] - mc.player.getX(), result[1] - mc.player.getZ());
                double yawTo = Math.toDegrees(Math.atan2(-(result[0] - mc.player.getX()), result[1] - mc.player.getZ()));
                double turn = MathHelper.wrapDegrees(yawTo - mc.player.getYaw());
                lines.add("§a" + String.format(L.t("Stronghold: %d, %d", "Stronghold: %d, %d"), x, z));
                lines.add(String.format(L.t("Nether: %d, %d", "Nether: %d, %d"), Math.floorDiv(x, 8), Math.floorDiv(z, 8)));
                lines.add(String.format(L.t("Entfernung: %d Blöcke", "Distance: %d blocks"), (int) dist));
                lines.add(Math.abs(turn) < 3 ? "§a" + L.t("↑ geradeaus", "↑ straight ahead")
                        : String.format(turn < 0 ? L.t("← %.0f° nach links", "← %.0f° to the left") : L.t("→ %.0f° nach rechts", "→ %.0f° to the right"), Math.abs(turn)));
                if (angle < 5) lines.add("§e" + L.t("Ungenau – Würfe weiter auseinander machen", "Inaccurate – throw further apart"));
            }
        }
        int w = 0;
        for (String s : lines) w = Math.max(w, tr.getWidth(s));
        int x = 4, y = 4;
        ctx.fill(x - 2, y - 2, x + w + 2, y + lines.size() * 10, 0x90000000);
        for (String s : lines) {
            ctx.drawTextWithShadow(tr, s, x, y, Theme.text());
            y += 10;
        }
    }
}
