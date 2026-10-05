package com.linkered.betterminecarts.coupling;

import com.linkered.betterminecarts.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Dispensers couple and uncouple minecarts, the way they already shear sheep and cut leads, for automatic sorting
 * stations:
 * <ul>
 *     <li>a chain couples the minecart in front of the dispenser to the nearest minecart it can be coupled to;</li>
 *     <li>shears cut the coupling closest to the block in front of the dispenser and drop its chain, like a player
 *     cuts the one closest to where they click.</li>
 * </ul>
 * When there is nothing to couple or cut, the dispenser behaves as usual.
 */
public final class CouplingDispensing {
	private CouplingDispensing() {
	}

	/**
	 * Uses a chain or shears from a dispenser on the minecarts in front of it, if possible.
	 *
	 * @param source the dispenser
	 * @param stack  the stack being dispensed: a chain is shrunk by one, shears lose durability
	 * @return whether the item was used; if not, the dispenser behaves as usual
	 */
	public static boolean dispense(BlockSource source, ItemStack stack) {
		boolean chain = stack.is(ModTags.MINECART_COUPLERS);
		if (!chain && !stack.is(Items.SHEARS)) {
			return false;
		}

		ServerLevel level = source.level();
		if (!AbstractMinecart.useExperimentalMovement(level)) {
			return false;
		}

		Direction facing = source.state().getValue(DispenserBlock.FACING);
		BlockPos front = source.pos().relative(facing);
		boolean used = chain ? couple(level, front, stack) : cut(level, front);
		if (!used) {
			return false;
		}

		if (chain) {
			stack.shrink(1);
		} else {
			stack.hurtAndBreak(1, level, null, item -> {
			});
		}

		level.levelEvent(1000, source.pos(), 0);
		level.levelEvent(2000, source.pos(), facing.get3DDataValue());
		return true;
	}

	/**
	 * Couples the minecart in front, the closest one to the middle of the block if there are several, to the nearest
	 * minecart it can be coupled to.
	 */
	private static boolean couple(ServerLevel level, BlockPos front, ItemStack stack) {
		Vec3 center = Vec3.atCenterOf(front);
		AbstractMinecart first = null;
		double firstDistance = Double.MAX_VALUE;
		for (AbstractMinecart minecart : level.getEntitiesOfClass(AbstractMinecart.class, new AABB(front), Entity::isAlive)) {
			double distance = minecart.distanceToSqr(center);
			if (distance < firstDistance && Couplings.freeSlot(minecart) >= 0) {
				first = minecart;
				firstDistance = distance;
			}
		}

		if (first == null) {
			return false;
		}

		AbstractMinecart second = null;
		double secondDistance = Double.MAX_VALUE;
		AABB reach = first.getBoundingBox().inflate(Couplings.MAX_COUPLE_DISTANCE);
		for (AbstractMinecart minecart : level.getEntitiesOfClass(AbstractMinecart.class, reach, Entity::isAlive)) {
			double distance = minecart.distanceToSqr(first);
			if (distance < secondDistance && Couplings.canCouple(level, first, minecart)) {
				second = minecart;
				secondDistance = distance;
			}
		}

		if (second == null) {
			return false;
		}

		Couplings.couple(level, first, Couplings.freeSlot(first), second, stack.copyWithCount(1));
		first.gameEvent(GameEvent.ENTITY_INTERACT);
		return true;
	}

	/**
	 * Cuts the coupling of the minecarts in front whose middle is closest to the middle of the block.
	 */
	private static boolean cut(ServerLevel level, BlockPos front) {
		Vec3 center = Vec3.atCenterOf(front);
		AbstractMinecart nearest = null;
		int nearestSlot = -1;
		double nearestDistance = Double.MAX_VALUE;
		for (AbstractMinecart minecart : level.getEntitiesOfClass(AbstractMinecart.class, new AABB(front), Entity::isAlive)) {
			Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
			for (int slot = 0; slot < Couplings.SLOTS; slot++) {
				Coupling coupling = couplings[slot];
				Entity other = coupling.holder != null ? coupling.holder : Couplings.partner(level, minecart, slot);
				if (other == null && !coupling.isCoupled()) {
					continue;
				}

				// A partner that is not loaded: measure from this end, so the chain can still be cut.
				Vec3 end = other != null ? other.position() : minecart.position();
				double distance = center.distanceToSqr((minecart.getX() + end.x) * 0.5, (minecart.getY() + end.y) * 0.5, (minecart.getZ() + end.z) * 0.5);
				if (distance < nearestDistance) {
					nearest = minecart;
					nearestSlot = slot;
					nearestDistance = distance;
				}
			}
		}

		if (nearest == null) {
			return false;
		}

		Couplings.uncouple(level, nearest, nearestSlot, Couplings.Reason.SHEARS);
		return true;
	}
}
