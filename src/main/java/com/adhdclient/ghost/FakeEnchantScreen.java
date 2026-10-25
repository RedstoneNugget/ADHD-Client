package com.adhdclient.ghost;

import com.adhdclient.gui.L;
import com.adhdclient.gui.PickerScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fake-Verzauberungen: alle Verzauberungen zum Anklicken, sie kommen auf das Item in deiner Hand – nur bei dir.
 * Die Stufe gilt für alle gewählten Verzauberungen.
 */
public final class FakeEnchantScreen extends PickerScreen {

    private static final int ROW = 20;
    private final List<String> selected;
    private int level;

    public FakeEnchantScreen(Screen parent) {
        super(Text.literal(L.t("Fake-Verzauberungen", "Fake enchantments")), parent, entries());
        Map<String, Integer> now = GhostItemsClient.fakesInHand();
        selected = new ArrayList<>(now.keySet());
        level = now.values().stream().findFirst().orElse(5);
    }

    private static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return out;
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).streamEntries().forEach(e -> {
            String id = e.getKey().map(k -> k.getValue().toString()).orElse("");
            if (!id.isEmpty()) out.add(new Entry(id, Enchantment.getName((RegistryEntry<Enchantment>) e, 1).getString().replaceAll(" I$", ""), book));
        });
        return out;
    }

    @Override
    protected int buildExtras(int x, int y, int w) {
        addDrawableChild(new ValueSlider(x, y, w, ROW - 2, L.t("Stufe", "Level"), 1, 255, 1, level,
                v -> String.valueOf((int) Math.round(v)), v -> {
            level = (int) Math.round(v);
            apply();
        }));
        return ROW;
    }

    @Override
    protected List<String> selection() {
        return selected;
    }

    @Override
    protected void selectionChanged() {
        apply();
    }

    private void apply() {
        if (!GhostItemsClient.enabled) {
            status(L.t("Modul ist aus – im ADHD-Menü einschalten", "Module is off – turn it on in the ADHD menu"));
            return;
        }
        Map<String, Integer> map = new java.util.LinkedHashMap<>();
        for (String id : selected) map.put(id, level);
        boolean ok = GhostItemsClient.setFakesInHand(map);
        status(ok ? selected.size() + L.t(" Verzauberungen auf dem Item in der Hand – nur du siehst sie", " enchantments on the item in your hand – only you see them")
                : L.t("Nimm zuerst ein Item in die Hand", "Hold an item first"));
    }

    @Override
    protected String noun() {
        return L.t("Verzauberungen", "Enchantments");
    }
}
