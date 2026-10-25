package com.adhdclient.autototem;

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
 * Inventar direkt in die Offhand, wenn in der Hotbar keins ist.
 */
public final class AutoTotemClient {

    public static boolean enabled = false;
    /** Auch im Inventar (nicht nur in der Hotbar) suchen. */
    public static boolean wholeInventory = false;
    /** Ticks pro Schritt durch die Hotbar. */
    public static int delay = 2;
    /** Zahl der Tausche (für Anzeige und Tests). */
    public static int swaps;

    private enum Phase { IDLE, TO_TOTEM, BACK }

    private static Phase phase = Phase.IDLE;
    private static int target = -1, home = -1;
    private static int cooldown;

    private AutoTotemClient() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoTotemClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!enabled || p == null || mc.interactionManager == null || p.isSpectator()) {
            phase = Phase.IDLE;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
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
                        cooldown = Math.max(1, delay);
                    }
                }
            }
            case TO_TOTEM -> {
                int cur = inv.getSelectedSlot();
                if (cur != target) {
                    inv.setSelectedSlot(cur + Integer.signum(target - cur));   // ein Feld weiter, sichtbar
                } else {
                    if (inv.getStack(target).isOf(Items.TOTEM_OF_UNDYING)) {
                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                        swaps++;
                    }
                    phase = Phase.BACK;
                }
                cooldown = Math.max(1, delay);
            }
            case BACK -> {
                int cur = inv.getSelectedSlot();
                if (cur != home) inv.setSelectedSlot(cur + Integer.signum(home - cur));
                else phase = Phase.IDLE;
                cooldown = Math.max(1, delay);
            }
        }
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
