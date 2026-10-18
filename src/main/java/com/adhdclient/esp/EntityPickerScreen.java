package com.adhdclient.esp;

import com.adhdclient.gui.PickerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** ESP: alle Entity-Typen des Spiels zum Auswählen. */
public class EntityPickerScreen extends PickerScreen {

    public EntityPickerScreen(Screen parent) {
        super(Text.literal("ESP · Entities"), parent, entries());
    }

    private static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            out.add(new Entry(Registries.ENTITY_TYPE.getId(type).toString(), type.getName().getString(), iconFor(type)));
        }
        return out;
    }

    private static ItemStack iconFor(EntityType<?> type) {
        if (type == EntityType.PLAYER) return new ItemStack(Items.PLAYER_HEAD);
        SpawnEggItem egg = SpawnEggItem.forEntity(type);
        return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
    }

    @Override
    protected List<String> selection() {
        return EspClient.config().entities;
    }

    @Override
    protected void selectionChanged() {
        EspClient.onConfigEdited();
    }

    @Override
    protected String noun() {
        return "Entities";
    }

    @Override
    protected void onClose() {
        EspClient.saveNow();
    }
}
