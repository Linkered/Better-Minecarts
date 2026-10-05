package com.linkered.betterminecarts.coupling;

import com.linkered.betterminecarts.furnace.FurnaceChunkLoading;
import com.linkered.betterminecarts.furnace.PausableFurnace;
import com.linkered.betterminecarts.mixin.AbstractMinecartInvoker;
import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * Moves coupled minecarts together as one train.
 *
 * <p>The first minecart of a train to tick moves the whole train, and the others skip their own movement that tick.
 * Each minecart still runs the vanilla rail logic ({@code NewMinecartBehavior}): slopes, powered rails and friction
 * affect every minecart as usual. Before moving, every minecart is given the same speed along the train (the average
 * of their speeds, which keeps momentum, plus the push of its furnace minecarts shared by all), plus a small correction
 * that brings each coupling back to {@link Couplings#SPACING}.
 *
 * <p>Distances and directions are measured along the rails ({@link RailPath}), so the train keeps together on curves
 * that turn back on themselves, where minecarts can be close in a straight line but far apart along the track.
 *
 * <p>If any minecart of the train is unloaded or in a chunk that is not ticking, the whole train waits. A train with a
 * burning furnace minecart keeps the chunks of all its minecarts loaded ({@link FurnaceChunkLoading}), so it only
 * waits for a tick or two while they load.
 */
public final class TrainPhysics {
	/**
	 * Fraction of the spacing error corrected per tick.
	 */
	private static final double CORRECTION_GAIN = 0.4;
	/**
	 * Maximum correction speed, in blocks per tick.
	 */
	private static final double MAX_CORRECTION = 0.2;
	/**
	 * Spacing errors below this are ignored, so a train at rest stays at rest.
	 */
	private static final double SPACING_TOLERANCE = 0.02;
	/**
	 * How far along the rails to look for the next minecart before falling back to the straight line.
	 */
	private static final double MAX_PATH_DISTANCE = Couplings.BREAK_DISTANCE + 2.0;
	private static final double MIN_DIRECTION_LENGTH = 1.0E-4;
	/**
	 * Push of a furnace minecart in a train, as a fraction of its top speed per tick. An empty minecart loses 2.5% of
	 * its speed per tick to friction, so this lets one furnace minecart keep itself and four empty minecarts at top
	 * speed on flat track. Chest minecarts are lighter when empty and heavier when full, as in vanilla.
	 */
	private static final double FURNACE_PULL = 5 * 0.025;

	// Reused between trains: only the server thread moves minecarts.
	private static final ArrayList<AbstractMinecart> CARS = new ArrayList<>();
	private static long[] railPos = new long[16];
	private static RailShape[] railShape = new RailShape[16];
	// Unit vectors of the forward and backward direction of each minecart along its rail.
	private static double[] forwardX = new double[16];
	private static double[] forwardZ = new double[16];
	private static double[] backwardX = new double[16];
	private static double[] backwardZ = new double[16];
	private static double[] offsets = new double[16];
	private static boolean stepping;
	// Whether the train collected last has a burning furnace minecart.
	private static boolean burning;

	private TrainPhysics() {
	}

	/**
	 * Called in place of the vanilla track movement of a minecart.
	 *
	 * @return {@code true} if the movement of this minecart is handled by its train, {@code false} to run the vanilla
	 * movement
	 */
	public static boolean moveAsTrain(ServerLevel level, AbstractMinecart minecart) {
		if (!Couplings.isCoupled(minecart)) {
			return false;
		}

		long gameTime = level.getGameTime();
		if (CoupledMinecart.of(minecart).bm$trainTick() == gameTime) {
			return true;
		}

		if (stepping) {
			return false;
		}

		stepping = true;
		RailPath.clearChunkCache();
		try {
			step(level, minecart, gameTime);
		} finally {
			stepping = false;
			CARS.clear();
			RailPath.clearChunkCache();
		}

		return true;
	}

	/**
	 * Whether two minecarts are moving in the same train this tick. They do not collide with or push each other, even
	 * when not coupled directly, for example on both sides of a tight U-turn.
	 */
	public static boolean inSameTrain(AbstractMinecart minecart, Entity other) {
		if (!(other instanceof AbstractMinecart otherMinecart)) {
			return false;
		}

		CoupledMinecart a = CoupledMinecart.of(minecart);
		CoupledMinecart b = CoupledMinecart.of(otherMinecart);
		long tick = a.bm$trainTick();
		return tick == b.bm$trainTick() && a.bm$trainId() == b.bm$trainId() && tick == minecart.level().getGameTime();
	}

	/**
	 * How much a furnace minecart in a train adds to the speed of a single minecart every tick, for the given top speed.
	 */
	private static double furnacePull(double maxSpeed) {
		return maxSpeed * FURNACE_PULL;
	}

	private static void step(ServerLevel level, AbstractMinecart origin, long gameTime) {
		// Every minecart is visited as few times as possible: with many trains, reading each minecart from memory costs
		// more than the math done with it.
		ArrayList<AbstractMinecart> cars = CARS;
		collect(level, origin, gameTime, cars);
		int count = cars.size();
		ensureCapacity(count);
		// Before checking whether the train can move, so a train waiting for its minecarts to load gets them loaded.
		if (burning) {
			FurnaceChunkLoading.keepLoaded(level, cars, gameTime);
		}

		if (!canMove(level, origin, cars)) {
			return;
		}

		// Measure each coupling along the rails, which also tells which way along its rail each minecart moves
		// forward. offset[i] accumulates the spacing errors from the front: how far minecart i should move forward.
		// In the same pass, add up the speed of every minecart along the train and the push of lit furnace minecarts.
		double[] offset = offsets;
		offset[0] = 0.0;
		double speed = 0.0;
		double offsetSum = 0.0;
		for (int i = 1; i < count; i++) {
			AbstractMinecart front = cars.get(i - 1);
			AbstractMinecart back = cars.get(i);
			double dx = front.getX() - back.getX();
			double dy = front.getY() - back.getY();
			double dz = front.getZ() - back.getZ();
			double straight = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (straight > Couplings.BREAK_DISTANCE) {
				Couplings.uncouple(level, front, Couplings.slotCoupledTo(front, back), Couplings.Reason.SNAPPED);
				return;
			}

			double distance = measureAlongRails(level, front, back, i, dx, dz);
			if (Double.isNaN(distance)) {
				// Off the rails or no rail path between them: use the straight line.
				distance = straight;
				useStraightLine(i, i == 1, dx, dz);
			}

			double error = distance - Couplings.SPACING;
			offset[i] = offset[i - 1] + (Math.abs(error) > SPACING_TOLERANCE ? error : 0.0);
			offsetSum += offset[i];
			if (i == 1) {
				speed += speedAlongTrain(level, front, 0);
			}

			speed += speedAlongTrain(level, back, i);
		}

		speed /= count;
		double meanOffset = offsetSum / count;

		for (int i = 0; i < count; i++) {
			AbstractMinecart car = cars.get(i);
			if (!car.isAlive()) {
				continue;
			}

			double carSpeed = speed + Mth.clamp((offset[i] - meanOffset) * CORRECTION_GAIN, -MAX_CORRECTION, MAX_CORRECTION);
			double y = car.getDeltaMovement().y;
			if (carSpeed >= 0.0) {
				car.setDeltaMovement(forwardX[i] * carSpeed, y, forwardZ[i] * carSpeed);
			} else {
				car.setDeltaMovement(backwardX[i] * -carSpeed, y, backwardZ[i] * -carSpeed);
			}

			((AbstractMinecartInvoker) car).bm$moveAlongTrack(level);
		}
	}

	/**
	 * The speed of minecart {@code i} along the train (negative if it moves backward), plus the push it adds to the
	 * whole train if it is a lit furnace minecart.
	 */
	private static double speedAlongTrain(ServerLevel level, AbstractMinecart car, int i) {
		Vec3 movement = car.getDeltaMovement();
		double magnitude = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
		double speed = magnitude > 1.0E-7 ? (isForward(movement.x, movement.z, i) ? magnitude : -magnitude) : 0.0;
		return car instanceof MinecartFurnace furnace ? speed + furnacePush(level, furnace, i) : speed;
	}

	/**
	 * Measures the coupling between minecart {@code i - 1} ({@code front}) and minecart {@code i} ({@code back}) along
	 * the rails, and sets the forward and backward directions of {@code back} (and of {@code front} if it is the head
	 * of the train). Usually reads two or three rail blocks.
	 *
	 * @return the distance along the rails, or {@link Double#NaN} if no rail path connects them
	 */
	private static double measureAlongRails(ServerLevel level, AbstractMinecart front, AbstractMinecart back, int i, double dx, double dz) {
		RailShape backShape = railShape[i];
		RailShape frontShape = railShape[i - 1];
		if (backShape == null || frontShape == null) {
			return Double.NaN;
		}

		Direction toFront;
		Direction arrival;
		double distance;
		if (railPos[i] == railPos[i - 1]) {
			Direction a = RailPath.firstExit(backShape);
			Direction b = RailPath.otherExit(backShape, a);
			toFront = a.getStepX() * dx + a.getStepZ() * dz >= b.getStepX() * dx + b.getStepZ() * dz ? a : b;
			arrival = RailPath.otherExit(backShape, toFront);
			distance = RailPath.within(back.position(), front.position(), backShape);
		} else {
			PathCache path = CoupledMinecart.of(back).bm$pathCache();
			if (!path.matches(railPos[i], backShape, railPos[i - 1], frontShape)) {
				findPath(level, path, i, backShape, frontShape, dx, dz);
			}

			toFront = path.exit;
			if (toFront == null) {
				return Double.NaN;
			}

			arrival = path.arrival;
			distance = RailPath.toEdge(back.position(), railPos[i], backShape, toFront)
				+ path.between
				+ RailPath.toEdge(front.position(), railPos[i - 1], frontShape, arrival);
		}

		setDirections(i, toFront, RailPath.otherExit(backShape, toFront));
		if (i == 1) {
			// The head of the train: backward is where the path to the second minecart arrived from.
			setDirections(0, RailPath.otherExit(frontShape, arrival), arrival);
		}

		return distance;
	}

	/**
	 * Walks the rails from minecart {@code i} to the one in front of it and stores the result in {@code path}.
	 */
	private static void findPath(ServerLevel level, PathCache path, int i, RailShape backShape, RailShape frontShape, double dx, double dz) {
		path.valid = true;
		path.fromBlock = railPos[i];
		path.toBlock = railPos[i - 1];
		path.fromShape = backShape;
		path.toShape = frontShape;
		// Try first the exit that points more towards the minecart in front: it is almost always the right one.
		Direction a = RailPath.firstExit(backShape);
		Direction b = RailPath.otherExit(backShape, a);
		Direction exit = a.getStepX() * dx + a.getStepZ() * dz >= b.getStepX() * dx + b.getStepZ() * dz ? a : b;
		double between = RailPath.between(level, path.fromBlock, exit, path.toBlock, frontShape, MAX_PATH_DISTANCE);
		if (Double.isNaN(between)) {
			exit = RailPath.otherExit(backShape, exit);
			between = RailPath.between(level, path.fromBlock, exit, path.toBlock, frontShape, MAX_PATH_DISTANCE);
		}

		if (Double.isNaN(between)) {
			path.exit = null;
		} else {
			path.exit = exit;
			path.arrival = RailPath.arrivalExit;
			path.between = between;
		}
	}

	private static void setDirections(int i, Direction forward, Direction backward) {
		forwardX[i] = forward.getStepX();
		forwardZ[i] = forward.getStepZ();
		backwardX[i] = backward.getStepX();
		backwardZ[i] = backward.getStepZ();
	}

	/**
	 * Uses the straight line towards the front of the train as the direction of minecart {@code i}, and of the head too
	 * if {@code head} is set.
	 */
	private static void useStraightLine(int i, boolean head, double dx, double dz) {
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double x = horizontal > MIN_DIRECTION_LENGTH ? dx / horizontal : 1.0;
		double z = horizontal > MIN_DIRECTION_LENGTH ? dz / horizontal : 0.0;
		forwardX[i] = x;
		forwardZ[i] = z;
		backwardX[i] = -x;
		backwardZ[i] = -z;
		if (head) {
			forwardX[0] = x;
			forwardZ[0] = z;
			backwardX[0] = -x;
			backwardZ[0] = -z;
		}
	}

	/**
	 * Whether a horizontal vector points forward rather than backward along the rail of minecart {@code i}. Comparing
	 * with both directions works on curves too, where one of them can be perpendicular to the vector.
	 */
	private static boolean isForward(double x, double z, int i) {
		return x * (forwardX[i] - backwardX[i]) + z * (forwardZ[i] - backwardZ[i]) >= 0.0;
	}

	/**
	 * The speed a lit furnace minecart adds to the whole train this tick, signed along the train. Its push is also
	 * turned to follow its rail, so it keeps pushing the same way through curves (and after leaving the train), even
	 * while paused.
	 */
	private static double furnacePush(ServerLevel level, MinecartFurnace furnace, int i) {
		Vec3 push = furnace.push;
		if (push.lengthSqr() <= 1.0E-7) {
			return 0.0;
		}

		boolean forward = isForward(push.x, push.z, i);
		double length = push.horizontalDistance();
		furnace.push = forward
			? new Vec3(forwardX[i] * length, 0.0, forwardZ[i] * length)
			: new Vec3(backwardX[i] * length, 0.0, backwardZ[i] * length);
		if (PausableFurnace.isPaused(furnace)) {
			return 0.0;
		}

		double pull = furnacePull(((AbstractMinecartInvoker) furnace).bm$getMaxSpeed(level));
		return forward ? pull : -pull;
	}

	/**
	 * Fills {@code cars} with the train of {@code origin}, from one end to the other, and marks each minecart as moved
	 * by this train this tick. Also sets {@link #burning}.
	 */
	private static void collect(ServerLevel level, AbstractMinecart origin, long gameTime, ArrayList<AbstractMinecart> cars) {
		int trainId = origin.getId();
		burning = false;
		// One side of the origin, walking away from it, then reversed so the list starts at that end.
		walk(level, origin, Couplings.partner(level, origin, 0), gameTime, trainId, cars);
		Collections.reverse(cars);
		mark(origin, gameTime, trainId);
		cars.add(origin);
		walk(level, origin, Couplings.partner(level, origin, 1), gameTime, trainId, cars);
	}

	private static void walk(
		ServerLevel level, AbstractMinecart origin, AbstractMinecart first, long gameTime, int trainId, ArrayList<AbstractMinecart> cars
	) {
		AbstractMinecart previous = origin;
		AbstractMinecart current = first;
		for (int i = 0; current != null && current != origin && i < Couplings.MAX_TRAIN_LENGTH; i++) {
			mark(current, gameTime, trainId);
			cars.add(current);
			AbstractMinecart next = Couplings.next(level, current, previous);
			previous = current;
			current = next;
		}
	}

	private static void mark(AbstractMinecart car, long gameTime, int trainId) {
		if (car instanceof MinecartFurnace furnace && FurnaceChunkLoading.isBurning(furnace)) {
			burning = true;
		}

		CoupledMinecart coupled = CoupledMinecart.of(car);
		coupled.bm$setTrainTick(gameTime);
		coupled.bm$setTrainId(trainId);
	}

	/**
	 * Whether every coupling of the train was found and every minecart is in a ticking chunk. Also finds the rail
	 * under each minecart.
	 */
	private static boolean canMove(ServerLevel level, AbstractMinecart origin, ArrayList<AbstractMinecart> cars) {
		int count = cars.size();
		boolean canMove = count > 1;
		// The origin is ticking, so its chunk is. Consecutive minecarts are usually in the same chunk: check each chunk once.
		ChunkPos tickingChunk = origin.chunkPosition();
		for (int i = 0; i < count; i++) {
			AbstractMinecart car = cars.get(i);
			int neighbours = (i > 0 ? 1 : 0) + (i < count - 1 ? 1 : 0);
			if (Couplings.couplingCount(car) != neighbours) {
				// A partner is missing: unloaded (wait for it) or gone (break the coupling).
				Couplings.dropMissingPartners(level, car);
				canMove = false;
			} else if (!car.chunkPosition().equals(tickingChunk)) {
				if (level.isPositionEntityTicking(car.blockPosition())) {
					tickingChunk = car.chunkPosition();
				} else {
					canMove = false;
				}
			}

			if (canMove) {
				railPos[i] = RailPath.railUnder(level, car);
				railShape[i] = RailPath.foundShape;
			}
		}

		return canMove;
	}

	private static void ensureCapacity(int count) {
		if (offsets.length < count) {
			int capacity = Math.max(count, offsets.length * 2);
			railPos = new long[capacity];
			railShape = new RailShape[capacity];
			forwardX = new double[capacity];
			forwardZ = new double[capacity];
			backwardX = new double[capacity];
			backwardZ = new double[capacity];
			offsets = new double[capacity];
		}
	}
}
