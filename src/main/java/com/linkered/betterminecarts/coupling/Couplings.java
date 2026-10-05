package com.linkered.betterminecarts.coupling;

import java.util.Iterator;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Creating, breaking, saving and syncing couplings between minecarts.
 *
 * <p>Every method that changes state must run on the logical server.
 */
public final class Couplings {
	public static final int SLOTS = 2;
	/**
	 * Distance between the centers of two coupled minecarts. A minecart is 1.25 blocks long, so this leaves a short
	 * stretch of chain visible between them.
	 */
	public static final double SPACING = 1.6;
	/**
	 * Maximum distance between two minecarts for a player to couple them. They are pulled together afterwards.
	 */
	public static final double MAX_COUPLE_DISTANCE = 4.0;
	/**
	 * Distance at which a coupling snaps, for example when a minecart derails or gets stuck.
	 */
	public static final double BREAK_DISTANCE = 6.0;
	/**
	 * Maximum distance between a player holding a chain and its minecart, the same as for leads.
	 */
	public static final double MAX_HOLD_DISTANCE = 10.0;
	/**
	 * How long a partner can be missing from loaded chunks before its coupling is considered broken. Gives entities
	 * spawned one by one (by commands or structures) time to appear.
	 */
	private static final int MAX_MISSING_TICKS = 40;
	/**
	 * Upper bound when walking along a train, only there to stop on corrupted data. Not a gameplay limit.
	 */
	static final int MAX_TRAIN_LENGTH = 4096;
	private static final String SAVE_KEY = "BetterMinecartsCouplings";
	private static final double ATTACH_HEIGHT = 0.4;

	private Couplings() {
	}

	/**
	 * Whether the minecart is coupled to at least one other minecart. Server only.
	 */
	public static boolean isCoupled(AbstractMinecart minecart) {
		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		return couplings[0].partnerId != null || couplings[1].partnerId != null;
	}

	/**
	 * Whether either end of the minecart is coupled or held, according to synced data. Works on both sides.
	 */
	public static boolean hasSyncedCouplings(AbstractMinecart minecart) {
		CoupledMinecart coupled = CoupledMinecart.of(minecart);
		return coupled.bm$syncedPartner(0).isPresent() || coupled.bm$syncedPartner(1).isPresent();
	}

	/**
	 * Whether {@code other} is a minecart coupled to {@code minecart}. Only uses cached references, so it is cheap
	 * enough to be called from collision checks.
	 */
	public static boolean isCoupledTo(AbstractMinecart minecart, Entity other) {
		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		return other == couplings[0].partner || other == couplings[1].partner;
	}

	static int couplingCount(AbstractMinecart minecart) {
		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		return (couplings[0].partnerId != null ? 1 : 0) + (couplings[1].partnerId != null ? 1 : 0);
	}

	static int freeSlot(AbstractMinecart minecart) {
		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		for (int slot = 0; slot < SLOTS; slot++) {
			if (couplings[slot].isFree()) {
				return slot;
			}
		}

		return -1;
	}

	static int slotCoupledTo(AbstractMinecart minecart, AbstractMinecart partner) {
		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		UUID partnerId = partner.getUUID();
		for (int slot = 0; slot < SLOTS; slot++) {
			if (partnerId.equals(couplings[slot].partnerId)) {
				return slot;
			}
		}

		return -1;
	}

	static int slotHeldBy(AbstractMinecart minecart, Player player) {
		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		for (int slot = 0; slot < SLOTS; slot++) {
			if (couplings[slot].holder == player) {
				return slot;
			}
		}

		return -1;
	}

	/**
	 * Returns the minecart coupled to the given slot, looking it up by UUID if the cached reference is missing or
	 * stale. Returns {@code null} if the slot is not coupled or the partner is not loaded.
	 */
	public static @Nullable AbstractMinecart partner(ServerLevel level, AbstractMinecart minecart, int slot) {
		Coupling coupling = CoupledMinecart.of(minecart).bm$couplings()[slot];
		UUID partnerId = coupling.partnerId;
		if (partnerId == null) {
			return null;
		}

		AbstractMinecart partner = coupling.partner;
		if (partner != null && !partner.isRemoved()) {
			return partner;
		}

		partner = level.getEntity(partnerId) instanceof AbstractMinecart found ? found : null;
		if (partner != null) {
			if (slotCoupledTo(partner, minecart) < 0) {
				// The partner does not know about this coupling (corrupted or edited data): keep the chain, drop the link.
				drop(level, minecart.position(), coupling.chain);
				coupling.clear();
				partner = null;
			} else {
				coupling.missingTicks = 0;
			}
		}

		coupling.partner = partner;
		sync(minecart, slot);
		return partner;
	}

	/**
	 * Returns the minecart coupled to {@code minecart} other than {@code from}, or {@code null} if there is none.
	 */
	static @Nullable AbstractMinecart next(ServerLevel level, AbstractMinecart minecart, @Nullable AbstractMinecart from) {
		for (int slot = 0; slot < SLOTS; slot++) {
			AbstractMinecart partner = partner(level, minecart, slot);
			if (partner != null && partner != from) {
				return partner;
			}
		}

		return null;
	}

	/**
	 * Whether {@code a} can be coupled to {@code b}: two different minecarts close enough, {@code b} with a free end,
	 * and not already part of the same train (a train never closes into a loop).
	 */
	static boolean canCouple(ServerLevel level, AbstractMinecart a, AbstractMinecart b) {
		return a != b
			&& a.isAlive()
			&& b.isAlive()
			&& freeSlot(b) >= 0
			&& a.distanceToSqr(b) <= MAX_COUPLE_DISTANCE * MAX_COUPLE_DISTANCE
			&& !inSameTrain(level, a, b);
	}

	private static boolean inSameTrain(ServerLevel level, AbstractMinecart a, AbstractMinecart b) {
		for (int slot = 0; slot < SLOTS; slot++) {
			AbstractMinecart previous = a;
			AbstractMinecart current = partner(level, a, slot);
			for (int i = 0; current != null && i < MAX_TRAIN_LENGTH; i++) {
				if (current == b) {
					return true;
				}

				AbstractMinecart next = next(level, current, previous);
				previous = current;
				current = next;
			}
		}

		return false;
	}

	/**
	 * Couples {@code a} to {@code b}. {@code a} becomes the owner of the chain. The caller must have checked
	 * {@link #canCouple}.
	 */
	static void couple(ServerLevel level, AbstractMinecart a, int slotA, AbstractMinecart b, ItemStack chain) {
		int slotB = freeSlot(b);
		Coupling couplingA = CoupledMinecart.of(a).bm$couplings()[slotA];
		couplingA.clear();
		couplingA.partnerId = b.getUUID();
		couplingA.partner = b;
		couplingA.chain = chain;
		Coupling couplingB = CoupledMinecart.of(b).bm$couplings()[slotB];
		couplingB.clear();
		couplingB.partnerId = a.getUUID();
		couplingB.partner = a;
		sync(a, slotA);
		sync(b, slotB);
		Vec3 middle = middle(a, b);
		level.playSound(null, middle.x, middle.y, middle.z, soundType(chain).getPlaceSound(), SoundSource.NEUTRAL, 1.0F, 0.9F);
	}

	/**
	 * Lets a player hold the free end of a chain attached to the given slot.
	 */
	static void hold(AbstractMinecart minecart, int slot, Player player, ItemStack chain) {
		Coupling coupling = CoupledMinecart.of(minecart).bm$couplings()[slot];
		coupling.clear();
		coupling.holder = player;
		coupling.chain = chain;
		sync(minecart, slot);
		minecart.playSound(soundType(chain).getHitSound(), 1.0F, 1.0F);
	}

	/**
	 * Clears a slot held by a player. The chain was never taken from the player, so nothing is dropped.
	 */
	static void release(AbstractMinecart minecart, int slot) {
		CoupledMinecart.of(minecart).bm$couplings()[slot].clear();
		sync(minecart, slot);
	}

	/**
	 * Breaks the coupling of the given slot on both minecarts and drops its chain between them.
	 */
	static void uncouple(ServerLevel level, AbstractMinecart minecart, int slot, Reason reason) {
		Coupling coupling = CoupledMinecart.of(minecart).bm$couplings()[slot];
		if (coupling.holder != null) {
			release(minecart, slot);
			return;
		}

		AbstractMinecart partner = partner(level, minecart, slot);
		if (coupling.partnerId == null) {
			// Already cleared while looking up the partner.
			return;
		}

		ItemStack chain = coupling.chain;
		Vec3 dropPos = minecart.position().add(0.0, ATTACH_HEIGHT, 0.0);
		if (partner != null) {
			int partnerSlot = slotCoupledTo(partner, minecart);
			Coupling partnerCoupling = CoupledMinecart.of(partner).bm$couplings()[partnerSlot];
			if (chain.isEmpty()) {
				chain = partnerCoupling.chain;
			}

			partnerCoupling.clear();
			sync(partner, partnerSlot);
			dropPos = middle(minecart, partner);
		}

		coupling.clear();
		sync(minecart, slot);
		SoundEvent sound = switch (reason) {
			case SHEARS -> SoundEvents.SHEARS_SNIP;
			case SNAPPED -> soundType(chain).getBreakSound();
			case REMOVED, STALE -> null;
		};
		if (sound != null) {
			level.playSound(null, dropPos.x, dropPos.y, dropPos.z, sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}

		if (reason == Reason.SHEARS) {
			minecart.gameEvent(GameEvent.SHEAR);
		}

		drop(level, dropPos, chain);
	}

	/**
	 * Breaks every coupling of a minecart that is being destroyed or leaving the dimension.
	 */
	public static void onRemoved(AbstractMinecart minecart, Entity.RemovalReason reason) {
		if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
			|| !(minecart.level() instanceof ServerLevel level)) {
			return;
		}

		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		for (int slot = 0; slot < SLOTS; slot++) {
			if (!couplings[slot].isFree()) {
				uncouple(level, minecart, slot, Reason.REMOVED);
			}
		}
	}

	/**
	 * Breaks the couplings of this minecart whose partner cannot be found even though every chunk it could be in is
	 * loaded and ticking. That happens when the partner was destroyed while this minecart was unloaded.
	 */
	static void dropMissingPartners(ServerLevel level, AbstractMinecart minecart) {
		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		for (int slot = 0; slot < SLOTS; slot++) {
			Coupling coupling = couplings[slot];
			if (coupling.partnerId != null && partner(level, minecart, slot) == null && isAreaTicking(level, minecart)
				&& ++coupling.missingTicks > MAX_MISSING_TICKS) {
				uncouple(level, minecart, slot, Reason.STALE);
			}
		}
	}

	private static boolean isAreaTicking(ServerLevel level, AbstractMinecart minecart) {
		int minX = SectionPos.blockToSectionCoord(Mth.floor(minecart.getX() - BREAK_DISTANCE));
		int maxX = SectionPos.blockToSectionCoord(Mth.floor(minecart.getX() + BREAK_DISTANCE));
		int minZ = SectionPos.blockToSectionCoord(Mth.floor(minecart.getZ() - BREAK_DISTANCE));
		int maxZ = SectionPos.blockToSectionCoord(Mth.floor(minecart.getZ() + BREAK_DISTANCE));
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(x, z))) {
					return false;
				}
			}
		}

		return true;
	}

	public static void save(AbstractMinecart minecart, ValueOutput output) {
		ValueOutput.TypedOutputList<Coupling.Saved> list = null;
		for (Coupling coupling : CoupledMinecart.of(minecart).bm$couplings()) {
			if (coupling.partnerId != null) {
				if (list == null) {
					list = output.list(SAVE_KEY, Coupling.CODEC);
				}

				list.add(new Coupling.Saved(coupling.partnerId, coupling.chain));
			}
		}
	}

	public static void load(AbstractMinecart minecart, ValueInput input) {
		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		for (Coupling coupling : couplings) {
			coupling.clear();
		}

		Iterator<Coupling.Saved> saved = input.listOrEmpty(SAVE_KEY, Coupling.CODEC).stream().iterator();
		for (int slot = 0; slot < SLOTS && saved.hasNext(); slot++) {
			Coupling.Saved entry = saved.next();
			couplings[slot].partnerId = entry.partner();
			couplings[slot].chain = entry.chain();
		}

		for (int slot = 0; slot < SLOTS; slot++) {
			sync(minecart, slot);
		}
	}

	/**
	 * Copies the state of a slot into synced entity data. Unchanged values are not sent again.
	 */
	private static void sync(AbstractMinecart minecart, int slot) {
		Coupling coupling = CoupledMinecart.of(minecart).bm$couplings()[slot];
		Entity other = coupling.holder != null ? coupling.holder : coupling.partner;
		if (other == null || other.isRemoved()) {
			CoupledMinecart.of(minecart).bm$setSynced(slot, OptionalInt.empty(), Optional.empty());
		} else {
			Optional<BlockState> chain = coupling.chain.isEmpty() ? Optional.empty() : Optional.of(chainBlock(coupling.chain));
			CoupledMinecart.of(minecart).bm$setSynced(slot, OptionalInt.of(other.getId()), chain);
		}
	}

	/**
	 * The block drawn for a chain item. Items in the coupler tag that are not blocks are drawn as an iron chain.
	 */
	private static BlockState chainBlock(ItemStack chain) {
		return chain.getItem() instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState() : Blocks.IRON_CHAIN.defaultBlockState();
	}

	private static SoundType soundType(ItemStack chain) {
		return chainBlock(chain).getSoundType();
	}

	private static Vec3 middle(AbstractMinecart a, AbstractMinecart b) {
		return new Vec3((a.getX() + b.getX()) * 0.5, (a.getY() + b.getY()) * 0.5 + ATTACH_HEIGHT, (a.getZ() + b.getZ()) * 0.5);
	}

	private static void drop(ServerLevel level, Vec3 pos, ItemStack chain) {
		if (!chain.isEmpty() && level.getGameRules().get(GameRules.ENTITY_DROPS)) {
			ItemEntity item = new ItemEntity(level, pos.x, pos.y, pos.z, chain);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	enum Reason {
		/**
		 * Cut by a player with shears.
		 */
		SHEARS,
		/**
		 * Stretched past {@link #BREAK_DISTANCE}.
		 */
		SNAPPED,
		/**
		 * One of the minecarts was destroyed or left the dimension.
		 */
		REMOVED,
		/**
		 * The partner no longer exists.
		 */
		STALE
	}
}
