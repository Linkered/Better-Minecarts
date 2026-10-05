package com.linkered.betterminecarts.client.render;

import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Chains added to every {@link MinecartRenderState} by {@code MinecartRenderStateMixin}, one per coupling slot.
 */
public interface CoupledMinecartRenderState {
	static CoupledMinecartRenderState of(MinecartRenderState state) {
		return (CoupledMinecartRenderState) state;
	}

	@Nullable CouplingChain bm$chain(int slot);

	void bm$setChain(int slot, @Nullable CouplingChain chain);
}
