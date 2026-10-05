package com.linkered.betterminecarts.client.render;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * A chain to draw, from {@code start} to {@code end}, relative to the render position of the minecart that owns it.
 */
public record CouplingChain(float startX, float startY, float startZ, float endX, float endY, float endZ, TextureAtlasSprite sprite) {
}
