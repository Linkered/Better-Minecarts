package com.linkered.betterminecarts.mixin;

import com.linkered.betterminecarts.coupling.Couplings;
import com.linkered.betterminecarts.furnace.FurnaceChunkLoading;
import com.linkered.betterminecarts.furnace.PausableFurnace;
import com.linkered.betterminecarts.furnace.Tender;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A furnace minecart in a train pushes with a steady force shared by the whole train, instead of jumping to its top
 * speed every tick: long or heavy trains are slower, and more furnace minecarts make them faster. A furnace minecart
 * on its own behaves as in vanilla.
 *
 * <p>Also fixes furnace minecarts losing their push for good on tight S-curves, where the minecart turns 90 degrees in
 * a single tick and the vanilla code projects the push onto a perpendicular direction, leaving it at zero.
 *
 * <p>A coupled hopper minecart keeps it burning ({@link Tender}), a powered activator rail pauses it
 * ({@link PausableFurnace}), it also burns blocks of coal, and while burning it keeps the chunks around it loaded
 * ({@link FurnaceChunkLoading}; in a train, {@code TrainPhysics} does it for the whole train).
 */
@Mixin(MinecartFurnace.class)
abstract class MinecartFurnaceMixin extends AbstractMinecart implements PausableFurnace {
	private static final String PAUSED_KEY = "BetterMinecartsPaused";
	/**
	 * A block of coal burns as long as 8 pieces of coal, not 9: that would be more than the vanilla maximum of 32000
	 * ticks, so it would never fit. 8 fit when less than 3200 ticks are left.
	 */
	private static final int COAL_BLOCK_FUEL_TICKS = 8 * 3600;

	@Shadow
	public Vec3 push;

	private boolean bm$paused;

	private MinecartFurnaceMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Shadow
	protected abstract void setHasFuel(boolean fuel);

	@Override
	public boolean bm$isPaused() {
		return bm$paused;
	}

	/**
	 * Called every tick on an activator rail: a powered one pauses this furnace minecart, an unpowered one resumes it,
	 * the way it turns a hopper minecart off and on.
	 */
	@Override
	public void activateMinecart(ServerLevel level, int x, int y, int z, boolean powered) {
		if (powered != bm$paused) {
			bm$paused = powered;
			if (powered) {
				// Burning resumes on the next tick, which lights it again if it still has fuel.
				setHasFuel(false);
			}
		}
	}

	/**
	 * While paused, skips the server part of the tick: the fuel does not burn, the push is kept, and the furnace stays
	 * unlit, which also stops the smoke on clients.
	 */
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z"))
	private boolean skipBurningWhilePaused(boolean clientSide) {
		return clientSide || bm$paused;
	}

	@ModifyReturnValue(method = "calculateNewPushAlong", at = @At("RETURN"))
	private Vec3 keepPushOnSharpTurns(Vec3 newPush, Vec3 deltaMovement) {
		if (newPush.horizontalDistanceSqr() < 1.0E-7 && push.horizontalDistanceSqr() > 1.0E-4 && deltaMovement.horizontalDistanceSqr() > 0.001) {
			// Keep pushing the way the minecart is going: it only turned, it did not reverse.
			return deltaMovement.horizontal().normalize().scale(push.length());
		}

		return newPush;
	}

	/**
	 * In a train, the push of a furnace minecart is shared by the whole train ({@code TrainPhysics}), so here it only
	 * slows down like any other minecart.
	 */
	@Inject(method = "applyNaturalSlowdown", at = @At("HEAD"), cancellable = true)
	private void leavePushToTrain(Vec3 deltaMovement, CallbackInfoReturnable<Vec3> cir) {
		if (level() instanceof ServerLevel && Couplings.isCoupled(this)) {
			cir.setReturnValue(super.applyNaturalSlowdown(deltaMovement));
		} else if (bm$paused) {
			// Rolls on like a furnace minecart without fuel.
			cir.setReturnValue(super.applyNaturalSlowdown(deltaMovement.multiply(0.98, 0.0, 0.98)));
		}
	}

	/**
	 * The fuel a piece adds, both to check that it fits and to add it. Every item of
	 * {@code #minecraft:furnace_minecart_fuel} adds the same in vanilla.
	 */
	@ModifyExpressionValue(method = "addFuel", at = @At(value = "CONSTANT", args = "intValue=3600"))
	private int burnCoalBlockLonger(int fuelTicks, @Local(argsOnly = true) ItemStack stack) {
		return stack.is(Items.COAL_BLOCK) ? COAL_BLOCK_FUEL_TICKS : fuelTicks;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void takeFuelFromTenderAndLoadChunks(CallbackInfo ci) {
		if (level() instanceof ServerLevel level) {
			MinecartFurnace self = (MinecartFurnace) (Object) this;
			Tender.tick(level, self);
			if (!Couplings.isCoupled(self) && FurnaceChunkLoading.isBurning(self)) {
				FurnaceChunkLoading.keepLoaded(level, self, level.getGameTime());
			}
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void savePaused(ValueOutput output, CallbackInfo ci) {
		if (bm$paused) {
			output.putBoolean(PAUSED_KEY, true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void loadPaused(ValueInput input, CallbackInfo ci) {
		bm$paused = input.getBooleanOr(PAUSED_KEY, false);
	}
}
