package com.linkered.betterminecarts.coupling;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;

/**
 * The rail path from a minecart to the one in front of it in its train, kept while neither of them changes rail
 * block. Only the part inside their own blocks changes as they move, and that is plain arithmetic.
 */
public final class PathCache {
	long fromBlock;
	long toBlock;
	@Nullable RailShape fromShape;
	@Nullable RailShape toShape;
	/**
	 * Exit of this minecart's rail that leads to the minecart in front, or {@code null} if no rail path connects them.
	 */
	@Nullable Direction exit;
	/**
	 * Exit of the front minecart's rail through which the path arrives.
	 */
	Direction arrival = Direction.NORTH;
	/**
	 * Length of the rails between both blocks.
	 */
	double between;
	boolean valid;

	boolean matches(long fromBlock, RailShape fromShape, long toBlock, RailShape toShape) {
		return valid && this.fromBlock == fromBlock && this.toBlock == toBlock && this.fromShape == fromShape && this.toShape == toShape;
	}
}
