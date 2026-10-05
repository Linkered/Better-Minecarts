package com.linkered.betterminecarts.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Turns on the "Minecart Improvements" experiment by default when a world is created, since couplings are built on
 * it. It shows up already ticked in the Experiments screen and can still be turned off there. Existing worlds are never
 * changed: Minecraft only lets experiments be chosen when a world is created.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
	@Unique
	private static final String MINECART_IMPROVEMENTS = "minecart_improvements";

	@WrapMethod(method = "configurePackRepository")
	private static WorldDataConfiguration enableMinecartImprovements(
		PackRepository packRepository, WorldDataConfiguration initialDataConfig, boolean initMode, boolean safeMode, Operation<WorldDataConfiguration> original
	) {
		DataPackConfig packs = initialDataConfig.dataPacks();
		if (initMode && !safeMode && !packs.getEnabled().contains(MINECART_IMPROVEMENTS) && !packs.getDisabled().contains(MINECART_IMPROVEMENTS)) {
			List<String> enabled = ImmutableList.<String>builder().addAll(packs.getEnabled()).add(MINECART_IMPROVEMENTS).build();
			initialDataConfig = new WorldDataConfiguration(new DataPackConfig(enabled, packs.getDisabled()), initialDataConfig.enabledFeatures());
		}

		return original.call(packRepository, initialDataConfig, initMode, safeMode);
	}
}
