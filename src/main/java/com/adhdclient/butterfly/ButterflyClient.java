package com.adhdclient.butterfly;

import com.adhdclient.gui.HotKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Butterfly: ausgewählte Items sehen in der Hand aus wie ein Butterfly-Messer – mit Aufklappen beim Ziehen,
 * Inspect auf Taste, Flip beim Schlagen und Spielen im Stand. Nur bei dir sichtbar, der Server merkt nichts.
 */
public final class ButterflyClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("Butterfly");

    /** Modul im ADHD-Menü eingeschaltet. */
    public static boolean enabled = false;

    /** Gerade laufende Animation. */
    public enum Anim { NONE, EQUIP, INSPECT, ATTACK, IDLE }

    /** Länge der Inspect-Animation in Sekunden (bei Tempo 1). */
    public static final float INSPECT_SECONDS = 3.6f;
    private static final HotKey INSPECT = new HotKey();
    private static final Random RANDOM = new Random();
    private static ButterflyConfig config;
    private static long saveAt = -1;

    private static Anim anim = Anim.NONE;
    private static long animStart;
    private static String lastItem = "";
    private static int lastSlot = -1;
    private static long stillSince = System.currentTimeMillis();
    private static long nextIdleDelay = 7000;
    private static int slashDir = 1;

    public static ButterflyConfig config() {
        if (config == null) config = ButterflyConfig.load();
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
        return HotKey.name(config().inspectKey);
    }

    public void onInitializeClient() {
        config();
        ClientTickEvents.END_CLIENT_TICK.register(ButterflyClient::tick);
        LOGGER.info("Butterfly geladen (ADHD Client Modul)");
    }

    /** Look für das Item, falls das Modul an ist und das Item einem Look zugeordnet ist. */
    @Nullable
    public static ButterflyLook lookFor(ItemStack stack) {
        if (!enabled || stack.isEmpty()) return null;
        return config().lookFor(Registries.ITEM.getId(stack.getItem()).toString());
    }

    public static Anim anim() {
        return anim;
    }

    /** Sekunden seit Start der aktuellen Animation, schon mit dem Tempo verrechnet. */
    public static float animTime() {
        return (System.currentTimeMillis() - animStart) / 1000f * (float) config().speed;
    }

    public static void start(Anim next) {
        anim = next;
        animStart = System.currentTimeMillis();
    }

    /** Vom Renderer: Animation ist fertig. */
    static void finish() {
        anim = Anim.NONE;
        stillSince = System.currentTimeMillis();
        nextIdleDelay = 6000 + RANDOM.nextInt(5000);
    }

    /** Vom Renderer: Schlag begonnen – Schnitte wechseln die Richtung wie in CS2. */
    static void onSwingStart() {
        if (!config().attackFlip || anim == Anim.INSPECT) return;
        slashDir = -slashDir;
        start(Anim.ATTACK);
    }

    /** +1 = von rechts nach links, -1 = von links nach rechts. */
    static int slashDirection() {
        return slashDir;
    }

    private static void tick(MinecraftClient mc) {
        if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
        boolean inspect = INSPECT.pressed(config().inspectKey);
        if (!enabled || mc.player == null) {
            anim = Anim.NONE;
            lastItem = "";
            return;
        }
        ItemStack main = mc.player.getMainHandStack();
        String id = main.isEmpty() ? "" : Registries.ITEM.getId(main.getItem()).toString();
        int slot = mc.player.getInventory().getSelectedSlot();
        boolean holding = lookFor(main) != null;
        if (holding && (!id.equals(lastItem) || slot != lastSlot)) {
            if (config().equipFlip) start(Anim.EQUIP);
        }
        lastItem = id;
        lastSlot = slot;
        if (!holding) return;

        if (inspect) start(Anim.INSPECT);

        // Spielen im Stand: nur wenn man still steht und gerade nichts anderes läuft
        boolean moving = mc.player.getVelocity().horizontalLengthSquared() > 0.0004 || mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
        long now = System.currentTimeMillis();
        if (moving || anim != Anim.NONE) stillSince = now;
        else if (config().idleFidget && now - stillSince > nextIdleDelay) start(Anim.IDLE);
    }
}
