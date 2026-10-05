package com.linkered.betterminecarts.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A comparator reads the fuel left in a furnace minecart on a detector rail, from 0 to 15, the same way it reads how
 * full a chest minecart is. Only checked when vanilla found no command block or container minecart, so those keep
 * priority. The detector rail already refreshes comparators every 20 ticks while a minecart is on it.
 */
@Mixin(DetectorRailBlock.class)
abstract class DetectorRailBlockMixin {
	/** Vanilla {@code MinecartFurnace.MAX_FUEL_TICKS}. */
	private static final float MAX_FUEL = 32000.0F;

	@Shadow
	protected abstract AABB getSearchBB(BlockPos pos);

	@Inject(method = "getAnalogOutputSignal", at = @At("RETURN"), cancellable = true)
	private void readFurnaceFuel(BlockState state, Level level, BlockPos pos, Direction direction, CallbackInfoReturnable<Integer> cir) {
		if (cir.getReturnValueI() != 0 || !state.getValue(DetectorRailBlock.POWERED)) {
			return;
		}
		List<MinecartFurnace> furnaces = level.getEntitiesOfClass(MinecartFurnace.class, getSearchBB(pos));
		if (!furnaces.isEmpty()) {
			int fuel = ((MinecartFurnaceAccessor) furnaces.getFirst()).bm$getFuel();
			cir.setReturnValue(Mth.lerpDiscrete(Mth.clamp(fuel / MAX_FUEL, 0.0F, 1.0F), 0, 15));
		}
	}
}
