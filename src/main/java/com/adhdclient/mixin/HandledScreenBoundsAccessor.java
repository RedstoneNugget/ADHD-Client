package com.adhdclient.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lage des Inventar-Fensters, damit Knöpfe (Sortieren, Alles nehmen) darüber Platz finden. */
@Mixin(HandledScreen.class)
public interface HandledScreenBoundsAccessor {
    @Accessor("x")
    int adhd$getX();

    @Accessor("y")
    int adhd$getY();

    @Accessor("backgroundWidth")
    int adhd$getBackgroundWidth();
}
