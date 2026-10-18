package com.adhdclient.freelook;

import com.adhdclient.freecam.FreeCamClient;
import com.adhdclient.gui.HotKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Free Look: Kamera in der dritten Person um den Spieler drehen, ohne dass er sich mitdreht.
 * Abstand einstellbar, Kamera stoppt vor Blöcken oder geht auf Wunsch durch sie hindurch.
 * Die Einstellungen gelten auf Wunsch auch für die normale F5-Ansicht.
 */
public final class FreeLookClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("Free Look");

    /** Modul eingeschaltet (im ADHD-Menü). */
    public static boolean enabled = false;
    private static final HotKey KEY = new HotKey();

    private static FreeLookConfig config;
    private static long saveAt = -1;
    private static boolean active;
    private static float yaw;
    private static float pitch;
    private static Perspective savedPerspective;

    public static FreeLookConfig config() {
        if (config == null) config = FreeLookConfig.load();
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

    /** Free Look dreht gerade die Kamera (Taste gehalten bzw. eingeschaltet). */
    public static boolean active() {
        return enabled && active;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static String keyName() {
        return HotKey.name(config().key);
    }

    public void onInitializeClient() {
        config();
        ClientTickEvents.END_CLIENT_TICK.register(FreeLookClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (saveAt >= 0) saveNow();
        });
        LOGGER.info("Free Look geladen (ADHD Client Modul)");
    }

    private static void tick(MinecraftClient mc) {
        if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
        int code = config().key;
        boolean pressed = KEY.pressed(code);
        boolean want;
        if (!enabled || mc.player == null || FreeCamClient.active) want = false;
        else if (config().hold) want = HotKey.isHeld(code);
        else want = pressed != active;
        if (want != active) setActive(mc, want);
    }

    private static void setActive(MinecraftClient mc, boolean on) {
        active = on;
        if (on && mc.player != null) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            savedPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (savedPerspective != null) {
            mc.options.setPerspective(savedPerspective);
            savedPerspective = null;
        }
    }

    /** Mausbewegung, solange Free Look aktiv ist (wie Entity.changeLookDirection). */
    public static void look(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }

    /** Mausrad, solange Free Look aktiv ist. Gibt true zurück, wenn verbraucht. */
    public static boolean scroll(double amount) {
        if (!active() || !config().scrollZoom || amount == 0) return false;
        config().distance = MathHelper.clamp(config().distance - Math.signum(amount), 1, 32);
        onConfigEdited();
        return true;
    }

    /**
     * Abstand der Kamera vom Auge entlang {@code back} (Einheitsvektor nach hinten). Stoppt vor Blöcken,
     * außer "Durch Blöcke" ist an – geprüft werden wie in Minecraft acht leicht versetzte Strahlen.
     */
    public static double clippedDistance(Entity focus, Vec3d eye, Vec3d back) {
        double dist = config().distance;
        if (config().throughBlocks) return dist;
        World world = focus.getEntityWorld();
        for (int i = 0; i < 8; i++) {
            double ox = ((i & 1) * 2 - 1) * 0.1;
            double oy = ((i >> 1 & 1) * 2 - 1) * 0.1;
            double oz = ((i >> 2 & 1) * 2 - 1) * 0.1;
            Vec3d from = eye.add(ox, oy, oz);
            Vec3d to = from.add(back.multiply(dist));
            HitResult hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, focus));
            if (hit.getType() != HitResult.Type.MISS) {
                double d = hit.getPos().distanceTo(from);
                if (d < dist) dist = d;
            }
        }
        return Math.max(0.1, dist - 0.05);
    }
}
