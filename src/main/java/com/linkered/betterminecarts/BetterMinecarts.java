package com.linkered.betterminecarts;

import com.linkered.betterminecarts.coupling.CouplingInteractions;
import com.linkered.betterminecarts.furnace.FurnaceChunkLoading;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

/**
 * Main entrypoint of Better Minecarts, run by Fabric Loader on both client and server.
 */
public final class BetterMinecarts implements ModInitializer {
	/**
	 * The mod ID, also used as the namespace of every data file the mod adds.
	 */
	public static final String MOD_ID = "better-minecarts";

	/**
	 * Created by Fabric Loader through the {@code main} entrypoint declared in {@code fabric.mod.json}.
	 */
	public BetterMinecarts() {
	}

	@Override
	public void onInitialize() {
		CouplingInteractions.init();
		FurnaceChunkLoading.init();
	}

	/**
	 * Creates an identifier in the mod's namespace.
	 *
	 * @param path the path of the identifier
	 * @return {@code better-minecarts:<path>}
	 */
	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
