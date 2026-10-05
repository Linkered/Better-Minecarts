package com.linkered.betterminecarts.client.mixin;

import com.linkered.betterminecarts.client.render.CouplingChainRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps drawing a minecart outside the view while the chain it owns is still visible, like vanilla does for leads.
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
	@ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
	private boolean renderVisibleChains(boolean original, Entity entity, Frustum frustum, double camX, double camY, double camZ, float partialTicks) {
		return original
			|| entity instanceof AbstractMinecart minecart && entity.shouldRender(camX, camY, camZ) && CouplingChainRenderer.isChainVisible(minecart, frustum);
	}
}
