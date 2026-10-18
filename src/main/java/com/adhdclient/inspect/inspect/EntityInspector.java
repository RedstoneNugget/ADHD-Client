package com.adhdclient.inspect.inspect;

import com.adhdclient.gui.L;
import com.adhdclient.inspect.InspectClient;
import com.adhdclient.inspect.data.Category;
import com.adhdclient.inspect.data.Coord;
import com.adhdclient.inspect.data.InspectData;
import com.adhdclient.inspect.data.Role;
import com.adhdclient.inspect.data.Section;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.scoreboard.Team;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.text.Text;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Sammelt alle Daten zu einer Entity. Läuft auf dem Client- oder dem Server-Thread. */
public final class EntityInspector {

    private EntityInspector() {
    }

    /**
     * @param queryNbt     vollständige Server-NBT aus der Vanilla-Abfrage (OP) oder null
     * @param clientEntity Client-Objekt für das Modell-Icon (darf null sein)
     */
    public static InspectData inspect(Ctx ctx, Entity entity, @Nullable NbtCompound queryNbt, @Nullable Entity clientEntity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        InspectData data = new InspectData(InspectData.Kind.ENTITY);
        data.name = entity.getName();
        data.icon = icon(entity);
        data.iconEntity = clientEntity;
        data.registryId = id.toString();
        data.namespace = id.getNamespace();
        data.modName = Nbts.modName(id.getNamespace());
        data.position = entity.getBlockPos().toImmutable();
        Vec3d p = entity.getEntityPos();
        data.positionText = fmt3(p);
        data.source = ctx.source().label();
        data.multiplayer = ctx.mpClient() || ctx.source() == Ctx.Source.OP_QUERY;

        NbtCompound nbt = null;
        try {
            nbt = queryNbt != null ? withId(queryNbt.copy(), id) : write(entity, ctx.registries());
        } catch (RuntimeException e) {
            InspectClient.LOGGER.debug(L.t("Entity-NBT nicht lesbar", "Entity NBT not readable"), e);
        }

        general(data, ctx, entity, id, nbt);
        if (entity instanceof LivingEntity living) {
            stats(data, living);
            equipment(data, living);
            effects(data, ctx, living, nbt);
            attributes(data, ctx, living);
        }
        tags(data, ctx, entity, nbt);
        if (nbt != null) {
            EntitySummaries.add(data, ctx, entity, id, nbt);
        }

        Section s = data.section(Category.NBT, L.t("Entity-Daten (SNBT)", "Entity data (SNBT)"));
        if (ctx.mpClient()) {
            s.warn(L.t("Mehrspieler: Das ist das Client-Abbild der Entity.", "Multiplayer: this is the client copy of the entity."));
            s.dim(L.t("Server-only-Daten (Inventar, KI-Gedächtnis, Befehls-Tags, Heimat-/Blumen-Positionen, Effekte anderer",
                    "Server-only data (inventory, AI memory, command tags, home/flower positions, effects of other"));
            s.dim(L.t("Entities u. a.) werden vom Server nicht übertragen und fehlen hier oder stehen auf Standardwerten.", "entities and more) is not sent by the server and is missing here or shows default values."));
        }
        if (nbt != null) {
            s.snbt(nbt);
            data.rawNbt = nbt;
        } else {
            s.warn(L.t("Entity-Daten nicht lesbar.", "Entity data not readable."));
        }
        return data.sortSections();
    }

    public static NbtCompound write(Entity entity, RegistryWrapper.WrapperLookup registries) {
        NbtWriteView view = NbtWriteView.create(ErrorReporter.EMPTY, registries);
        entity.writeData(view);
        return withId(view.getNbt(), Registries.ENTITY_TYPE.getId(entity.getType()));
    }

    private static NbtCompound withId(NbtCompound nbt, Identifier id) {
        if (!nbt.contains("id")) {
            nbt.putString("id", id.toString());
        }
        return nbt;
    }

    private static ItemStack icon(Entity entity) {
        try {
            if (entity instanceof ItemEntity item && !item.getStack().isEmpty()) {
                return item.getStack().copy();
            }
            ItemStack pick = entity.getPickBlockStack();
            if (pick != null && !pick.isEmpty()) {
                return pick;
            }
        } catch (RuntimeException ignored) {
        }
        return new ItemStack(Items.NAME_TAG);
    }

    // ------------------------------------------------------------ Abschnitte

    private static void general(InspectData data, Ctx ctx, Entity e, Identifier id, @Nullable NbtCompound nbt) {
        Section s = data.section(Category.GENERAL, L.t("Allgemein", "General"));
        s.kv(L.t("Typ", "Type"), id.toString(), Role.STRING);
        s.kv("Namespace / Mod", id.getNamespace() + " – " + data.modName, Role.TEXT);
        s.kv("Name", e.getName().getString(), Role.STRING);
        Text custom = e.getCustomName();
        s.kv("Custom Name", custom == null ? "–" : custom.getString(), custom == null ? Role.DIM : Role.STRING);
        s.kv("UUID", e.getUuidAsString(), Role.STRING);
        s.kv(L.t("Netzwerk-ID", "Network ID"), e.getId());
        Vec3d p = e.getEntityPos();
        s.coord("Position", fmt3(p), e.getBlockPos(), id.getPath());
        s.kv(L.t("Block-Position", "Block position"), e.getBlockPos().getX() + ", " + e.getBlockPos().getY() + ", " + e.getBlockPos().getZ(), Role.NUMBER);
        s.kv("Rotation (Yaw / Pitch)", Nbts.fmt(round1(e.getYaw())) + "° / " + Nbts.fmt(round1(e.getPitch())) + "°", Role.NUMBER);
        Vec3d v = e.getVelocity();
        s.kv(L.t("Bewegung", "Motion"), fmt3(v) + "  (" + Nbts.fmt(Math.round(v.length() * 20 * 100) / 100.0) + " m/s)", Role.NUMBER);
        s.kv("Dimension", ctx.dimension(), Role.STRING);
        try {
            s.kv(L.t("Biom", "Biome"), ctx.world().getBiome(e.getBlockPos()).getKey().map(k -> k.getValue().toString()).orElse("?"), Role.STRING);
        } catch (RuntimeException ignored) {
        }
        s.kv(L.t("Alter", "Age"), Nbts.ticks(e.age));
        s.kv("Pose", e.getPose().name().toLowerCase(Locale.ROOT), Role.STRING);
        s.kv(L.t("Auf dem Boden", "On ground"), Section.yesNo(e.isOnGround()), Role.BOOLEAN);
        s.kv(L.t("Brennt", "On fire"), e.isOnFire() ? L.t("ja (", "yes (") + e.getFireTicks() + L.t(" Ticks)", " ticks)") : Section.yesNo(false), Role.BOOLEAN);
        s.kv(L.t("Luft", "Air"), e.getAir() + " / " + e.getMaxAir());
        if (e.getFrozenTicks() > 0) {
            s.kv(L.t("Vereist", "Frozen"), Nbts.ticks(e.getFrozenTicks()));
        }
        if (ctx.mpClient()) {
            s.notSent(L.t("Unverwundbar (Invulnerable)", "Invulnerable"));
        } else if (ctx.source() == Ctx.Source.OP_QUERY && nbt != null) {
            Boolean inv = Nbts.bool(nbt, "Invulnerable");
            s.kv(L.t("Unverwundbar", "Invulnerable"), Section.yesNo(inv != null && inv), Role.BOOLEAN);
        } else {
            s.kv(L.t("Unverwundbar", "Invulnerable"), Section.yesNo(e.isInvulnerable()), Role.BOOLEAN);
        }
        s.kv(L.t("Lautlos / Keine Schwerkraft", "Silent / no gravity"), Section.yesNo(e.isSilent()) + " / " + Section.yesNo(e.hasNoGravity()), Role.BOOLEAN);
        s.kv(L.t("Leuchtet / Unsichtbar", "Glowing / invisible"), Section.yesNo(e.isGlowing()) + " / " + Section.yesNo(e.isInvisible()), Role.BOOLEAN);
        Team team = e.getScoreboardTeam();
        s.kv("Team", team == null ? "–" : team.getName(), team == null ? Role.DIM : Role.STRING);
        Entity vehicle = e.getVehicle();
        s.kv(L.t("Fahrzeug", "Vehicle"), vehicle == null ? "–" : vehicle.getName().getString() + " (" + Registries.ENTITY_TYPE.getId(vehicle.getType()) + ")",
                vehicle == null ? Role.DIM : Role.STRING);
        List<Entity> passengers = e.getPassengerList();
        if (!passengers.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Entity pass : passengers) {
                names.add(pass.getName().getString());
            }
            s.kv(L.t("Passagiere", "Passengers"), String.join(", ", names), Role.STRING);
        }
        s.kv(L.t("Datenquelle", "Data source"), ctx.source().label(), ctx.mpClient() ? Role.WARN : Role.DIM);
    }

    private static void stats(InspectData data, LivingEntity living) {
        Section s = data.section(Category.ENTITY_STATS, L.t("Werte", "Stats"));
        s.kv(L.t("Gesundheit", "Health"), Nbts.fmt(round1(living.getHealth())) + " / " + Nbts.fmt(round1(living.getMaxHealth()))
                + "  (" + Nbts.fmt(round1(living.getHealth() / 2.0)) + L.t(" Herzen)", " hearts)"), Role.NUMBER);
        if (living.getAbsorptionAmount() > 0) {
            s.kv("Absorption", Nbts.fmt(round1(living.getAbsorptionAmount())));
        }
        s.kv(L.t("Rüstung", "Armor"), living.getArmor());
        double toughness = BlockInspector.safe(() -> living.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS), 0.0);
        s.kv(L.t("Rüstungshärte", "Armor toughness"), Nbts.fmt(round1(toughness)));
        s.kv("Baby", Section.yesNo(living.isBaby()), Role.BOOLEAN);
    }

    private static void equipment(InspectData data, LivingEntity living) {
        Section s = data.section(Category.EQUIPMENT, L.t("Ausrüstung", "Equipment"));
        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = BlockInspector.safe(() -> living.getEquippedStack(slot), ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                count++;
                s.kv(slotName(slot), Nbts.stackLabel(stack), Role.TEXT);
            }
        }
        if (count == 0) {
            s.dim(L.t("Keine Ausrüstung.", "No equipment."));
        }
    }

    private static String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> L.t("Haupthand", "Main hand");
            case OFFHAND -> L.t("Nebenhand", "Off hand");
            case HEAD -> L.t("Kopf", "Head");
            case CHEST -> L.t("Brust", "Chest");
            case LEGS -> L.t("Beine", "Legs");
            case FEET -> L.t("Füße", "Feet");
            case BODY -> L.t("Körper", "Body");
            case SADDLE -> L.t("Sattel", "Saddle");
        };
    }

    private static void effects(InspectData data, Ctx ctx, LivingEntity living, @Nullable NbtCompound nbt) {
        Section s = data.section(Category.EFFECTS, L.t("Effekte", "Effects"));
        java.util.Collection<StatusEffectInstance> effects = living.getStatusEffects();
        if (ctx.source() == Ctx.Source.OP_QUERY && nbt != null) {
            // Server-NBT ist maßgeblich – das Client-Objekt kennt die Effekte anderer Entities nicht
            List<StatusEffectInstance> fromNbt = new ArrayList<>();
            RegistryOps<NbtElement> ops = ctx.ops();
            for (NbtElement el : Nbts.list(nbt, "active_effects")) {
                StatusEffectInstance.CODEC.parse(ops, el).result().ifPresent(fromNbt::add);
            }
            effects = fromNbt;
        }
        if (effects.isEmpty()) {
            if (ctx.mpClient() && !ctx.selfPlayer()) {
                s.kv(L.t("Effekte", "Effects"), Section.notSentText(), Role.WARN);
                s.dim(L.t("Vanilla überträgt Effekte nur an den betroffenen Spieler selbst (andere sehen nur Partikel).", "Vanilla only sends effects to the affected player (others only see particles)."));
            } else {
                s.dim(L.t("Keine aktiven Effekte.", "No active effects."));
            }
            return;
        }
        for (StatusEffectInstance effect : effects) {
            s.kv(effectName(effect), effectValue(effect), Role.NUMBER);
        }
    }

    public static String effectName(StatusEffectInstance effect) {
        String name = Text.translatable(effect.getTranslationKey()).getString();
        return name + (effect.getAmplifier() > 0 ? " " + roman(effect.getAmplifier() + 1) : "");
    }

    public static String effectValue(StatusEffectInstance effect) {
        String id = effect.getEffectType().getKey().map(k -> k.getValue().toString()).orElse("?");
        String duration = effect.isInfinite() ? "∞" : formatDuration(effect.getDuration());
        return duration + L.t(" · Stufe ", " · level ") + (effect.getAmplifier() + 1) + " · " + id
                + (effect.isAmbient() ? L.t(" · Umgebung", " · ambient") : "") + (effect.shouldShowParticles() ? "" : L.t(" · ohne Partikel", " · no particles"));
    }

    private static String formatDuration(int ticks) {
        int seconds = ticks / 20;
        return String.format(Locale.ROOT, L.t("%d:%02d (%d Ticks)", "%d:%02d (%d ticks)"), seconds / 60, seconds % 60, ticks);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    private static void attributes(InspectData data, Ctx ctx, LivingEntity living) {
        Section s = data.section(Category.ATTRIBUTES, L.t("Attribute", "Attributes"));
        s.collapsedByDefault = true;
        AttributeContainer container = living.getAttributes();
        boolean anyUntracked = false;
        List<String[]> rows = new ArrayList<>();
        for (EntityAttribute attribute : Registries.ATTRIBUTE) {
            RegistryEntry<EntityAttribute> entry = Registries.ATTRIBUTE.getEntry(attribute);
            if (!container.hasAttribute(entry)) {
                continue;
            }
            EntityAttributeInstance inst = container.getCustomInstance(entry);
            if (inst == null) {
                continue;
            }
            String aid = entry.getKey().map(k -> k.getValue().toString()).orElse("?");
            boolean untracked = (ctx.mpClient() || ctx.source() == Ctx.Source.OP_QUERY) && !attribute.isTracked();
            anyUntracked |= untracked;
            String value = Nbts.fmt(round3(inst.getValue())) + L.t("  (Basis ", "  (base ") + Nbts.fmt(round3(inst.getBaseValue()))
                    + (inst.getModifiers().isEmpty() ? "" : ", " + inst.getModifiers().size() + L.t(" Modifikator(en)", " modifier(s)")) + ")"
                    + (untracked ? L.t("  – nicht synchronisiert", "  – not synced") : "");
            rows.add(new String[]{aid, value, untracked ? "dim" : ""});
        }
        rows.sort((a, b) -> a[0].compareTo(b[0]));
        for (String[] r : rows) {
            s.kv(r[0], r[1], r[2].isEmpty() ? Role.NUMBER : Role.DIM);
        }
        if (anyUntracked) {
            s.dim(L.t("Mehrspieler: nicht synchronisierte Attribute zeigen nur den Client-Standardwert.", "Multiplayer: attributes that are not synced only show the client default."));
        }
    }

    private static void tags(InspectData data, Ctx ctx, Entity e, @Nullable NbtCompound nbt) {
        Section s = data.section(Category.TAGS, "Tags");
        s.sub(L.t("Befehls-Tags (/tag)", "Command tags (/tag)"));
        List<String> commandTags = new ArrayList<>();
        if (ctx.source() == Ctx.Source.OP_QUERY && nbt != null) {
            for (NbtElement el : Nbts.list(nbt, "Tags")) {
                el.asString().ifPresent(commandTags::add);
            }
        } else {
            commandTags.addAll(e.getCommandTags());
        }
        commandTags.sort(null);
        if (ctx.mpClient()) {
            s.kv("Tags", Section.notSentText(), Role.WARN);
        } else if (commandTags.isEmpty()) {
            s.dim("keine");
        } else {
            for (String tag : commandTags) {
                s.kv(tag, "", Role.STRING);
            }
        }
        List<String> typeTags = Registries.ENTITY_TYPE.getEntry(e.getType()).streamTags().map(TagKey::id).map(Identifier::toString).sorted().toList();
        s.sub(L.t("Entity-Typ-Tags (", "Entity type tags (") + typeTags.size() + ")");
        if (typeTags.isEmpty()) {
            s.dim("keine");
        }
        for (String tag : typeTags) {
            s.kv("#" + tag, "", Role.DIM);
        }
    }

    // ------------------------------------------------------------ Helfer

    static String fmt3(Vec3d v) {
        return String.format(Locale.ROOT, "%.3f, %.3f, %.3f", v.x, v.y, v.z);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    @Nullable
    static UUID uuid(@Nullable NbtElement el) {
        if (el instanceof NbtIntArray a && a.size() == 4) {
            int[] v = a.getIntArray();
            return new UUID((long) v[0] << 32 | v[1] & 0xFFFFFFFFL, (long) v[2] << 32 | v[3] & 0xFFFFFFFFL);
        }
        return null;
    }

    /** Spezielle, lesbare Felder für bekannte Entity-Typen (aus der NBT gelesen). */
    static final class EntitySummaries {

        static void add(InspectData data, Ctx ctx, Entity entity, Identifier id, NbtCompound n) {
            RegistryOps<NbtElement> ops = ctx.ops();
            String path = id.getNamespace().equals("minecraft") ? id.getPath() : "";
            switch (path) {
                case "bee" -> bee(data.section(Category.SUMMARY, L.t("Biene", "Bee")), ctx, n);
                case "item" -> {
                    Section s = data.section(Category.SUMMARY, "Item-Entity");
                    ItemStack stack = Nbts.decodeStack(n.get("Item"), ops);
                    s.kv("Item", Nbts.stackLabel(stack), Role.TEXT);
                    s.kv(L.t("Alter", "Age"), Nbts.num(n, "Age") == null ? "–" : Nbts.ticks(Nbts.num(n, "Age")) + L.t(" (verschwindet bei 6000)", " (despawns at 6000)"), Role.NUMBER);
                    s.kv(L.t("Aufsammel-Verzögerung", "Pickup delay"), Nbts.num(n, "PickupDelay"));
                    uuidKv(s, ctx, L.t("Besitzer", "Owner"), n, "Owner");
                    uuidKv(s, ctx, L.t("Geworfen von", "Thrown by"), n, "Thrower");
                }
                case "villager", "wandering_trader", "zombie_villager" -> {
                    Section s = data.section(Category.SUMMARY, L.t("Dorfbewohner", "Villager"));
                    NbtCompound vd = n.getCompoundOrEmpty("VillagerData");
                    if (!vd.isEmpty()) {
                        s.kv(L.t("Beruf", "Profession"), Nbts.str(vd, "profession", "?"), Role.STRING);
                        s.kv(L.t("Stufe", "Level"), Nbts.num(vd, "level"));
                        s.kv(L.t("Biom-Typ", "Biome type"), Nbts.str(vd, "type", "?"), Role.STRING);
                    }
                    if (ctx.mpClient()) {
                        s.notSent(L.t("Handelsangebote / Erfahrung / Inventar", "Trades / experience / inventory"));
                    } else {
                        s.kv(L.t("Erfahrung", "Experience"), Nbts.num(n, "Xp"));
                        s.kv(L.t("Handelsangebote", "Trades"), Nbts.list(n.getCompoundOrEmpty("Offers"), "Recipes").size());
                        s.kv(L.t("Inventar-Stapel", "Inventory stacks"), Nbts.list(n, "Inventory").size());
                        s.kv(L.t("Gerüchte (Gossips)", "Gossips"), Nbts.list(n, "Gossips").size());
                    }
                }
                case "player" -> {
                    Section s = data.section(Category.SUMMARY, L.t("Spieler", "Player"));
                    if (ctx.mpClient() && !ctx.selfPlayer()) {
                        s.notSent(L.t("Inventar / Erfahrung / Hunger", "Inventory / experience / hunger"));
                    } else {
                        s.kv(L.t("Erfahrungsstufe", "Experience level"), Nbts.num(n, "XpLevel"));
                        s.kv(L.t("Hunger / Sättigung", "Hunger / saturation"), Nbts.num(n, "foodLevel") + " / " + Nbts.fmt(Nbts.dbl(n, "foodSaturationLevel") == null ? 0
                                : Nbts.dbl(n, "foodSaturationLevel")), Role.NUMBER);
                        s.kv(L.t("Spielmodus (ID)", "Game mode (ID)"), Nbts.num(n, "playerGameType"));
                        s.kv(L.t("Gewählter Slot", "Selected slot"), Nbts.num(n, "SelectedItemSlot"));
                        s.kv(L.t("Inventar-Stapel", "Inventory stacks"), Nbts.list(n, "Inventory").size());
                        s.kv(L.t("Punkte", "Score"), Nbts.num(n, "Score"));
                    }
                }
                default -> {
                }
            }
            // allgemeine Mob-Felder
            if (n.contains("NoAI") || n.contains("PersistenceRequired") || n.contains("LeftHanded") || n.contains("CanPickUpLoot")) {
                Section s = data.section(Category.SUMMARY, L.t("Mob-Verhalten", "Mob behavior"));
                flag(s, ctx, L.t("Keine KI (NoAI)", "No AI (NoAI)"), n, "NoAI");
                flag(s, ctx, L.t("Verschwindet nie (PersistenceRequired)", "Never despawns (PersistenceRequired)"), n, "PersistenceRequired");
                flag(s, ctx, L.t("Linkshänder", "Left-handed"), n, "LeftHanded");
                flag(s, ctx, L.t("Hebt Items auf", "Picks up items"), n, "CanPickUpLoot");
                Coord home = SpecialSummaries.coordOf(n, "home_pos");
                if (home != null) {
                    s.coord("home_pos", home.pos(), null, "home_pos");
                }
                if (n.contains("Owner")) {
                    uuidKv(s, ctx, L.t("Besitzer", "Owner"), n, "Owner");
                }
                if (n.contains("Age") && entity instanceof LivingEntity) {
                    Integer age = Nbts.num(n, "Age");
                    s.kv(L.t("Wachstum / Zucht-Abklingzeit (Age)", "Growth / breeding cooldown (Age)"), age == null ? "–" : age < 0 ? age + " (Baby)" : String.valueOf(age), Role.NUMBER);
                }
                if (n.contains("InLove")) {
                    s.kv(L.t("Verliebt (InLove)", "In love (InLove)"), Nbts.ticks(Nbts.num(n, "InLove") == null ? 0 : Nbts.num(n, "InLove")));
                }
            }
        }

        private static void bee(Section s, Ctx ctx, NbtCompound n) {
            Coord flower = SpecialSummaries.coordOf(n, "flower_pos", "FlowerPos");
            Coord hive = SpecialSummaries.coordOf(n, "hive_pos", "HivePos");
            if (flower != null) {
                s.coord("flower_pos", flower.pos(), null, "flower_pos");
            } else if (ctx.mpClient()) {
                s.notSent("flower_pos");
            } else {
                s.kv("flower_pos", L.t("keine gemerkte Blume", "no remembered flower"), Role.DIM);
            }
            if (hive != null) {
                s.coord("hive_pos", hive.pos(), null, "hive_pos");
            } else if (ctx.mpClient()) {
                s.notSent("hive_pos");
            } else {
                s.kv("hive_pos", L.t("kein Zuhause", "no home"), Role.DIM);
            }
            flag(s, ctx, "HasNectar", n, "HasNectar");
            flag(s, ctx, "HasStung", n, "HasStung");
            if (ctx.mpClient()) {
                s.notSent(L.t("Pollinations-/Nest-Zähler", "Pollination/nest counters"));
            } else {
                s.kv("CannotEnterHiveTicks", Nbts.num(n, "CannotEnterHiveTicks"));
                s.kv("TicksSincePollination", Nbts.num(n, "TicksSincePollination"));
                s.kv("CropsGrownSincePollination", Nbts.num(n, "CropsGrownSincePollination"));
            }
        }

        private static void flag(Section s, Ctx ctx, String label, NbtCompound n, String key) {
            Boolean b = Nbts.bool(n, key);
            s.kv(label, b == null ? Section.yesNo(false) : Section.yesNo(b), Role.BOOLEAN);
        }

        private static void uuidKv(Section s, Ctx ctx, String label, NbtCompound n, String key) {
            UUID uuid = uuid(n.get(key));
            if (uuid != null) {
                s.kv(label, uuid.toString(), Role.STRING);
            } else if (ctx.mpClient() && !n.contains(key)) {
                s.kv(label, "–", Role.DIM);
            }
        }

        private EntitySummaries() {
        }
    }
}
