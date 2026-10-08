package dev.stonebanner.client.camera;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraAnchorTransitionTest {
    @Test void smoothBlendIsBoundedAndFinishes() {
        var blend = new CameraAnchorTransition();
        Vec3 from=new Vec3(0,2,0),to=new Vec3(12,2,0);
        blend.start(from,8);
        assertEquals(from,blend.toward(to,0));
        assertTrue(blend.toward(to,0.5).x>0);
        for(int i=0;i<7;i++)blend.tick();
        assertTrue(blend.toward(to,0).x<to.x);
        blend.tick();
        assertFalse(blend.active());
        assertEquals(to,blend.toward(to,0));
    }
    @Test void interruptionStartsFromLastSafeAnchor() {
        var blend=new CameraAnchorTransition();
        blend.start(new Vec3(2,0,0),5);
        blend.tick();
        blend.start(new Vec3(3,0,0),5);
        assertEquals(new Vec3(3,0,0),blend.toward(new Vec3(30,0,0),0));
        blend.reset();
        assertEquals(new Vec3(30,0,0),blend.toward(new Vec3(30,0,0),0));
    }
}
