package com.linkered.betterminecarts.mixin;

import com.linkered.betterminecarts.coupling.TrainPhysics;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hands the movement of coupled minecarts over to their train.
 */
@Mixin(NewMinecartBehavior.class)
abstract class NewMinecartBehaviorMixin {
	@WrapOperation(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;moveAlongTrack(Lnet/minecraft/server/level/ServerLevel;)V")
	)
	private void moveAsTrain(AbstractMinecart minecart, ServerLevel level, Operation<Void> original) {
		if (!TrainPhysics.moveAsTrain(level, minecart)) {
			original.call(minecart, level);
		}
	}
}
