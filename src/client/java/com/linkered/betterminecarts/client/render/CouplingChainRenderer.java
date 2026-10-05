package com.linkered.betterminecarts.client.render;

import com.linkered.betterminecarts.coupling.CoupledMinecart;
import com.linkered.betterminecarts.coupling.Couplings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the chain of each coupling, from the minecart that owns it.
 *
 * <p>The chain looks like a chain block stretched between both minecarts: two crossed planes using the chain block's
 * own texture (so resource packs and every copper variant work), repeated once per block of length.
 */
public final class CouplingChainRenderer {
	/**
	 * Height of the chain above the minecart position, around the middle of the minecart body.
	 */
	private static final float ATTACH_HEIGHT = 0.35F;
	/**
	 * Distance from the minecart center to the point where the chain is attached, just inside the minecart's end.
	 */
	private static final float ATTACH_OFFSET = 0.55F;
	/**
	 * Half the width of each plane, matching the 3 pixel wide chain block model.
	 */
	private static final float HALF_WIDTH = 1.5F / 16.0F;

	private CouplingChainRenderer() {
	}

	public static void extract(AbstractMinecart minecart, MinecartRenderState state, float partialTicks) {
		CoupledMinecart coupled = CoupledMinecart.of(minecart);
		CoupledMinecartRenderState chains = CoupledMinecartRenderState.of(state);
		for (int slot = 0; slot < Couplings.SLOTS; slot++) {
			chains.bm$setChain(slot, extractChain(minecart, coupled, slot, state, partialTicks));
		}
	}

	private static @Nullable CouplingChain extractChain(AbstractMinecart minecart, CoupledMinecart coupled, int slot, MinecartRenderState state, float partialTicks) {
		Optional<BlockState> block = coupled.bm$syncedChain(slot);
		OptionalInt partnerId = coupled.bm$syncedPartner(slot);
		if (block.isEmpty() || partnerId.isEmpty()) {
			return null;
		}

		Entity other = minecart.level().getEntity(partnerId.getAsInt());
		if (other == null) {
			return null;
		}

		// The pose stack is at the minecart's render position when the chain is submitted.
		Vec3 origin = state.renderPos != null ? state.renderPos : new Vec3(state.x, state.y, state.z);
		Vec3 target = other instanceof AbstractMinecart otherMinecart ? renderPosition(otherMinecart, partialTicks) : other.getRopeHoldPosition(partialTicks);
		double dx = target.x - origin.x;
		double dy = target.y - origin.y;
		double dz = target.z - origin.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float dirX = horizontal > 1.0E-4 ? (float) (dx / horizontal) : 0.0F;
		float dirZ = horizontal > 1.0E-4 ? (float) (dz / horizontal) : 0.0F;
		float endX = (float) dx;
		float endY = (float) dy;
		float endZ = (float) dz;
		if (other instanceof AbstractMinecart) {
			endX -= dirX * ATTACH_OFFSET;
			endY += ATTACH_HEIGHT;
			endZ -= dirZ * ATTACH_OFFSET;
		}

		TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(block.get()).sprite();
		return new CouplingChain(dirX * ATTACH_OFFSET, ATTACH_HEIGHT, dirZ * ATTACH_OFFSET, endX, endY, endZ, sprite);
	}

	public static void submit(MinecartRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		CoupledMinecartRenderState chains = CoupledMinecartRenderState.of(state);
		for (int slot = 0; slot < Couplings.SLOTS; slot++) {
			CouplingChain chain = chains.bm$chain(slot);
			if (chain != null) {
				int light = state.lightCoords;
				collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(chain.sprite().atlasLocation()), (pose, buffer) -> render(pose, buffer, chain, light));
			}
		}
	}

	/**
	 * Whether a chain owned by this minecart can be seen even though the minecart itself is outside the view.
	 */
	public static boolean isChainVisible(AbstractMinecart minecart, Frustum frustum) {
		CoupledMinecart coupled = CoupledMinecart.of(minecart);
		for (int slot = 0; slot < Couplings.SLOTS; slot++) {
			OptionalInt partnerId = coupled.bm$syncedPartner(slot);
			if (partnerId.isPresent() && coupled.bm$syncedChain(slot).isPresent()) {
				Entity other = minecart.level().getEntity(partnerId.getAsInt());
				if (other != null && frustum.isVisible(minecart.getBoundingBox().minmax(other.getBoundingBox()))) {
					return true;
				}
			}
		}

		return false;
	}

	private static Vec3 renderPosition(AbstractMinecart minecart, float partialTicks) {
		return minecart.getBehavior() instanceof NewMinecartBehavior behavior && behavior.cartHasPosRotLerp()
			? behavior.getCartLerpPosition(partialTicks)
			: minecart.getPosition(partialTicks);
	}

	private static void render(PoseStack.Pose pose, VertexConsumer buffer, CouplingChain chain, int light) {
		float dx = chain.endX() - chain.startX();
		float dy = chain.endY() - chain.startY();
		float dz = chain.endZ() - chain.startZ();
		float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1.0E-3F) {
			return;
		}

		// Axis of the chain, and two directions perpendicular to it: u is horizontal, v is "up" relative to the chain.
		float ax = dx / length;
		float ay = dy / length;
		float az = dz / length;
		float ux = -az;
		float uz = ax;
		float horizontal = Mth.sqrt(ux * ux + uz * uz);
		if (horizontal < 1.0E-4F) {
			ux = 1.0F;
			uz = 0.0F;
		} else {
			ux /= horizontal;
			uz /= horizontal;
		}

		float vx = -uz * ay;
		float vy = uz * ax - ux * az;
		float vz = ux * ay;

		// Two planes crossed at 90 degrees and turned 45 degrees around the axis, like the chain block model.
		float scale = HALF_WIDTH * Mth.SQRT_OF_TWO * 0.5F;
		TextureAtlasSprite sprite = chain.sprite();
		renderPlane(pose, buffer, chain, ax, ay, az, length, (ux + vx) * scale, vy * scale, (uz + vz) * scale, sprite.getU(0.0F), sprite.getU(3.0F / 16.0F), sprite, light);
		renderPlane(pose, buffer, chain, ax, ay, az, length, (ux - vx) * scale, -vy * scale, (uz - vz) * scale, sprite.getU(3.0F / 16.0F), sprite.getU(6.0F / 16.0F), sprite, light);
	}

	private static void renderPlane(
		PoseStack.Pose pose,
		VertexConsumer buffer,
		CouplingChain chain,
		float ax,
		float ay,
		float az,
		float length,
		float wx,
		float wy,
		float wz,
		float u0,
		float u1,
		TextureAtlasSprite sprite,
		int light
	) {
		// The normal is perpendicular to both the axis and the width of the plane.
		float nx = ay * wz - az * wy;
		float ny = az * wx - ax * wz;
		float nz = ax * wy - ay * wx;
		float normalLength = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		nx /= normalLength;
		ny /= normalLength;
		nz /= normalLength;

		// One quad per block of length, so the 16 pixel tall texture repeats instead of stretching.
		float v0 = sprite.getV(0.0F);
		for (float from = 0.0F; from < length; from += 1.0F) {
			float to = Math.min(from + 1.0F, length);
			float v1 = sprite.getV(to - from);
			float x0 = chain.startX() + ax * from;
			float y0 = chain.startY() + ay * from;
			float z0 = chain.startZ() + az * from;
			float x1 = chain.startX() + ax * to;
			float y1 = chain.startY() + ay * to;
			float z1 = chain.startZ() + az * to;
			vertex(pose, buffer, x0 - wx, y0 - wy, z0 - wz, u0, v0, nx, ny, nz, light);
			vertex(pose, buffer, x0 + wx, y0 + wy, z0 + wz, u1, v0, nx, ny, nz, light);
			vertex(pose, buffer, x1 + wx, y1 + wy, z1 + wz, u1, v1, nx, ny, nz, light);
			vertex(pose, buffer, x1 - wx, y1 - wy, z1 - wz, u0, v1, nx, ny, nz, light);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
		buffer.addVertex(pose, x, y, z)
			.setColor(255, 255, 255, 255)
			.setUv(u, v)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(pose, nx, ny, nz);
	}
}
