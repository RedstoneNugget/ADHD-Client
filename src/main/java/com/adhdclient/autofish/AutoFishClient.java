package com.adhdclient.autofish;

import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import com.adhdclient.mixin.FishingBobberAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

/** Auto Fish: beißt ein Fisch an, wird die Angel eingeholt und nach kurzer Pause neu ausgeworfen. */
public final class AutoFishClient {

    private static boolean reeled;
    private static int recastIn = -1;
    private static int caught;

    private AutoFishClient() {
    }

    public static Extras.AutoFish cfg() {
        return Extras.get().autoFish;
    }

    public static int caught() {
        return caught;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoFishClient::tick);
    }

    @Nullable
    static Hand rodHand(ClientPlayerEntity p) {
        if (p.getMainHandStack().isOf(Items.FISHING_ROD)) return Hand.MAIN_HAND;
        if (p.getOffHandStack().isOf(Items.FISHING_ROD)) return Hand.OFF_HAND;
        return null;
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || !cfg().enabled) {
            reeled = false;
            recastIn = -1;
            return;
        }
        Hand hand = rodHand(p);
        FishingBobberEntity hook = p.fishHook;
        if (hook == null) {
            reeled = false;
            if (recastIn > 0 && --recastIn == 0 && hand != null && mc.currentScreen == null) use(mc, p, hand);
            return;
        }
        if (!reeled && hand != null && ((FishingBobberAccessor) hook).adhd$caughtFish()) {
            reeled = true;
            caught++;
            use(mc, p, hand);
            recastIn = cfg().recast ? Math.max(1, cfg().recastDelay) : -1;
        }
    }

    private static void use(MinecraftClient mc, ClientPlayerEntity p, Hand hand) {
        mc.interactionManager.interactItem(p, hand);
        p.swingHand(hand);
    }

    public static Screen screen(Screen parent) {
        Extras.AutoFish c = cfg();
        return OptionsScreen.builder("Auto Fish", c)
                .toggle(L.t("Neu auswerfen", "Recast"), L.t("Nach dem Einholen wieder auswerfen", "Cast again after reeling in"), () -> c.recast, v -> c.recast = v)
                .intSlider(L.t("Pause", "Pause"), 1, 200, () -> c.recastDelay, v -> c.recastDelay = v, " Ticks")
                .hint(() -> L.t("Angel auswerfen – den Rest macht Auto Fish. Gefangen: ", "Cast your rod – Auto Fish does the rest. Caught: ") + caught)
                .build(parent);
    }
}
