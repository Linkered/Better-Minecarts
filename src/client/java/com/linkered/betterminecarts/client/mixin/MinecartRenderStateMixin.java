package com.linkered.betterminecarts.client.mixin;

import com.linkered.betterminecarts.client.render.CoupledMinecartRenderState;
import com.linkered.betterminecarts.client.render.CouplingChain;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecartRenderState.class)
abstract class MinecartRenderStateMixin implements CoupledMinecartRenderState {
	@Unique
	private @Nullable CouplingChain chain0;
	@Unique
	private @Nullable CouplingChain chain1;

	@Override
	public @Nullable CouplingChain bm$chain(int slot) {
		return slot == 0 ? chain0 : chain1;
	}

	@Override
	public void bm$setChain(int slot, @Nullable CouplingChain chain) {
		if (slot == 0) {
			chain0 = chain;
		} else {
			chain1 = chain;
		}
	}
}
