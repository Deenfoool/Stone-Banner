package dev.stonebanner.client.camera;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class CameraCollisionTest {
    private static Function<AABB,List<AABB>> world(AABB... boxes) { return bounds -> List.of(boxes); }
    @Test void roofStopsHeightOffsetBeforeItCanBecomeAnAnchorInsideStone() {
        var eyes = new Vec3(.5,1.62,.5);
        var result = CameraCollision.sweep(eyes,eyes.add(0,3,0),.12,world(new AABB(-5,2,-5,5,3,5)));
        assertFalse(result.blockedStart());assertTrue(result.position().y<1.86);assertTrue(result.position().y>=1.62);
    }
    @Test void clearEndpointOnOtherSideOfRoofDoesNotPermitPassingThroughIt() {
        var r=CameraCollision.sweep(new Vec3(0,1.6,0),new Vec3(0,8,0),.12,world(new AABB(-4,2,-4,4,2.25,4)));
        assertTrue(r.position().y<2);assertFalse(r.blockedStart());
    }
    @Test void retreatStopsAtThinWallEvenWithMaximumZoom() {
        var r=CameraCollision.sweep(new Vec3(0,1.6,0),new Vec3(0,1.6,24),.12,world(new AABB(-5,0,2,5,4,2.0625)));
        assertTrue(r.position().z<1.86);assertTrue(r.position().z>1.8);
    }
    @Test void volumeProtectsNearPlaneWhenCenterRayMissesCorner() {
        var r=CameraCollision.sweep(new Vec3(0,1.6,0),new Vec3(0,1.6,10),.2,world(new AABB(.15,0,2,1,4,3)));
        assertTrue(r.position().z<2);
    }
    @Test void crampedShaftMayUseLessThanPreferredMinimumDistance() {
        var r=CameraCollision.sweep(new Vec3(.5,1.6,.5),new Vec3(.5,1.6,8),.12,world(new AABB(0,0,1,1,4,2)));
        assertTrue(r.position().z-.5<.5);assertFalse(r.blockedStart());
    }
    @Test void partialShapesPermitPassageAboveSlabButNotInsideIt() {
        var slab=new AABB(-2,0,1,2,.5,2);
        assertEquals(new Vec3(0,1,4),CameraCollision.sweep(new Vec3(0,1,0),new Vec3(0,1,4),.12,world(slab)).position());
        assertTrue(CameraCollision.sweep(new Vec3(0,.55,0),new Vec3(0,.55,4),.12,world(slab)).position().z<1);
    }
    @Test void remoteFocusCannotCrossRockToAnotherRoom() {
        var r=CameraCollision.sweep(new Vec3(0,1,0),new Vec3(20,1,0),.12,world(new AABB(2,-5,-5,3,5,5)));
        assertTrue(r.position().x<2);
    }
    @Test void engulfedEyesFailClosedInsteadOfChoosingExitThroughWall() {
        var eyes=new Vec3(.5,.5,.5);var r=CameraCollision.sweep(eyes,new Vec3(0,8,0),.12,world(new AABB(0,0,0,1,1,1)));
        assertTrue(r.blockedStart());assertEquals(eyes,r.position());
    }
    @Test void openingRoofRestoresPreferredHeightWithoutMutatingZoomPreference() {
        var eyes=new Vec3(0,1.6,0);var desired=eyes.add(0,3,0);
        assertNotEquals(desired,CameraCollision.sweep(eyes,desired,.12,world(new AABB(-2,2,-2,2,3,2))).position());
        assertEquals(desired,CameraCollision.sweep(eyes,desired,.12,world()).position());
    }
    @Test void wideFovUsesEnoughClearanceForNearPlaneCorners() {
        assertTrue(CameraCollision.radius(110,3)>CameraCollision.radius(70,16d/9));
        assertTrue(CameraCollision.radius(110,3)>.05*Math.tan(Math.toRadians(55))*3);
    }
    @Test void veryRemoteFocusHasBoundedWorkAndZeroMovementIsSafe() {
        var eyes=new Vec3(0,1,0);assertEquals(96,CameraCollision.sweep(eyes,new Vec3(100000,1,0),.12,world()).position().x,1e-6);
        assertEquals(eyes,CameraCollision.sweep(eyes,eyes,.12,world()).position());
    }
}
