package com.linkered.betterminecarts.coupling;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Coupling data added to every {@link AbstractMinecart} by {@code AbstractMinecartMixin}.
 *
 * <p>The server-side state lives in {@link #bm$couplings()}. A copy of what clients need (who each end is coupled to
 * and which chain to draw) is kept in synced entity data, so it reaches clients only when it changes.
 */
public interface CoupledMinecart {
	static CoupledMinecart of(AbstractMinecart minecart) {
		return (CoupledMinecart) minecart;
	}

	/**
	 * The two coupling slots of this minecart, one per end. Server only.
	 */
	Coupling[] bm$couplings();

	/**
	 * The game time of the last tick in which this minecart was moved as part of a train.
	 */
	long bm$trainTick();

	void bm$setTrainTick(long gameTime);

	/**
	 * The entity ID of the minecart that moved this minecart's train in {@link #bm$trainTick()}, identifying the train.
	 */
	int bm$trainId();

	void bm$setTrainId(int trainId);

	/**
	 * The cached rail path to the minecart in front of this one in its train.
	 */
	PathCache bm$pathCache();

	/**
	 * The network ID of the entity the given slot is coupled to (or held by), on both sides.
	 */
	OptionalInt bm$syncedPartner(int slot);

	/**
	 * The chain block to draw from the given slot, present only on the minecart that owns the coupling.
	 */
	Optional<BlockState> bm$syncedChain(int slot);

	void bm$setSynced(int slot, OptionalInt partner, Optional<BlockState> chain);
}
