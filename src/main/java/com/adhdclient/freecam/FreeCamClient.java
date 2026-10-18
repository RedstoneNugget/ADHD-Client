package com.adhdclient.freecam;

import com.adhdclient.gui.L;
import com.adhdclient.gui.HotKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Free Cam: die Kamera löst sich vom Spieler und fliegt frei herum, der Körper bleibt stehen und ist sichtbar.
 * Die Kamera wird direkt gesteuert (eigene Position und Blickrichtung, gleichmäßig zwischen den Ticks
 * interpoliert) – es gibt keine Ersatz-Figur, nichts davon geht an den Server. Lauf-Eingaben, Angreifen und
 * Benutzen sind solange gesperrt, die Hand wird nicht gezeichnet.
 */
public final class FreeCamClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("Free Cam");

    /** Modul im ADHD-Menü eingeschaltet – nur dann reagiert die Taste. */
    public static boolean enabled = false;
    /** Kamera gerade losgelöst (wird nicht gespeichert, startet immer aus). */
    public static boolean active = false;

    private static final HotKey KEY = new HotKey();
    private static FreeCamConfig config;
    private static long saveAt = -1;
    private static Vec3d pos = Vec3d.ZERO;
    private static Vec3d lastPos = Vec3d.ZERO;
    private static Vec3d velocity = Vec3d.ZERO;
    private static float yaw;
    private static float pitch;
    private static Perspective savedPerspective;

    public static FreeCamConfig config() {
        if (config == null) config = FreeCamConfig.load();
        return config;
    }

    public static void onConfigEdited() {
        config().sanitize();
        saveAt = System.currentTimeMillis() + 1000;
    }

    public static void saveNow() {
        saveAt = -1;
        config().save();
    }

    public static String keyName() {
        return HotKey.name(config().key);
    }

    /** Kameraposition für dieses Bild (zwischen letztem und aktuellem Tick interpoliert). */
    public static Vec3d cameraPos(float tickProgress) {
        return lastPos.lerp(pos, tickProgress);
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    /** Mausbewegung – gleiche Empfindlichkeit wie Minecraft (Entity.changeLookDirection). */
    public static void look(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }

    public void onInitializeClient() {
        config();
        ClientTickEvents.END_CLIENT_TICK.register(FreeCamClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> set(false)));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (saveAt >= 0) saveNow();
        });
        LOGGER.info("Free Cam geladen (ADHD Client Modul)");
    }

    public static void toggle() {
        set(!active);
    }

    public static void set(boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (on == active) return;
        if (on) start(mc);
        else stop(mc);
    }

    private static void start(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        pos = mc.player.getCameraPosVec(1.0f);
        lastPos = pos;
        velocity = Vec3d.ZERO;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        savedPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.FIRST_PERSON);
        active = true;
        mc.player.sendMessage(Text.literal(L.t("Free Cam an – ", "Free Cam on – ") + keyName() + L.t(" zum Zurückkehren", " to return")).formatted(Formatting.AQUA), true);
    }

    private static void stop(MinecraftClient mc) {
        active = false;
        velocity = Vec3d.ZERO;
        if (savedPerspective != null) mc.options.setPerspective(savedPerspective);
        savedPerspective = null;
        if (mc.player != null) mc.player.sendMessage(Text.literal(L.t("Free Cam aus", "Free Cam off")).formatted(Formatting.GRAY), true);
    }

    private static void tick(MinecraftClient mc) {
        if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
        boolean pressed = KEY.pressed(config().key);
        if (!enabled) {
            if (active) set(false);
            return;
        }
        if (pressed && mc.player != null) toggle();
        if (!active) return;
        if (mc.player == null || mc.world == null || mc.player.isDead()) {
            set(false);
            return;
        }
        move(mc);
    }

    /**
     * Fliegt mit den normalen Lauf-Tasten: vor/zurück/seitlich, Springen = hoch, Schleichen = runter.
     * Mit "sanft" wird die Geschwindigkeit weich angepasst statt sprunghaft.
     */
    private static void move(MinecraftClient mc) {
        lastPos = pos;
        Vec3d target = Vec3d.ZERO;
        if (mc.currentScreen == null) {
            GameOptions o = mc.options;
            double forward = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
            double side = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
            double up = (o.jumpKey.isPressed() ? 1 : 0) - (o.sneakKey.isPressed() ? 1 : 0);
            double len = Math.sqrt(forward * forward + side * side);
            if (len > 1) {
                forward /= len;
                side /= len;
            }
            FreeCamConfig cfg = config();
            double speed = cfg.speed / 20.0 * (o.sprintKey.isPressed() ? cfg.sprintFactor : 1);
            double rad = Math.toRadians(yaw);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);
            target = new Vec3d(side * cos - forward * sin, up, forward * cos + side * sin).multiply(speed);
        }
        velocity = config().smooth ? velocity.lerp(target, 0.35) : target;
        if (velocity.lengthSquared() < 1e-6) velocity = Vec3d.ZERO;
        pos = pos.add(velocity);
    }
}
