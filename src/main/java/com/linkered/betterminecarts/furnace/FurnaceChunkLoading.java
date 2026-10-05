package com.linkered.betterminecarts.furnace;

import com.linkered.betterminecarts.BetterMinecarts;
import com.linkered.betterminecarts.mixin.MinecartFurnaceAccessor;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.ChunkPos;

/**
 * A burning furnace minecart keeps the chunks around it loaded, the way a thrown ender pearl does, so it does not stop
 * at the edge of the chunks loaded by players. In a train, every minecart of the train does, so the train can keep
 * moving as a whole.
 *
 * <p>Each minecart holds a short ticket on the chunk it is in, renewed while it stays there and moved when it enters
 * another chunk. Like the ender pearl ticket, it makes that chunk tick entities, the chunks around it tick blocks and
 * a ring around those load, and it is not saved: once the furnace minecart runs out of fuel or is paused, the tickets
 * run out within two seconds, and after a restart a train far from players waits for one to come by. It also keeps
 * the dimension running without players in it, like ender pearls.
 *
 * <p>Checking a minecart costs two comparisons per tick: the ticket is only renewed when the minecart changes chunk
 * or the ticket is about to run out, and minecarts in the same chunk share one ticket.
 */
public final class FurnaceChunkLoading {
	/**
	 * Same timeout and flags as {@link TicketType#ENDER_PEARL}: loads and ticks chunks, keeps the dimension active and
	 * is not saved.
	 */
	public static final TicketType TICKET = Registry.register(
		BuiltInRegistries.TICKET_TYPE,
		BetterMinecarts.id("furnace_minecart"),
		new TicketType(40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE)
	);
	/**
	 * Ticket radius of an ender pearl: the smallest one that makes the chunk in the middle tick entities.
	 */
	private static final int RADIUS = 2;
	/**
	 * How often a ticket is renewed while its minecart stays in the same chunk: half the timeout, so a minecart that
	 * misses a few ticks keeps its chunks.
	 */
	private static final long RENEW_INTERVAL = TICKET.timeout() / 2;

	private FurnaceChunkLoading() {
	}

	/**
	 * Registers the ticket type.
	 */
	public static void init() {
	}

	/**
	 * Whether a furnace minecart is burning: it has fuel and is not paused.
	 */
	public static boolean isBurning(MinecartFurnace furnace) {
		return ((MinecartFurnaceAccessor) furnace).bm$getFuel() > 0 && !PausableFurnace.isPaused(furnace);
	}

	/**
	 * Keeps the chunks around a minecart loaded for a little longer, if it needs to.
	 */
	public static void keepLoaded(ServerLevel level, AbstractMinecart minecart, long gameTime) {
		ChunkPos chunk = minecart.chunkPosition();
		renewIfNeeded(level, (Holder) minecart, chunk, chunk.pack(), gameTime);
	}

	/**
	 * Keeps the chunks around every minecart of a train loaded for a little longer, where needed. Consecutive minecarts
	 * in the same chunk share the ticket of the first one.
	 */
	public static void keepLoaded(ServerLevel level, List<AbstractMinecart> train, long gameTime) {
		Holder previous = null;
		for (int i = 0, count = train.size(); i < count; i++) {
			AbstractMinecart minecart = train.get(i);
			Holder holder = (Holder) minecart;
			ChunkPos chunk = minecart.chunkPosition();
			long key = chunk.pack();
			if (previous != null && previous.bm$ticketChunk() == key) {
				holder.bm$setTicket(key, previous.bm$ticketRenewal());
			} else {
				renewIfNeeded(level, holder, chunk, key, gameTime);
				previous = holder;
			}
		}
	}

	private static void renewIfNeeded(ServerLevel level, Holder holder, ChunkPos chunk, long key, long gameTime) {
		if (gameTime >= holder.bm$ticketRenewal() || key != holder.bm$ticketChunk()) {
			// Adding the same ticket again only resets its timeout.
			level.getChunkSource().addTicketWithRadius(TICKET, chunk, RADIUS);
			holder.bm$setTicket(key, gameTime + RENEW_INTERVAL);
		}
	}

	/**
	 * The ticket of a minecart, added to every {@link AbstractMinecart} by {@code AbstractMinecartMixin}.
	 */
	public interface Holder {
		/**
		 * The chunk of the last ticket, as {@link ChunkPos#pack()}.
		 */
		long bm$ticketChunk();

		/**
		 * The game time at which the ticket has to be renewed.
		 */
		long bm$ticketRenewal();

		void bm$setTicket(long chunk, long renewal);
	}
}
