package dev.stonebanner.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionDataTest {
    private final UUID owner=UUID.randomUUID();
    @Test void plansKeepOwnerRotationPauseAndCompletionAcrossReload(){
        var d=new ConstructionData();long id=d.add(owner,new BlockPos(-10,65,12),Rotation.CLOCKWISE_90);
        assertTrue(d.edit(owner,id,"pause"));var loaded=ConstructionData.load(d.save(new CompoundTag()));
        assertEquals(owner,loaded.plan(id).owner);assertEquals(Rotation.CLOCKWISE_90,loaded.plan(id).rotation);assertTrue(loaded.plan(id).paused);
        assertTrue(loaded.edit(owner,id,"resume"));loaded.complete(loaded.plan(id));
        assertTrue(ConstructionData.load(loaded.save(new CompoundTag())).plan(id).completed);
    }
    @Test void foreignEditsAndOverlappingRotatedSitesAreRejected(){
        var d=new ConstructionData();long id=d.add(owner,BlockPos.ZERO,Rotation.NONE);
        assertEquals(-1,d.add(UUID.randomUUID(),new BlockPos(4,0,6),Rotation.CLOCKWISE_180));
        assertFalse(d.edit(UUID.randomUUID(),id,"cancel"));assertNotNull(d.plan(id));
        assertTrue(d.add(owner,new BlockPos(5,0,0),Rotation.NONE)>id);
    }
    @Test void removingPlanDoesNotReuseIdAndUnknownActionsAreRejected(){
        var d=new ConstructionData();long id=d.add(owner,BlockPos.ZERO,Rotation.NONE);
        assertFalse(d.edit(owner,id,"invalid"));assertTrue(d.edit(owner,id,"cancel"));assertNull(d.at(BlockPos.ZERO));
        assertTrue(d.add(owner,BlockPos.ZERO,Rotation.NONE)>id);
    }
    @Test void corruptedSavedIdAndRotationCannotPoisonValidPlanOrNextId(){
        var d=new ConstructionData();long id=d.add(owner,BlockPos.ZERO,Rotation.NONE);var tag=d.save(new CompoundTag());var list=tag.getList("Plans",10);
        var invalid=list.getCompound(0).copy();invalid.putLong("Id",Long.MAX_VALUE);list.add(invalid);
        invalid=list.getCompound(0).copy();invalid.putLong("Id",5);invalid.putInt("Rotation",4);list.add(invalid);tag.putLong("Next",Long.MAX_VALUE);
        var loaded=ConstructionData.load(tag);assertEquals(1,loaded.plans().size());assertNotNull(loaded.plan(id));assertTrue(loaded.add(owner,new BlockPos(20,0,0),Rotation.NONE)>id);
    }
    @Test void globalLimitAlsoAppliesAcrossOwners(){
        var d=new ConstructionData();for(int i=0;i<ConstructionData.LIMIT;i++)assertTrue(d.add(UUID.randomUUID(),new BlockPos(i*10,0,0),Rotation.NONE)>0);
        assertEquals(-1,d.add(owner,new BlockPos(10000,0,0),Rotation.NONE));
    }
}
