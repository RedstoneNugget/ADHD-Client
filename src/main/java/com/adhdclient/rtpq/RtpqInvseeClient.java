package com.adhdclient.rtpq;

import com.adhdclient.gui.L;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RTPQ Invsee: Meldet der Server per Chat einen RTPQ-Gegner, wird automatisch {@code /invsee <Gegner>}
 * geöffnet. Im Inventar erscheinen "Ja – kämpfen" / "Nein – /rtpqueue" mit Countdown-Balken, und es wird
 * vor Elytra, Mace und vielen End-Kristallen gewarnt. Vor dem Teleport wird das Inventar geschlossen.
 */
public final class RtpqInvseeClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("RTPQ Invsee");
    private static final Pattern TELEPORT_IN = Pattern.compile("Teleport in (\\d+)");
    private static final int BTN_W = 100;
    private static final int BTN_H = 20;
    private static final int GAP = 6;
    private static final int BAR_W = 2 * BTN_W + GAP;

    public static boolean enabled = false;

    private static RtpqConfig config;
    private static long saveAt = -1;
    private static Pattern opponentRx;
    private static Pattern endRx;

    private String opponent;
    private boolean awaitingScreen;
    private Screen trackedScreen;
    private ButtonWidget yesButton;
    private ButtonWidget noButton;
    private long startTick;
    private long endTick;
    private long awaitDeadline;
    private long now;
    private boolean decided;
    private boolean warnedElytra;
    private boolean warnedMace;
    private boolean warnedCrystal;
    private long secondRtpqueueAt;

    public static RtpqConfig config() {
        if (config == null) {
            config = RtpqConfig.load();
            compilePatterns();
        }
        return config;
    }

    public static void onConfigEdited() {
        config().sanitize();
        compilePatterns();
        saveAt = System.currentTimeMillis() + 1000;
    }

    public static void saveNow() {
        saveAt = -1;
        config().save();
    }

    private static void compilePatterns() {
        opponentRx = Pattern.compile(Pattern.quote(config.opponentText) + "\\s*(\\.?[A-Za-z0-9_]{3,16})", Pattern.CASE_INSENSITIVE);
        endRx = config.endText.isBlank() ? null : Pattern.compile(Pattern.quote(config.endText), Pattern.CASE_INSENSITIVE);
    }

    public void onInitializeClient() {
        config();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> onMessage(message.getString()));
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> onScreenInit(screen));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (saveAt >= 0) saveNow();
        });
        LOGGER.info("RTPQ Invsee geladen (ADHD Client Modul)");
    }

    private static boolean serverMatches(MinecraftClient mc) {
        String filter = config().serverFilter.trim().toLowerCase(Locale.ROOT);
        if (filter.isEmpty()) return true;
        ServerInfo info = mc.getCurrentServerEntry();
        return info != null && info.address != null && info.address.toLowerCase(Locale.ROOT).contains(filter);
    }

    // ---------------------------------------------------------------- Chat

    private void onMessage(String raw) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || !serverMatches(mc)) return;
        String msg = raw.replaceAll("§.", "");

        if (opponent != null && endRx != null && endRx.matcher(msg).find()) {
            mc.execute(this::closeTracked);
            return;
        }
        Matcher m = opponentRx.matcher(msg);
        if (!m.find() || m.groupCount() < 1) return;
        String name = m.group(1);
        Matcher t = TELEPORT_IN.matcher(msg);
        int seconds = t.find() ? Integer.parseInt(t.group(1)) : config().countdownSeconds;
        mc.execute(() -> startMatch(mc, name, seconds));
    }

    private void startMatch(MinecraftClient mc, String name, int seconds) {
        if (mc.player == null) return;
        opponent = name;
        decided = false;
        warnedElytra = false;
        warnedMace = false;
        warnedCrystal = false;
        startTick = now;
        endTick = now + seconds * 20L;
        awaitingScreen = true;
        awaitDeadline = now + 60;
        mc.player.networkHandler.sendChatCommand("invsee " + name);
    }

    // ---------------------------------------------------------------- Inventar-Bildschirm

    private void onScreenInit(Screen screen) {
        if (!(screen instanceof HandledScreen<?>)) return;
        boolean fresh = awaitingScreen;
        if (!fresh && screen != trackedScreen) return;
        if (fresh) {
            awaitingScreen = false;
            trackedScreen = screen;
            ScreenEvents.remove(screen).register(s -> {
                if (trackedScreen == s) trackedScreen = null;
            });
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> renderOverlay(s, ctx));
        }
        addButtons(screen);
    }

    /** Knöpfe direkt unter dem Container-Fenster (Höhe aus der Zahl der Container-Slots geschätzt). */
    private void addButtons(Screen screen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int containerSlots = 0;
        if (screen instanceof HandledScreen<?> hs && mc.player != null) {
            for (Slot slot : hs.getScreenHandler().slots) {
                if (slot.inventory != mc.player.getInventory()) containerSlots++;
            }
        }
        int rows = Math.max(1, (containerSlots + 8) / 9);
        int guiHeight = 114 + rows * 18;
        int y = Math.min((screen.height + guiHeight) / 2 + GAP, screen.height - BTN_H - 12);
        int x = screen.width / 2 - BAR_W / 2;

        yesButton = ButtonWidget.builder(Text.literal(L.t("Ja – kämpfen", "Yes – fight")), b -> {
            decided = true;
            closeTracked();
        }).dimensions(x, y, BTN_W, BTN_H).build();
        noButton = ButtonWidget.builder(Text.literal(L.t("Nein – /rtpqueue", "No – /rtpqueue")), b -> {
            decided = true;
            closeTracked();
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player != null) {
                c.player.networkHandler.sendChatCommand("rtpqueue");
                secondRtpqueueAt = now + Math.max(1, config().rtpqueueDelayTicks);
            }
        }).dimensions(x + BTN_W + GAP, y, BTN_W, BTN_H).build();
        Screens.getButtons(screen).add(yesButton);
        Screens.getButtons(screen).add(noButton);
    }

    private void renderOverlay(Screen screen, DrawContext ctx) {
        if (screen != trackedScreen || yesButton == null || noButton == null) return;
        RtpqButtonStyle.draw(ctx, yesButton);
        RtpqButtonStyle.draw(ctx, noButton);
        if (endTick <= 0 || endTick <= startTick) return;
        float left = Math.max(0f, Math.min(1f, (endTick - now) / (float) (endTick - startTick)));
        int x = yesButton.getX();
        int y = yesButton.getY() + BTN_H + 3;
        ctx.fill(x, y, x + BAR_W, y + 4, RtpqButtonStyle.OUTLINE);
        ctx.fill(x + 1, y + 1, x + 1 + (int) ((BAR_W - 2) * left), y + 3, RtpqButtonStyle.PINK);
    }

    // ---------------------------------------------------------------- Tick

    private void tick(MinecraftClient mc) {
        now++;
        if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
        if (mc.player == null) {
            reset();
            return;
        }
        if (secondRtpqueueAt > 0 && now >= secondRtpqueueAt) {
            secondRtpqueueAt = 0;
            mc.player.networkHandler.sendChatCommand("rtpqueue");
        }
        if (awaitingScreen && now > awaitDeadline) awaitingScreen = false;
        if (opponent != null && endTick > 0 && now >= endTick) closeTracked();
        if (trackedScreen != null && mc.currentScreen == trackedScreen && trackedScreen instanceof HandledScreen<?> hs) scan(mc, hs);
    }

    private void scan(MinecraftClient mc, HandledScreen<?> hs) {
        RtpqConfig cfg = config();
        boolean elytra = false;
        boolean mace = false;
        int crystals = 0;
        for (Slot slot : hs.getScreenHandler().slots) {
            if (slot.inventory == mc.player.getInventory()) continue;
            ItemStack s = slot.getStack();
            if (s.isEmpty()) continue;
            if (s.isOf(Items.ELYTRA)) elytra = true;
            else if (s.isOf(Items.MACE)) mace = true;
            else if (s.isOf(Items.END_CRYSTAL)) crystals += s.getCount();
        }
        String who = opponent == null ? L.t("Gegner", "Opponent") : opponent;
        if (elytra && cfg.warnElytra && !warnedElytra) {
            warnedElytra = true;
            info(who + L.t(" hat eine ELYTRA!", " has an ELYTRA!"), Formatting.YELLOW);
            play("entity.firework_rocket.launch");
        }
        if (mace && cfg.warnMace && !warnedMace) {
            warnedMace = true;
            info(who + L.t(" hat eine MACE!", " has a MACE!"), Formatting.RED);
            play("item.mace.smash_ground");
        }
        if (crystals >= cfg.crystalThreshold && cfg.warnCrystals && !warnedCrystal) {
            warnedCrystal = true;
            info(who + L.t(" hat ", " has ") + crystals + " CRYSTALS!", Formatting.LIGHT_PURPLE);
            play("entity.generic.explode");
        }
    }

    private void closeTracked() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && trackedScreen != null && mc.currentScreen == trackedScreen) mc.player.closeHandledScreen();
        trackedScreen = null;
        opponent = null;
        endTick = 0;
        awaitingScreen = false;
    }

    private void reset() {
        opponent = null;
        trackedScreen = null;
        awaitingScreen = false;
        endTick = 0;
        secondRtpqueueAt = 0;
    }

    private static void info(String msg, Formatting color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[RTPQ] " + msg).formatted(color), false);
    }

    private static void play(String id) {
        if (!config().sounds) return;
        SoundEvent sound = Registries.SOUND_EVENT.get(Identifier.of("minecraft", id));
        if (sound != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(sound, 1.0f));
    }
}
