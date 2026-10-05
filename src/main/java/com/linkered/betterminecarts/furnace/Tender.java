package com.linkered.betterminecarts.furnace;

import com.linkered.betterminecarts.coupling.Couplings;
import com.linkered.betterminecarts.mixin.MinecartFurnaceAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A hopper minecart coupled directly to a burning furnace minecart works as its tender: it hands over one piece of fuel
 * whenever the furnace minecart has room for it, so the train keeps going as long as the tender has fuel.
 *
 * <p>The tender only keeps a furnace minecart burning. It never lights one that has gone out, so a train that ran out
 * of fuel does not start again on its own when fuel is added to its tender.
 */
public final class Tender {
	/**
	 * How often a coupled furnace minecart checks its tenders. A piece of coal burns for 3600 ticks, so this is far
	 * more often than needed, and almost nothing is done on the other ticks.
	 */
	private static final int CHECK_INTERVAL = 20;

	private Tender() {
	}

	/**
	 * Called at the end of every server tick of a furnace minecart.
	 */
	public static void tick(ServerLevel level, MinecartFurnace furnace) {
		// The entity ID spreads the checks of different furnace minecarts over different ticks.
		if ((furnace.tickCount + furnace.getId()) % CHECK_INTERVAL != 0
			|| ((MinecartFurnaceAccessor) furnace).bm$getFuel() <= 0
			|| !Couplings.isCoupled(furnace)) {
			return;
		}

		for (int slot = 0; slot < Couplings.SLOTS; slot++) {
			if (Couplings.partner(level, furnace, slot) instanceof MinecartHopper hopper && feed(furnace, hopper)) {
				return;
			}
		}
	}

	/**
	 * Moves one piece of fuel from the hopper minecart into the furnace minecart, if both have one and room for it.
	 */
	private static boolean feed(MinecartFurnace furnace, MinecartHopper hopper) {
		for (int i = 0, size = hopper.getContainerSize(); i < size; i++) {
			ItemStack stack = hopper.getItem(i);
			if (stack.is(ItemTags.FURNACE_MINECART_FUEL)) {
				// Adding fuel also points the push away from where it came from: keep the current direction instead.
				Vec3 push = furnace.push;
				if (furnace.addFuel(furnace.position(), stack)) {
					furnace.push = push;
					hopper.removeItem(i, 1);
					return true;
				}
				// No room for it: a smaller piece in another slot may still fit (a block of coal burns longer).
			}
		}

		return false;
	}
}
