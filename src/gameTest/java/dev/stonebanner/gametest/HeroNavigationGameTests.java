package dev.stonebanner.gametest;

import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.navigation.HeroRouteSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Server-side terrain regressions; client input/physics requires the separate manual run. */
@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class HeroNavigationGameTests {
    private static void floor(GameTestHelper h) {
        for(int x=1;x<=7;x++)for(int z=1;z<=7;z++) {
            h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            for(int y=1;y<=3;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
    }
    @GameTest(template="empty",timeoutTicks=30)
    public static void fireIsAvoidedInsteadOfUsedAsShortcut(GameTestHelper h) {
        floor(h);h.setBlock(new BlockPos(4,1,3),Blocks.FIRE);
        var start=h.absolutePos(new BlockPos(1,1,3));var goal=h.absolutePos(new BlockPos(7,1,3));
        var route=BlockPathfinder.findPermittedPath(h.getLevel(),start,goal,p->HeroRouteSafety.permitted(h.getLevel(),p));
        h.assertTrue(route.isPresent(),"Safe detour missing");
        h.assertTrue(route.get().stream().allMatch(p->HeroRouteSafety.permitted(h.getLevel(),p)),"Route crosses hazard");
        h.assertTrue(route.get().get(route.get().size()-1).equals(goal),"Goal silently replaced");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=30)
    public static void deepWaterGoalNeverSnapsToNearbyLand(GameTestHelper h) {
        floor(h);var pos=new BlockPos(4,1,3);h.setBlock(pos,Blocks.WATER);h.setBlock(pos.above(),Blocks.WATER);
        var goal=h.absolutePos(pos);
        h.assertTrue(HeroRouteSafety.terrain(h.getLevel(),goal)==HeroRouteSafety.Reason.WATER,"Deep water not identified");
        h.assertTrue(HeroRouteSafety.terrain(h.getLevel(),goal.above())==HeroRouteSafety.Reason.WATER,"Deep water surface allowed unattended entry");
        h.assertTrue(BlockPathfinder.findPermittedPath(h.getLevel(),h.absolutePos(new BlockPos(1,1,3)),goal,
                p->HeroRouteSafety.permitted(h.getLevel(),p)).isEmpty(),"Unsafe goal replaced by land");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=30)
    public static void unsupportedCellAndLowCeilingAreRejected(GameTestHelper h) {
        floor(h);var pit=new BlockPos(4,1,3);h.setBlock(pit.below(),Blocks.AIR);
        h.assertTrue(HeroRouteSafety.terrain(h.getLevel(),h.absolutePos(pit))==HeroRouteSafety.Reason.DROP,"Unsupported goal accepted");
        var low=new BlockPos(6,1,3);h.setBlock(low.above(),Blocks.STONE);
        h.assertTrue(BlockPathfinder.findPermittedPath(h.getLevel(),h.absolutePos(new BlockPos(1,1,3)),h.absolutePos(low),
                p->HeroRouteSafety.permitted(h.getLevel(),p)).isEmpty(),"Low ceiling goal replaced or crossed");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=30)
    public static void magmaBelowFeetAndLavaAtGoalStayImpassable(GameTestHelper h) {
        floor(h);
        var magma = new BlockPos(3, 0, 3);
        var lava = new BlockPos(5, 1, 3);
        h.setBlock(magma, Blocks.MAGMA_BLOCK);
        h.setBlock(lava, Blocks.LAVA);
        var level = h.getLevel();
        var magmaFeet = h.absolutePos(magma.above());
        var lavaFeet = h.absolutePos(lava);
        h.assertTrue(HeroRouteSafety.terrain(level, magmaFeet) == HeroRouteSafety.Reason.FIRE,
                "Magma below feet is not a hazard");
        h.assertTrue(HeroRouteSafety.terrain(level, lavaFeet) == HeroRouteSafety.Reason.FIRE,
                "Lava destination is not a hazard");
        var route = BlockPathfinder.findPermittedPath(level, h.absolutePos(new BlockPos(1,1,3)),
                h.absolutePos(new BlockPos(7,1,3)), p -> HeroRouteSafety.permitted(level, p));
        h.assertTrue(route.isPresent(), "No safe detour around lava/magma");
        h.assertTrue(route.get().stream().allMatch(p -> HeroRouteSafety.permitted(level, p)),
                "Hazard used as shortest route");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=30)
    public static void shallowWaterRemainsUsable(GameTestHelper h) {
        floor(h);var water=new BlockPos(4,1,3);h.setBlock(water,Blocks.WATER);
        var goal=h.absolutePos(water);
        h.assertTrue(HeroRouteSafety.terrain(h.getLevel(),goal)==HeroRouteSafety.Reason.NONE,"Shallow water rejected");
        h.assertTrue(BlockPathfinder.findPermittedPath(h.getLevel(),h.absolutePos(new BlockPos(1,1,3)),goal,
                p->HeroRouteSafety.permitted(h.getLevel(),p)).isPresent(),"Shallow-water route missing");h.succeed();
    }
}
