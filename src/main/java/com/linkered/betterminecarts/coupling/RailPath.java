package com.linkered.betterminecarts.coupling;

import com.mojang.datafixers.util.Pair;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Distances along the rails, measured the way {@code NewMinecartBehavior} moves minecarts: on every rail block a
 * minecart follows a straight line between the middles of the two edges the rail connects. That is 1 block on a flat
 * straight rail, half a diagonal on a curve and a full diagonal on a slope.
 *
 * <p>Server thread only: results of the last walk are kept in static fields to avoid allocating.
 */
final class RailPath {
	private static final double CURVE_LENGTH = Math.sqrt(0.5);
	private static final double SLOPE_LENGTH = Math.sqrt(2.0);
	private static final int MAX_BLOCKS = 16;
	private static final Direction[] FIRST_EXIT = new Direction[RailShape.values().length];
	private static final Direction[] SECOND_EXIT = new Direction[RailShape.values().length];
	private static final double[] LENGTH = new double[RailShape.values().length];
	private static final BlockPos.MutableBlockPos CURSOR = new BlockPos.MutableBlockPos();
	private static final BlockPos.MutableBlockPos READ = new BlockPos.MutableBlockPos();
	// The chunk of the last block read. The minecarts of a train are nearly always in the same chunk or two, so this
	// skips the chunk lookup of almost every read. Only kept during one train's step: see clearChunkCache().
	private static long cachedChunkKey = Long.MIN_VALUE;
	private static @Nullable LevelChunk cachedChunk;

	/**
	 * After a successful {@link #between}, the exit of the target's rail through which the path arrived.
	 */
	static Direction arrivalExit = Direction.NORTH;
	/**
	 * After {@link #railUnder}, the shape of the rail found, or {@code null}.
	 */
	static @Nullable RailShape foundShape;

	static {
		for (RailShape shape : RailShape.values()) {
			Pair<Vec3i, Vec3i> exits = AbstractMinecart.exits(shape);
			Direction first = horizontal(exits.getFirst());
			Direction second = horizontal(exits.getSecond());
			FIRST_EXIT[shape.ordinal()] = first;
			SECOND_EXIT[shape.ordinal()] = second;
			LENGTH[shape.ordinal()] = shape.isSlope() ? SLOPE_LENGTH : first == second.getOpposite() ? 1.0 : CURVE_LENGTH;
		}
	}

	private RailPath() {
	}

	/**
	 * Forgets the cached chunk. Called around every train step, so a chunk unloaded in between is never read.
	 */
	static void clearChunkCache() {
		cachedChunkKey = Long.MIN_VALUE;
		cachedChunk = null;
	}

	private static BlockState blockState(ServerLevel level, BlockPos pos) {
		int chunkX = SectionPos.blockToSectionCoord(pos.getX());
		int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
		long key = ChunkPos.pack(chunkX, chunkZ);
		if (key != cachedChunkKey) {
			cachedChunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
			cachedChunkKey = key;
		}

		LevelChunk chunk = cachedChunk;
		return chunk != null ? chunk.getBlockState(pos) : level.getBlockState(pos);
	}

	private static Direction horizontal(Vec3i exit) {
		return Objects.requireNonNull(Direction.getNearest(exit.getX(), 0, exit.getZ(), null));
	}

	/**
	 * The shape of a rail block state, or {@code null} if it is not a rail.
	 */
	static @Nullable RailShape shape(BlockState state) {
		return BaseRailBlock.isRail(state) ? state.getValue(((BaseRailBlock) state.getBlock()).getShapeProperty()) : null;
	}

	static Direction firstExit(RailShape shape) {
		return FIRST_EXIT[shape.ordinal()];
	}

	static Direction otherExit(RailShape shape, Direction exit) {
		Direction first = FIRST_EXIT[shape.ordinal()];
		return exit == first ? SECOND_EXIT[shape.ordinal()] : first;
	}

	/**
	 * Distance between two points on the same rail block.
	 */
	static double within(Vec3 from, Vec3 to, RailShape shape) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		return shape.isSlope() ? distance * SLOPE_LENGTH : distance;
	}

	/**
	 * Walks along the rails from the rail at {@code fromBlock}, leaving through {@code exit}, until reaching the rail at
	 * {@code toBlock} (whose shape is already known). Rails are followed one block up or down, like slopes connect.
	 *
	 * @return the length of the rails between both blocks (not counting either of them), or {@link Double#NaN} if the
	 * path leads elsewhere, ends, or is longer than {@code maxLength}. On success {@link #arrivalExit} is set.
	 */
	static double between(ServerLevel level, long fromBlock, Direction exit, long toBlock, RailShape toShape, double maxLength) {
		BlockPos.MutableBlockPos cursor = CURSOR.set(fromBlock);
		double length = 0.0;
		Direction out = exit;
		for (int i = 0; i < MAX_BLOCKS && length <= maxLength; i++) {
			Direction in = out.getOpposite();
			RailShape shape = nextRail(level, cursor, out, in, toBlock, toShape);
			if (shape == null) {
				return Double.NaN;
			}

			if (cursor.asLong() == toBlock) {
				arrivalExit = in;
				return length;
			}

			out = otherExit(shape, in);
			length += LENGTH[shape.ordinal()];
		}

		return Double.NaN;
	}

	/**
	 * The rail block a minecart is on, as {@code AbstractMinecart.getCurrentBlockPosOrRailBelow()} finds it, but
	 * usually with a single block read and without allocating. Sets {@link #foundShape} (or {@code null} if there is
	 * no rail) and returns the packed position.
	 */
	static long railUnder(ServerLevel level, AbstractMinecart minecart) {
		int x = Mth.floor(minecart.getX());
		int z = Mth.floor(minecart.getZ());
		int y = Mth.floor(minecart.getY());
		// A rail never sits on another rail, so checking the minecart's own block first finds the same rail as vanilla.
		RailShape shape = shape(blockState(level, READ.set(x, y, z)));
		if (shape == null) {
			int below = Mth.floor(minecart.getY() - 0.1 - 1.0E-5F);
			if (below != y) {
				RailShape belowShape = shape(blockState(level, READ.set(x, below, z)));
				if (belowShape != null) {
					foundShape = belowShape;
					return BlockPos.asLong(x, below, z);
				}
			}
		}

		foundShape = shape;
		return BlockPos.asLong(x, y, z);
	}

	/**
	 * Distance along the rail from {@code pos} to the edge of the rail block at {@code block} on the side of
	 * {@code exit}.
	 */
	static double toEdge(Vec3 pos, long block, RailShape shape, Direction exit) {
		double dx = pos.x - (BlockPos.getX(block) + 0.5 + exit.getStepX() * 0.5);
		double dz = pos.z - (BlockPos.getZ(block) + 0.5 + exit.getStepZ() * 0.5);
		double distance = Math.sqrt(dx * dx + dz * dz);
		return shape.isSlope() ? distance * SLOPE_LENGTH : distance;
	}

	/**
	 * Moves {@code cursor} to the rail next to it in direction {@code out}, at the same height, one above or one below,
	 * that connects back through {@code in}. Returns its shape, or {@code null} if there is none.
	 */
	private static @Nullable RailShape nextRail(
		ServerLevel level, BlockPos.MutableBlockPos cursor, Direction out, Direction in, long toBlock, RailShape toShape
	) {
		cursor.move(out);
		RailShape shape = connecting(level, cursor, in, toBlock, toShape);
		if (shape != null) {
			return shape;
		}

		cursor.move(Direction.UP);
		shape = connecting(level, cursor, in, toBlock, toShape);
		if (shape != null) {
			return shape;
		}

		cursor.move(Direction.DOWN, 2);
		return connecting(level, cursor, in, toBlock, toShape);
	}

	private static @Nullable RailShape connecting(ServerLevel level, BlockPos.MutableBlockPos pos, Direction in, long toBlock, RailShape toShape) {
		// The rail of the target minecart is already known: no need to read it again.
		RailShape shape = pos.asLong() == toBlock ? toShape : shape(blockState(level, pos));
		return shape != null && (FIRST_EXIT[shape.ordinal()] == in || SECOND_EXIT[shape.ordinal()] == in) ? shape : null;
	}
}
