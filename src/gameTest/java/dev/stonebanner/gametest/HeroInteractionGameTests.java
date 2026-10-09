package dev.stonebanner.gametest;

import com.mojang.authlib.GameProfile;
import dev.stonebanner.control.HeroActionRules;
import dev.stonebanner.control.TacticalInteractionRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

/** Real server entities and collision traces; this does not replace client input acceptance. */
@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class HeroInteractionGameTests {
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h) {
        for(int x=1;x<=7;x++)for(int z=1;z<=7;z++) {
            h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
            for(int y=1;y<=4;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        }
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"HeroActions"));
        var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1,1,3)));
        player.setPos(at.x,at.y,at.z);
        return player;
    }
    @GameTest(setupTicks = 5, template="empty",timeoutTicks=30)
    public static void villagersAndTamePetsAreProtectedButHostilesRemainAttackable(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var p=player(h);
        var villager=h.spawn(EntityType.VILLAGER,new BlockPos(2,1,3));
        var pet=h.spawn(EntityType.WOLF,new BlockPos(2,1,4));
        pet.setTame(true);pet.setOwnerUUID(p.getUUID());
        var hostile=h.spawn(EntityType.ZOMBIE,new BlockPos(3,1,3));hostile.setNoAi(true);
        h.assertTrue(!HeroActionRules.canAttack(p,villager),"Villager became an attack target");
        h.assertTrue(!HeroActionRules.canAttack(p,pet),"Tame pet became an attack target");
        h.assertTrue(!HeroActionRules.canAttack(p,p),"Hero can attack itself");
        h.assertTrue(HeroActionRules.canAttack(p,hostile),"Ordinary hostile was incorrectly protected");
        h.succeed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",timeoutTicks=30)
    public static void reachAndSolidWallAreIndependentServerLimits(GameTestHelper h) {
        GameTestFixtures.runWhenReady(h, () -> {
        var p=player(h);
        var target=h.spawn(EntityType.ZOMBIE,new BlockPos(5,1,3));target.setNoAi(true);
        h.assertTrue(!p.canReach(target,0),"Distant entity gained camera reach");
        h.assertTrue(TacticalInteractionRules.visible(h.getLevel(),p,target.getBoundingBox().getCenter(),null),"Open sight blocked");
        for(int y=1;y<=3;y++)h.setBlock(new BlockPos(3,y,3),Blocks.STONE);
        h.assertTrue(!TacticalInteractionRules.visible(h.getLevel(),p,target.getBoundingBox().getCenter(),null),"Sight passed through solid wall");
        var near=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,3)));
        target.setPos(near.x,near.y,near.z);
        h.assertTrue(p.canReach(target,0),"Nearby target rejected by real reach");
        h.assertTrue(TacticalInteractionRules.visible(h.getLevel(),p,target.getBoundingBox().getCenter(),null),"Wall behind target blocked near interaction");
        h.succeed();

        });
    }
}
