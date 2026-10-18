package com.adhdclient.inspect.inspect;

import com.adhdclient.gui.L;
import com.adhdclient.inspect.data.Category;
import com.adhdclient.inspect.data.InspectData;
import com.adhdclient.inspect.data.Role;
import com.adhdclient.inspect.data.Section;
import com.adhdclient.inspect.export.Exporter;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Block;
import net.minecraft.component.Component;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.EnchantableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Sammelt alle Daten zu einem Item-Stack (Client-Thread). Items werden vollständig an den Client übertragen. */
public final class ItemInspector {

    private ItemInspector() {
    }

    /** @param origin z. B. "Haupthand" oder "Inventar-Slot 5" */
    public static InspectData inspect(ItemStack stack, World world, String origin) {
        Ctx ctx = new Ctx(world, world.getRegistryManager(), Ctx.Source.CLIENT_SP, false);
        RegistryOps<NbtElement> ops = ctx.ops();
        Item item = stack.getItem();
        Identifier id = Registries.ITEM.getId(item);

        InspectData data = new InspectData(InspectData.Kind.ITEM);
        data.name = stack.getName();
        data.icon = stack.copy();
        data.registryId = id.toString();
        data.namespace = id.getNamespace();
        data.modName = Nbts.modName(id.getNamespace());
        data.source = L.t("Client (Items sind vollständig)", "Client (items are complete)");

        general(data, stack, item, id, origin);
        properties(data, stack, world);
        summaries(data, stack, ops);
        tags(data, stack);
        components(data, stack, ops);
        data.giveCommand = Exporter.giveCommand(stack, ops);
        return data.sortSections();
    }

    private static void general(InspectData data, ItemStack stack, Item item, Identifier id, String origin) {
        Section s = data.section(Category.GENERAL, L.t("Allgemein", "General"));
        s.kv("Name", stack.getName().getString(), Role.STRING);
        Text custom = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (custom != null) {
            s.kv(L.t("Eigener Name (custom_name)", "Custom name (custom_name)"), custom.getString(), Role.STRING);
            s.kv("Item-Name", stack.getItemName().getString(), Role.STRING);
        }
        s.kv("Registry-ID", id.toString(), Role.STRING);
        s.kv("Namespace / Mod", id.getNamespace() + " – " + data.modName, Role.TEXT);
        s.kv(L.t("Übersetzungsschlüssel", "Translation key"), item.getTranslationKey(), Role.DIM);
        s.kv(L.t("Anzahl", "Count"), stack.getCount());
        s.kv(L.t("Herkunft", "Origin"), origin, Role.TEXT);
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            s.kv(L.t("Platziert Block", "Places block"), Registries.BLOCK.getId(block).toString(), Role.STRING);
        }
    }

    private static void properties(InspectData data, ItemStack stack, World world) {
        Section s = data.section(Category.ITEM_PROPERTIES, L.t("Item-Eigenschaften", "Item properties"));
        if (stack.isDamageable()) {
            int max = stack.getMaxDamage();
            int dmg = stack.getDamage();
            s.kv(L.t("Haltbarkeit", "Durability"), (max - dmg) + " / " + max + L.t("  (Schaden ", "  (damage ") + dmg + ", " + Nbts.pct(max - dmg, max) + ")", Role.NUMBER);
        } else {
            s.kv(L.t("Haltbarkeit", "Durability"), stack.contains(DataComponentTypes.UNBREAKABLE) ? "unzerbrechlich" : L.t("– (nicht beschädigbar)", "– (cannot be damaged)"), Role.DIM);
        }
        if (stack.contains(DataComponentTypes.UNBREAKABLE) && stack.isDamageable()) {
            s.kv(L.t("Unzerbrechlich", "Unbreakable"), Section.yesNo(true), Role.BOOLEAN);
        }
        s.kv(L.t("Max. Stapelgröße", "Max stack size"), stack.getMaxCount());
        s.kv(L.t("Rarität", "Rarity"), stack.getRarity().asString(), Role.STRING);

        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food != null) {
            s.kv(L.t("Nahrung", "Food"), food.nutrition() + L.t(" Hungerpunkte, Sättigung ", " hunger points, saturation ") + Nbts.fmt(food.saturation())
                    + (food.canAlwaysEat() ? L.t(", immer essbar", ", always edible") : ""), Role.NUMBER);
        } else {
            s.kv(L.t("Nahrung", "Food"), L.t("– (nicht essbar)", "– (not edible)"), Role.DIM);
        }
        ConsumableComponent consumable = stack.get(DataComponentTypes.CONSUMABLE);
        if (consumable != null) {
            s.kv(L.t("Verbrauchsdauer", "Consume time"), Nbts.fmt(consumable.consumeSeconds()) + " s (" + consumable.useAction().asString() + ")", Role.NUMBER);
            if (!consumable.onConsumeEffects().isEmpty()) {
                s.kv(L.t("Effekte beim Verbrauch", "Effects when consumed"), consumable.onConsumeEffects().size());
            }
        }
        int fuel = BlockInspector.safe(() -> world.getFuelRegistry().getFuelTicks(stack), 0);
        s.kv(L.t("Brennzeit", "Burn time"), fuel > 0 ? Nbts.ticks(fuel) + L.t(" – schmilzt ", " – smelts ") + Nbts.fmt(fuel / 200.0) + " Items" : L.t("– (kein Brennstoff)", "– (not a fuel)"),
                fuel > 0 ? Role.NUMBER : Role.DIM);

        EnchantableComponent enchantable = stack.get(DataComponentTypes.ENCHANTABLE);
        if (enchantable != null) {
            s.kv(L.t("Verzauberbarkeit", "Enchantability"), enchantable.value());
        }
        enchantments(s, L.t("Verzauberungen", "Enchantments"), stack.getEnchantments());
        ItemEnchantmentsComponent stored = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            enchantments(s, L.t("Gespeicherte Verzauberungen", "Stored enchantments"), stored);
        }
        AttributeModifiersComponent attributes = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (attributes != null && !attributes.modifiers().isEmpty()) {
            s.sub(L.t("Attribut-Modifikatoren", "Attribute modifiers"));
            for (AttributeModifiersComponent.Entry e : attributes.modifiers()) {
                String attr = e.attribute().getKey().map(k -> k.getValue().toString()).orElse("?");
                s.kv(attr, Nbts.fmt(e.modifier().value()) + " (" + e.modifier().operation().asString() + ", Slot "
                        + e.slot().asString() + ", " + e.modifier().id() + ")", Role.NUMBER);
            }
        }
        ToolComponent tool = stack.get(DataComponentTypes.TOOL);
        if (tool != null) {
            s.kv(L.t("Werkzeug", "Tool"), L.t("Grundgeschwindigkeit ", "Base speed ") + Nbts.fmt(tool.defaultMiningSpeed()) + ", " + tool.rules().size()
                    + L.t(" Regel(n), ", " rule(s), ") + tool.damagePerBlock() + L.t(" Schaden/Block", " damage/block"), Role.NUMBER);
        }
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            s.sub(L.t("Beschreibung (lore)", "Description (lore)"));
            for (Text line : lore.lines()) {
                s.kv("  ", line.getString(), Role.STRING);
            }
        }
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        if (model != null) {
            s.kv(L.t("Item-Modell", "Item model"), model.toString(), Role.STRING);
        }
    }

    private static void enchantments(Section s, String title, @Nullable ItemEnchantmentsComponent enchants) {
        if (enchants == null || enchants.isEmpty()) {
            return;
        }
        s.sub(title + " (" + enchants.getSize() + ")");
        for (var entry : enchants.getEnchantmentEntries()) {
            RegistryEntry<Enchantment> ench = entry.getKey();
            int level = entry.getIntValue();
            String eid = ench.getKey().map(k -> k.getValue().toString()).orElse("?");
            s.kv(Enchantment.getName(ench, level).getString(), eid + L.t(" – Stufe ", " – level ") + level, Role.NUMBER);
        }
    }

    // ------------------------------------------------------------ Sonderformate

    private static void summaries(InspectData data, ItemStack stack, RegistryOps<NbtElement> ops) {
        Item item = stack.getItem();
        // Bienennest / Bienenstock als Item
        if (item instanceof BlockItem bi && bi.getBlock() instanceof BeehiveBlock) {
            Section s = data.section(Category.SUMMARY, L.t("Bienennest / Bienenstock", "Bee nest / beehive"));
            Integer honey = 0;
            BlockStateComponent bs = stack.get(DataComponentTypes.BLOCK_STATE);
            if (bs != null && !bs.isEmpty()) {
                try {
                    Integer v = bs.getValue(BeehiveBlock.HONEY_LEVEL);
                    honey = v != null ? v : 0;
                } catch (RuntimeException e) {
                    honey = null;
                }
            }
            NbtElement bees = Nbts.encodeComponent(DataComponentTypes.BEES, stack.get(DataComponentTypes.BEES), ops);
            SpecialSummaries.hive(s, honey, bees instanceof NbtList l ? l : new NbtList(), false);
        }

        // Container-Items (Shulkerkisten u. a.)
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            Section s = data.section(Category.SUMMARY, L.t("Container-Inhalt (minecraft:container)", "Container contents (minecraft:container)"));
            List<ItemStack> stacks = container.stream().toList();
            int filled = 0;
            for (ItemStack st : stacks) {
                if (!st.isEmpty()) {
                    filled++;
                }
            }
            s.kv(L.t("Belegte Slots", "Used slots"), filled + " / " + stacks.size(), Role.NUMBER);
            for (int i = 0; i < stacks.size(); i++) {
                if (!stacks.get(i).isEmpty()) {
                    s.kv("Slot " + i, Nbts.stackLabel(stacks.get(i)), Role.TEXT);
                }
            }
            if (filled == 0) {
                s.dim("leer");
            }
        }
        BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            Section s = data.section(Category.SUMMARY, L.t("Bündel-Inhalt", "Bundle contents"));
            s.kv(L.t("Belegung", "Fill"), bundle.getOccupancy().toString(), Role.NUMBER);
            int i = 0;
            for (ItemStack st : bundle.iterate()) {
                s.kv("#" + (++i), Nbts.stackLabel(st), Role.TEXT);
            }
        }

        // Block-Entity-Daten im Item (Spawner, Schild, Truhe mit NBT …)
        TypedEntityData<?> beData = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (beData != null) {
            String beId = beData.getType() instanceof net.minecraft.block.entity.BlockEntityType<?> t
                    ? String.valueOf(Registries.BLOCK_ENTITY_TYPE.getId(t)) : null;
            NbtCompound nbt = beData.copyNbtWithoutId();
            InspectData tmp = new InspectData(InspectData.Kind.BLOCK);
            SpecialSummaries.block(tmp, null, beId, nbt, Set.of(), null, false, ops);
            for (Section sec : tmp.sections) {
                Section copy = data.section(Category.SUMMARY, sec.title + " (block_entity_data)");
                copy.entries.addAll(sec.entries);
            }
        }

        // Bücher
        if (stack.contains(DataComponentTypes.WRITTEN_BOOK_CONTENT) || stack.contains(DataComponentTypes.WRITABLE_BOOK_CONTENT)) {
            SpecialSummaries.bookInfo(data.section(Category.SUMMARY, L.t("Buch", "Book")), stack, null);
        }

        PotionContentsComponent potion = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potion != null) {
            Section s = data.section(Category.SUMMARY, L.t("Trank-Inhalt", "Potion contents"));
            s.kv(L.t("Trank", "Potion"), potion.potion().flatMap(RegistryEntry::getKey).map(k -> k.getValue().toString()).orElse("keiner"), Role.STRING);
            for (StatusEffectInstance effect : potion.getEffects()) {
                s.kv(EntityInspector.effectName(effect), EntityInspector.effectValue(effect), Role.NUMBER);
            }
            potion.customColor().ifPresent(c -> s.kv(L.t("Eigene Farbe", "Custom color"), String.format("#%06X", c & 0xFFFFFF), Role.STRING));
        }

        LodestoneTrackerComponent lodestone = stack.get(DataComponentTypes.LODESTONE_TRACKER);
        if (lodestone != null) {
            Section s = data.section(Category.SUMMARY, L.t("Leitstein-Kompass", "Lodestone compass"));
            if (lodestone.target().isPresent()) {
                var target = lodestone.target().get();
                s.coord(L.t("Ziel", "Target"), target.pos(), target.dimension().getValue().toString(), L.t("Leitstein", "Lodestone"));
                s.kv("Dimension", target.dimension().getValue().toString(), Role.STRING);
            } else {
                s.kv(L.t("Ziel", "Target"), L.t("keins", "none"), Role.DIM);
            }
            s.kv(L.t("Verfolgt Leitstein", "Tracks lodestone"), Section.yesNo(lodestone.tracked()), Role.BOOLEAN);
        }
        MapIdComponent map = stack.get(DataComponentTypes.MAP_ID);
        if (map != null) {
            data.section(Category.SUMMARY, L.t("Karte", "Map")).kv(L.t("Karten-ID", "Map ID"), map.id());
        }
        TypedEntityData<?> entityData = stack.get(DataComponentTypes.ENTITY_DATA);
        if (entityData != null) {
            Section s = data.section(Category.SUMMARY, L.t("Entity-Daten im Item (entity_data)", "Entity data in the item (entity_data)"));
            s.kv(L.t("Entity-Typ", "Entity type"), String.valueOf(entityData.getType() instanceof net.minecraft.entity.EntityType<?> t
                    ? Registries.ENTITY_TYPE.getId(t) : entityData.getType()), Role.STRING);
            s.kv(L.t("Felder", "Fields"), entityData.copyNbtWithoutId().getSize());
        }
    }

    private static void tags(InspectData data, ItemStack stack) {
        List<String> tags = stack.streamTags().map(TagKey::id).map(Identifier::toString).sorted().toList();
        Section s = data.section(Category.TAGS, L.t("Item-Tags (", "Item tags (") + tags.size() + ")");
        s.collapsedByDefault = tags.size() > 6;
        if (tags.isEmpty()) {
            s.dim(L.t("Keine Tags.", "No tags."));
        }
        for (String tag : tags) {
            s.kv("#" + tag, "", Role.DIM);
        }
    }

    // ------------------------------------------------------------ Komponenten

    private static void components(InspectData data, ItemStack stack, RegistryOps<NbtElement> ops) {
        // 1) Stack-SNBT (id, count, geänderte Komponenten) – genau das, was gespeichert/übertragen wird
        NbtElement stackNbt = Nbts.encodeStack(stack, ops);
        Section changed = data.section(Category.COMPONENTS, L.t("Item-SNBT (gespeichert, mit geänderten Komponenten)", "Item SNBT (saved, with changed components)"));
        int changes = stack.getComponentChanges().size();
        changed.kv(L.t("Geänderte Komponenten", "Changed components"), changes);
        if (stackNbt != null) {
            changed.snbt(stackNbt);
            data.rawNbt = stackNbt;
        } else {
            changed.warn(L.t("Item-Stack ließ sich nicht kodieren.", "The item stack could not be encoded."));
        }

        // 2) Alle Komponenten inkl. Standardwerte
        Section all = data.section(Category.COMPONENTS, L.t("Alle Datenkomponenten (inkl. Standardwerte)", "All data components (incl. defaults)"));
        Map<String, NbtElement> sorted = new TreeMap<>();
        List<String> transientTypes = new ArrayList<>();
        for (Component<?> c : stack.getComponents()) {
            ComponentType<?> type = c.type();
            String cid = Nbts.componentId(type);
            NbtElement value = Nbts.encodeComponent(type, c.value(), ops);
            if (value != null) {
                sorted.put(cid, value);
            } else if (c.value() instanceof Unit) {
                sorted.put(cid, new NbtCompound());
            } else {
                transientTypes.add(cid);
            }
        }
        NbtCompound allNbt = new NbtCompound();
        for (Map.Entry<String, NbtElement> e : sorted.entrySet()) {
            allNbt.put(e.getKey(), e.getValue());
        }
        List<String> changedIds = new ArrayList<>();
        for (var e : stack.getComponentChanges().entrySet()) {
            changedIds.add((e.getValue().isPresent() ? "" : "!") + Nbts.componentId(e.getKey()));
        }
        all.kv(L.t("Anzahl", "Count"), sorted.size());
        if (!changedIds.isEmpty()) {
            all.kv(L.t("Davon geändert / entfernt", "Of these changed / removed"), String.join(", ", changedIds), Role.STRING);
        }
        if (!transientTypes.isEmpty()) {
            all.kv(L.t("Nicht serialisierbar (nur Laufzeit)", "Not serializable (runtime only)"), String.join(", ", transientTypes), Role.DIM);
        }
        all.snbt(allNbt);
    }
}
