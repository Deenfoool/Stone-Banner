package dev.stonebanner.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

/** Wait for the fixture's real entity chunks before spawning or querying test entities. */
final class GameTestFixtures {
    @FunctionalInterface interface Scene { void run() throws Exception; }
    static void runWhenReady(GameTestHelper helper, Scene scene) {
        var level = helper.getLevel();
        var first = helper.absolutePos(BlockPos.ZERO);
        var last = helper.absolutePos(new BlockPos(15, 0, 15));
        helper.startSequence().thenWaitUntil(() -> {
            for (int x = Math.min(first.getX(), last.getX()) >> 4; x <= (Math.max(first.getX(), last.getX()) >> 4); x++) {
                for (int z = Math.min(first.getZ(), last.getZ()) >> 4; z <= (Math.max(first.getZ(), last.getZ()) >> 4); z++) {
                    helper.assertTrue(level.areEntitiesLoaded(ChunkPos.asLong(x, z))
                            && level.isPositionEntityTicking(new BlockPos(x * 16, first.getY(), z * 16)),
                            "Fixture entity chunk is not ready: " + x + ", " + z);
                }
            }
        }).thenExecute(() -> {
            try { scene.run(); }
            catch (RuntimeException error) { throw error; }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }
    private GameTestFixtures() {}
}
