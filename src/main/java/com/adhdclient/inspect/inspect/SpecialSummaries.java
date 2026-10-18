package com.adhdclient.inspect.inspect;

import com.adhdclient.gui.L;
import com.adhdclient.inspect.data.Category;
import com.adhdclient.inspect.data.Coord;
import com.adhdclient.inspect.data.InspectData;
import com.adhdclient.inspect.data.Role;
import com.adhdclient.inspect.data.Section;
import com.adhdclient.inspect.snbt.SnbtTree;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryOps;
import net.minecraft.state.property.Property;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Lesbare Zusammenfassungen für bekannte Block-Entities (oben im Panel);
 * das Roh-SNBT steht immer darunter. Alles wird aus NBT gelesen, damit
 * dieselbe Logik für Client-, Server- und Item-Daten funktioniert.
 */
public final class SpecialSummaries {

    public static final int MAX_BEES = 3;
    public static final int MAX_HONEY = BeehiveBlock.FULL_HONEY_LEVEL;

    private SpecialSummaries() {
    }

    /** Fehlt ein Feld, weil der Server es nicht schickt? (nur bei Client-Daten auf Servern) */
    private record Avail(boolean mpClient, Set<String> notSent, NbtCompound nbt) {
        boolean missing(String key) {
            return mpClient && (notSent.contains(key) || !nbt.contains(key));
        }
    }

    /**
     * @param beId     Block-Entity-Typ, z. B. "minecraft:chest" (oder null)
     * @param nbt      Block-Entity-NBT (bei Mehrspieler bereits auf übertragene Felder gefiltert)
     * @param notSent  Felder, die der Server nicht übertragen hat
     * @param invSize  Inventargröße, falls bekannt
     */
    public static void block(InspectData data, @Nullable BlockState state, @Nullable String beId, @Nullable NbtCompound nbt,
                             Set<String> notSent, @Nullable Integer invSize, boolean mpClient, RegistryOps<NbtElement> ops) {
        if (nbt == null) {
            nbt = new NbtCompound();
        }
        Avail a = new Avail(mpClient, notSent, nbt);
        String id = beId != null ? beId : "";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;

        if (state != null && state.getBlock() instanceof BeehiveBlock || path.equals("beehive")) {
            Section s = data.section(Category.SUMMARY, L.t("Bienennest / Bienenstock", "Bee nest / beehive"));
            Integer honey = state != null && state.contains(BeehiveBlock.HONEY_LEVEL) ? state.get(BeehiveBlock.HONEY_LEVEL) : null;
            hive(s, honey, a.missing("bees") ? null : Nbts.list(nbt, "bees"), a.missing("bees"));
            return;
        }
        switch (path) {
            case "mob_spawner" -> spawner(data.section(Category.SUMMARY, "Spawner"), a, ops);
            case "trial_spawner" -> trialSpawner(data.section(Category.SUMMARY, L.t("Trial-Spawner", "Trial spawner")), a);
            case "sign", "hanging_sign" -> sign(data.section(Category.SUMMARY, path.equals("sign") ? L.t("Schild", "Sign") : L.t("Hängeschild", "Hanging sign")), a, ops);
            case "command_block" -> commandBlock(data.section(Category.SUMMARY, L.t("Kommandoblock", "Command block")), state, a, ops);
            case "lectern" -> lectern(data.section(Category.SUMMARY, L.t("Lesepult", "Lectern")), state, a, ops);
            case "brewing_stand" -> brewing(data.section(Category.SUMMARY, L.t("Braustand", "Brewing stand")), a, ops);
            case "furnace", "blast_furnace", "smoker" -> furnace(data.section(Category.SUMMARY,
                    path.equals("furnace") ? L.t("Ofen", "Furnace") : path.equals("smoker") ? L.t("Räucherofen", "Smoker") : L.t("Schmelzofen", "Blast furnace")), state, a, ops);
            case "beacon" -> beacon(data.section(Category.SUMMARY, L.t("Leuchtfeuer", "Beacon")), a);
            case "jukebox" -> jukebox(data.section(Category.SUMMARY, L.t("Plattenspieler", "Jukebox")), a, ops);
            default -> {
                if (nbt.contains("Items") || invSize != null && invSize > 0 || notSent.contains("Items")) {
                    container(data.section(Category.SUMMARY, "Container"), a, invSize, ops);
                }
            }
        }
    }

    // ------------------------------------------------------------ Bienennest

    /** Bienennest/-stock: gemeinsam für Block (NBT "bees") und Item (Komponente minecraft:bees). */
    public static void hive(Section s, @Nullable Integer honey, @Nullable NbtList bees, boolean beesNotSent) {
        s.kv("Bees", (bees == null ? "?" : String.valueOf(bees.size())) + " / " + MAX_BEES, Role.NUMBER);
        s.kv("Honey", (honey == null ? "?" : String.valueOf(honey)) + " / " + MAX_HONEY, Role.NUMBER);
        if (beesNotSent || bees == null) {
            s.kv(L.t("Bienen-Daten", "Bee data"), Section.notSentText(), Role.WARN);
            s.dim(L.t("Vanilla-Server schicken die Bienen eines platzierten Nests nicht an den Client.", "Vanilla servers do not send the bees of a placed nest to the client."));
            s.dim(L.t("Als Item (abgebaut mit Behutsamkeit) sind die Daten vollständig sichtbar.", "As an item (mined with Silk Touch) the data is fully visible."));
            return;
        }
        if (bees.isEmpty()) {
            s.dim(L.t("Keine Bienen enthalten.", "No bees inside."));
        }
        for (int i = 0; i < bees.size(); i++) {
            if (!(bees.get(i) instanceof NbtCompound bee)) {
                continue;
            }
            NbtCompound entity = bee.getCompoundOrEmpty("entity_data");
            String type = Nbts.str(entity, "id", "minecraft:bee");
            s.sub(L.t("Biene ", "Bee ") + (i + 1) + (type.equals("minecraft:bee") ? "" : " (" + type + ")"));
            Coord flower = coordOf(entity, "flower_pos", "FlowerPos");
            if (flower != null) {
                s.coord("flower_pos", flower.pos(), null, L.t("flower_pos (Biene ", "flower_pos (bee ") + (i + 1) + ")");
            } else {
                s.kv("flower_pos", L.t("keine gemerkte Blume", "no remembered flower"), Role.DIM);
            }
            Boolean nectar = Nbts.bool(entity, "HasNectar");
            s.kv("HasNectar", nectar == null ? Section.yesNo(false) : Section.yesNo(nectar), Role.BOOLEAN);
            s.kv("ticks_in_hive", Nbts.num(bee, "ticks_in_hive", "TicksInHive"));
            s.kv("min_ticks_in_hive", Nbts.num(bee, "min_ticks_in_hive", "MinOccupationTicks"));
            Double health = Nbts.dbl(entity, "Health");
            if (health != null) {
                s.kv("Health", Nbts.fmt(health));
            }
            Coord hive = coordOf(entity, "hive_pos", "HivePos");
            if (hive != null) {
                s.coord("hive_pos", hive.pos(), null, L.t("hive_pos (Biene ", "hive_pos (bee ") + (i + 1) + ")");
            }
        }
    }

    @Nullable
    static Coord coordOf(NbtCompound c, String... keys) {
        for (String k : keys) {
            NbtElement el = c.get(k);
            if (el != null) {
                Coord coord = SnbtTree.detectCoord(k, el, null);
                if (coord != null) {
                    return coord;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ Spawner

    private static void spawner(Section s, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (a.missing("SpawnData") && a.missing("Delay")) {
            s.kv(L.t("Spawner-Daten", "Spawner data"), Section.notSentText(), Role.WARN);
            return;
        }
        NbtCompound spawnData = n.getCompoundOrEmpty("SpawnData");
        String entity = Nbts.str(spawnData.getCompoundOrEmpty("entity"), "id", "");
        s.kv("Entity", entity.isEmpty() ? L.t("keine (leerer Spawner)", "none (empty spawner)") : entity, entity.isEmpty() ? Role.DIM : Role.STRING);
        Integer delay = Nbts.num(n, "Delay");
        s.kv(L.t("Nächster Spawn in", "Next spawn in"), delay == null ? "–" : Nbts.ticks(delay));
        s.kv(L.t("Min. Verzögerung", "Min delay"), ticksOrDash(Nbts.num(n, "MinSpawnDelay")));
        s.kv(L.t("Max. Verzögerung", "Max delay"), ticksOrDash(Nbts.num(n, "MaxSpawnDelay")));
        s.kv(L.t("Anzahl pro Spawn", "Count per spawn"), Nbts.num(n, "SpawnCount"));
        s.kv(L.t("Max. Entities in der Nähe", "Max nearby entities"), Nbts.num(n, "MaxNearbyEntities"));
        s.kv(L.t("Spieler-Reichweite", "Player range"), blocks(Nbts.num(n, "RequiredPlayerRange")));
        s.kv(L.t("Spawn-Radius", "Spawn radius"), blocks(Nbts.num(n, "SpawnRange")));
        if (spawnData.contains("custom_spawn_rules")) {
            s.kv(L.t("Eigene Spawn-Regeln", "Custom spawn rules"), spawnData.get("custom_spawn_rules").toString(), Role.TEXT);
        }
        if (spawnData.contains("equipment")) {
            s.kv(L.t("Ausrüstung (Loot-Table)", "Equipment (loot table)"), spawnData.get("equipment").toString(), Role.TEXT);
        }
        if (a.missing("SpawnPotentials")) {
            s.notSent(L.t("Spawn-Potenziale", "Spawn potentials"));
        } else {
            NbtList potentials = Nbts.list(n, "SpawnPotentials");
            s.kv(L.t("Spawn-Potenziale", "Spawn potentials"), potentials.size());
            for (NbtElement e : potentials) {
                if (e instanceof NbtCompound p) {
                    String id = Nbts.str(p.getCompoundOrEmpty("data").getCompoundOrEmpty("entity"), "id", "?");
                    s.kv("  • " + id, L.t("Gewicht ", "Weight ") + Nbts.num(p, "weight"), Role.NUMBER);
                }
            }
        }
    }

    private static void trialSpawner(Section s, Avail a) {
        NbtCompound n = a.nbt;
        s.kv(L.t("Spieler-Reichweite", "Player range"), blocks(Nbts.num(n, "required_player_range")));
        s.kv(L.t("Spawn-Radius", "Spawn radius"), blocks(Nbts.num(n, "spawn_range")));
        s.kv(L.t("Abklingzeit", "Cooldown"), ticksOrDash(Nbts.num(n, "target_cooldown_length")));
        s.kv(L.t("Normale Konfiguration", "Normal config"), Nbts.str(n, "normal_config", "–"));
        s.kv(L.t("Ominöse Konfiguration", "Ominous config"), Nbts.str(n, "ominous_config", "–"));
        if (a.missing("registered_players")) {
            s.notSent(L.t("Registrierte Spieler", "Registered players"));
        } else {
            s.kv(L.t("Registrierte Spieler", "Registered players"), Nbts.list(n, "registered_players").size());
            s.kv(L.t("Aktive Mobs", "Active mobs"), Nbts.list(n, "current_mobs").size());
        }
    }

    // ------------------------------------------------------------ Container

    private static void container(Section s, Avail a, @Nullable Integer size, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (n.contains("LootTable")) {
            s.kv(L.t("Loot-Table (noch nicht generiert)", "Loot table (not generated yet)"), Nbts.str(n, "LootTable", "?"), Role.STRING);
            if (n.contains("LootTableSeed")) {
                s.kv(L.t("Loot-Seed", "Loot seed"), String.valueOf(n.get("LootTableSeed")), Role.NUMBER);
            }
        }
        if (a.missing("Items")) {
            s.kv(L.t("Inhalt", "Contents"), Section.notSentText(), Role.WARN);
            s.dim(L.t("Vanilla-Server schicken Container-Inhalte erst, wenn du den Container öffnest.", "Vanilla servers only send container contents when you open the container."));
            s.dim(L.t("Tipp: Container öffnen und mit ", "Tip: open the container and press ")
                    + com.adhdclient.gui.HotKey.name(com.adhdclient.inspect.config.InspectConfig.get().openKey)
                    + L.t(" das Item unter dem Mauszeiger prüfen.", " to inspect the item under the mouse cursor."));
        } else {
            items(s, Nbts.list(n, "Items"), size, null, ops);
        }
        if (n.contains("lock")) {
            s.kv(L.t("Schloss", "Lock"), String.valueOf(n.get("lock")), Role.TEXT);
        }
        if (n.contains("CustomName")) {
            s.kv(L.t("Eigener Name", "Custom name"), Nbts.text(n.get("CustomName"), ops), Role.STRING);
        }
    }

    /** Listet ein NBT-Inventar ({Slot, id, count, components}) auf. */
    static void items(Section s, NbtList items, @Nullable Integer size, @Nullable String[] slotNames, RegistryOps<NbtElement> ops) {
        int filled = 0;
        long total = 0;
        for (NbtElement e : items) {
            ItemStack stack = Nbts.decodeStack(e, ops);
            if (!stack.isEmpty()) {
                filled++;
                total += stack.getCount();
            }
        }
        s.kv(L.t("Belegte Slots", "Used slots"), filled + (size != null && size > 0 ? " / " + size : ""), Role.NUMBER);
        s.kv(L.t("Items gesamt", "Items total"), total);
        if (filled == 0) {
            s.dim("leer");
        }
        for (NbtElement e : items) {
            if (!(e instanceof NbtCompound c)) {
                continue;
            }
            ItemStack stack = Nbts.decodeStack(c, ops);
            if (stack.isEmpty()) {
                continue;
            }
            Integer slot = Nbts.num(c, "Slot", "slot");
            String name = slot == null ? "Slot ?" : slotNames != null && slot >= 0 && slot < slotNames.length
                    ? slotNames[slot] : "Slot " + slot;
            s.kv(name, Nbts.stackLabel(stack), Role.TEXT);
        }
    }

    // ------------------------------------------------------------ Schild

    private static void sign(Section s, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (a.missing("front_text") && a.missing("back_text")) {
            s.kv("Text", Section.notSentText(), Role.WARN);
            return;
        }
        side(s, L.t("Vorderseite", "Front"), n.getCompoundOrEmpty("front_text"), ops);
        side(s, L.t("Rückseite", "Back"), n.getCompoundOrEmpty("back_text"), ops);
        Boolean waxed = Nbts.bool(n, "is_waxed");
        s.kv(L.t("Gewachst", "Waxed"), waxed == null ? Section.yesNo(false) : Section.yesNo(waxed), Role.BOOLEAN);
    }

    private static void side(Section s, String title, NbtCompound text, RegistryOps<NbtElement> ops) {
        s.sub(title);
        NbtList messages = Nbts.list(text, "messages");
        for (int i = 0; i < Math.max(4, messages.size()); i++) {
            String line = i < messages.size() ? Nbts.text(messages.get(i), ops) : "";
            s.kv(L.t("Zeile ", "Line ") + (i + 1), line.isEmpty() ? L.t("(leer)", "(empty)") : line, line.isEmpty() ? Role.DIM : Role.STRING);
        }
        s.kv(L.t("Farbe", "Color"), Nbts.str(text, "color", "black"), Role.STRING);
        Boolean glow = Nbts.bool(text, "has_glowing_text");
        s.kv(L.t("Leuchttinte", "Glowing ink"), glow == null ? Section.yesNo(false) : Section.yesNo(glow), Role.BOOLEAN);
    }

    // ------------------------------------------------------------ Kommandoblock

    private static void commandBlock(Section s, @Nullable BlockState state, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (state != null) {
            String block = state.getBlock().getTranslationKey();
            s.kv(L.t("Typ", "Type"), block.contains("repeating") ? L.t("Wiederholen", "Repeat") : block.contains("chain") ? L.t("Kette", "Chain") : L.t("Impuls", "Impulse"), Role.TEXT);
            Boolean conditional = propertyBool(state, "conditional");
            if (conditional != null) {
                s.kv(L.t("Bedingt", "Conditional"), Section.yesNo(conditional), Role.BOOLEAN);
            }
            String facing = propertyString(state, "facing");
            if (facing != null) {
                s.kv(L.t("Richtung", "Direction"), facing, Role.STRING);
            }
        }
        if (a.missing("Command")) {
            s.notSent(L.t("Befehl", "Command"));
            s.dim(L.t("Vanilla-Server schicken Befehle erst, wenn ein OP den Kommandoblock öffnet.", "Vanilla servers only send commands when an OP opens the command block."));
        } else {
            String cmd = Nbts.str(n, "Command", "");
            s.kv(L.t("Befehl", "Command"), cmd.isEmpty() ? L.t("(leer)", "(empty)") : cmd, cmd.isEmpty() ? Role.DIM : Role.STRING);
        }
        boolKv(s, L.t("Immer aktiv (auto)", "Always active (auto)"), a, "auto");
        boolKv(s, L.t("Mit Strom (powered)", "Powered"), a, "powered");
        boolKv(s, L.t("Bedingung erfüllt", "Condition met"), a, "conditionMet");
        boolKv(s, L.t("Ausgabe verfolgen", "Track output"), a, "TrackOutput");
        if (a.missing("SuccessCount")) {
            s.notSent(L.t("Erfolgszähler", "Success count"));
        } else {
            s.kv(L.t("Erfolgszähler", "Success count"), Nbts.num(n, "SuccessCount"));
        }
        if (n.contains("LastOutput")) {
            s.kv(L.t("Letzte Ausgabe", "Last output"), Nbts.text(n.get("LastOutput"), ops), Role.STRING);
        }
        if (n.contains("LastExecution")) {
            s.kv(L.t("Letzte Ausführung (Weltzeit)", "Last execution (world time)"), String.valueOf(n.get("LastExecution")), Role.NUMBER);
        }
        if (n.contains("CustomName")) {
            s.kv("Name", Nbts.text(n.get("CustomName"), ops), Role.STRING);
        }
    }

    private static void boolKv(Section s, String label, Avail a, String key) {
        if (a.missing(key)) {
            s.notSent(label);
        } else {
            Boolean b = Nbts.bool(a.nbt, key);
            s.kv(label, b == null ? Section.yesNo(false) : Section.yesNo(b), Role.BOOLEAN);
        }
    }

    // ------------------------------------------------------------ Lesepult

    private static void lectern(Section s, @Nullable BlockState state, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        Boolean hasBook = state != null ? propertyBool(state, "has_book") : null;
        if (hasBook != null) {
            s.kv(L.t("Buch eingelegt", "Book inside"), Section.yesNo(hasBook), Role.BOOLEAN);
        }
        Boolean powered = state != null ? propertyBool(state, "powered") : null;
        if (powered != null) {
            s.kv(L.t("Redstone-Impuls", "Redstone pulse"), Section.yesNo(powered), Role.BOOLEAN);
        }
        if (a.missing("Book")) {
            if (hasBook == null || hasBook) {
                s.notSent(L.t("Buch", "Book"));
            }
            return;
        }
        ItemStack book = Nbts.decodeStack(n.get("Book"), ops);
        if (book.isEmpty()) {
            s.dim(L.t("Kein Buch.", "No book."));
            return;
        }
        Integer page = Nbts.num(n, "Page");
        bookInfo(s, book, page);
    }

    static void bookInfo(Section s, ItemStack book, @Nullable Integer page) {
        s.kv(L.t("Buch", "Book"), Nbts.stackLabel(book), Role.TEXT);
        WrittenBookContentComponent written = book.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        WritableBookContentComponent writable = book.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
        int pages = 0;
        String first = "";
        if (written != null) {
            s.kv(L.t("Titel", "Title"), written.title().raw(), Role.STRING);
            s.kv(L.t("Autor", "Author"), written.author(), Role.STRING);
            s.kv("Generation", generationName(written.generation()), Role.TEXT);
            pages = written.pages().size();
            if (pages > 0) {
                first = written.pages().get(0).raw().getString();
            }
        } else if (writable != null) {
            pages = writable.pages().size();
            if (pages > 0) {
                first = writable.pages().get(0).raw();
            }
        }
        s.kv(L.t("Seiten", "Pages"), pages);
        if (page != null) {
            s.kv(L.t("Aufgeschlagen", "Open at"), L.t("Seite ", "Page ") + (page + 1) + (pages > 0 ? " / " + pages : ""), Role.NUMBER);
        }
        if (!first.isEmpty()) {
            String preview = first.replace('\n', ' ');
            s.kv(L.t("Seite 1", "Page 1"), preview.length() > 160 ? preview.substring(0, 160) + " …" : preview, Role.STRING);
        }
    }

    private static String generationName(int generation) {
        return switch (generation) {
            case 0 -> "Original";
            case 1 -> L.t("Kopie des Originals", "Copy of original");
            case 2 -> L.t("Kopie einer Kopie", "Copy of a copy");
            default -> L.t("Zerfleddert (", "Tattered (") + generation + ")";
        };
    }

    // ------------------------------------------------------------ Braustand

    private static final String[] BREWING_SLOTS = {L.t("Flasche links", "Bottle left"), L.t("Flasche Mitte", "Bottle middle"), L.t("Flasche rechts", "Bottle right"), L.t("Zutat", "Ingredient"), L.t("Brennstoff", "Fuel")};

    private static void brewing(Section s, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        if (a.missing("BrewTime")) {
            s.notSent(L.t("Braufortschritt", "Brewing progress"));
        } else {
            int brew = Nbts.num(n, "BrewTime") == null ? 0 : Nbts.num(n, "BrewTime");
            s.kv(L.t("Braufortschritt", "Brewing progress"), brew <= 0 ? L.t("braut nicht", "not brewing") : L.t("noch ", "remaining ") + Nbts.ticks(brew) + " (" + Nbts.pct(400 - brew, 400) + ")",
                    brew <= 0 ? Role.DIM : Role.NUMBER);
        }
        if (a.missing("Fuel")) {
            s.notSent(L.t("Brennstoff-Ladung", "Fuel charge"));
        } else {
            s.kv(L.t("Brennstoff-Ladung", "Fuel charge"), (Nbts.num(n, "Fuel") == null ? 0 : Nbts.num(n, "Fuel")) + " / 20", Role.NUMBER);
        }
        if (a.missing("Items")) {
            s.kv(L.t("Inhalt", "Contents"), Section.notSentText(), Role.WARN);
        } else {
            items(s, Nbts.list(n, "Items"), 5, BREWING_SLOTS, ops);
        }
    }

    // ------------------------------------------------------------ Ofen

    private static final String[] FURNACE_SLOTS = {L.t("Zutat", "Ingredient"), L.t("Brennstoff", "Fuel"), L.t("Ergebnis", "Result")};

    private static void furnace(Section s, @Nullable BlockState state, Avail a, RegistryOps<NbtElement> ops) {
        NbtCompound n = a.nbt;
        Boolean lit = state != null ? propertyBool(state, "lit") : null;
        if (lit != null) {
            s.kv(L.t("Brennt", "Burning"), Section.yesNo(lit), Role.BOOLEAN);
        }
        Integer spent = Nbts.num(n, "cooking_time_spent", "CookTime");
        Integer total = Nbts.num(n, "cooking_total_time", "CookTimeTotal");
        Integer litLeft = Nbts.num(n, "lit_time_remaining", "BurnTime");
        Integer litTotal = Nbts.num(n, "lit_total_time");
        if (a.missing("cooking_time_spent") && a.missing("CookTime")) {
            s.notSent(L.t("Fortschritt", "Progress"));
        } else {
            s.kv(L.t("Fortschritt", "Progress"), (spent == null ? 0 : spent) + " / " + (total == null ? "?" : total) + L.t(" Ticks (", " ticks (")
                    + Nbts.pct(spent == null ? 0 : spent, total == null ? 0 : total) + ")", Role.NUMBER);
        }
        if (a.missing("lit_time_remaining") && a.missing("BurnTime")) {
            s.notSent(L.t("Restbrennzeit", "Remaining burn time"));
        } else {
            s.kv(L.t("Restbrennzeit", "Remaining burn time"), (litLeft == null ? 0 : litLeft) + (litTotal != null ? " / " + litTotal : "") + L.t(" Ticks", " ticks"), Role.NUMBER);
        }
        if (a.missing("Items")) {
            s.kv(L.t("Inhalt", "Contents"), Section.notSentText(), Role.WARN);
        } else {
            items(s, Nbts.list(n, "Items"), 3, FURNACE_SLOTS, ops);
        }
        if (a.missing("RecipesUsed")) {
            s.notSent(L.t("Benutzte Rezepte (für XP)", "Used recipes (for XP)"));
        } else {
            NbtCompound used = n.getCompoundOrEmpty("RecipesUsed");
            int count = 0;
            for (String key : used.getKeys()) {
                Integer v = Nbts.num(used, key);
                count += v == null ? 0 : v;
            }
            s.kv(L.t("Geschmolzen seit Leerung (XP ausstehend)", "Smelted since emptied (XP pending)"), count + " × in " + used.getSize() + L.t(" Rezept(en)", " recipe(s)"), Role.NUMBER);
        }
    }

    // ------------------------------------------------------------ weitere

    private static void beacon(Section s, Avail a) {
        NbtCompound n = a.nbt;
        s.kv(L.t("Stufe (Pyramide)", "Level (pyramid)"), a.missing("Levels") ? Section.notSentText() : String.valueOf(Nbts.num(n, "Levels")),
                a.missing("Levels") ? Role.WARN : Role.NUMBER);
        s.kv(L.t("Primärer Effekt", "Primary effect"), Nbts.str(n, "primary_effect", "keiner"), Role.STRING);
        s.kv(L.t("Sekundärer Effekt", "Secondary effect"), Nbts.str(n, "secondary_effect", "keiner"), Role.STRING);
    }

    private static void jukebox(Section s, Avail a, RegistryOps<NbtElement> ops) {
        if (a.missing("RecordItem")) {
            s.notSent(L.t("Schallplatte", "Music disc"));
            return;
        }
        ItemStack disc = Nbts.decodeStack(a.nbt.get("RecordItem"), ops);
        s.kv(L.t("Schallplatte", "Music disc"), disc.isEmpty() ? "keine" : Nbts.stackLabel(disc), Role.TEXT);
        Integer ticks = Nbts.num(a.nbt, "ticks_since_song_started");
        if (ticks != null) {
            s.kv(L.t("Spielt seit", "Playing for"), Nbts.ticks(ticks));
        }
    }

    // ------------------------------------------------------------ Blockstate-Helfer

    @Nullable
    static Boolean propertyBool(BlockState state, String name) {
        String v = propertyString(state, name);
        return v == null ? null : Boolean.parseBoolean(v);
    }

    @Nullable
    static String propertyString(BlockState state, String name) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name)) {
                return valueName(state, p);
            }
        }
        return null;
    }

    static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.name(state.get(property));
    }

    private static String ticksOrDash(@Nullable Integer ticks) {
        return ticks == null ? "–" : Nbts.ticks(ticks);
    }

    private static String blocks(@Nullable Integer n) {
        return n == null ? "–" : n + L.t(" Blöcke", " blocks");
    }
}
