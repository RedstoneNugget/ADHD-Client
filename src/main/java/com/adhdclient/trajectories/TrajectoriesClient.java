package com.adhdclient.trajectories;

import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Trajectories: rechnet die Flugbahn von Pfeil, Armbrust, Trident, Enderperle, Schneeball, Ei, Wurftränken,
 * Erfahrungsflaschen und Windkugeln mit der Spielphysik voraus und zeichnet sie samt Aufschlagpunkt.
 */
public final class TrajectoriesClient {

    /** Wurf-Werte wie im Spiel: Tempo, Schwerkraft, Neigung nach oben (Tränke werfen 20° höher). */
    record Throw(double speed, double gravity, float pitchOffset) {
    }

    private static final int STEPS = 400;

    private TrajectoriesClient() {
    }

    public static Extras.Trajectories cfg() {
        return Extras.get().trajectories;
    }

    public static void init() {
        WorldRenderEvents.END_EXTRACTION.register(TrajectoriesClient::render);
    }

    /** Wurf-Werte für das Item in der Hand, oder null, wenn es nichts wirft. */
    @Nullable
    static Throw throwFor(PlayerEntity p, ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BowItem) {
            if (!p.isUsingItem() || p.getActiveItem() != stack) return null;
            float pull = BowItem.getPullProgress(stack.getMaxUseTime(p) - p.getItemUseTimeLeft());
            return pull < 0.1f ? null : new Throw(pull * 3.0, 0.05, 0f);
        }
        if (item instanceof CrossbowItem) return CrossbowItem.isCharged(stack) ? new Throw(3.15, 0.05, 0f) : null;
        if (stack.isOf(Items.TRIDENT)) return p.isUsingItem() && p.getActiveItem() == stack ? new Throw(2.5, 0.05, 0f) : null;
        if (stack.isOf(Items.ENDER_PEARL) || stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG)
                || stack.isOf(Items.BLUE_EGG) || stack.isOf(Items.BROWN_EGG)) return new Throw(1.5, 0.03, 0f);
        if (stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION)) return new Throw(0.5, 0.05, -20f);
        if (stack.isOf(Items.EXPERIENCE_BOTTLE)) return new Throw(0.7, 0.07, -20f);
        if (stack.isOf(Items.WIND_CHARGE)) return new Throw(1.5, 0.0, 0f);
        return null;
    }

    /** Ergebnis der Vorausrechnung: Punkte der Bahn, Treffer (Block oder Entity) oder nichts. */
    record Path(List<Vec3d> points, @Nullable Vec3d hit, @Nullable Entity hitEntity) {
    }

    static Path simulate(PlayerEntity p, Throw t, float tickDelta) {
        float yaw = p.getYaw(tickDelta), pitch = p.getPitch(tickDelta);
        double x = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
        double y = -MathHelper.sin((pitch + t.pitchOffset()) * MathHelper.RADIANS_PER_DEGREE);
        double z = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
        Vec3d vel = new Vec3d(x, y, z).normalize().multiply(t.speed());
        Vec3d move = p.getMovement();
        vel = vel.add(move.x, p.isOnGround() ? 0 : move.y, move.z);
        Vec3d pos = p.getCameraPosVec(tickDelta).subtract(0, 0.1, 0);
        List<Vec3d> pts = new ArrayList<>();
        pts.add(pos);
        var world = p.getEntityWorld();
        for (int i = 0; i < STEPS; i++) {
            vel = vel.subtract(0, t.gravity(), 0);
            Vec3d next = pos.add(vel);
            BlockHitResult bhr = world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            Vec3d end = bhr.getType() == HitResult.Type.MISS ? next : bhr.getPos();
            Entity best = null;
            double bestD = Double.MAX_VALUE;
            Vec3d bestPos = null;
            Box sweep = new Box(pos, end).expand(1.0);
            for (Entity e : world.getOtherEntities(p, sweep, e -> e.canHit() && !e.isSpectator())) {
                Optional<Vec3d> h = e.getBoundingBox().expand(0.3).raycast(pos, end);
                if (h.isPresent() && h.get().squaredDistanceTo(pos) < bestD) {
                    bestD = h.get().squaredDistanceTo(pos);
                    best = e;
                    bestPos = h.get();
                }
            }
            if (best != null) {
                pts.add(bestPos);
                return new Path(pts, bestPos, best);
            }
            pts.add(end);
            if (bhr.getType() != HitResult.Type.MISS) return new Path(pts, end, null);
            pos = next;
            vel = vel.multiply(0.99);
            if (pos.y < world.getBottomY() - 64) break;
        }
        return new Path(pts, null, null);
    }

    private static void render(WorldExtractionContext ctx) {
        Extras.Trajectories c = cfg();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.enabled || mc.world == null || mc.player == null || mc.worldRenderer == null) return;
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player && !c.otherPlayers) continue;
                for (Hand hand : Hand.values()) {
                    Throw t = throwFor(p, p.getStackInHand(hand));
                    if (t == null) continue;
                    draw(simulate(p, t, tickDelta), p == mc.player, c);
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // ein Bild ohne Bahn ist kein Problem
        }
    }

    private static void draw(Path path, boolean own, Extras.Trajectories c) {
        int color = 0xFF000000 | (own ? c.color : 0xFF8080);
        List<Vec3d> pts = path.points();
        // die ersten Punkte liegen direkt vor der Kamera – weglassen, sonst verdeckt die Linie die Sicht
        int start = own ? Math.min(2, pts.size() - 1) : 0;
        for (int i = start; i + 1 < pts.size(); i++) GizmoDrawing.line(pts.get(i), pts.get(i + 1), color, 2f).ignoreOcclusion();
        if (!c.showHit || path.hit() == null) return;
        if (path.hitEntity() != null) {
            GizmoDrawing.box(path.hitEntity().getBoundingBox().expand(0.05), DrawStyle.filledAndStroked(0xFFFF4040, 2f, 0x40FF4040)).ignoreOcclusion();
        } else {
            GizmoDrawing.box(Box.of(path.hit(), 0.3, 0.3, 0.3), DrawStyle.filledAndStroked(color, 2f, 0x40000000 | (color & 0xFFFFFF))).ignoreOcclusion();
        }
    }

    public static Screen screen(Screen parent) {
        Extras.Trajectories c = cfg();
        return OptionsScreen.builder("Trajectories", c)
                .toggle(L.t("Aufschlag zeigen", "Show impact"), L.t("Box am Aufschlagpunkt, rot bei Entity-Treffer", "Box at the impact, red when it hits an entity"),
                        () -> c.showHit, v -> c.showHit = v)
                .toggle(L.t("Andere Spieler", "Other players"), L.t("Auch Bahnen anderer Spieler (rot)", "Also other players' paths (red)"),
                        () -> c.otherPlayers, v -> c.otherPlayers = v)
                .color(L.t("Farbe", "Color"), () -> c.color, v -> c.color = v)
                .hint(() -> L.t("Bogen spannen oder Wurf-Item halten", "Draw a bow or hold a throwable"))
                .build(parent);
    }
}
