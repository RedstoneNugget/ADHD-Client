package com.adhdclient.inspect.inspect;

import com.adhdclient.gui.L;
import com.adhdclient.inspect.InspectClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/** Kleine, absturzsichere Helfer rund um NBT, Codecs und Formatierung. */
public final class Nbts {

    private Nbts() {
    }

    @Nullable
    public static NbtElement encodeStack(ItemStack stack, RegistryOps<NbtElement> ops) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
        } catch (RuntimeException e) {
            InspectClient.LOGGER.debug("Item-Stack nicht kodierbar", e);
            return null;
        }
    }

    public static ItemStack decodeStack(@Nullable NbtElement nbt, RegistryOps<NbtElement> ops) {
        if (nbt == null) {
            return ItemStack.EMPTY;
        }
        try {
            return ItemStack.CODEC.parse(ops, nbt).result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    @Nullable
    public static <T> NbtElement encodeComponent(ComponentType<T> type, @Nullable Object value, RegistryOps<NbtElement> ops) {
        if (value == null || type.shouldSkipSerialization() || type.getCodec() == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            T typed = (T) value;
            return type.getCodec().encodeStart(ops, typed).result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String componentId(ComponentType<?> type) {
        Identifier id = Registries.DATA_COMPONENT_TYPE.getId(type);
        return id != null ? id.toString() : String.valueOf(type);
    }

    /** Text-Komponente (als NBT gespeichert) → Klartext. */
    public static String text(@Nullable NbtElement nbt, RegistryOps<NbtElement> ops) {
        if (nbt == null) {
            return "";
        }
        try {
            Optional<Text> text = TextCodecs.CODEC.parse(ops, nbt).result();
            if (text.isPresent()) {
                return text.get().getString();
            }
        } catch (RuntimeException ignored) {
        }
        return nbt instanceof NbtString s ? s.value() : nbt.toString();
    }

    public static String str(NbtCompound c, String key, String fallback) {
        return c.get(key) instanceof NbtString s ? s.value() : fallback;
    }

    @Nullable
    public static Integer num(NbtCompound c, String... keys) {
        for (String k : keys) {
            if (c.get(k) instanceof AbstractNbtNumber n) {
                return n.intValue();
            }
        }
        return null;
    }

    @Nullable
    public static Double dbl(NbtCompound c, String... keys) {
        for (String k : keys) {
            if (c.get(k) instanceof AbstractNbtNumber n) {
                return n.doubleValue();
            }
        }
        return null;
    }

    @Nullable
    public static Boolean bool(NbtCompound c, String key) {
        return c.get(key) instanceof AbstractNbtNumber n ? n.byteValue() != 0 : null;
    }

    public static NbtList list(NbtCompound c, String key) {
        return c.get(key) instanceof NbtList l ? l : new NbtList();
    }

    /** "64 × Stein (minecraft:stone)", mit ✦ bei Verzauberungen. */
    public static String stackLabel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return L.t("leer", "empty");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(stack.getCount()).append(" × ").append(stack.getName().getString());
        sb.append(" (").append(Registries.ITEM.getId(stack.getItem())).append(')');
        if (stack.hasEnchantments() || stack.contains(DataComponentTypes.STORED_ENCHANTMENTS)) {
            sb.append(" ✦");
        }
        return sb.toString();
    }

    public static String modName(String namespace) {
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        try {
            return FabricLoader.getInstance().getModContainer(namespace)
                    .map(c -> c.getMetadata().getName())
                    .orElse(namespace + L.t(" (Mod nicht gefunden – evtl. Datapack/Server)", " (mod not found – maybe a datapack/server)"));
        } catch (RuntimeException e) {
            return namespace;
        }
    }

    public static String ticks(long ticks) {
        return ticks + L.t(" Ticks (", " ticks (") + fmt(ticks / 20.0) + " s)";
    }

    public static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return String.valueOf(v);
        }
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    public static String pct(double part, double whole) {
        if (whole <= 0) {
            return "–";
        }
        return fmt(Math.round(part / whole * 1000.0) / 10.0) + " %";
    }
}
