package com.adhdclient.mixin;

import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Auto Fish: hat ein Fisch angebissen? (Der Server meldet das über die Entity-Daten.) */
@Mixin(FishingBobberEntity.class)
public interface FishingBobberAccessor {
    @Accessor("caughtFish")
    boolean adhd$caughtFish();
}
