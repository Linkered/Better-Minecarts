package com.linkered.betterminecarts.mixin;

import com.linkered.betterminecarts.coupling.CoupledMinecart;
import com.linkered.betterminecarts.coupling.Coupling;
import com.linkered.betterminecarts.coupling.Couplings;
import com.linkered.betterminecarts.coupling.PathCache;
import com.linkered.betterminecarts.coupling.TrainPhysics;
import com.linkered.betterminecarts.furnace.FurnaceChunkLoading;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds two coupling slots to every minecart, saves them, syncs them to clients and keeps minecarts of the same train
 * from colliding with or pushing each other. Also holds the chunk ticket of minecarts pulled by a burning furnace
 * minecart ({@link FurnaceChunkLoading}).
 */
@Mixin(AbstractMinecart.class)
abstract class AbstractMinecartMixin extends VehicleEntity implements CoupledMinecart, FurnaceChunkLoading.Holder {
	@Unique
	private static final EntityDataAccessor<OptionalInt> PARTNER_0 = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
	@Unique
	private static final EntityDataAccessor<OptionalInt> PARTNER_1 = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.OPTIONAL_UNSIGNED_INT);
	@Unique
	private static final EntityDataAccessor<Optional<BlockState>> CHAIN_0 = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);
	@Unique
	private static final EntityDataAccessor<Optional<BlockState>> CHAIN_1 = SynchedEntityData.defineId(AbstractMinecart.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);

	@Unique
	private final Coupling[] couplings = {new Coupling(), new Coupling()};
	@Unique
	private long trainTick = Long.MIN_VALUE;
	@Unique
	private int trainId;
	@Unique
	private final PathCache pathCache = new PathCache();
	@Unique
	private long ticketChunk;
	@Unique
	private long ticketRenewal = Long.MIN_VALUE;

	private AbstractMinecartMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void defineCouplingData(SynchedEntityData.Builder entityData, CallbackInfo ci) {
		entityData.define(PARTNER_0, OptionalInt.empty());
		entityData.define(PARTNER_1, OptionalInt.empty());
		entityData.define(CHAIN_0, Optional.empty());
		entityData.define(CHAIN_1, Optional.empty());
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void saveCouplings(ValueOutput output, CallbackInfo ci) {
		Couplings.save((AbstractMinecart) (Object) this, output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void loadCouplings(ValueInput input, CallbackInfo ci) {
		Couplings.load((AbstractMinecart) (Object) this, input);
	}

	@Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
	private void ignoreCoupledCollision(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (isSameTrain(entity)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void ignoreCoupledPush(Entity entity, CallbackInfo ci) {
		if (isSameTrain(entity)) {
			ci.cancel();
		}
	}

	@Unique
	private boolean isSameTrain(Entity entity) {
		AbstractMinecart self = (AbstractMinecart) (Object) this;
		return Couplings.isCoupledTo(self, entity) || TrainPhysics.inSameTrain(self, entity);
	}

	@Override
	public void onRemoval(Entity.RemovalReason reason) {
		super.onRemoval(reason);
		Couplings.onRemoved((AbstractMinecart) (Object) this, reason);
	}

	@Override
	public Coupling[] bm$couplings() {
		return couplings;
	}

	@Override
	public long bm$trainTick() {
		return trainTick;
	}

	@Override
	public void bm$setTrainTick(long gameTime) {
		trainTick = gameTime;
	}

	@Override
	public int bm$trainId() {
		return trainId;
	}

	@Override
	public void bm$setTrainId(int trainId) {
		this.trainId = trainId;
	}

	@Override
	public PathCache bm$pathCache() {
		return pathCache;
	}

	@Override
	public OptionalInt bm$syncedPartner(int slot) {
		return entityData.get(slot == 0 ? PARTNER_0 : PARTNER_1);
	}

	@Override
	public Optional<BlockState> bm$syncedChain(int slot) {
		return entityData.get(slot == 0 ? CHAIN_0 : CHAIN_1);
	}

	@Override
	public void bm$setSynced(int slot, OptionalInt partner, Optional<BlockState> chain) {
		entityData.set(slot == 0 ? PARTNER_0 : PARTNER_1, partner);
		entityData.set(slot == 0 ? CHAIN_0 : CHAIN_1, chain);
	}

	@Override
	public long bm$ticketChunk() {
		return ticketChunk;
	}

	@Override
	public long bm$ticketRenewal() {
		return ticketRenewal;
	}

	@Override
	public void bm$setTicket(long chunk, long renewal) {
		ticketChunk = chunk;
		ticketRenewal = renewal;
	}
}
