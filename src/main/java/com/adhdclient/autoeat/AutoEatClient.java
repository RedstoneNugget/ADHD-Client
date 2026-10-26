package com.adhdclient.autoeat;

import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Auto Eat: bei wenig Hunger (oder wenig Leben) Essen aus der Hotbar nehmen, essen und zurück aufs vorherige Feld. */
public final class AutoEatClient {

    private static boolean eating;
    private static int previous = -1;
    private static int eatSlot = -1;

    private AutoEatClient() {
    }

    public static Extras.AutoEat cfg() {
        return Extras.get().autoEat;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoEatClient::tick);
    }

    public static boolean eating() {
        return eating;
    }

    static boolean isBad(ItemStack s) {
        return s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.SPIDER_EYE) || s.isOf(Items.POISONOUS_POTATO) || s.isOf(Items.PUFFERFISH)
                || s.isOf(Items.CHICKEN) || s.isOf(Items.SUSPICIOUS_STEW) || s.isOf(Items.CHORUS_FRUIT);
    }

    static boolean edible(ItemStack s, boolean avoidBad) {
        return s.get(DataComponentTypes.FOOD) != null && !(avoidBad && isBad(s));
    }

    static boolean needsFood(ClientPlayerEntity p, Extras.AutoEat c) {
        int food = p.getHungerManager().getFoodLevel();
        if (food >= 20) return false;
        return food <= c.hunger || (c.healthToo && p.getHealth() <= c.health);
    }

    /** Hotbar-Feld mit dem nahrhaftesten Essen, -1 = keins. */
    static int findFood(ClientPlayerEntity p, boolean avoidBad) {
        int best = -1, bestNutrition = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!edible(s, avoidBad)) continue;
            int n = s.get(DataComponentTypes.FOOD).nutrition();
            if (n > bestNutrition) {
                bestNutrition = n;
                best = i;
            }
        }
        return best;
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        Extras.AutoEat c = cfg();
        if (p == null || mc.interactionManager == null) {
            eating = false;
            previous = -1;
            return;
        }
        if (eating) {
            boolean stillFood = p.getInventory().getSelectedSlot() == eatSlot && edible(p.getInventory().getStack(eatSlot), c.avoidBad);
            if (!c.enabled || mc.currentScreen != null || !stillFood || p.getHungerManager().getFoodLevel() >= 20
                    || (!needsFood(p, c) && !p.isUsingItem())) {
                stop(mc);
            } else {
                mc.options.useKey.setPressed(true);
            }
            return;
        }
        if (!c.enabled || mc.currentScreen != null || p.isUsingItem() || p.getAbilities().creativeMode || !needsFood(p, c)) return;
        int slot = findFood(p, c.avoidBad);
        if (slot < 0) return;
        previous = p.getInventory().getSelectedSlot();
        eatSlot = slot;
        p.getInventory().setSelectedSlot(slot);
        eating = true;
        mc.options.useKey.setPressed(true);
    }

    private static void stop(MinecraftClient mc) {
        eating = false;
        mc.options.useKey.setPressed(false);
        if (previous >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(previous);
        previous = -1;
        eatSlot = -1;
    }

    public static Screen screen(Screen parent) {
        Extras.AutoEat c = cfg();
        return OptionsScreen.builder("Auto Eat", c)
                .toggle(L.t("Bei wenig Leben", "On low health"), L.t("Auch essen, wenn das Leben niedrig ist (für Regeneration)", "Also eat when health is low (for regeneration)"),
                        () -> c.healthToo, v -> c.healthToo = v)
                .toggle(L.t("Schlechtes meiden", "Avoid bad food"), L.t("Kein Gammelfleisch, Spinnenauge, Kugelfisch, rohes Hühnchen, Chorus", "No rotten flesh, spider eye, pufferfish, raw chicken, chorus"),
                        () -> c.avoidBad, v -> c.avoidBad = v)
                .slider(L.t("Essen ab Hunger", "Eat at hunger"), 1, 19, 1, () -> c.hunger, v -> c.hunger = (int) Math.round(v),
                        v -> (int) Math.round(v) + "/20")
                .slider(L.t("Essen ab Leben", "Eat at health"), 1, 19, 1, () -> c.health, v -> c.health = (int) Math.round(v),
                        v -> String.format(java.util.Locale.ROOT, "%.1f❤", v / 2))
                .hint(() -> L.t("Isst automatisch aus der Hotbar", "Eats from your hotbar automatically"))
                .build(parent);
    }
}
