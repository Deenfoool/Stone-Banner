package dev.stonebanner.entity;

import dev.stonebanner.citizen.CitizenBrainState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Player-proportioned Stone & Banner citizen entity.
 *
 * Identity is generated once, synchronized to clients and persisted in NBT so an NPC never changes
 * appearance after a reload or profession change. Work/profession systems are deliberately separate.
 */
public class HumanNpcEntity extends PathfinderMob {
    public static final int MVP_SKIN_POOL_SIZE = 32;

    private static final String TAG_IDENTITY_INITIALIZED = "IdentityInitialized";
    private static final String TAG_VARIANT_SEED = "VariantSeed";
    private static final String TAG_SKIN_ID = "SkinId";
    private static final String TAG_SLIM_MODEL = "SlimModel";
    private static final String TAG_BRAIN_STATE = "BrainState";

    private static final EntityDataAccessor<Boolean> DATA_IDENTITY_INITIALIZED =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_VARIANT_SEED =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKIN_ID =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SLIM_MODEL =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BRAIN_STATE =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);

    public HumanNpcEntity(EntityType<? extends HumanNpcEntity> entityType, Level level) {
        super(entityType, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.10D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IDENTITY_INITIALIZED, false);
        entityData.define(DATA_VARIANT_SEED, 0);
        entityData.define(DATA_SKIN_ID, 0);
        entityData.define(DATA_SLIM_MODEL, false);
        entityData.define(DATA_BRAIN_STATE, CitizenBrainState.IDLE.ordinal());
    }

    @Override
    protected void registerGoals() {
        // Citizen AI will own movement/work goals. These two only keep an idle NPC visually alive.
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag spawnTag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnData, spawnTag);
        ensureIdentity();
        setBrainState(CitizenBrainState.IDLE);
        return result;
    }

    public void ensureIdentity() {
        if (entityData.get(DATA_IDENTITY_INITIALIZED)) {
            return;
        }

        int seed = random.nextInt();
        entityData.set(DATA_VARIANT_SEED, seed);
        entityData.set(DATA_SKIN_ID, Math.floorMod(seed, MVP_SKIN_POOL_SIZE));
        entityData.set(DATA_SLIM_MODEL, ((seed >>> 5) & 1) == 1);
        entityData.set(DATA_IDENTITY_INITIALIZED, true);
    }

    public int variantSeed() {
        return entityData.get(DATA_VARIANT_SEED);
    }

    public int skinId() {
        return entityData.get(DATA_SKIN_ID);
    }

    public boolean usesSlimModel() {
        return entityData.get(DATA_SLIM_MODEL);
    }

    public CitizenBrainState brainState() {
        return CitizenBrainState.byId(entityData.get(DATA_BRAIN_STATE));
    }

    public void setBrainState(CitizenBrainState state) {
        entityData.set(DATA_BRAIN_STATE, (state == null ? CitizenBrainState.IDLE : state).ordinal());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_IDENTITY_INITIALIZED, entityData.get(DATA_IDENTITY_INITIALIZED));
        tag.putInt(TAG_VARIANT_SEED, variantSeed());
        tag.putInt(TAG_SKIN_ID, skinId());
        tag.putBoolean(TAG_SLIM_MODEL, usesSlimModel());
        tag.putString(TAG_BRAIN_STATE, brainState().serializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        boolean hasIdentity = tag.getBoolean(TAG_IDENTITY_INITIALIZED)
                || tag.contains(TAG_VARIANT_SEED, Tag.TAG_INT)
                || tag.contains(TAG_SKIN_ID, Tag.TAG_INT);
        if (hasIdentity) {
            entityData.set(DATA_VARIANT_SEED, tag.getInt(TAG_VARIANT_SEED));
            entityData.set(DATA_SKIN_ID, Math.floorMod(tag.getInt(TAG_SKIN_ID), MVP_SKIN_POOL_SIZE));
            entityData.set(DATA_SLIM_MODEL, tag.getBoolean(TAG_SLIM_MODEL));
            entityData.set(DATA_IDENTITY_INITIALIZED, true);
        } else if (!level().isClientSide) {
            ensureIdentity();
        }

        setBrainState(CitizenBrainState.fromSerializedName(tag.getString(TAG_BRAIN_STATE)));
    }
}
