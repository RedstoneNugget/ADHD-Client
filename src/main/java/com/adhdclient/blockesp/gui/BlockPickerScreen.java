package com.adhdclient.blockesp.gui;

import com.adhdclient.gui.L;
import com.adhdclient.blockesp.BlockESPClient;
import com.adhdclient.blockesp.BlockESPConfig;
import com.adhdclient.gui.PickerScreen;
import com.adhdclient.gui.ValueSlider;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Block ESP: alle Blöcke des Spiels zum Auswählen, darüber Tracer, Füllung und Reichweite. */
public class BlockPickerScreen extends PickerScreen {

    public BlockPickerScreen(Screen parent) {
        super(Text.literal("Block ESP"), parent, entries());
    }

    private static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Block b : Registries.BLOCK) {
            if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) continue;
            out.add(new Entry(Registries.BLOCK.getId(b).toString(), b.getName().getString(), new ItemStack(b.asItem())));
        }
        return out;
    }

    private static BlockESPConfig cfg() {
        return BlockESPClient.config();
    }

    @Override
    protected List<String> selection() {
        return cfg().selectedBlocks;
    }

    @Override
    protected void selectionChanged() {
        BlockESPClient.onConfigEdited(true);
    }

    @Override
    protected String noun() {
        return L.t("Blöcke", "Blocks");
    }

    @Override
    protected int buildExtras(int x, int y, int w) {
        int third = (w - 8) / 3;
        toggle("Tracer", () -> cfg().tracers, v -> cfg().tracers = v, x, y, third, 18,
                L.t("Linien vom Fadenkreuz zu den Blöcken", "Lines from your crosshair to the blocks"), () -> BlockESPClient.onConfigEdited(false));
        toggle(L.t("Füllung", "Fill"), () -> cfg().fill, v -> cfg().fill = v, x + third + 4, y, third, 18,
                L.t("Boxen halbdurchsichtig füllen", "Fill boxes semi-transparently"), () -> BlockESPClient.onConfigEdited(false));
        addDrawableChild(new ValueSlider(x + 2 * (third + 4), y, w - 2 * (third + 4), 18, L.t("Reichweite", "Range"),
                16, 512, 16, cfg().range, v -> (int) Math.round(v) + " m", v -> {
            cfg().range = (int) Math.round(v);
            BlockESPClient.onConfigEdited(false);
        }));
        return 20;
    }

    @Override
    protected void onClose() {
        BlockESPClient.saveNow();
    }
}
