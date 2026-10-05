package com.linkered.betterminecarts.coupling;

import com.linkered.betterminecarts.ModTags;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Player interactions with couplings, modelled on leads:
 * <ol>
 *     <li>Using a chain on a minecart attaches the chain to it, and the player holds the free end.</li>
 *     <li>Using a chain on a second minecart couples both and uses up one chain. Using it on the first minecart again
 *     lets go of the chain.</li>
 *     <li>Using shears on a minecart cuts the coupling closest to where it was clicked and drops the chain.</li>
 * </ol>
 *
 * <p>Couplings need the "Minecart Improvements" experiment, so nothing happens in worlds without it.
 */
public final class CouplingInteractions {
	/**
	 * Minecarts whose chain is held by a player, by player. Usually empty.
	 */
	private static final Map<Player, AbstractMinecart> HELD = new HashMap<>();

	private CouplingInteractions() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register(CouplingInteractions::onUseEntity);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!HELD.isEmpty()) {
				tickHeldChains();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HELD.clear());
	}

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!(entity instanceof AbstractMinecart minecart) || player.isSpectator() || !AbstractMinecart.useExperimentalMovement(level)) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(ModTags.MINECART_COUPLERS)) {
			return level instanceof ServerLevel serverLevel ? useChain(serverLevel, player, stack, minecart) : InteractionResult.SUCCESS;
		}

		if (stack.is(Items.SHEARS) && Couplings.hasSyncedCouplings(minecart)) {
			return level instanceof ServerLevel serverLevel ? useShears(serverLevel, player, hand, stack, minecart, hitResult) : InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	private static InteractionResult useChain(ServerLevel level, Player player, ItemStack stack, AbstractMinecart minecart) {
		AbstractMinecart held = HELD.get(player);
		int heldSlot = held != null ? Couplings.slotHeldBy(held, player) : -1;
		if (heldSlot >= 0) {
			if (held == minecart) {
				Couplings.release(held, heldSlot);
				HELD.remove(player);
				return InteractionResult.SUCCESS;
			}

			if (!Couplings.canCouple(level, held, minecart)) {
				return InteractionResult.FAIL;
			}

			Couplings.couple(level, held, heldSlot, minecart, stack.copyWithCount(1));
			stack.consume(1, player);
			HELD.remove(player);
			minecart.gameEvent(GameEvent.ENTITY_INTERACT, player);
			return InteractionResult.SUCCESS;
		}

		int slot = Couplings.freeSlot(minecart);
		if (slot < 0) {
			return InteractionResult.FAIL;
		}

		Couplings.hold(minecart, slot, player, stack.copyWithCount(1));
		HELD.put(player, minecart);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult useShears(
		ServerLevel level, Player player, InteractionHand hand, ItemStack stack, AbstractMinecart minecart, @Nullable EntityHitResult hitResult
	) {
		Vec3 target = hitResult != null ? hitResult.getLocation() : player.getEyePosition();
		Coupling[] couplings = CoupledMinecart.of(minecart).bm$couplings();
		int nearest = -1;
		double nearestDistance = Double.MAX_VALUE;
		for (int slot = 0; slot < Couplings.SLOTS; slot++) {
			Coupling coupling = couplings[slot];
			Entity other = coupling.holder != null ? coupling.holder : Couplings.partner(level, minecart, slot);
			if (other != null) {
				double distance = other.distanceToSqr(target);
				if (distance < nearestDistance) {
					nearest = slot;
					nearestDistance = distance;
				}
			} else if (coupling.isCoupled() && nearest < 0) {
				// The partner is not loaded: still allow cutting the chain from this side.
				nearest = slot;
			}
		}

		if (nearest < 0) {
			return InteractionResult.PASS;
		}

		Couplings.uncouple(level, minecart, nearest, Couplings.Reason.SHEARS);
		stack.hurtAndBreak(1, player, hand);
		return InteractionResult.SUCCESS;
	}

	/**
	 * Lets go of held chains whose player walked too far, stopped holding a chain, or left.
	 */
	private static void tickHeldChains() {
		for (Iterator<Map.Entry<Player, AbstractMinecart>> iterator = HELD.entrySet().iterator(); iterator.hasNext(); ) {
			Map.Entry<Player, AbstractMinecart> entry = iterator.next();
			Player player = entry.getKey();
			AbstractMinecart minecart = entry.getValue();
			int slot = Couplings.slotHeldBy(minecart, player);
			if (slot < 0 || !isStillHolding(player, minecart)) {
				if (slot >= 0) {
					Couplings.release(minecart, slot);
				}

				iterator.remove();
			}
		}
	}

	private static boolean isStillHolding(Player player, AbstractMinecart minecart) {
		return player.isAlive()
			&& minecart.isAlive()
			&& player.level() == minecart.level()
			&& player.distanceToSqr(minecart) <= Couplings.MAX_HOLD_DISTANCE * Couplings.MAX_HOLD_DISTANCE
			&& (player.getMainHandItem().is(ModTags.MINECART_COUPLERS) || player.getOffhandItem().is(ModTags.MINECART_COUPLERS));
	}
}
