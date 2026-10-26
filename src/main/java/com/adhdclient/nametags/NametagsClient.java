package com.adhdclient.nametags;

import com.adhdclient.extras.Draw;
import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Nametags+: große Namensschilder durch Wände mit Leben, Entfernung, Rüstungs-Haltbarkeit und Item in der Hand.
 * Zeigt auch, ob ein Totem gehalten wird (passend zu „Enemy Totems“).
 */
public final class NametagsClient {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private NametagsClient() {
    }

    public static Extras.Nametags cfg() {
        return Extras.get().nametags;
    }

    public static void init() {
        WorldRenderEvents.END_EXTRACTION.register(NametagsClient::render);
    }

    private static void render(WorldExtractionContext ctx) {
        Extras.Nametags c = cfg();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.enabled || mc.world == null || mc.player == null || mc.worldRenderer == null) return;
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            int drawn = 0;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity living) || e == mc.player || !e.isAlive()) continue;
                if (c.playersOnly && !(e instanceof PlayerEntity)) continue;
                if (++drawn > 256) break;
                Vec3d pos = e.getLerpedPos(tickDelta);
                double top = pos.y + e.getHeight() + 0.55;
                float dist = (float) pos.distanceTo(mc.player.getLerpedPos(tickDelta));
                float s = (float) c.scale;
                float lineH = (float) (0.28 * s * MathHelper.clamp(dist / 8.0, 1.0, 16.0));
                String second = secondLine(living, c);
                if (!second.isEmpty()) {
                    Draw.label(second, new Vec3d(pos.x, top, pos.z), 0xD0D0D0, ctx.camera(), s * 0.8f);
                    top += lineH;
                }
                Draw.label(firstLine(living, dist, c), new Vec3d(pos.x, top, pos.z), healthColor(living), ctx.camera(), s);
            }
        } catch (RuntimeException ignored) {
            // ein Bild ohne Schilder ist kein Problem
        }
    }

    static String firstLine(LivingEntity e, float dist, Extras.Nametags c) {
        StringBuilder sb = new StringBuilder(e.getName().getString());
        if (c.health) {
            float hp = e.getHealth() + e.getAbsorptionAmount();
            sb.append(" ").append(String.format(Locale.ROOT, "%.1f", hp / 2f)).append("❤");
        }
        if (c.distance) sb.append(" ").append(Math.round(dist)).append("m");
        return sb.toString();
    }

    static String secondLine(LivingEntity e, Extras.Nametags c) {
        StringBuilder sb = new StringBuilder();
        if (c.armor) {
            int dmg = 0, max = 0;
            for (EquipmentSlot slot : ARMOR) {
                ItemStack s = e.getEquippedStack(slot);
                if (s.isDamageable()) {
                    dmg += s.getDamage();
                    max += s.getMaxDamage();
                }
            }
            if (max > 0) sb.append("⛨ ").append(Math.round(100f * (max - dmg) / max)).append("%");
        }
        if (c.heldItem) {
            ItemStack main = e.getMainHandStack();
            if (!main.isEmpty()) {
                if (!sb.isEmpty()) sb.append("  ");
                sb.append("✋ ").append(main.getName().getString());
            }
            if (e.getOffHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING) || main.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
                sb.append("  ⚑ Totem");
            }
        }
        return sb.toString();
    }

    private static int healthColor(LivingEntity e) {
        float f = MathHelper.clamp(e.getHealth() / Math.max(1f, e.getMaxHealth()), 0f, 1f);
        if (f > 0.66f) return 0x55FF55;
        if (f > 0.33f) return 0xFFD040;
        return 0xFF5050;
    }

    public static Screen screen(Screen parent) {
        Extras.Nametags c = cfg();
        return OptionsScreen.builder("Nametags+", c)
                .toggle(L.t("Leben", "Health"), null, () -> c.health, v -> c.health = v)
                .toggle(L.t("Entfernung", "Distance"), null, () -> c.distance, v -> c.distance = v)
                .toggle(L.t("Rüstung %", "Armor %"), L.t("Haltbarkeit der ganzen Rüstung", "Durability of the whole armor"), () -> c.armor, v -> c.armor = v)
                .toggle(L.t("Item in der Hand", "Held item"), null, () -> c.heldItem, v -> c.heldItem = v)
                .toggle(L.t("Nur Spieler", "Players only"), null, () -> c.playersOnly, v -> c.playersOnly = v)
                .slider(L.t("Größe", "Size"), 0.3, 5, 0.1, () -> c.scale, v -> c.scale = v, v -> String.format(Locale.ROOT, "%.1f×", v))
                .hint(() -> L.t("Große Namensschilder durch Wände", "Big name tags through walls"))
                .build(parent);
    }
}
