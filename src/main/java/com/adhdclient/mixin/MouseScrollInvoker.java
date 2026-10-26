package com.adhdclient.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Auto Totem: ein echtes Mausrad-Scroll-Ereignis auslösen, genau wie Minecraft es beim Scrollen selbst tut. */
@Mixin(Mouse.class)
public interface MouseScrollInvoker {
    @Invoker("onMouseScroll")
    void adhd$onMouseScroll(long window, double horizontal, double vertical);
}
