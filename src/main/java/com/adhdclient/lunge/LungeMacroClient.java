package com.adhdclient.lunge;

import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import com.adhdclient.mixin.LungeAttackInvoker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * Lunge Macro: Taste drücken → Speer mit Lunge aus der Hotbar nehmen, zustechen (Lunge-Sprung) und im selben Tick
 * zurück auf das vorherige Item. Taste halten wiederholt es im eingestellten Abstand.
 */
public final class LungeMacroClient {

    public static boolean enabled = false;
    public static int key = GLFW.GLFW_KEY_V;
    /** Taste halten = immer wieder lungen. */
    public static boolean repeat = true;
    /** Abstand beim Halten (Ticks). */
    public static int delay = 10;
    /** Zahl der ausgeführten Lunges (für Anzeige/Tests). */
    public static int count;

    private static final HotKey KEY = new HotKey();
    private static int cooldown;

    private LungeMacroClient() {
    }

    public static String keyName() {
        return HotKey.name(key);
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(LungeMacroClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        boolean pressed = KEY.pressed(key);
        if (cooldown > 0) cooldown--;
        if (!enabled || mc.player == null || mc.currentScreen != null) return;
        boolean held = repeat && HotKey.isHeld(key);
        if (!pressed && !(held && cooldown == 0)) return;
        int slot = lungeSlot(mc.player);
        if (slot < 0) {
            if (pressed) mc.player.sendMessage(Text.literal(L.t("Kein Speer mit Lunge in der Hotbar", "No lunge spear in your hotbar")).formatted(Formatting.GRAY), true);
            return;
        }
        PlayerInventory inv = mc.player.getInventory();
        int previous = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        boolean attacked = ((LungeAttackInvoker) mc).adhd$doAttack();
        inv.setSelectedSlot(previous);
        if (attacked) count++;
        cooldown = Math.max(0, delay);
    }

    /** Hotbar-Feld mit dem stärksten Lunge-Speer, oder −1. */
    public static int lungeSlot(ClientPlayerEntity p) {
        var lunge = p.getEntityWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.LUNGE.getValue()).orElse(null);
        if (lunge == null) return -1;
        int best = -1, bestLevel = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            int lvl = EnchantmentHelper.getLevel(lunge, s);
            if (lvl > bestLevel) {
                bestLevel = lvl;
                best = i;
            }
        }
        return best;
    }
}
