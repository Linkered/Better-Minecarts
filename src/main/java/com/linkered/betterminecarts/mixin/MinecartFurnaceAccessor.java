package com.linkered.betterminecarts.mixin;

import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets a tender check whether its furnace minecart is still burning, and a detector rail read its fuel.
 */
@Mixin(MinecartFurnace.class)
public interface MinecartFurnaceAccessor {
	@Accessor("fuel")
	int bm$getFuel();
}
