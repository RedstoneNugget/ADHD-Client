package com.adhdclient.blink;

import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Blink: Bewegungspakete werden zurückgehalten, solange das Modul an ist. Für den Server bleibt man an der
 * Startstelle stehen; beim Ausschalten wird alles auf einmal gesendet und man "springt" zur neuen Position.
 * Abbrechen verwirft die Pakete und setzt einen zurück an den Start.
 */
public final class BlinkClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("Blink");

    /** Modul im ADHD-Menü eingeschaltet – nur dann reagieren die Tasten. */
    public static boolean enabled = false;
    /** Blink hält gerade Pakete zurück (wird nicht gespeichert, startet immer aus). */
    public static boolean active = false;

    private static final int FAKE_ID = -424242;
    private static final HotKey KEY = new HotKey();
    private static final HotKey CANCEL_KEY = new HotKey();
    private static final List<Packet<?>> held = new ArrayList<>();
    private static BlinkConfig config;
    private static long saveAt = -1;
    private static boolean releasing;
    private static int ticks;
    private static Vec3d startPos = Vec3d.ZERO;
    private static float startYaw;
    private static float startPitch;
    private static OtherClientPlayerEntity fake;

    public static BlinkConfig config() {
        if (config == null) config = BlinkConfig.load();
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

    public static int heldCount() {
        return held.size();
    }

    public void onInitializeClient() {
        config();
        ClientTickEvents.END_CLIENT_TICK.register(BlinkClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(BlinkClient::reset));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (saveAt >= 0) saveNow();
        });
        LOGGER.info("Blink geladen (ADHD Client Modul)");
    }

    /** Aus dem Mixin: true = Paket zurückhalten statt senden. */
    public static boolean hold(Packet<?> packet) {
        if (!active || releasing || !(packet instanceof PlayerMoveC2SPacket)) return false;
        if (!MinecraftClient.getInstance().isOnThread()) return false;
        held.add(packet);
        return true;
    }

    public static void set(boolean on) {
        if (on == active) return;
        if (on) start(MinecraftClient.getInstance());
        else send();
    }

    private static void start(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        held.clear();
        ticks = 0;
        startPos = mc.player.getEntityPos();
        startYaw = mc.player.getYaw();
        startPitch = mc.player.getPitch();
        if (config().fakePlayer) spawnFake(mc);
        active = true;
    }

    /** Alles Zurückgehaltene senden und ausschalten. */
    public static void send() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int count = held.size();
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (net != null) {
            releasing = true;
            try {
                for (Packet<?> p : held) net.sendPacket(p);
            } finally {
                releasing = false;
            }
        }
        held.clear();
        active = false;
        removeFake(mc);
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(L.t("Blink aus – ", "Blink off – ") + count + L.t(" Pakete gesendet", " packets sent")).formatted(Formatting.GRAY), true);
        }
    }

    /** Zurückgehaltenes verwerfen und den Spieler zurück an die Startstelle setzen. */
    public static void cancel() {
        MinecraftClient mc = MinecraftClient.getInstance();
        held.clear();
        active = false;
        removeFake(mc);
        if (mc.player != null) {
            mc.player.refreshPositionAndAngles(startPos, startYaw, startPitch);
            mc.player.setVelocity(Vec3d.ZERO);
            mc.player.sendMessage(Text.literal(L.t("Blink abgebrochen – zurück zum Start", "Blink cancelled – back to the start")).formatted(Formatting.GRAY), true);
        }
    }

    /** Verbindung weg, Welt gewechselt oder Spieler tot: alles still verwerfen. */
    private static void reset() {
        held.clear();
        active = false;
        removeFake(MinecraftClient.getInstance());
    }

    private static void tick(MinecraftClient mc) {
        if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
        BlinkConfig cfg = config();
        boolean toggle = KEY.pressed(cfg.key);
        boolean cancelPressed = CANCEL_KEY.pressed(cfg.cancelKey);
        if (!enabled) {
            if (active) send();
            return;
        }
        if (mc.player == null) return;
        if (active && (mc.world == null || mc.player.isDead() || (fake != null && fake.getEntityWorld() != mc.world))) {
            reset();
            return;
        }
        if (cancelPressed && active) {
            cancel();
            return;
        }
        if (toggle) {
            set(!active);
            return;
        }
        if (!active) return;
        ticks++;
        if (cfg.autoSendSeconds > 0 && ticks >= cfg.autoSendSeconds * 20) {
            send();
            return;
        }
        if (cfg.showInfo) {
            String info = String.format(Locale.ROOT, "Blink · %.1f s · %d %s", ticks / 20.0, held.size(), L.t("Pakete", "packets"));
            mc.player.sendMessage(Text.literal(info).formatted(Formatting.LIGHT_PURPLE), true);
        }
    }

    private static void spawnFake(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) return;
        UUID realId = mc.player.getUuid();
        fake = new OtherClientPlayerEntity(world, new GameProfile(UUID.randomUUID(), mc.player.getName().getString())) {
            // gleicher Skin wie der echte Spieler
            @Override
            protected PlayerListEntry getPlayerListEntry() {
                ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
                return net == null ? null : net.getPlayerListEntry(realId);
            }
        };
        fake.setId(FAKE_ID);
        fake.copyPositionAndRotation(mc.player);
        fake.setHeadYaw(mc.player.getHeadYaw());
        fake.setBodyYaw(mc.player.getBodyYaw());
        fake.setPose(mc.player.getPose());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            fake.equipStack(slot, mc.player.getEquippedStack(slot).copy());
        }
        world.addEntity(fake);
    }

    private static void removeFake(MinecraftClient mc) {
        if (fake != null && mc.world != null && mc.world.getEntityById(FAKE_ID) == fake) {
            mc.world.removeEntity(FAKE_ID, Entity.RemovalReason.DISCARDED);
        }
        fake = null;
    }
}
