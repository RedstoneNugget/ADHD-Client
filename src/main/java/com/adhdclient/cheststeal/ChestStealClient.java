package com.adhdclient.cheststeal;

import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import com.adhdclient.mixin.HandledScreenBoundsAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * Chest Stealer / Dumper: zwei Knöpfe über Kisten und Shulkern – „Alles nehmen“ holt alles ins Inventar,
 * „Alles rein“ legt dein Hauptinventar (ohne Hotbar) in die Kiste. Optional nimmt es beim Öffnen automatisch alles.
 */
public final class ChestStealClient {

    /** Beim Öffnen automatisch ausräumen: das Fenster, auf das gewartet wird. */
    @Nullable
    private static HandledScreen<?> pendingAuto;
    private static int autoWait;

    private ChestStealClient() {
    }

    public static Extras.ChestSteal cfg() {
        return Extras.get().chestSteal;
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!cfg().enabled || !(screen instanceof GenericContainerScreen || screen instanceof ShulkerBoxScreen)) return;
            HandledScreen<?> hs = (HandledScreen<?>) screen;
            HandledScreenBoundsAccessor b = (HandledScreenBoundsAccessor) hs;
            int bw = 56;
            ButtonWidget take = ButtonWidget.builder(Text.literal(L.t("Alles nehmen", "Take all")), x -> steal(hs))
                    .dimensions(b.adhd$getX(), b.adhd$getY() - 14, bw, 12).build();
            take.setTooltip(Tooltip.of(Text.literal(L.t("Alles aus der Kiste ins Inventar", "Everything from the container into your inventory"))));
            ButtonWidget dump = ButtonWidget.builder(Text.literal(L.t("Alles rein", "Put all")), x -> dump(hs))
                    .dimensions(b.adhd$getX() + bw + 2, b.adhd$getY() - 14, bw, 12).build();
            dump.setTooltip(Tooltip.of(Text.literal(L.t("Dein Inventar (ohne Hotbar) in die Kiste", "Your inventory (without hotbar) into the container"))));
            Screens.getButtons(screen).add(take);
            Screens.getButtons(screen).add(dump);
            if (cfg().auto && pendingAuto != hs) {
                pendingAuto = hs;
                autoWait = 3;   // kurz warten, bis der Server den Inhalt geschickt hat
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ChestStealClient::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (pendingAuto == null) return;
        if (mc.currentScreen != pendingAuto) {
            pendingAuto = null;
            return;
        }
        if (--autoWait > 0) return;
        HandledScreen<?> hs = pendingAuto;
        pendingAuto = null;
        steal(hs);
        if (cfg().closeAfter) hs.close();
    }

    public static int steal(HandledScreen<?> screen) {
        return move(screen, true);
    }

    public static int dump(HandledScreen<?> screen) {
        return move(screen, false);
    }

    /** Shift-Klick auf jeden belegten Slot der Kiste (fromContainer) oder des Hauptinventars. */
    private static int move(HandledScreen<?> screen, boolean fromContainer) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return 0;
        ScreenHandler h = screen.getScreenHandler();
        var inv = mc.player.getInventory();
        int n = 0;
        for (Slot s : h.slots) {
            boolean isContainer = s.inventory != inv;
            if (isContainer != fromContainer || !s.hasStack()) continue;
            if (!fromContainer && s.getIndex() < 9) continue;   // Hotbar bleibt
            mc.interactionManager.clickSlot(h.syncId, s.id, 0, SlotActionType.QUICK_MOVE, mc.player);
            n++;
        }
        return n;
    }

    public static Screen screen(Screen parent) {
        Extras.ChestSteal c = cfg();
        return OptionsScreen.builder("Chest Stealer", c)
                .toggle(L.t("Automatisch", "Automatic"), L.t("Beim Öffnen einer Kiste sofort alles nehmen", "Take everything as soon as a container opens"),
                        () -> c.auto, v -> c.auto = v)
                .toggle(L.t("Danach schließen", "Close after"), L.t("Nach dem automatischen Ausräumen die Kiste schließen", "Close the container after taking everything automatically"),
                        () -> c.closeAfter, v -> c.closeAfter = v)
                .hint(() -> L.t("Knöpfe „Alles nehmen“ und „Alles rein“ über Kisten", "\"Take all\" and \"Put all\" buttons above containers"))
                .build(parent);
    }
}
