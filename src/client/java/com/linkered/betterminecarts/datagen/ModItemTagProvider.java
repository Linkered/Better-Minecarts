package com.linkered.betterminecarts.datagen;

import com.linkered.betterminecarts.ModTags;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.references.BlockItemIds;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.ItemTags;

/**
 * Every chain, iron or copper in any weathering state, couples minecarts, and furnace minecarts also burn blocks of
 * coal.
 */
final class ModItemTagProvider extends FabricTagsProvider.ItemTagsProvider {
	ModItemTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		// A vanilla tag, so it is not known to the data generator: reference it without validating.
		builder(ModTags.MINECART_COUPLERS).forceAddTag(BlockItemTags.CHAINS.item());
		builder(ItemTags.FURNACE_MINECART_FUEL).add(BlockItemIds.COAL_BLOCK);
	}
}
