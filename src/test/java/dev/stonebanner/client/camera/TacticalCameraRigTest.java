package dev.stonebanner.client.camera;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TacticalCameraRigTest {
    @Test void pivotRemainsStationaryAcrossTicksWithoutPanOrRecenter(){var r=new TacticalCameraRig();var anchor=new Vec3(5,10,8);r.reset(anchor);for(int i=0;i<100;i++)r.tick();assertEquals(anchor,r.desired());assertEquals(anchor,r.interpolated(.5));}
    @Test void cameraPanFollowsCameraYaw(){var r=new TacticalCameraRig();r.reset(Vec3.ZERO);r.pan(0,1,0,90,1);assertEquals(-1,r.desired().x,1e-6);assertEquals(0,r.desired().z,1e-6);}
    @Test void diagonalKeysDoNotAccelerateCamera(){var r=new TacticalCameraRig();r.reset(Vec3.ZERO);r.pan(1,1,0,0,.35);assertEquals(.35,r.desired().length(),1e-6);}
    @Test void verticalPanAndInterpolationPreserveIndependentOrigin(){var r=new TacticalCameraRig();r.reset(new Vec3(10,20,30));r.tick();r.pan(0,0,1,0,2);assertEquals(new Vec3(10,21,30),r.interpolated(.5));r.tick();assertEquals(new Vec3(10,22,30),r.interpolated(.2));}
    @Test void collisionCorrectionAndRecenterRemoveStaleMotion(){var r=new TacticalCameraRig();r.reset(Vec3.ZERO);r.pan(0,1,0,0,10);var safe=new Vec3(0,0,1);r.reset(safe);assertEquals(safe,r.interpolated(.2));r.reset(null);assertFalse(r.initialized());r.reset(new Vec3(100,50,20));assertEquals(new Vec3(100,50,20),r.desired());}
}
