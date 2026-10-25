package com.adhdclient.anticrystal;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Anti Crystal Death: taucht ein End-Kristall in der Nähe auf, wird kurz das Inventar geöffnet, ein Totem in die
 * Offhand und eins in die Main Hand (gewähltes Hotbar-Feld) gelegt und das Inventar wieder geschlossen.
 */
public final class AntiCrystalClient {

    public static boolean enabled = false;
    /** Reichweite in Blöcken, in der ein Kristall auslöst. */
    public static double radius = 6;
    /** Zahl der Auslösungen (für Anzeige/Tests). */
    public static int triggers;

    private enum Step { IDLE, OPEN, OFFHAND, MAINHAND, CLOSE }

    private static Step step = Step.IDLE;
    private static int wait;
    private static boolean weOpened;

    private AntiCrystalClient() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AntiCrystalClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (!enabled || p == null || mc.world == null || mc.interactionManager == null) {
            step = Step.IDLE;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        int sync = p.playerScreenHandler.syncId;
        switch (step) {
            case IDLE -> {
                if (!crystalNear(mc) || armed(p)) return;
                if (findTotem(p, -1) < 0) return;
                triggers++;
                step = Step.OPEN;
            }
            case OPEN -> {
                weOpened = !(mc.currentScreen instanceof InventoryScreen);
                if (weOpened) {
                    if (mc.currentScreen != null) {          // anderes Menü (Kiste …): nicht dazwischenfunken
                        step = Step.IDLE;
                        return;
                    }
                    mc.setScreen(new InventoryScreen(p));
                }
                step = Step.OFFHAND;
                wait = 1;
            }
            case OFFHAND -> {
                if (!p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    int s = findTotem(p, -1);
                    if (s >= 0) mc.interactionManager.clickSlot(sync, s, 40, SlotActionType.SWAP, p);
                }
                step = Step.MAINHAND;
                wait = 1;
            }
            case MAINHAND -> {
                int sel = p.getInventory().getSelectedSlot();
                if (!p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                    int s = findTotem(p, 36 + sel);
                    if (s >= 0) mc.interactionManager.clickSlot(sync, s, sel, SlotActionType.SWAP, p);
                }
                step = Step.CLOSE;
                wait = 1;
            }
            case CLOSE -> {
                if (weOpened && mc.currentScreen instanceof InventoryScreen) mc.setScreen(null);
                step = Step.IDLE;
                wait = 10;
            }
        }
    }

    /** Beide Hände halten schon ein Totem. */
    private static boolean armed(ClientPlayerEntity p) {
        return p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) && p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    private static boolean crystalNear(MinecraftClient mc) {
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity && e.squaredDistanceTo(mc.player) <= radius * radius) return true;
        }
        return false;
    }

    /** Feld im Inventar-Bildschirm (9–44) mit Totem, außer {@code skip}; Inventar vor Hotbar, oder −1. */
    private static int findTotem(ClientPlayerEntity p, int skip) {
        for (int s = 9; s <= 44; s++) {
            if (s == skip) continue;
            int inv = s >= 36 ? s - 36 : s;
            if (p.getInventory().getStack(inv).isOf(Items.TOTEM_OF_UNDYING)) return s;
        }
        return -1;
    }
}
