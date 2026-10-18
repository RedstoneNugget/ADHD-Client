package com.adhdclient.inspect.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.adhdclient.inspect.data.Coord;
import com.adhdclient.inspect.data.Entry;
import com.adhdclient.inspect.data.InspectData;
import com.adhdclient.inspect.data.Section;
import com.adhdclient.inspect.inspect.Nbts;
import com.adhdclient.inspect.snbt.SnbtFormatter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** SNBT-Text, /give-Befehl und JSON-Export. */
public final class Exporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Exporter() {
    }

    /** /give @s id[komponente=wert,!entfernt] anzahl – nur die geänderten Komponenten. */
    @Nullable
    public static String giveCommand(ItemStack stack, RegistryOps<NbtElement> ops) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("/give @s ").append(Registries.ITEM.getId(stack.getItem()));
        List<String> parts = new ArrayList<>();
        for (Map.Entry<ComponentType<?>, Optional<?>> e : stack.getComponentChanges().entrySet()) {
            ComponentType<?> type = e.getKey();
            String id = Nbts.componentId(type);
            if (e.getValue().isEmpty()) {
                parts.add("!" + id);
            } else {
                NbtElement value = Nbts.encodeComponent(type, e.getValue().get(), ops);
                if (value != null) {
                    parts.add(id + "=" + value);
                }
            }
        }
        parts.sort(null);
        if (!parts.isEmpty()) {
            sb.append('[').append(String.join(",", parts)).append(']');
        }
        if (stack.getCount() > 1) {
            sb.append(' ').append(stack.getCount());
        }
        return sb.toString();
    }

    /** SNBT des Haupt-NBT; ohne NBT (z. B. Block ohne Block-Entity) der Blockstate bzw. alle Werte als Text. */
    public static String snbtText(InspectData data) {
        if (data.rawNbt != null) {
            return SnbtFormatter.format(data.rawNbt);
        }
        return plainText(data);
    }

    /** Alle sichtbaren Werte als einfacher Text. */
    public static String plainText(InspectData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(data.name.getString()).append(" (").append(data.registryId).append(")\n");
        if (data.positionText != null) {
            sb.append("Position: ").append(data.positionText).append('\n');
        }
        for (Section s : data.sections) {
            sb.append("\n== ").append(s.title).append(" ==\n");
            for (Entry e : s.entries) {
                switch (e) {
                    case Entry.Kv kv -> sb.append(kv.key()).append(kv.value().isEmpty() ? "" : ": " + kv.value()).append('\n');
                    case Entry.Note note -> sb.append(note.text()).append('\n');
                    case Entry.Sub sub -> sb.append("-- ").append(sub.title()).append(" --\n");
                    case Entry.Snbt snbt -> sb.append(SnbtFormatter.format(snbt.nbt())).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** Speichert alles als JSON-Datei in <Spielordner>/adhd_client/inspect_exports und liefert den Pfad. */
    public static Path saveJson(InspectData data) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("type", data.kind.name().toLowerCase(Locale.ROOT));
        root.addProperty("name", data.name.getString());
        root.addProperty("id", data.registryId);
        root.addProperty("namespace", data.namespace);
        root.addProperty("mod", data.modName);
        if (data.position != null) {
            JsonArray pos = new JsonArray();
            pos.add(data.position.getX());
            pos.add(data.position.getY());
            pos.add(data.position.getZ());
            root.add("block_pos", pos);
        }
        if (data.positionText != null) {
            root.addProperty("position", data.positionText);
        }
        root.addProperty("source", data.source);
        root.addProperty("exported_at", LocalDateTime.now().toString());
        if (data.giveCommand != null) {
            root.addProperty("give_command", data.giveCommand);
        }

        JsonArray sections = new JsonArray();
        for (Section s : data.sections) {
            JsonObject so = new JsonObject();
            so.addProperty("title", s.title);
            so.addProperty("category", s.category.name().toLowerCase(Locale.ROOT));
            JsonArray entries = new JsonArray();
            for (Entry e : s.entries) {
                JsonObject eo = new JsonObject();
                switch (e) {
                    case Entry.Kv kv -> {
                        eo.addProperty("key", kv.key());
                        eo.addProperty("value", kv.value());
                        if (kv.coord() != null) {
                            eo.add("coord", coordJson(kv.coord()));
                        }
                    }
                    case Entry.Note note -> eo.addProperty("note", note.text());
                    case Entry.Sub sub -> eo.addProperty("heading", sub.title());
                    case Entry.Snbt snbt -> {
                        eo.add("nbt", nbtToJson(snbt.nbt()));
                        eo.addProperty("snbt", SnbtFormatter.format(snbt.nbt()));
                    }
                }
                entries.add(eo);
            }
            so.add("entries", entries);
            sections.add(so);
        }
        root.add("sections", sections);
        if (data.rawNbt != null) {
            root.add("nbt", nbtToJson(data.rawNbt));
            root.addProperty("snbt", SnbtFormatter.format(data.rawNbt));
        }

        Path dir = FabricLoader.getInstance().getGameDir().resolve("adhd_client").resolve("inspect_exports");
        Files.createDirectories(dir);
        String base = data.kind.name().toLowerCase(Locale.ROOT) + "_" + data.registryId.replaceAll("[^A-Za-z0-9._-]", "_")
                + "_" + LocalDateTime.now().format(FILE_TIME);
        Path file = dir.resolve(base + ".json");
        for (int i = 2; Files.exists(file); i++) {
            file = dir.resolve(base + "_" + i + ".json");
        }
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        return file;
    }

    private static JsonElement coordJson(Coord c) {
        JsonArray a = new JsonArray();
        a.add(c.pos().getX());
        a.add(c.pos().getY());
        a.add(c.pos().getZ());
        return a;
    }

    private static JsonElement nbtToJson(NbtElement nbt) {
        try {
            return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, nbt);
        } catch (RuntimeException e) {
            return GSON.toJsonTree(nbt.toString());
        }
    }
}
