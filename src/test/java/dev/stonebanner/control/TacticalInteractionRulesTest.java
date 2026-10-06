package dev.stonebanner.control;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TacticalInteractionRulesTest {
    @Test void actualFaceCoordinatesAreAcceptedAtNegativeWorldPositions(){var p=new BlockPos(-3,64,8);assertTrue(TacticalInteractionRules.validHit(p,new Vec3(-3,64.5,8.5)));assertTrue(TacticalInteractionRules.validHit(p,new Vec3(-2,65,9)));}
    @Test void forgedHitOutsideTargetBlockIsRejected(){assertFalse(TacticalInteractionRules.validHit(BlockPos.ZERO,new Vec3(2,.5,.5)));assertFalse(TacticalInteractionRules.validHit(BlockPos.ZERO,new Vec3(.5,10,.5)));}
    @Test void tallBannerAndFenceShapesAcceptRealHitsAboveUnitCube() {
        var p = new BlockPos(0, 64, 0);
        var shape = java.util.List.of(new net.minecraft.world.phys.AABB(.25, 0, .25, .75, 1.8125, .75));
        assertTrue(TacticalInteractionRules.validHit(p, new Vec3(.5,65.8,.5), shape));
        assertFalse(TacticalInteractionRules.validHit(p, new Vec3(.9,65.8,.5), shape));
    }
    @Test void nonFiniteInteractionCoordinatesAreRejected(){for(double v:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})assertFalse(TacticalInteractionRules.validHit(BlockPos.ZERO,new Vec3(v,.5,.5)));}
}
