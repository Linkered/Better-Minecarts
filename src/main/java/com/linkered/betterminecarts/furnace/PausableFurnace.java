package com.linkered.betterminecarts.furnace;

import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;

/**
 * A furnace minecart that a powered activator rail has paused, added to every {@link MinecartFurnace} by
 * {@code MinecartFurnaceMixin}. Like a hopper minecart, it stays paused after leaving the rail until it passes over an
 * unpowered one.
 *
 * <p>A paused furnace minecart goes out and stops pushing, but keeps its fuel and the direction of its push, so it sets
 * off the same way when resumed.
 */
public interface PausableFurnace {
	static boolean isPaused(MinecartFurnace furnace) {
		return ((PausableFurnace) furnace).bm$isPaused();
	}

	boolean bm$isPaused();
}
