package dev.stonebanner.acceptance;

import dev.stonebanner.citizen.CitizenJobBoard;
import dev.stonebanner.citizen.WorkPriority;
import dev.stonebanner.citizen.WorkType;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
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

/** Three real server processes; excluded from the mod JAR and ordinary run configurations. */
@Mod.EventBusSubscriber(modid = "stonebanner")
public final class PersistenceRestartProbe {
    private static final Path STATE = Path.of("persistence-probe.properties");
    private static final BlockPos TARGET = new BlockPos(8, 65, 2);
    private static final AABB ARENA = new AABB(-1, 64, -1, 16, 72, 16);
    private static final String STAGE = System.getProperty("stonebanner.persistenceStage", "disabled");
    private static final Properties state = new Properties();
    private static MinecraftServer server;
    private static int ticks;
    private static int completedAt = -1;
    private static boolean finished;
    private static boolean restoredClean;
    private static HumanNpcEntity preparingWorker;
    private static int workingSince = -1;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (STAGE.equals("disabled")) return;
        server = event.getServer();
        try {
            require(server.getWorldData().getLevelName().equals("stonebanner-persistence-probe"),
                    "Refusing to alter a non-probe world");
            require(STAGE.equals("prepare") || STAGE.equals("resume") || STAGE.equals("completed"), "Unknown stage");
            if (!STAGE.equals("prepare")) try (var in = Files.newInputStream(STATE)) { state.load(in); }
            server.overworld().setChunkForced(0, 0, true);
            if (STAGE.equals("resume")) {
                require(CitizenJobBoard.forLevel(server.overworld()).job(jobId()).isPresent(), "Saved job missing");
                require(server.overworld().getBlockState(TARGET).is(Blocks.OAK_LOG), "Unfinished target missing");
            }
        } catch (Exception error) { fail(error); }
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!STAGE.equals("prepare") && event.loadedFromDisk() && event.getEntity() instanceof HumanNpcEntity npc
                && npc.getUUID().toString().equals(state.getProperty("worker"))) {
            restoredClean = !npc.workController().hasActiveJob() && !npc.commandController().hasActiveCommand()
                    && npc.commandController().queuedMoveCount() == 0;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (server == null || finished || event.phase != TickEvent.Phase.START) return;
        try {
            ticks++;
            require(ticks < 900, "Probe timeout in " + STAGE);
            var level = server.overworld();
            if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0, 0))
                    || !level.isPositionEntityTicking(TARGET)) return;
            var board = CitizenJobBoard.forLevel(level);
            if (STAGE.equals("prepare")) {
                if (preparingWorker != null) {
                    require(preparingWorker.isAlive() && preparingWorker.workController().hasActiveJob(), "Active work interrupted before save");
                    require(board.job(jobId()).isPresent() && level.getBlockState(TARGET).is(Blocks.OAK_LOG)
                            && logs() == 0, "Target mutated before interrupted-work checkpoint");
                    if (preparingWorker.workController().phase() != dev.stonebanner.citizen.CitizenWorkController.WorkPhase.WORKING) return;
                    if (workingSince < 0) workingSince = ticks;
                    if (ticks - workingSince < 3) return;
                    state.setProperty("pausedPhase", "WORKING");
                    try (var out = Files.newOutputStream(STATE)) { state.store(out, "Persistent identity, not executable worker state"); }
                    pass();
                    return;
                }
                require(!Files.exists(STATE) && board.size() == 0, "Prepare requires a fresh probe directory");
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.setDayTime(6000);
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    level.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
                    for (int y = 65; y < 72; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(TARGET, Blocks.OAK_LOG.defaultBlockState(), 3);
                var npc = ModEntities.HUMAN_NPC.get().create(level);
                require(npc != null, "Cannot create worker");
                npc.moveTo(2.5, 65, 2.5, 0, 0);
                npc.setPersistenceRequired();
                npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.IRON_AXE));
                npc.citizenData().inventory().add(new net.minecraft.world.item.ItemStack(Items.APPLE, 3));
                for (var type : WorkType.values()) npc.setWorkPriority(type, WorkPriority.DISABLED);
                npc.setWorkPriority(WorkType.FORESTRY, WorkPriority.NORMAL);
                require(level.addFreshEntity(npc), "Cannot spawn worker");
                long id = board.publish(WorkType.FORESTRY, TARGET, level.getGameTime());
                require(npc.workController().assign(board.job(id).orElseThrow()), "Cannot assign real work");
                require(npc.workController().hasActiveJob() && npc.commandController().hasActiveCommand(), "Worker not active");
                require(level.getBlockState(TARGET).is(Blocks.OAK_LOG) && logs() == 0, "Work already mutated world");
                state.setProperty("worker", npc.getUUID().toString());
                state.setProperty("job", Long.toString(id));
                preparingWorker = npc;
                return;
            }
            require("WORKING".equals(state.getProperty("pausedPhase")), "Save was not interrupted during real work");
            var entity = level.getEntity(UUID.fromString(state.getProperty("worker")));
            if (!(entity instanceof HumanNpcEntity npc)) return;
            require(restoredClean, "Loaded worker restored a stale route/lease or was not loaded from disk");
            require(npc.citizenData().workPriority(WorkType.FORESTRY) == WorkPriority.NORMAL, "Priority lost");
            require(npc.getMainHandItem().is(Items.IRON_AXE), "Saved tool lost");
            require(npc.citizenData().inventory().snapshot().stream().filter(stack -> stack.is(Items.APPLE))
                    .mapToInt(stack -> stack.getCount()).sum() == 3, "Personal inventory changed");
            if (STAGE.equals("resume") && board.job(jobId()).isPresent()) return;
            require(board.job(jobId()).isEmpty(), "Completed job reappeared");
            require(level.getBlockState(TARGET).isAir(), "Target not harvested");
            require(logs() == 1, "Physical log lost or duplicated: " + logs());
            require(!npc.citizenData().inventory().hasHaulCargo(), "Disabled hauling collected output");
            if (completedAt < 0) completedAt = ticks;
            if (ticks - completedAt >= 40) pass();
        } catch (Exception error) { fail(error); }
    }

    private static long jobId() { return Long.parseLong(state.getProperty("job")); }
    private static int logs() {
        return server.overworld().getEntitiesOfClass(ItemEntity.class, ARENA).stream()
                .filter(item -> item.getItem().is(Items.OAK_LOG)).mapToInt(item -> item.getItem().getCount()).sum();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    private static void pass() throws Exception {
        server.saveEverything(false, true, true);
        Files.writeString(Path.of("probe-" + STAGE + ".passed"), "PASS " + STAGE + " worker="
                + state.getProperty("worker") + " job=" + state.getProperty("job") + " paused="
                + state.getProperty("pausedPhase") + " logs=" + logs() + "\n");
        System.out.println("STONEBANNER_PERSISTENCE_PASS " + STAGE);
        finished = true;
        server.halt(false);
    }
    private static void fail(Exception error) {
        finished = true;
        error.printStackTrace();
        System.err.println("STONEBANNER_PERSISTENCE_FAIL " + STAGE + ": " + error.getMessage());
        server.halt(false);
    }
    private PersistenceRestartProbe() {}
}
