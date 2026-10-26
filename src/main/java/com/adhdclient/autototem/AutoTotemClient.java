package com.adhdclient.autototem;

import com.adhdclient.mixin.MouseScrollInvoker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Auto Totem: liegt kein Totem in der Offhand, läuft die Hotbar-Auswahl sichtbar Feld für Feld zum Totem, tauscht
 * es mit der Offhand (wie Taste F) und läuft dann zurück zum ursprünglichen Feld. Optional kommt ein Totem aus dem
 * Inventar direkt in die Offhand, wenn in der Hotbar keins ist. Das Umschalten läuft über ein echtes
 * Mausrad-Scroll-Ereignis (wie beim echten Scrollen), nicht über ein selbst gebautes Paket.
 */
public final class AutoTotemClient {

    public static boolean enabled = false;
    /** Auch im Inventar (nicht nur in der Hotbar) suchen. */
    public static boolean wholeInventory = false;
    /** Ticks pro Schritt durch die Hotbar (0,5 = zwei Schritte pro echtem Tick, 0 = alles sofort). */
    public static double delay = 0.5;
    /** Zahl der Tausche (für Anzeige und Tests). */
    public static int swaps;

    private enum Phase { IDLE, TO_TOTEM, BACK }

    private static Phase phase = Phase.IDLE;
    private static int target = -1, home = -1;
    private static int cooldown;

    private AutoTotemClient() {
    }

    /** Taste zum An-/Ausschalten (−1 = keine). */
    public static int toggleKey = com.adhdclient.gui.HotKey.NONE;
    private static final com.adhdclient.gui.HotKey TOGGLE = new com.adhdclient.gui.HotKey();

    public static String keyName() {
        return com.adhdclient.gui.HotKey.name(toggleKey);
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoTotemClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (TOGGLE.pressed(toggleKey) && mc.player != null) {
            enabled = !enabled;
            com.adhdclient.core.AdhdModuleState.save();
            mc.player.sendMessage(net.minecraft.text.Text.literal("Auto Totem " + (enabled ? com.adhdclient.gui.L.t("an", "on") : com.adhdclient.gui.L.t("aus", "off")))
                    .formatted(enabled ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.GRAY), true);
        }
        ClientPlayerEntity p = mc.player;
        if (!enabled || p == null || mc.interactionManager == null || p.isSpectator()) {
            phase = Phase.IDLE;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        // Tempo 0 = alles in diesem einen Tick (Weg hin, Tausch, Weg zurück); Tempo < 1 (z. B. 0,5) = mehrere
        // Schritte in jedem echten Tick (bei 0,5: zwei Schritte pro Tick, also ein halber Tick pro Feld)
        int steps = delay <= 0 ? 24 : delay < 1 ? Math.max(1, (int) Math.round(1.0 / delay)) : 1;
        boolean sameTickBatch = steps > 1;
        for (int i = 0; i < steps; i++) {
            step(mc, p, sameTickBatch);
            if (phase == Phase.IDLE || cooldown > 0) break;
        }
    }

    private static void step(MinecraftClient mc, ClientPlayerEntity p, boolean sameTickBatch) {
        var inv = p.getInventory();
        switch (phase) {
            case IDLE -> {
                if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
                if (p.currentScreenHandler != p.playerScreenHandler) return;   // nicht in fremde Container klicken
                int slot = hotbarTotem(p);
                if (slot >= 0) {
                    home = inv.getSelectedSlot();
                    target = slot;
                    phase = Phase.TO_TOTEM;
                } else if (wholeInventory) {
                    int inner = inventoryTotem(p);
                    if (inner >= 0) {
                        mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, inner, 40, SlotActionType.SWAP, p);
                        swaps++;
                        cooldown = cooldownTicks();
                    }
                }
            }
            case TO_TOTEM -> {
                int cur = inv.getSelectedSlot();
                if (cur != target) {
                    scrollHotbar(mc, Integer.signum(target - cur));   // ein Feld weiter, per echtem Mausrad-Klick
                } else {
                    if (inv.getStack(target).isOf(Items.TOTEM_OF_UNDYING)) {
                        // Lief das Umschalten über echte, einzelne Ticks – der normale Tick
                        // (ClientPlayerInteractionManager) hat den gewählten Slot da schon selbst gemeldet, genau wie
                        // beim echten Scrollen mit dem Mausrad. Liefen aber mehrere Schritte im selben Tick (Tempo 0
                        // oder < 1), muss hier noch nachgeholfen werden, weil dieser automatische Sync sonst erst
                        // einen Tick zu spät käme.
                        if (sameTickBatch) {
                            mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(target));
                        }
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                        swaps++;
                    }
                    phase = Phase.BACK;
                }
                cooldown = cooldownTicks();
            }
            case BACK -> {
                int cur = inv.getSelectedSlot();
                if (cur != home) scrollHotbar(mc, Integer.signum(home - cur));
                else phase = Phase.IDLE;
                cooldown = cooldownTicks();
            }
        }
    }

    /** Wartezeit in echten Ticks bis zum nächsten Schritt; bei Tempo < 1 läuft alles noch im selben Tick (0). */
    private static int cooldownTicks() {
        return delay >= 1 ? (int) Math.round(delay) : 0;
    }

    /**
     * Ein Feld weiter (dir = +1) oder zurück (dir = −1), über ein echtes Mausrad-Scroll-Ereignis statt eines
     * eigenen Pakets oder direkt gesetzten Feldes – wie beim tatsächlichen Scrollen mit der Maus.
     */
    private static void scrollHotbar(MinecraftClient mc, int dir) {
        if (mc.currentScreen != null || mc.player == null) return;
        ((MouseScrollInvoker) mc.mouse).adhd$onMouseScroll(mc.getWindow().getHandle(), 0.0, -dir);
    }

    /** Hotbar-Feld mit Totem (das nächste zum gewählten Feld), oder −1. */
    static int hotbarTotem(ClientPlayerEntity p) {
        int sel = p.getInventory().getSelectedSlot(), best = -1;
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING) && (best < 0 || Math.abs(i - sel) < Math.abs(best - sel))) best = i;
        }
        return best;
    }

    /** Feld-Nummer im Spieler-Inventar-Bildschirm (9–35) mit Totem, oder −1. */
    static int inventoryTotem(ClientPlayerEntity p) {
        for (int i = 9; i < 36; i++) if (p.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        return -1;
    }
}
