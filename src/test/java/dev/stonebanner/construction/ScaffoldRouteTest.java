package dev.stonebanner.construction;

import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScaffoldRouteTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void discontinuousOverlappingOrUnboundedAccessRoutesAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new ScaffoldRoute(BlockPos.ZERO,2,Direction.UP,List.of()));
        assertThrows(IllegalArgumentException.class,()->new ScaffoldRoute(BlockPos.ZERO,ScaffoldRoute.MAX_HEIGHT,Direction.NORTH,List.of()));
        assertThrows(IllegalArgumentException.class,()->new ScaffoldRoute(BlockPos.ZERO,2,Direction.NORTH,List.of(new BlockPos(2,2,0))));
        assertThrows(IllegalArgumentException.class,()->new ScaffoldRoute(BlockPos.ZERO,2,Direction.NORTH,List.of(new BlockPos(0,2,-1))));
        assertThrows(IllegalArgumentException.class,()->new ScaffoldRoute(BlockPos.ZERO,2,Direction.NORTH,List.of(new BlockPos(1,2,0),new BlockPos(0,2,0))));
    }
    @Test void cancellationRetainsOwnedBlocksAndAccessAcrossSaveUntilCleanupFinishes(){
        var data=new ConstructionData();var owner=UUID.randomUUID();long id=data.add(owner,new BlockPos(10,65,10),Rotation.NONE);var plan=data.plan(id);
        plan.route=new ScaffoldRoute(new BlockPos(8,65,10),68,Direction.WEST,List.of(new BlockPos(9,68,10)));
        plan.temporary.putAll(plan.route.blocks());assertTrue(data.edit(owner,id,"cancel"));assertNotNull(data.plan(id));assertTrue(plan.cancelled);assertTrue(plan.cleanup);
        var loaded=ConstructionData.load(data.save(new CompoundTag()));var restored=loaded.plan(id);
        assertEquals(plan.route,restored.route);assertEquals(plan.temporary,restored.temporary);assertTrue(restored.cancelled);assertFalse(restored.completed);
        loaded.finishCancel(restored);assertNotNull(loaded.plan(id));
        restored.temporary.clear();loaded.finishCancel(restored);assertNull(loaded.plan(id));
    }
    @Test void takingOwnershipAwayDoesNotCreateAnItemOrKeepTheCellForAutomaticRemoval(){
        var data=new ConstructionData();var owner=UUID.randomUUID();var plan=data.plan(data.add(owner,new BlockPos(10,65,10),Rotation.NONE));
        var pos=new BlockPos(8,65,10);plan.temporary.put(pos,Blocks.COBBLESTONE.defaultBlockState());
        data.forget(pos);assertFalse(plan.temporary.containsKey(pos));assertTrue(data.edit(owner,plan.id,"cancel"));assertNull(data.plan(plan.id));
    }
    @Test void temporaryRoutesReserveExternalCellsForOtherPlans(){
        var data=new ConstructionData();var owner=UUID.randomUUID();var a=data.plan(data.add(owner,new BlockPos(10,65,10),Rotation.NONE));
        var b=data.plan(data.add(owner,new BlockPos(25,65,10),Rotation.NONE));a.route=new ScaffoldRoute(new BlockPos(8,65,10),68,Direction.WEST,List.of());
        assertTrue(data.reserved(new BlockPos(8,67,10),b));assertTrue(data.reserved(new BlockPos(7,67,10),b));assertFalse(data.reserved(new BlockPos(7,67,10),a));
    }
}
