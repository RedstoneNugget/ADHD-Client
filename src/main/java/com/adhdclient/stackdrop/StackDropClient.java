package com.adhdclient.stackdrop;

import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Stack Drop: im Menü ausgewählte Items/Blöcke werden per Taste oder Knopf komplett aus Hotbar und
 * Hauptinventar rausgeworfen (wie Strg+Q auf jedem passenden Feld).
 */
public final class StackDropClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("Stack Drop");

    public static boolean enabled = false;
    /** Taste zum sofortigen Rauswerfen (ohne Menü zu öffnen). */
    public static int key = HotKey.NONE;
    private static final HotKey KEY = new HotKey();

    private static StackDropConfig config;
    private static long saveAt = -1;

    private StackDropClient() {
    }

    public static StackDropConfig config() {
        if (config == null) config = StackDropConfig.load();
        return config;
    }

    public static String keyName() {
        return HotKey.name(key);
    }

    /** Nach jeder Änderung der Auswahl: Speichern gebündelt kurz danach. */
    public static void onConfigEdited() {
        config().sanitize();
        saveAt = System.currentTimeMillis() + 1000;
    }

    public static void saveNow() {
        saveAt = -1;
        config().save();
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (saveAt >= 0 && System.currentTimeMillis() >= saveAt) saveNow();
            if (enabled && KEY.pressed(key) && mc.currentScreen == null) dropSelected(mc);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            if (saveAt >= 0) saveNow();
        });
    }

    /** Wirft alle ausgewählten Items/Blöcke aus Hotbar und Hauptinventar; liefert die Zahl der geworfenen Felder. */
    public static int dropSelected(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return 0;
        if (p.currentScreenHandler != p.playerScreenHandler) return 0;
        Set<String> sel = new HashSet<>(config().selectedItems);
        if (sel.isEmpty()) return 0;

        int syncId = p.playerScreenHandler.syncId;
        int dropped = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = p.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            if (!sel.contains(id)) continue;
            int slot = i < 9 ? i + 36 : i;
            mc.interactionManager.clickSlot(syncId, slot, 1, SlotActionType.THROW, p);
            dropped++;
        }

        if (dropped > 0) {
            p.sendMessage(Text.literal(dropped + L.t(" Stapel rausgeworfen", " stacks dropped")).formatted(Formatting.GREEN), true);
        }
        return dropped;
    }
}
