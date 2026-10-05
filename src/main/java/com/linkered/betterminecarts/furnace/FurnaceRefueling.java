package com.linkered.betterminecarts.furnace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Refueling furnace minecarts with a dispenser, the way dispensers already put saddles or armor on mobs.
 */
public final class FurnaceRefueling {
	private FurnaceRefueling() {
	}

	/**
	 * Puts one piece of fuel from a dispenser into a furnace minecart in front of it, if there is one with room for it.
	 * A furnace minecart that was already pushing, or still rolling after running out of fuel, keeps going the same
	 * way; a stopped one is pushed away from the dispenser, as when a player refuels it.
	 *
	 * @param source the dispenser
	 * @param stack  the stack being dispensed, shrunk by one on success
	 * @return whether a furnace minecart took the fuel; if not, the dispenser behaves as usual
	 */
	public static boolean dispense(BlockSource source, ItemStack stack) {
		if (!stack.is(ItemTags.FURNACE_MINECART_FUEL)) {
			return false;
		}

		ServerLevel level = source.level();
		Direction facing = source.state().getValue(DispenserBlock.FACING);
		BlockPos front = source.pos().relative(facing);
		Vec3 center = source.center();
		for (MinecartFurnace furnace : level.getEntitiesOfClass(MinecartFurnace.class, new AABB(front), Entity::isAlive)) {
			Vec3 push = furnace.push;
			if (furnace.addFuel(center, stack)) {
				if (push.lengthSqr() > 1.0E-7) {
					furnace.push = push;
				} else {
					Vec3 movement = furnace.getDeltaMovement();
					if (movement.horizontalDistanceSqr() > 1.0E-4) {
						// Out of fuel but still rolling: the dispenser is usually beside the rail and slightly behind
						// or ahead of the minecart, so pushing away from it could send the minecart back.
						furnace.push = movement.horizontal().normalize();
					}
				}

				stack.shrink(1);
				level.levelEvent(1000, source.pos(), 0);
				level.levelEvent(2000, source.pos(), facing.get3DDataValue());
				return true;
			}
		}

		return false;
	}
}
