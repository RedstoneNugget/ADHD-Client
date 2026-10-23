package com.adhdclient.butterfly;

import com.adhdclient.gui.L;
import com.adhdclient.gui.PickerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Alle Items zum Anklicken: welche davon in der Hand zu diesem Look werden. */
public final class ItemLookPickerScreen extends PickerScreen {

    private final ButterflyLook look;

    public ItemLookPickerScreen(Screen parent, ButterflyLook look) {
        super(Text.literal(look.title()), parent, entries());
        this.look = look;
    }

    private static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            out.add(new Entry(Registries.ITEM.getId(item).toString(), stack.getName().getString(), stack));
        }
        return out;
    }

    @Override
    protected List<String> selection() {
        return ButterflyClient.config().looks.computeIfAbsent(look.id, k -> new ArrayList<>());
    }

    @Override
    protected void selectionChanged() {
        ButterflyClient.onConfigEdited();
    }

    @Override
    protected String noun() {
        return L.t("Items", "Items");
    }

    @Override
    protected void onClose() {
        ButterflyClient.saveNow();
    }
}
