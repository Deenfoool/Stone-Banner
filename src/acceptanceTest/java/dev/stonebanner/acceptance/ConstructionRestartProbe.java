package dev.stonebanner.acceptance;

import dev.stonebanner.citizen.*;
import dev.stonebanner.construction.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.entity.ModEntities;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/** Checks a partially furnished cottage across three real dedicated-server processes. */
@Mod.EventBusSubscriber(modid = "stonebanner")
public final class ConstructionRestartProbe {
    private static final String STAGE = System.getProperty("stonebanner.persistenceScenario", "forestry").equals("construction")
            ? System.getProperty("stonebanner.persistenceStage", "disabled") : "disabled";
    private static final Path STATE = Path.of("persistence-probe.properties");
    private static final BlockPos ORIGIN = new BlockPos(5, 65, 4), CHEST = new BlockPos(2, 65, 4);
    private static final AABB ARENA = new AABB(0, 64, 0, 16, 73, 16);
    private static final Properties state = new Properties();
    private static MinecraftServer server;
    private static HumanNpcEntity preparingWorker;
    private static boolean finished, restoredClean;
    private static int ticks, partialSince = -1, completedAt = -1;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (STAGE.equals("disabled")) return;
        server = event.getServer();
        try {
            require(server.getWorldData().getLevelName().equals("stonebanner-construction-probe"), "Non-probe world refused");
            require(java.util.Set.of("prepare", "resume", "completed").contains(STAGE), "Unknown stage");
            if (!STAGE.equals("prepare")) try (var in = Files.newInputStream(STATE)) { state.load(in); }
            server.overworld().setChunkForced(0, 0, true);
            if (STAGE.equals("resume")) {
                require(!plan().completed && furniture() == 1, "Partial construction did not survive restart");
                require(CitizenJobBoard.forLevel(server.overworld()).job(jobId()).isPresent(), "Persistent construction job missing");
            }
        } catch (Exception error) { fail(error); }
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!STAGE.equals("prepare") && event.loadedFromDisk() && event.getEntity() instanceof HumanNpcEntity npc
                && npc.getUUID().toString().equals(state.getProperty("worker"))) {
            restoredClean = !npc.workController().hasActiveJob() && !npc.commandController().hasActiveCommand()
                    && npc.commandController().queuedMoveCount() == 0;
            if (STAGE.equals("resume")) restoredClean &= npc.citizenData().inventory().countPersonalItem(Items.WHITE_BED) == 1;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (server == null || finished || event.phase != TickEvent.Phase.START) return;
        try {
            require(++ticks < 2400, "Construction timeout: " + (preparingWorker == null ? STAGE : preparingWorker.workController().blockReason()));
            var level = server.overworld();
            if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0, 0)) || !level.isPositionEntityTicking(ORIGIN)) return;
            if (STAGE.equals("prepare") && preparingWorker == null) { prepare(); return; }
            var entity = STAGE.equals("prepare") ? preparingWorker : level.getEntity(UUID.fromString(state.getProperty("worker")));
            if (!(entity instanceof HumanNpcEntity npc)) return;
            if (!STAGE.equals("prepare")) require(restoredClean, "Stale executable state or carried bed lost during load");
            require(npc.isAlive() && npc.citizenData().workPriority(WorkType.BUILDING) == WorkPriority.HIGH, "Builder/priority lost");
            require(npc.citizenData().inventory().countPersonalItem(Items.APPLE) == 3, "Personal food changed");
            require(StorageData.forLevel(level).isRegistered(CHEST), "Storage registration lost");
            require(ConstructionData.forLevel(level).plans().size() == 1 && plan().owner.toString().equals(state.getProperty("owner")), "Plan identity/owner changed");
            require(plan().origin.equals(ORIGIN) && plan().rotation == Rotation.NONE
                    && plan().sizeX == 5 && plan().sizeY == 5 && plan().sizeZ == 7, "Plan geometry changed");
            require(plan().blueprintId.equals("stonebanner:cottage") && plan().fingerprint.equals("cottage-v1"), "Blueprint identity changed");
            for (var placement : CottageBlueprint.placements()) if (!isFurniture(placement))
                require(placement.matches(level, ORIGIN, Rotation.NONE), "Pre-existing structure changed");
            int available = available(npc), placed = furniture();
            require(placed + available == 3, "Furniture lost/duplicated: placed=" + placed + " available=" + available);
            require(level.getEntitiesOfClass(ItemEntity.class, ARENA).stream().noneMatch(item -> item.getItem().is(Items.WHITE_BED) || item.getItem().is(Items.OAK_DOOR)), "Furniture dropped unexpectedly");
            if (ticks % 200 == 0) System.out.println("CONSTRUCTION_PROBE " + STAGE + " placed=" + placed
                    + " available=" + available + " status=" + plan().status + " pos=" + npc.position()
                    + " reason=" + npc.workController().blockReason());
            if (STAGE.equals("prepare")) {
                if (placed != 1 || npc.workController().phase() != CitizenWorkController.WorkPhase.WORKING) return;
                require(npc.workController().hasActiveJob() && npc.citizenData().inventory().countPersonalItem(Items.WHITE_BED) == 1, "No active carried-material checkpoint");
                if (partialSince < 0) partialSince = ticks;
                if (ticks - partialSince < 3) return;
                state.setProperty("paused", "WORKING");
                try (var out = Files.newOutputStream(STATE)) { state.store(out, "Physical construction checkpoint"); }
                pass(npc); return;
            }
            if (STAGE.equals("resume") && !plan().completed) return;
            require(plan().completed && placed == 3 && available == 0, "Completed plan/material accounting changed");
            require(CitizenJobBoard.forLevel(level).snapshot().stream().noneMatch(job -> job.workType() == WorkType.BUILDING && job.target().equals(ORIGIN)), "Completed building work reappeared");
            for (var placement : CottageBlueprint.placements()) require(placement.matches(level, ORIGIN, Rotation.NONE), "Incomplete furniture geometry");
            if (completedAt < 0) completedAt = ticks;
            if (ticks - completedAt >= 120) pass(npc); // Cross a normal 100-tick reconciliation boundary.
        } catch (Exception error) { fail(error); }
    }

    private static void prepare() throws Exception {
        require(!Files.exists(STATE) && ConstructionData.forLevel(server.overworld()).plans().isEmpty(), "Fresh probe required");
        var level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.setDayTime(6000);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 65; y < 73; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (var placement : CottageBlueprint.placements()) if (!isFurniture(placement))
            for (var cell : placement.cells()) level.setBlock(cell.at(ORIGIN, Rotation.NONE), cell.oriented(Rotation.NONE), 2);
        level.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
        require(StorageData.forLevel(level).register(level, CHEST) == StorageData.RegisterResult.ADDED, "Storage registration failed");
        var chest = chest(); chest.setItem(0, new ItemStack(Items.WHITE_BED)); chest.setItem(1, new ItemStack(Items.WHITE_BED));
        chest.setItem(2, new ItemStack(Items.OAK_DOOR)); chest.setChanged();
        var npc = ModEntities.HUMAN_NPC.get().create(level); require(npc != null, "Cannot create builder");
        npc.moveTo(3.5, 65, 4.5, 0, 0); npc.setPersistenceRequired();
        for (var type : WorkType.values()) npc.setWorkPriority(type, WorkPriority.DISABLED);
        npc.setWorkPriority(WorkType.BUILDING, WorkPriority.HIGH);
        npc.citizenData().inventory().add(new ItemStack(Items.APPLE, 3));
        require(level.addFreshEntity(npc), "Cannot spawn builder");
        UUID owner = UUID.randomUUID(); long id = ConstructionData.forLevel(level).add(owner, ORIGIN, Rotation.NONE);
        require(id > 0, "Cannot add cottage plan"); ConstructionService.reconcile(level);
        var job = CitizenJobBoard.forLevel(level).snapshot().stream().filter(j -> j.workType() == WorkType.BUILDING && j.target().equals(ORIGIN)).findFirst().orElseThrow();
        require(npc.workController().assign(job), "Cannot assign building work");
        state.setProperty("worker", npc.getUUID().toString()); state.setProperty("owner", owner.toString());
        state.setProperty("plan", Long.toString(id)); state.setProperty("job", Long.toString(job.id())); preparingWorker = npc;
    }
    private static boolean isFurniture(CottageBlueprint.Placement p) { return p.item() == Items.WHITE_BED || p.item() == Items.OAK_DOOR; }
    private static ConstructionData.Plan plan() {
        var p = ConstructionData.forLevel(server.overworld()).plan(Long.parseLong(state.getProperty("plan")));
        require(p != null, "Saved plan missing"); return p;
    }
    private static long jobId() { return Long.parseLong(state.getProperty("job")); }
    private static int furniture() { return (int) CottageBlueprint.placements().stream().filter(ConstructionRestartProbe::isFurniture).filter(p -> p.matches(server.overworld(), ORIGIN, Rotation.NONE)).count(); }
    private static Container chest() { var c = server.overworld().getBlockEntity(CHEST); require(c instanceof Container, "Physical chest missing"); return (Container) c; }
    private static int available(HumanNpcEntity npc) {
        int count = npc.citizenData().inventory().countPersonalItem(Items.WHITE_BED) + npc.citizenData().inventory().countPersonalItem(Items.OAK_DOOR);
        var c = chest(); for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.WHITE_BED) || c.getItem(i).is(Items.OAK_DOOR)) count += c.getItem(i).getCount(); return count;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    private static void pass(HumanNpcEntity npc) throws Exception {
        server.saveEverything(false, true, true);
        Files.writeString(Path.of("probe-" + STAGE + ".passed"), "PASS " + STAGE + " worker=" + npc.getUUID() + " plan=" + state.getProperty("plan")
                + " placed=" + furniture() + " available=" + available(npc) + " completed=" + plan().completed + "\n");
        System.out.println("STONEBANNER_PERSISTENCE_PASS " + STAGE); finished = true; server.halt(false);
    }
    private static void fail(Exception error) { finished = true; error.printStackTrace(); System.err.println("STONEBANNER_PERSISTENCE_FAIL " + STAGE + ": " + error.getMessage()); server.halt(false); }
    private ConstructionRestartProbe() {}
}
