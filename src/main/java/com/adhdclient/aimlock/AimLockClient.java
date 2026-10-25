package com.adhdclient.aimlock;

import com.adhdclient.core.AdhdClientClient;
import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Aim Lock: Taste drücken, dann bleibt das Fadenkreuz auf der Hitbox der Entity. Solange es irgendwo auf der Hitbox
 * ist, bewegt sich nichts – erst wenn es runterrutscht, wird es zum nächsten Rand zurückgezogen. Das Lock löst sich, wenn das Ziel weiter weg ist als eingestellt, stirbt,
 * verschwindet oder die Taste erneut gedrückt wird.
 */
public final class AimLockClient {

    public static boolean enabled = false;

    public static final class Config {
        /** Taste zum Ein-/Ausschalten des Locks. */
        public int key = GLFW.GLFW_KEY_G;
        /** Ab dieser Entfernung (Blöcke) löst sich das Lock. */
        public double range = 6.0;
        /** Wie schnell der Blick folgt (10–100 %, 100 = sofort). */
        public int speed = 45;
        /** Nur Spieler anvisieren. */
        public boolean playersOnly = false;
        /** Abweichung vom genauen Punkt (0–100 %), damit es menschlicher wirkt. */
        public int deviation = 30;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HotKey KEY = new HotKey();
    private static Config config;
    @Nullable
    private static Entity target;

    private AimLockClient() {
    }

    public static Config config() {
        if (config == null) config = load();
        return config;
    }

    public static String keyName() {
        return HotKey.name(config().key);
    }

    @Nullable
    public static Entity target() {
        return target;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AimLockClient::tick);
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_EXTRACTION.register(ctx -> onFrame(MinecraftClient.getInstance()));
    }

    private static void tick(MinecraftClient mc) {
        boolean pressed = KEY.pressed(config().key);
        ClientPlayerEntity p = mc.player;
        if (!enabled || p == null || mc.world == null) {
            target = null;
            return;
        }
        if (pressed && mc.currentScreen == null) {
            if (target != null) {
                release(mc, L.t("Aim Lock aus", "Aim lock off"));
            } else {
                target = pick(mc);
                if (target != null) info(mc, L.t("Aim Lock: ", "Aim lock: ") + target.getName().getString(), Formatting.GREEN);
                else info(mc, L.t("Kein Ziel in Reichweite", "No target in range"), Formatting.GRAY);
            }
        }
        if (target == null) return;
        if (target.isRemoved() || !target.isAlive() || target.getEntityWorld() != p.getEntityWorld()) {
            release(mc, L.t("Ziel weg – Aim Lock aus", "Target gone – aim lock off"));
            return;
        }
        Vec3d eye = p.getEyePos();
        Vec3d aim = nearestOnBox(target.getBoundingBox(), eye);
        if (eye.distanceTo(aim) > config().range) {
            release(mc, L.t("Ziel zu weit weg – Aim Lock aus", "Target too far – aim lock off"));
            return;
        }
        // das Drehen selbst passiert jedes Bild, siehe onFrame
    }

    private static long lastFrame;
    private static final java.util.Random RANDOM = new java.util.Random();
    /** Langsam wandernde Abweichung (−1…1 je Achse) und ihr nächstes Ziel. */
    private static final float[] wobble = new float[3], wobbleGoal = new float[3];
    private static float wobbleTimer;

    /**
     * Zielpunkt leicht verschieben, damit es nicht immer exakt derselbe Punkt ist: eine weiche, zufällig wandernde
     * Abweichung innerhalb der Hitbox (Stärke einstellbar). Die Höhe bleibt nah an der nächsten Höhe.
     */
    private static Vec3d deviate(Box box, Vec3d aim, float dt) {
        float amount = MathHelper.clamp(config().deviation, 0, 100) / 100f;
        if (amount <= 0f) return aim;
        wobbleTimer -= dt;
        if (wobbleTimer <= 0f) {
            for (int i = 0; i < 3; i++) wobbleGoal[i] = RANDOM.nextFloat() * 2f - 1f;
            wobbleTimer = 0.4f + RANDOM.nextFloat() * 0.8f;
        }
        float follow = 1f - (float) Math.exp(-3f * dt);
        for (int i = 0; i < 3; i++) wobble[i] += (wobbleGoal[i] - wobble[i]) * follow;
        double x = aim.x + wobble[0] * amount * box.getLengthX() * 0.35;
        double y = aim.y + wobble[1] * amount * box.getLengthY() * 0.2;
        double z = aim.z + wobble[2] * amount * box.getLengthZ() * 0.35;
        return new Vec3d(MathHelper.clamp(x, box.minX, box.maxX), MathHelper.clamp(y, box.minY, box.maxY), MathHelper.clamp(z, box.minZ, box.maxZ));
    }

    /**
     * Jedes Bild: ist das Fadenkreuz von der Hitbox gerutscht, weich zum nächsten Punkt der Hitbox an der Blicklinie
     * drehen – unabhängig von der Bildrate (exponentielles Nachziehen). Auf der Hitbox bewegt sich nichts.
     */
    private static void onFrame(MinecraftClient mc) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        ClientPlayerEntity p = mc.player;
        if (!enabled || target == null || p == null || mc.currentScreen != null || dt <= 0f) return;
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d eye = p.getCameraPosVec(tickDelta);
        Vec3d look = p.getRotationVec(tickDelta);
        Box box = shrink(target.getBoundingBox().offset(target.getLerpedPos(tickDelta).subtract(target.getEntityPos())));
        if (box.raycast(eye, eye.add(look.multiply(config().range + 4))).isPresent() || box.contains(eye)) return;
        float k = 2f + MathHelper.clamp(config().speed, 10, 100) * 0.28f;   // 10 % ≈ 5/s, 100 % ≈ 30/s
        turnTowards(p, eye, deviate(box, nearestToRay(box, eye, look), dt), 1f - (float) Math.exp(-k * dt));
    }

    /** Entity im Fadenkreuz, sonst die Entity am nächsten zur Blickrichtung (innerhalb 30°) und Reichweite. */
    @Nullable
    private static Entity pick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (mc.targetedEntity != null && valid(p, mc.targetedEntity)) return mc.targetedEntity;
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        Entity best = null;
        double bestAngle = Math.toRadians(30);
        for (Entity e : mc.world.getEntities()) {
            if (!valid(p, e)) continue;
            Vec3d to = nearestOnBox(e.getBoundingBox(), eye).subtract(eye);
            double angle = Math.acos(MathHelper.clamp(to.normalize().dotProduct(look), -1, 1));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = e;
            }
        }
        return best;
    }

    private static boolean valid(ClientPlayerEntity p, Entity e) {
        if (e == p || !(e instanceof LivingEntity) || !e.isAlive() || e.isSpectator()) return false;
        if (config().playersOnly && !(e instanceof net.minecraft.entity.player.PlayerEntity)) return false;
        return p.getEyePos().distanceTo(nearestOnBox(e.getBoundingBox(), p.getEyePos())) <= config().range;
    }

    /** Hitbox etwas nach innen ziehen, damit der Blick sicher drauf landet. */
    static Box shrink(Box box) {
        return box.contract(Math.min(0.08, box.getLengthX() * 0.15), Math.min(0.08, box.getLengthY() * 0.1), Math.min(0.08, box.getLengthZ() * 0.15));
    }

    /** Punkt der Hitbox, der der Blicklinie am nächsten liegt – so dreht sich der Blick nur so weit wie nötig. */
    static Vec3d nearestToRay(Box b, Vec3d eye, Vec3d look) {
        Vec3d best = null;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i <= 64; i++) {
            Vec3d onRay = eye.add(look.multiply(i * 0.25));
            Vec3d q = new Vec3d(MathHelper.clamp(onRay.x, b.minX, b.maxX), MathHelper.clamp(onRay.y, b.minY, b.maxY), MathHelper.clamp(onRay.z, b.minZ, b.maxZ));
            Vec3d dir = q.subtract(eye).normalize();
            double d = 1 - dir.dotProduct(look);
            if (d < bestD) {
                bestD = d;
                best = q;
            }
        }
        return best;
    }

    /** Nächster Punkt der Hitbox zum Auge (für die Entfernung). */
    static Vec3d nearestOnBox(Box box, Vec3d eye) {
        Box b = shrink(box);
        return new Vec3d(MathHelper.clamp(eye.x, b.minX, b.maxX), MathHelper.clamp(eye.y, b.minY, b.maxY), MathHelper.clamp(eye.z, b.minZ, b.maxZ));
    }

    private static void turnTowards(ClientPlayerEntity p, Vec3d eye, Vec3d aim, float f) {
        Vec3d d = aim.subtract(eye);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, horiz));
        float dy = MathHelper.wrapDegrees(yaw - p.getYaw());
        float dp = pitch - p.getPitch();
        // wie eine Mausbewegung: dreht Blick und Vorbild-Blick gemeinsam, damit nichts zwischen den Ticks springt
        p.changeLookDirection(dy * f / 0.15f, dp * f / 0.15f);
    }

    private static void release(MinecraftClient mc, String msg) {
        target = null;
        info(mc, msg, Formatting.GRAY);
    }

    private static void info(MinecraftClient mc, String msg, Formatting color) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(msg).formatted(color), true);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("adhd_client").resolve("aim_lock.json");
    }

    private static Config load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                Config c = GSON.fromJson(r, Config.class);
                if (c != null) return c;
            } catch (IOException | JsonParseException e) {
                AdhdClientClient.LOGGER.warn("aim_lock.json ist fehlerhaft – Standardwerte werden verwendet ({})", e.getMessage());
            }
        }
        return new Config();
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("aim_lock.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(config(), w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            AdhdClientClient.LOGGER.warn("aim_lock.json konnte nicht gespeichert werden", e);
        }
    }
}
