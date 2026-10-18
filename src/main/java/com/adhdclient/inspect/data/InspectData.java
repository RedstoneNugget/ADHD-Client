package com.adhdclient.inspect.data;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Alles, was das Panel zu einem Ziel anzeigt. Wird entweder auf dem
 * Client-Thread (Client-Daten) oder auf dem Thread des integrierten Servers
 * (Singleplayer-Serverdaten) gebaut; danach wird es nur noch gelesen.
 */
public final class InspectData {

    public enum Kind {
        BLOCK("Block"), ITEM("Item"), ENTITY("Entity");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public final Kind kind;
    public Text name = Text.empty();
    public ItemStack icon = ItemStack.EMPTY;
    /** Client-Entity für das große Modell-Icon (nur Client-Objekte, nie Server-Entities). */
    @Nullable
    public Entity iconEntity;
    public String registryId = "?";
    public String namespace = "?";
    public String modName = "?";
    @Nullable
    public BlockPos position;
    @Nullable
    public String positionText;
    /** Woher die Daten stammen, z. B. "Integrierter Server (Singleplayer)". */
    public String source = "Client";
    public boolean multiplayer;

    public final List<Section> sections = new ArrayList<>();

    /** Haupt-NBT (Block-Entity, Item-Stack oder Entity) für "SNBT kopieren"/Export. */
    @Nullable
    public NbtElement rawNbt;
    /** Nur Items: fertiger /give-Befehl. */
    @Nullable
    public String giveCommand;

    /** Singleplayer/OP: hier kommen später die vollständigen Serverdaten an. */
    @Nullable
    public transient CompletableFuture<InspectData> pending;
    /** Hinweis, falls die Serverabfrage fehlschlug (Timeout, keine Rechte). */
    @Nullable
    public String pendingFailure;

    public InspectData(Kind kind) {
        this.kind = kind;
    }

    public Section section(Category category, String title) {
        Section s = new Section(category, title);
        sections.add(s);
        return s;
    }

    public boolean isPending() {
        return pending != null && !pending.isDone();
    }

    /** Lesbare Zusammenfassung zuerst, Roh-SNBT zuletzt (stabile Sortierung). */
    public InspectData sortSections() {
        sections.sort(java.util.Comparator.comparingInt(s -> order(s.category)));
        return this;
    }

    private static int order(Category c) {
        return switch (c) {
            case SUMMARY -> 0;
            case GENERAL -> 1;
            case BLOCKSTATE, ENTITY_STATS, ITEM_PROPERTIES -> 2;
            case EQUIPMENT -> 3;
            case EFFECTS -> 4;
            case BLOCK_PROPERTIES -> 5;
            case ENVIRONMENT -> 6;
            case ATTRIBUTES -> 7;
            case TAGS -> 8;
            case COMPONENTS -> 9;
            case NBT -> 10;
        };
    }
}
