package com.adhdclient.sorter;

import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import com.adhdclient.mixin.HandledScreenBoundsAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Inventory Sorter: ein Knopf über dem Inventar (oder eine Taste) sortiert das Hauptinventar oder die offene Kiste
 * nach Item-Art und Menge. Gleiche Items werden dabei zusammengelegt. Alles über normale Inventar-Klicks.
 */
public final class InventorySorter {

    private InventorySorter() {
    }

    public static Extras.Sorter cfg() {
        return Extras.get().sorter;
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!cfg().enabled || !(screen instanceof HandledScreen<?> hs) || screen instanceof CreativeInventoryScreen) return;
            boolean container = isContainer(screen);
            if (!container && !(screen instanceof InventoryScreen)) return;
            HandledScreenBoundsAccessor b = (HandledScreenBoundsAccessor) hs;
            int bw = 50;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(L.t("Sortieren", "Sort")), x -> sort(hs, container && cfg().containers))
                    .dimensions(b.adhd$getX() + b.adhd$getBackgroundWidth() - bw, b.adhd$getY() - 14, bw, 12).build();
            btn.setTooltip(Tooltip.of(Text.literal(container && cfg().containers ? L.t("Kiste nach Item-Art sortieren", "Sort the container by item type")
                    : L.t("Inventar nach Item-Art sortieren", "Sort your inventory by item type"))));
            Screens.getButtons(screen).add(btn);
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, input) -> {
                if (cfg().enabled && cfg().key != HotKey.NONE && input.key() == cfg().key) sort(hs, container && cfg().containers);
            });
        });
    }

    static boolean isContainer(Screen s) {
        return s instanceof GenericContainerScreen || s instanceof ShulkerBoxScreen;
    }

    /** Die zu sortierenden Slots: Kisten-Slots oder das Hauptinventar (ohne Hotbar und Rüstung). */
    static List<Slot> slots(ScreenHandler h, boolean container, PlayerInventory inv) {
        List<Slot> out = new ArrayList<>();
        for (Slot s : h.slots) {
            if (container ? s.inventory != inv : s.inventory == inv && s.getIndex() >= 9 && s.getIndex() < 36) out.add(s);
        }
        return out;
    }

    private static final Comparator<ItemStack> ORDER = Comparator
            .comparing((ItemStack s) -> s.isEmpty())
            .thenComparing(s -> Registries.ITEM.getId(s.getItem()).toString())
            .thenComparing(s -> -s.getCount());

    public static void sort(HandledScreen<?> screen, boolean container) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;
        ScreenHandler h = screen.getScreenHandler();
        if (!h.getCursorStack().isEmpty()) return;
        List<Slot> slots = slots(h, container, mc.player.getInventory());
        // erst gleiche Stapel zusammenlegen (hinteren aufnehmen, auf den vorderen legen, Rest zurück)
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                ItemStack a = slots.get(i).getStack(), b = slots.get(j).getStack();
                if (a.isEmpty() || b.isEmpty() || a.getCount() >= a.getMaxCount() || !ItemStack.areItemsAndComponentsEqual(a, b)) continue;
                click(mc, h, slots.get(j));
                click(mc, h, slots.get(i));
                if (!h.getCursorStack().isEmpty()) click(mc, h, slots.get(j));
                if (!h.getCursorStack().isEmpty()) return;
            }
        }
        for (int guard = 0; guard < 400; guard++) {
            List<ItemStack> want = new ArrayList<>();
            for (Slot s : slots) want.add(s.getStack());
            want.sort(ORDER);
            int i = 0;
            while (i < slots.size() && ItemStack.areEqual(slots.get(i).getStack(), want.get(i))) i++;
            if (i >= slots.size()) return;
            int j = -1;
            for (int k = i + 1; k < slots.size(); k++) {
                if (ItemStack.areEqual(slots.get(k).getStack(), want.get(i))) {
                    j = k;
                    break;
                }
            }
            if (j < 0) return;
            click(mc, h, slots.get(j));
            click(mc, h, slots.get(i));
            if (!h.getCursorStack().isEmpty()) click(mc, h, slots.get(j));
            if (!h.getCursorStack().isEmpty()) return;   // sollte nie passieren – lieber aufhören als Items verlieren
        }
    }

    private static void click(MinecraftClient mc, ScreenHandler h, Slot s) {
        mc.interactionManager.clickSlot(h.syncId, s.id, 0, SlotActionType.PICKUP, mc.player);
    }

    public static Screen screen(Screen parent) {
        Extras.Sorter c = cfg();
        return OptionsScreen.builder("Inventory Sorter", c)
                .key(L.t("Taste", "Key"), () -> c.key, v -> c.key = v, HotKey.NONE)
                .toggle(L.t("Kisten sortieren", "Sort containers"), L.t("In Kisten und Shulkern die Kiste sortieren, sonst immer dein Inventar",
                        "In chests and shulkers sort the container, otherwise always your inventory"), () -> c.containers, v -> c.containers = v)
                .hint(() -> L.t("Knopf „Sortieren“ über dem Inventar", "\"Sort\" button above the inventory"))
                .build(parent);
    }
}
