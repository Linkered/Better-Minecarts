package com.linkered.betterminecarts.mixin;

import com.linkered.betterminecarts.coupling.CouplingDispensing;
import com.linkered.betterminecarts.furnace.FurnaceRefueling;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A dispenser refuels a furnace minecart in front of it with any item of {@code #minecraft:furnace_minecart_fuel}
 * ({@link FurnaceRefueling}), and couples or uncouples minecarts with a chain or shears ({@link CouplingDispensing}).
 * Checked when dispensing rather than registered per item, so the tags can be changed by data packs and the item keeps
 * its usual behavior when there is no minecart to use it on. Droppers have their own {@code dispenseFrom} and are left
 * alone.
 */
@Mixin(DispenserBlock.class)
abstract class DispenserBlockMixin {
	@WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/dispenser/DispenseItemBehavior;dispense(Lnet/minecraft/core/dispenser/BlockSource;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack useOnMinecart(DispenseItemBehavior behavior, BlockSource source, ItemStack stack, Operation<ItemStack> original) {
		return FurnaceRefueling.dispense(source, stack) || CouplingDispensing.dispense(source, stack) ? stack : original.call(behavior, source, stack);
	}
}
