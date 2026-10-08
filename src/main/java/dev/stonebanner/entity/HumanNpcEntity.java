package dev.stonebanner.entity;

import dev.stonebanner.citizen.BodyPart;
import dev.stonebanner.citizen.CitizenBrainState;
import dev.stonebanner.citizen.CitizenCommandController;
import dev.stonebanner.citizen.CitizenData;
import dev.stonebanner.citizen.CitizenDecisionPolicy;
import dev.stonebanner.citizen.CitizenHudCodec;
import dev.stonebanner.citizen.CitizenNeeds;
import dev.stonebanner.citizen.CitizenParticipation;
import dev.stonebanner.citizen.CitizenProfession;
import dev.stonebanner.citizen.CitizenSkill;
import dev.stonebanner.citizen.CitizenWorkController;
import dev.stonebanner.citizen.InjuryState;
import dev.stonebanner.citizen.WorkPriority;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.command.ActorCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * Player-proportioned Stone & Banner citizen entity.
 *
 * Visual identity and Citizen gameplay data are persisted separately so profession/work changes never
 * reroll appearance, and future Villager-backed citizens can reuse CitizenData without this renderer.
 */
public class HumanNpcEntity extends PathfinderMob {
    public static final int MVP_SKIN_POOL_SIZE = 32;
    /** Normal Citizen travel pace, calibrated to stay close to the player's regular walking speed. */
    public static final double BASE_MOVEMENT_SPEED = 0.34D;

    private static final String TAG_IDENTITY_INITIALIZED = "IdentityInitialized";
    private static final String TAG_VARIANT_SEED = "VariantSeed";
    private static final String TAG_SKIN_ID = "SkinId";
    private static final String TAG_SLIM_MODEL = "SlimModel";
    private static final String TAG_BRAIN_STATE = "BrainState";
    private static final String TAG_CITIZEN_DATA = "CitizenData";
    private static final double THREAT_SCAN_RANGE = 10.0D;
    private static final double FLEE_DISTANCE = 8.0D;

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
    private static final EntityDataAccessor<Integer> DATA_PROFESSION =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HUNGER =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FATIGUE =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DANGER =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WORK_TYPE =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SKILLS_PACKED =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_INJURIES_PACKED =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_PRIORITIES_PACKED =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.LONG);

    private static final EntityDataAccessor<Integer> DATA_DELIVERY_STATUS =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CARGO_COUNT =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_QUEUED_MOVES =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_ORDER_PREVIEW =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Integer> DATA_WORK_BLOCK_REASON =
            SynchedEntityData.defineId(HumanNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> DATA_OWNER=SynchedEntityData.defineId(HumanNpcEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_RETURNING=SynchedEntityData.defineId(HumanNpcEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SEEKING_BED=SynchedEntityData.defineId(HumanNpcEntity.class,EntityDataSerializers.BOOLEAN);
    private static final java.util.UUID COMBAT_MODIFIER=java.util.UUID.fromString("cfc2456b-c42f-4998-a2bf-2a99524fbe92");

    private final CitizenCommandController commandController;
    private final dev.stonebanner.citizen.CitizenOrderSequence orderSequence;
    private final CitizenWorkController workController;
    private final dev.stonebanner.citizen.CitizenFoodController foodController;
    private final CitizenData citizenData;
    private final dev.stonebanner.citizen.CitizenSleepController sleepController;
    private boolean medicalBleedingDamage;
    private int bleedingSeconds;

    public HumanNpcEntity(EntityType<? extends HumanNpcEntity> entityType, Level level) {
        super(entityType, level);
        citizenData = new CitizenData();
        commandController = new CitizenCommandController(this);
        workController = new CitizenWorkController(this);
        orderSequence = new dev.stonebanner.citizen.CitizenOrderSequence(this);
        foodController = new dev.stonebanner.citizen.CitizenFoodController(this);
        sleepController = new dev.stonebanner.citizen.CitizenSleepController(this);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
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
        entityData.define(DATA_PROFESSION, CitizenProfession.UNEMPLOYED.ordinal());
        entityData.define(DATA_HUNGER, 0);
        entityData.define(DATA_FATIGUE, 0);
        entityData.define(DATA_DANGER, 0);
        entityData.define(DATA_WORK_TYPE, -1);
        entityData.define(DATA_DELIVERY_STATUS, 0);
        entityData.define(DATA_CARGO_COUNT, 0);
        entityData.define(DATA_QUEUED_MOVES, 0);
        entityData.define(DATA_ORDER_PREVIEW, "");
        entityData.define(DATA_WORK_BLOCK_REASON, 0);
        entityData.define(DATA_OWNER,java.util.Optional.empty());entityData.define(DATA_RETURNING,false);
        entityData.define(DATA_SEEKING_BED,false);
        entityData.define(DATA_SKILLS_PACKED, 0);
        entityData.define(DATA_INJURIES_PACKED, 0);
        entityData.define(DATA_PRIORITIES_PACKED, 0L);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) {
            return;
        }

        updateCombatEfficiency();
        commandController.tick();
        orderSequence.tick();
        sleepController.tick();
        foodController.tick();
        if (citizenData.returningToVillage()) {
            if (tickCount % 20 == 0) tickCitizenSecond();
            dev.stonebanner.village.VillageReturnService.tick(this);
            syncHudData();
            return;
        }
        if (dev.stonebanner.geology.GeologyService.surveying(this) && !commandController.hasActiveCommand())
            getNavigation().stop();
        if (!sleepController.engaged() && !citizenData.health().needsRecovery() && !foodController.isSeeking() && !dev.stonebanner.geology.GeologyService.surveying(this)) workController.tick();
        if (tickCount % 20 == 0) {
            tickCitizenSecond();
        }
        syncHudData();
    }

    private void tickCitizenSecond() {
        CitizenNeeds needs = citizenData.needs();
        boolean resting = brainState() == CitizenBrainState.SLEEP && !sleepController.isSeeking();
        needs.tickSecond(resting);
        if (citizenData.health().recoverSecond(resting, !needs.isHungry())) heal(2.0F);
        if (citizenData.health().isBleeding()) {
            if (++bleedingSeconds >= 10) {
                bleedingSeconds = 0;
                medicalBleedingDamage = true;
                try { hurt(damageSources().generic(), 0.5F); } finally { medicalBleedingDamage = false; }
                if (!isAlive()) return;
            }
        } else bleedingSeconds = 0;

        Monster threat = nearestThreat();
        if (threat != null) {
            needs.setDanger(CitizenNeeds.MAX);
        }

        if (CitizenDecisionPolicy.isCriticalPreemption(citizenData) && workController.hasActiveJob()) {
            workController.interrupt(true);
        }

        if (!CitizenDecisionPolicy.isCriticalPreemption(citizenData)
                && !(brainState()==CitizenBrainState.SLEEP&&needs.fatigue()>25)
                && citizenData.participation() != CitizenParticipation.COMPANION
                && citizenData.home().hasHome()
                && !citizenData.home().contains(blockPosition())) {
            foodController.cancel(true);
            if (workController.hasActiveJob()) {
                workController.interrupt(true);
            }
            if (!commandController.hasActiveCommand()
                    || commandController.movementState() != CitizenBrainState.RETURN_HOME) {
                commandController.issueSystemMove(citizenData.home().homePos(), CitizenBrainState.RETURN_HOME);
            }
            return;
        }

        CitizenBrainState commandedState = sleepController.engaged() ? CitizenBrainState.SLEEP : foodController.isSeeking() ? CitizenBrainState.EAT : dev.stonebanner.geology.GeologyService.surveying(this)
                && !commandController.hasActiveCommand() ? CitizenBrainState.WORK : workController.hasActiveJob()
                ? CitizenBrainState.WORK
                : commandController.hasActiveCommand()
                ? commandController.movementState()
                : brainState()==CitizenBrainState.SLEEP&&needs.fatigue()>25?CitizenBrainState.SLEEP:CitizenBrainState.IDLE;
        CitizenBrainState decision = CitizenDecisionPolicy.chooseState(citizenData, commandedState);

        if (decision == CitizenBrainState.FLEE) {
            sleepController.cancel(true);
            foodController.cancel(true);
            if (workController.hasActiveJob()) {
                workController.interrupt(true);
            }
            if (threat != null) {
                fleeFrom(threat);
            } else {
                commandController.stop();
                setBrainState(CitizenBrainState.FLEE);
            }
            return;
        }

        if (decision == CitizenBrainState.DEFEND) {
            sleepController.cancel(true);
            foodController.cancel(true);
            if (workController.hasActiveJob()) {
                workController.interrupt(true);
            }
            if (threat != null) commandController.defendFrom(threat);
            else commandController.stop();
            setBrainState(CitizenBrainState.DEFEND);
            return;
        }

        if (commandController.isDefensiveAttack() && threat == null) commandController.stop();

        if (decision == CitizenBrainState.EAT) {
            sleepController.cancel(true);
            if (foodController.isSeeking() || CitizenDecisionPolicy.isCriticalPreemption(citizenData) || !commandController.hasActiveCommand()) {
                if (CitizenDecisionPolicy.isCriticalPreemption(citizenData) && workController.hasActiveJob()) {
                    workController.interrupt(true);
                }
                foodController.eatSecond();
            }
            return;
        }

        if (decision == CitizenBrainState.SLEEP) {
            foodController.cancel(true);
            if (CitizenDecisionPolicy.isCriticalPreemption(citizenData) || !commandController.hasActiveCommand()) {
                if (CitizenDecisionPolicy.isCriticalPreemption(citizenData) && workController.hasActiveJob()) {
                    workController.interrupt(true);
                }
                sleepController.sleepSecond();
            }
            return;
        }

        if (!commandController.hasActiveCommand() && !workController.hasActiveJob()) {
            setBrainState(decision);
        }
    }

    @Nullable
    private Monster nearestThreat() {
        return level().getEntitiesOfClass(
                        Monster.class,
                        getBoundingBox().inflate(THREAT_SCAN_RANGE),
                        Monster::isAlive
                ).stream()
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private void fleeFrom(Monster threat) {
        Vec3 away = position().subtract(threat.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = new Vec3(((variantSeed() & 1) == 0) ? 1.0D : -1.0D, 0.0D, 0.0D);
        }

        BlockPos target = BlockPos.containing(position().add(horizontal.normalize().scale(FLEE_DISTANCE)));
        if (!commandController.issueSystemMove(target, CitizenBrainState.FLEE)) {
            setBrainState(CitizenBrainState.FLEE);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag spawnTag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnData, spawnTag);
        ensureIdentity();
        citizenData.initializeStarterSkills(variantSeed());
        setBrainState(CitizenBrainState.IDLE);
        syncHudData();
        return result;
    }

    public boolean issueCommand(ActorCommand command) {
        if (!isAlive()) return false;
        if (!orderSequence.starting()) orderSequence.clear();
        if (citizenData.health().needsRecovery() && !(command instanceof ActorCommand.Stop)) return false;
        sleepController.cancel(true);
        foodController.cancel(true);
        if (level() instanceof ServerLevel serverLevel)
            dev.stonebanner.geology.GeologyService.cancelSurvey(serverLevel, getUUID());
        workController.interrupt(false);
        workController.clearBlockReason();
        return commandController.issue(command);
    }

    @Override
    protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        float before = getHealth();
        super.actuallyHurt(source, amount);
        float lost = before - getHealth();
        if (level().isClientSide || medicalBleedingDamage || lost <= 0 || citizenData == null) return;
        sleepController.cancel(true);
        BodyPart part;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            part = random.nextBoolean() ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
        } else if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                && source.getDirectEntity() != null && source.getDirectEntity().getY() > getY() + getBbHeight() * 0.75) {
            part = BodyPart.HEAD;
        } else if (source.getEntity() == null) {
            part = BodyPart.TORSO;
        } else {
            part = BodyPart.values()[random.nextInt(BodyPart.values().length)];
        }
        citizenData.health().damage(part, lost);
        if (citizenData.health().needsRecovery()) {
            workController.interrupt(true);
            commandController.stop();
            if (level() instanceof ServerLevel serverLevel) dev.stonebanner.geology.GeologyService.cancelSurvey(serverLevel, getUUID());
            setBrainState(CitizenDecisionPolicy.chooseState(citizenData, CitizenBrainState.IDLE));
        }
        syncHudData();
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!level().isClientSide) {
            foodController.cancel(true);
            sleepController.cancel(true);
            workController.interrupt(true);
            if (level() instanceof ServerLevel serverLevel)
                dev.stonebanner.geology.GeologyService.cancelSurvey(serverLevel, getUUID());
        }
        super.die(source);
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource source,
                                       int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        // Called by vanilla's loot pipeline (doMobLoot and Forge death/drop hooks still apply).
        var contents = citizenData.inventory().snapshot();
        var cargo = citizenData.inventory().haulCargoSnapshot();
        citizenData.inventory().clear();
        for (int slot = 0; slot < contents.size(); slot++) {
            var stack = contents.get(slot);
            if (!stack.isEmpty() && !net.minecraft.world.item.enchantment.EnchantmentHelper.hasVanishingCurse(stack)) {
                var item = spawnAtLocation(stack);
                int droppedSlot = slot;
                cargo.stream().filter(c -> c.slot() == droppedSlot).findFirst().ifPresent(c ->
                        dev.stonebanner.citizen.CargoOwnership.markDrop(item, c.owner()));
            }
        }
    }

    public CitizenCommandController commandController() {
        return commandController;
    }

    public dev.stonebanner.citizen.CitizenOrderSequence orderSequence() {
        return orderSequence;
    }

    public CitizenWorkController workController() {
        return workController;
    }

    public dev.stonebanner.citizen.CitizenFoodController foodController() { return foodController; }
    public dev.stonebanner.citizen.CitizenSleepController sleepController() { return sleepController; }

    @Override public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (sleepController != null && !level().isClientSide) sleepController.cancel(true);
        super.remove(reason);
    }

    public CitizenData citizenData() {
        return citizenData;
    }
    public boolean hudCanDirect(java.util.UUID player){return !entityData.get(DATA_RETURNING)&&entityData.get(DATA_OWNER).map(player::equals).orElse(true);}
    public boolean hudCanView(java.util.UUID player){return entityData.get(DATA_OWNER).map(player::equals).orElse(true);}
    private void updateCombatEfficiency(){
        var attribute=getAttribute(Attributes.ATTACK_DAMAGE);if(attribute==null)return;
        double amount=dev.stonebanner.citizen.CitizenSkillRules.combatRate(citizenData)-1;
        var old=attribute.getModifier(COMBAT_MODIFIER);if(old!=null&&Math.abs(old.getAmount()-amount)<1e-6)return;
        attribute.removeModifier(COMBAT_MODIFIER);
        if(Math.abs(amount)>1e-6)attribute.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(COMBAT_MODIFIER,"Citizen combat efficiency",amount,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity target){
        if(!isAlive())return false;
        updateCombatEfficiency();boolean hit=super.doHurtTarget(target);
        if(hit)citizenData.practice(CitizenSkill.COMBAT,2);return hit;
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

    public CitizenProfession hudProfession() {
        int id = entityData.get(DATA_PROFESSION);
        CitizenProfession[] values = CitizenProfession.values();
        return id >= 0 && id < values.length ? values[id] : CitizenProfession.UNEMPLOYED;
    }

    public dev.stonebanner.storage.DeliveryStatus hudDeliveryStatus() {
        return dev.stonebanner.storage.DeliveryStatus.byId(entityData.get(DATA_DELIVERY_STATUS));
    }
    public dev.stonebanner.citizen.WorkBlockReason hudWorkBlockReason() {
        return dev.stonebanner.citizen.WorkBlockReason.byId(entityData.get(DATA_WORK_BLOCK_REASON));
    }
    public int hudCargoCount() { return entityData.get(DATA_CARGO_COUNT); }
    public int hudQueuedMoves() { return entityData.get(DATA_QUEUED_MOVES); }
    public String hudOrderPreview() { return entityData.get(DATA_ORDER_PREVIEW); }
    public boolean hudSeekingBed() { return entityData.get(DATA_SEEKING_BED); }

    public int hudHunger() {
        return entityData.get(DATA_HUNGER);
    }

    public int hudFatigue() {
        return entityData.get(DATA_FATIGUE);
    }

    public int hudDanger() {
        return entityData.get(DATA_DANGER);
    }

    @Nullable
    public WorkType hudWorkType() {
        int id = entityData.get(DATA_WORK_TYPE);
        WorkType[] values = WorkType.values();
        return id >= 0 && id < values.length ? values[id] : null;
    }

    public int hudSkill(CitizenSkill skill) {
        return CitizenHudCodec.skill(entityData.get(DATA_SKILLS_PACKED), skill);
    }

    public InjuryState hudInjury(BodyPart part) {
        return CitizenHudCodec.injury(entityData.get(DATA_INJURIES_PACKED), part);
    }

    public WorkPriority hudWorkPriority(WorkType type) {
        return CitizenHudCodec.priority(entityData.get(DATA_PRIORITIES_PACKED), type);
    }

    public void setWorkPriority(WorkType type, WorkPriority priority) {
        if (level().isClientSide || type == null || priority == null) {
            return;
        }
        citizenData.setWorkPriority(type, priority);
        syncHudData();
    }

    private void syncHudData() {
        if (level().isClientSide) {
            return;
        }
        entityData.set(DATA_PROFESSION, citizenData.profession().ordinal());
        entityData.set(DATA_SEEKING_BED,sleepController.isSeeking());
        entityData.set(DATA_OWNER,citizenData.recruitedBy());entityData.set(DATA_RETURNING,citizenData.returningToVillage());
        entityData.set(DATA_HUNGER, (int) Math.round(citizenData.needs().hunger()));
        entityData.set(DATA_FATIGUE, (int) Math.round(citizenData.needs().fatigue()));
        entityData.set(DATA_DANGER, (int) Math.round(citizenData.needs().danger()));
        entityData.set(DATA_WORK_TYPE, workController.activeWorkType().map(Enum::ordinal).orElse(-1));
        entityData.set(DATA_DELIVERY_STATUS, workController.deliveryStatus().ordinal());
        entityData.set(DATA_WORK_BLOCK_REASON, workController.blockReason().ordinal());
        entityData.set(DATA_QUEUED_MOVES, commandController.queuedMoveCount() + orderSequence.pendingCount());
        entityData.set(DATA_ORDER_PREVIEW, orderSequence.preview());
        entityData.set(DATA_CARGO_COUNT, citizenData.inventory().haulCargoSnapshot().stream()
                .mapToInt(cargo -> cargo.stack().getCount()).sum());
        entityData.set(DATA_SKILLS_PACKED, CitizenHudCodec.packSkills(citizenData));
        entityData.set(DATA_INJURIES_PACKED, CitizenHudCodec.packInjuries(citizenData.health()));
        entityData.set(DATA_PRIORITIES_PACKED, CitizenHudCodec.packPriorities(citizenData));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_IDENTITY_INITIALIZED, entityData.get(DATA_IDENTITY_INITIALIZED));
        tag.putInt(TAG_VARIANT_SEED, variantSeed());
        tag.putInt(TAG_SKIN_ID, skinId());
        tag.putBoolean(TAG_SLIM_MODEL, usesSlimModel());
        tag.putString(TAG_BRAIN_STATE, brainState().serializedName());
        tag.put(TAG_CITIZEN_DATA, citizenData.save());
        tag.putInt("MedicalBleedingSeconds", bleedingSeconds);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        sleepController.cancel(true);
        bleedingSeconds = Math.max(0, Math.min(9, tag.getInt("MedicalBleedingSeconds")));
        foodController.cancel(true);
        workController.interrupt(true);
        orderSequence.clear();
        // Older worlds persisted the prototype's 0.10 base speed in entity NBT. Reset only the base
        // value here; temporary attribute modifiers and the injury multiplier continue to work.
        AttributeInstance movementSpeed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(BASE_MOVEMENT_SPEED);
        }

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

        if (tag.contains(TAG_CITIZEN_DATA, Tag.TAG_COMPOUND)) {
            citizenData.load(tag.getCompound(TAG_CITIZEN_DATA));
        } else if (entityData.get(DATA_IDENTITY_INITIALIZED)) {
            citizenData.initializeStarterSkills(variantSeed());
        }

        // Orders and reservations are runtime-only; don't restore a phantom WORK/MOVE/FOLLOW state.
        setBrainState(CitizenDecisionPolicy.chooseState(citizenData,
            tag.getString(TAG_BRAIN_STATE).equals(CitizenBrainState.SLEEP.serializedName())&&citizenData.needs().fatigue()>25?CitizenBrainState.SLEEP:CitizenBrainState.IDLE));
        syncHudData();
    }
}
