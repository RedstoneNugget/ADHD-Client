package com.adhdclient.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lunge Macro: den normalen Angriff (wie Linksklick) aufrufen. */
@Mixin(MinecraftClient.class)
public interface LungeAttackInvoker {
    @Invoker("doAttack")
    boolean adhd$doAttack();
}
