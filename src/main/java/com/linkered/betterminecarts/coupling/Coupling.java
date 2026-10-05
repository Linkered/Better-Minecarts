package com.linkered.betterminecarts.coupling;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One coupling slot of a minecart. A minecart has two of them, one per end.
 *
 * <p>A slot is in one of three states:
 * <ul>
 *     <li><b>free</b>: {@link #partnerId} and {@link #holder} are both {@code null};</li>
 *     <li><b>coupled</b>: {@link #partnerId} points to the other minecart. Only one of the two minecarts owns the chain
 *     ({@link #chain} is not empty), and that one is the minecart that drops it and renders it;</li>
 *     <li><b>held</b>: a player is holding the free end of the chain ({@link #holder}) while looking for a second
 *     minecart. Never saved: it only lasts while the player keeps a chain in hand.</li>
 * </ul>
 *
 * <p>Only touched on the logical server.
 */
public final class Coupling {
	/**
	 * Save format of a coupled slot.
	 */
	static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		UUIDUtil.CODEC.fieldOf("Partner").forGetter(Saved::partner),
		ItemStack.OPTIONAL_CODEC.optionalFieldOf("Chain", ItemStack.EMPTY).forGetter(Saved::chain)
	).apply(instance, Saved::new));

	/**
	 * The UUID of the coupled minecart, or {@code null} if this slot is not coupled.
	 */
	@Nullable UUID partnerId;
	/**
	 * Cached reference to the coupled minecart. May be {@code null} or removed while {@link #partnerId} is set, for
	 * example when the partner is in an unloaded chunk; it is looked up again by UUID when needed.
	 */
	@Nullable AbstractMinecart partner;
	/**
	 * The player holding the free end of the chain, or {@code null}.
	 */
	@Nullable Player holder;
	/**
	 * The chain item, only stored by the minecart that owns the coupling (or that is being held).
	 */
	ItemStack chain = ItemStack.EMPTY;
	/**
	 * For how many train ticks the partner has been missing while every chunk it could be in is loaded.
	 */
	int missingTicks;

	public boolean isFree() {
		return partnerId == null && holder == null;
	}

	public boolean isCoupled() {
		return partnerId != null;
	}

	void clear() {
		partnerId = null;
		partner = null;
		holder = null;
		chain = ItemStack.EMPTY;
		missingTicks = 0;
	}

	record Saved(UUID partner, ItemStack chain) {
	}
}
