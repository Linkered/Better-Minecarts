package com.linkered.betterminecarts.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets a train move each of its minecarts with the vanilla track movement and read their top speed.
 */
@Mixin(AbstractMinecart.class)
public interface AbstractMinecartInvoker {
	@Invoker("moveAlongTrack")
	void bm$moveAlongTrack(ServerLevel level);

	@Invoker("getMaxSpeed")
	double bm$getMaxSpeed(ServerLevel level);
}
