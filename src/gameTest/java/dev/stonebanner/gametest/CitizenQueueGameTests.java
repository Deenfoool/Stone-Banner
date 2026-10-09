package dev.stonebanner.gametest;

import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CitizenQueueGameTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void loadedCargoReachesStorageWithoutPersonalItems(GameTestHelper helper) {
        var npc = prepare(helper);
        npc.citizenData().inventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 3));
        npc.citizenData().inventory().addHaulCargo(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 7));
        var tag = new net.minecraft.nbt.CompoundTag();
        npc.addAdditionalSaveData(tag);
        npc.readAdditionalSaveData(tag);
        BlockPos chestPos = new BlockPos(6, 1, 2);
        helper.setBlock(chestPos, Blocks.CHEST);
        var storage = dev.stonebanner.storage.StorageData.forLevel(helper.getLevel());
        helper.assertTrue(storage.register(helper.getLevel(), helper.absolutePos(chestPos))
                == dev.stonebanner.storage.StorageData.RegisterResult.ADDED, "Storage registration failed");
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(!npc.citizenData().inventory().hasHaulCargo(), "Loaded cargo not delivered");
            var chest = (net.minecraft.world.Container) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
            int count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                var stack = chest.getItem(slot);
                helper.assertTrue(stack.isEmpty() || stack.is(net.minecraft.world.item.Items.COBBLESTONE), "Personal food deposited");
                count += stack.getCount();
            }
            helper.assertTrue(count == 7, "Cargo lost or duplicated in storage");
            helper.assertTrue(npc.citizenData().inventory().snapshot().stream()
                    .filter(stack -> stack.is(net.minecraft.world.item.Items.APPLE)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum() == 3,
                    "Personal food was consumed or lost");
        }).thenExecuteAfter(20, () -> helper.assertTrue(!npc.citizenData().inventory().hasHaulCargo(), "Delivered cargo reappeared")).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deathDropsPersonalItemsAndCargoOnce(GameTestHelper helper) {
        var npc = prepare(helper);
        npc.citizenData().inventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 3));
        npc.citizenData().inventory().addHaulCargo(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 7));
        var cursed = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE);
        cursed.enchant(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE, 1);
        npc.citizenData().inventory().add(cursed);
        npc.hurt(npc.damageSources().genericKill(), 1000);
        helper.startSequence().thenExecuteAfter(5, () -> {
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(16, 6, 16))));
            int apples = drops.stream().filter(item -> item.getItem().is(net.minecraft.world.item.Items.APPLE)).mapToInt(item -> item.getItem().getCount()).sum();
            int stone = drops.stream().filter(item -> item.getItem().is(net.minecraft.world.item.Items.COBBLESTONE)).mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(apples == 3 && stone == 7, "Death lost or duplicated inventory/cargo");
            helper.assertTrue(drops.stream().noneMatch(item -> item.getItem().is(net.minecraft.world.item.Items.IRON_AXE)), "Vanishing item dropped");
            helper.assertTrue(npc.citizenData().inventory().snapshot().stream().allMatch(net.minecraft.world.item.ItemStack::isEmpty), "Death retained inventory copy");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void immobileNpcRejectsMovement(GameTestHelper helper) {
        var npc = prepare(helper);
        npc.citizenData().health().setInjury(dev.stonebanner.citizen.BodyPart.LEFT_LEG, dev.stonebanner.citizen.InjuryState.FRACTURE);
        npc.citizenData().health().setInjury(dev.stonebanner.citizen.BodyPart.RIGHT_LEG, dev.stonebanner.citizen.InjuryState.FRACTURE);
        var target = helper.absolutePos(new BlockPos(6, 1, 2));
        helper.assertTrue(!npc.issueCommand(new ActorCommand.MoveTo(target)), "Immobile NPC accepted move");
        helper.assertTrue(!npc.commandController().queueMove(target), "Immobile NPC accepted queue");
        helper.assertTrue(!npc.commandController().issueSystemMove(target, dev.stonebanner.citizen.CitizenBrainState.RETURN_HOME), "Immobile NPC accepted system move");
        helper.assertTrue(!npc.commandController().hasActiveCommand(), "Rejected command retained active state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deathReleasesWorkAndCommands(GameTestHelper helper) {
        var npc = prepare(helper);
        long id = assignTree(helper, npc);
        npc.hurt(npc.damageSources().genericKill(), 1000);
        helper.assertTrue(!npc.isAlive(), "NPC survived lethal damage");
        helper.assertTrue(!npc.workController().hasActiveJob(), "Dead NPC retained work");
        helper.assertTrue(!npc.commandController().hasActiveCommand(), "Dead NPC retained orders");
        helper.assertTrue(dev.stonebanner.citizen.CitizenJobBoard.forLevel(helper.getLevel())
                .reserve(id, java.util.UUID.randomUUID(), helper.getLevel().getGameTime()), "Death did not release reservation");
        helper.startSequence().thenExecuteAfter(10, () -> helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 2)).is(Blocks.OAK_LOG),
                "Dead worker changed world")).thenSucceed();
    }

    private static long assignTree(GameTestHelper helper, HumanNpcEntity npc) {
        npc.setWorkPriority(dev.stonebanner.citizen.WorkType.FORESTRY, dev.stonebanner.citizen.WorkPriority.NORMAL);
        BlockPos target = new BlockPos(4, 1, 2);
        helper.setBlock(target, Blocks.OAK_LOG);
        var board = dev.stonebanner.citizen.CitizenJobBoard.forLevel(helper.getLevel());
        long id = board.publish(dev.stonebanner.citizen.WorkType.FORESTRY, helper.absolutePos(target), helper.getLevel().getGameTime());
        helper.assertTrue(npc.workController().assign(board.job(id).orElseThrow()), "Tree job rejected");
        return id;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void disabledWorkStopsBeforeMutation(GameTestHelper helper) {
        var npc = prepare(helper);
        assignTree(helper, npc);
        npc.setWorkPriority(dev.stonebanner.citizen.WorkType.FORESTRY, dev.stonebanner.citizen.WorkPriority.DISABLED);
        helper.startSequence().thenExecuteAfter(5, () -> {
            helper.assertTrue(!npc.workController().hasActiveJob(), "Disabled work continued");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 2)).is(Blocks.OAK_LOG), "Disabled work destroyed tree");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reassignedWorkStopsOldWorker(GameTestHelper helper) {
        var npc = prepare(helper);
        long id = assignTree(helper, npc);
        var board = dev.stonebanner.citizen.CitizenJobBoard.forLevel(helper.getLevel());
        var successor = java.util.UUID.randomUUID();
        board.release(id, npc.getUUID());
        helper.assertTrue(board.reserve(id, successor, helper.getLevel().getGameTime()), "Reassignment failed");
        helper.startSequence().thenExecuteAfter(5, () -> {
            helper.assertTrue(!npc.workController().hasActiveJob(), "Old worker continued");
            helper.assertTrue(board.touch(id, successor, helper.getLevel().getGameTime()), "Old worker released successor lease");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 2)).is(Blocks.OAK_LOG), "Old worker changed world");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void loadDoesNotRestorePhantomWork(GameTestHelper helper) {
        var npc = prepare(helper);
        assignTree(helper, npc);
        var tag = new net.minecraft.nbt.CompoundTag();
        npc.addAdditionalSaveData(tag);
        tag.putString("BrainState", "work");
        npc.readAdditionalSaveData(tag);
        helper.assertTrue(!npc.workController().hasActiveJob() && !npc.commandController().hasActiveCommand(), "Runtime work restored");
        helper.assertTrue(npc.brainState() == dev.stonebanner.citizen.CitizenBrainState.IDLE, "Phantom work state restored");
        helper.succeed();
    }


    @GameTest(template = "empty", timeoutTicks = 110)
    public static void unloadedProductionJobsSurviveReconciliationWithoutForceLoading(GameTestHelper helper) {
        var npc = prepare(helper);
        var level = helper.getLevel();
        // Far away from the loaded test area, but inside the normal world border.
        BlockPos farFarm = new BlockPos(2_000_000, 68, 2_000_000);
        BlockPos farCraft = new BlockPos(2_000_032, 68, 2_000_032);
        helper.assertTrue(!level.hasChunkAt(farFarm) && !level.hasChunkAt(farCraft),
                "The test requires genuinely unloaded targets");
        var board = dev.stonebanner.citizen.CitizenJobBoard.forLevel(level);
        long farm = board.publish(dev.stonebanner.citizen.WorkType.FARMING, farFarm, level.getGameTime());
        long craft = board.publish(dev.stonebanner.citizen.WorkType.CRAFTING, farCraft, level.getGameTime());
        try {
            // A production tick used to interpret an unloaded farm as no work and delete
            // the persistent job, even though the farmland could still exist on disk.
            dev.stonebanner.production.ProductionService.reconcile(level);
            helper.assertTrue(board.job(farm).isPresent() && board.job(craft).isPresent(),
                    "Reconciliation deleted persistent work in unloaded chunks");
            helper.assertTrue(!level.hasChunkAt(farFarm) && !level.hasChunkAt(farCraft),
                    "Production validation force-loaded a remote chunk");
            helper.startSequence().thenExecuteAfter(25, () -> {
                try {
                    helper.assertTrue(board.job(farm).isPresent() && board.job(craft).isPresent(),
                            "Idle citizen or later production tick destroyed distant jobs");
                    helper.assertTrue(!level.hasChunkAt(farFarm) && !level.hasChunkAt(farCraft),
                            "Work planner loaded distant chunks");
                } finally {
                    board.remove(farm);
                    board.remove(craft);
                }
            }).thenSucceed();
        } catch (RuntimeException | Error exception) {
            board.remove(farm);
            board.remove(craft);
            throw exception;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void reloadedWorkerReacquiresPublishedWorkExactlyOnce(GameTestHelper helper) {
        var npc = prepare(helper);
        npc.setWorkPriority(dev.stonebanner.citizen.WorkType.HAULING, dev.stonebanner.citizen.WorkPriority.DISABLED);
        long id = assignTree(helper, npc);
        // Read a real compressed NBT round trip while work is unfinished. Published jobs are
        // world data, not executable state inside the entity; they must remain available.
        var tag = new net.minecraft.nbt.CompoundTag();
        npc.addAdditionalSaveData(tag);
        npc.readAdditionalSaveData(compressedRoundTrip(tag));
        var board = dev.stonebanner.citizen.CitizenJobBoard.forLevel(helper.getLevel());
        helper.assertTrue(board.job(id).isPresent(), "Reload removed published work");
        helper.assertTrue(!npc.workController().hasActiveJob()
                && !npc.commandController().hasActiveCommand(), "Reload restored a runtime lease or route");
        helper.assertTrue(npc.citizenData().workPriority(dev.stonebanner.citizen.WorkType.FORESTRY)
                == dev.stonebanner.citizen.WorkPriority.NORMAL, "Saved work priority lost");
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(board.job(id).isEmpty(), "Reloaded worker did not finish published work");
            helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 2)).isAir(), "Tree was not harvested");
            assertSingleLogDrop(helper);
        }).thenExecuteAfter(20, () -> {
            helper.assertTrue(board.job(id).isEmpty(), "Completed job reappeared");
            assertSingleLogDrop(helper);
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void compressedReloadClearsMixedAndMovementQueues(GameTestHelper helper) {
        var npc = prepare(helper);
        var commander = java.util.UUID.randomUUID();
        npc.citizenData().setRecruitedBy(commander);
        var first = helper.absolutePos(new BlockPos(9, 1, 2));
        var second = helper.absolutePos(new BlockPos(9, 1, 9));
        helper.assertTrue(npc.orderSequence().enqueue(
                dev.stonebanner.command.CitizenOrderQueue.Entry.move(first), commander), "First order rejected");
        helper.assertTrue(npc.orderSequence().enqueue(
                dev.stonebanner.command.CitizenOrderQueue.Entry.move(second), commander), "Queued order rejected");
        helper.assertTrue(npc.commandController().queueMove(second), "Movement waypoint rejected");
        var tag = new net.minecraft.nbt.CompoundTag();
        npc.addAdditionalSaveData(tag);
        npc.readAdditionalSaveData(compressedRoundTrip(tag));
        var position = npc.position();
        helper.assertTrue(npc.citizenData().recruitedBy().orElseThrow().equals(commander), "Reload lost employer");
        helper.startSequence().thenExecuteAfter(20, () -> {
            helper.assertTrue(!npc.orderSequence().hasOrders()
                    && npc.orderSequence().pendingCount() == 0, "Old mixed queue survived reload");
            helper.assertTrue(!npc.commandController().hasActiveCommand()
                    && npc.commandController().queuedMoveCount() == 0, "Old movement queue survived reload");
            helper.assertTrue(npc.position().distanceToSqr(position) < .1, "Reload replayed old movement");
        }).thenSucceed();
    }

    private static net.minecraft.nbt.CompoundTag compressedRoundTrip(net.minecraft.nbt.CompoundTag tag) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            net.minecraft.nbt.NbtIo.writeCompressed(tag, bytes);
            return net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes.toByteArray()));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("NPC persistence fixture could not round-trip NBT", exception);
        }
    }

    private static void assertSingleLogDrop(GameTestHelper helper) {
        int count = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(1, 1, 1)),
                        helper.absolutePos(new BlockPos(10, 5, 10))))
                .stream().filter(item -> item.getItem().is(net.minecraft.world.item.Items.OAK_LOG))
                .mapToInt(item -> item.getItem().getCount()).sum();
        helper.assertTrue(count == 1, "Reloaded forestry lost or duplicated its physical log: " + count);
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void guardInterruptsQueueAndDefends(GameTestHelper helper) {
        var npc = prepare(helper);
        start(helper, npc);
        npc.citizenData().setProfession(dev.stonebanner.citizen.CitizenProfession.GUARD, true);
        var zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(6, 1, 3));
        zombie.setNoAi(true);
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(npc.commandController().isDefensiveAttack(), "Guard did not acquire threat");
            helper.assertTrue(npc.commandController().queuedMoveCount() == 0, "Guard retained ordinary queue");
            helper.assertTrue(npc.brainState() == dev.stonebanner.citizen.CitizenBrainState.DEFEND, "Guard state overwritten");
        }).thenExecuteAfter(5, () -> helper.assertTrue(npc.brainState() == dev.stonebanner.citizen.CitizenBrainState.DEFEND,
                "Movement overwrote defense state"))
                .thenWaitUntil(() -> helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "Guard never attacked threat")).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void followRejectsChangedTargetIdentity(GameTestHelper helper) {
        var npc = prepare(helper);
        var target = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(10, 1, 2));
        target.setNoAi(true);
        helper.assertTrue(npc.issueCommand(new ActorCommand.FollowEntity(target.getId(), 1)), "Follow rejected");
        // Same network ID must not authorize a different persistent identity.
        target.setUUID(java.util.UUID.randomUUID());
        helper.startSequence().thenExecuteAfter(5, () -> helper.assertTrue(!npc.commandController().hasActiveCommand(),
                "Follow silently retargeted another UUID")).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 150)
    public static void blockedActiveRouteDoesNotComplete(GameTestHelper helper) {
        var npc = prepare(helper);
        helper.assertTrue(npc.issueCommand(new ActorCommand.MoveTo(helper.absolutePos(new BlockPos(6, 1, 2)))), "Move rejected");
        for (int x = 3; x <= 9; x++) for (int z = 1; z <= 5; z++)
            for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(!npc.commandController().hasActiveCommand(), "Blocked command still active");
            helper.assertTrue(npc.commandController().status() == dev.stonebanner.citizen.CitizenCommandController.CommandStatus.UNREACHABLE,
                    "Failed recovery was reported as successful completion");
        }).thenSucceed();
    }

    static HumanNpcEntity prepare(GameTestHelper helper) {
        // GameTest reuses its world/SavedData between runs; structure reset alone doesn't reset storage.
        var storage = dev.stonebanner.storage.StorageData.forLevel(helper.getLevel());
        var arena = new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(16, 6, 16)));
        for (var entity : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, arena,
                e -> e instanceof net.minecraft.world.entity.LivingEntity && !(e instanceof net.minecraft.world.entity.player.Player)
                        || e instanceof net.minecraft.world.entity.item.ItemEntity)) entity.discard();
        for (BlockPos pos : storage.registeredPositions())
            if (arena.contains(Vec3.atCenterOf(pos))) storage.unregister(pos);
        var jobs = dev.stonebanner.citizen.CitizenJobBoard.forLevel(helper.getLevel());
        for (var job : jobs.snapshot()) if (arena.contains(Vec3.atCenterOf(job.target()))) jobs.remove(job.id());
        helper.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, helper.getLevel().getServer());
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            if (x == 0 || z == 0 || x == 15 || z == 15)
                for (int y = 1; y < 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        return helper.spawn(ModEntities.HUMAN_NPC.get(), new BlockPos(2, 1, 2));
    }

    private static void start(GameTestHelper helper, HumanNpcEntity npc) {
        helper.assertTrue(npc.issueCommand(new ActorCommand.MoveTo(helper.absolutePos(new BlockPos(6, 1, 2)))), "Initial move rejected");
        helper.assertTrue(npc.commandController().queueMove(helper.absolutePos(new BlockPos(6, 1, 6))), "Second waypoint rejected");
        helper.assertTrue(npc.commandController().queueMove(helper.absolutePos(new BlockPos(2, 1, 6))), "Third waypoint rejected");
        helper.assertTrue(npc.commandController().queuedMoveCount() == 2, "Pending count is not two");
    }

    private static void assertFinished(GameTestHelper helper, HumanNpcEntity npc, BlockPos relative) {
        helper.assertTrue(!npc.commandController().hasActiveCommand(), "Command still active");
        helper.assertTrue(npc.commandController().queuedMoveCount() == 0, "Queue not empty");
        helper.assertTrue(npc.position().distanceToSqr(Vec3.atBottomCenterOf(helper.absolutePos(relative))) < 1,
                "NPC did not reach final waypoint: " + npc.position());
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void executesThreeWaypoints(GameTestHelper helper) {
        var npc = prepare(helper);
        start(helper, npc);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(npc.position().distanceToSqr(
                Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 1, 2)))) < 1, "First waypoint not visited"))
                .thenWaitUntil(() -> helper.assertTrue(npc.position().distanceToSqr(
                Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 1, 6)))) < 1, "Second waypoint not visited"))
                .thenWaitUntil(() -> assertFinished(helper, npc, new BlockPos(2, 1, 6))).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void stopClearsQueueWithoutRestart(GameTestHelper helper) {
        var npc = prepare(helper);
        start(helper, npc);
        var stoppedAt = new Vec3[1];
        helper.startSequence().thenExecuteAfter(10, () -> {
            npc.issueCommand(new ActorCommand.Stop());
            stoppedAt[0] = npc.position();
        }).thenExecuteAfter(40, () -> {
            helper.assertTrue(!npc.commandController().hasActiveCommand(), "Stopped command restarted");
            helper.assertTrue(npc.commandController().queuedMoveCount() == 0, "Stop retained waypoints");
            helper.assertTrue(npc.position().distanceToSqr(stoppedAt[0]) < 1, "NPC moved after Stop");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void newMoveReplacesQueue(GameTestHelper helper) {
        var npc = prepare(helper);
        start(helper, npc);
        var replacement = new BlockPos(2, 1, 9);
        helper.startSequence().thenExecuteAfter(10, () -> {
            helper.assertTrue(npc.issueCommand(new ActorCommand.MoveTo(helper.absolutePos(replacement))), "Replacement rejected");
            helper.assertTrue(npc.commandController().queuedMoveCount() == 0, "Replacement retained queue");
        }).thenWaitUntil(() -> assertFinished(helper, npc, replacement))
                .thenExecuteAfter(30, () -> assertFinished(helper, npc, replacement)).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void blockedNextWaypointCancelsRemainder(GameTestHelper helper) {
        var npc = prepare(helper);
        start(helper, npc);
        // Remove all possible standing cells around the second waypoint after it was queued.
        for (int x = 3; x <= 9; x++) for (int z = 3; z <= 9; z++)
            for (int y = 1; y <= 5; y++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(npc.commandController().status() == dev.stonebanner.citizen.CitizenCommandController.CommandStatus.UNREACHABLE,
                    "Blocked waypoint has not failed");
            helper.assertTrue(!npc.commandController().hasActiveCommand(), "Failed route retained commands");
            helper.assertTrue(npc.commandController().queuedMoveCount() == 0, "Failed route retained queue");
            helper.assertTrue(npc.position().distanceToSqr(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 6)))) > 1,
                    "NPC skipped the blocked point and reached the third");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void criticalHungerClearsQueue(GameTestHelper helper) {
        var npc = prepare(helper);
        start(helper, npc);
        npc.citizenData().needs().setHunger(100);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(!npc.commandController().hasActiveCommand(), "Critical hunger did not interrupt movement");
            helper.assertTrue(npc.commandController().queuedMoveCount() == 0, "Critical hunger retained queue");
            helper.assertTrue(npc.brainState() == dev.stonebanner.citizen.CitizenBrainState.EAT, "NPC not eating");
        }).thenSucceed();
    }
}
