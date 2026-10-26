package com.adhdclient.stackdrop;

import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import com.adhdclient.gui.PickerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Stack Drop: alle Items/Blöcke des Spiels zum Auswählen, darüber die Sofort-Taste und der Rauswerfen-Knopf. */
public class StackDropScreen extends PickerScreen {

    public StackDropScreen(Screen parent) {
        super(Text.literal("Stack Drop"), parent, entries());
    }

    private static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR) continue;
            out.add(new Entry(Registries.ITEM.getId(item).toString(), item.getName().getString(), new ItemStack(item)));
        }
        return out;
    }

    private static StackDropConfig cfg() {
        return StackDropClient.config();
    }

    @Override
    protected List<String> selection() {
        return cfg().selectedItems;
    }

    @Override
    protected void selectionChanged() {
        StackDropClient.onConfigEdited();
    }

    @Override
    protected String noun() {
        return L.t("Items", "Items");
    }

    @Override
    protected int buildExtras(int x, int y, int w) {
        int dropW = 140;
        keyButton(L.t("Sofort-Taste", "Instant key"), () -> StackDropClient.key, v -> StackDropClient.key = v, HotKey.NONE,
                x, y, w - dropW - 4, 18, StackDropClient::onConfigEdited);
        ButtonWidget drop = ButtonWidget.builder(Text.literal(L.t("Alles rauswerfen", "Drop everything")), b -> {
            StackDropClient.dropSelected(MinecraftClient.getInstance());
        }).dimensions(x + w - dropW, y, dropW, 18).build();
        addDrawableChild(drop);
        return 20;
    }

    @Override
    protected void onClose() {
        StackDropClient.saveNow();
    }

    @Override
    protected Runnable resetAction() {
        return () -> {
            cfg().selectedItems.clear();
            StackDropClient.saveNow();
        };
    }
}
