package dev.stonebanner.production;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProductionDataTest {
    private final UUID owner=UUID.randomUUID();
    private final ResourceLocation recipe=new ResourceLocation("minecraft:bread");
    @Test void fieldNormalizesCornersAndOwnsSoilLayer(){
        var data=new ProductionData();var f=data.field(data.addField(owner,new BlockPos(4,0,4),BlockPos.ZERO,FarmCrop.WHEAT));
        assertEquals(BlockPos.ZERO,f.min);assertTrue(f.contains(new BlockPos(2,0,3)));assertFalse(f.contains(new BlockPos(2,1,3)));
    }
    @Test void rejectsOverlapEvenForeignOwner(){
        var data=new ProductionData();assertTrue(data.addField(owner,BlockPos.ZERO,new BlockPos(15,0,15),FarmCrop.WHEAT)>0);
        assertEquals(-1,data.addField(UUID.randomUUID(),new BlockPos(15,0,15),new BlockPos(16,0,16),FarmCrop.CARROTS));
    }
    @Test void rejectsLargeVerticalAndOverflowAreas(){
        var data=new ProductionData();assertEquals(-1,data.addField(owner,BlockPos.ZERO,new BlockPos(16,0,16),FarmCrop.WHEAT));
        assertEquals(-1,data.addField(owner,BlockPos.ZERO,new BlockPos(0,1,0),FarmCrop.WHEAT));
        assertEquals(-1,data.addField(owner,new BlockPos(Integer.MIN_VALUE,0,0),new BlockPos(Integer.MAX_VALUE,0,0),FarmCrop.WHEAT));
    }
    @Test void billAmountsAndStationOwnershipValidated(){
        var d=new ProductionData();assertEquals(-1,d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,0));
        assertEquals(-1,d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,4097));
        assertTrue(d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,3)>0);
        assertEquals(-1,d.addBill(UUID.randomUUID(),BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,3));
    }
    @Test void foreignOwnerCannotChangePlans(){
        var d=new ProductionData();long id=d.addField(owner,BlockPos.ZERO,BlockPos.ZERO,FarmCrop.POTATOES);
        assertFalse(d.edit(UUID.randomUUID(),id,"remove"));assertNotNull(d.field(id));assertTrue(d.edit(owner,id,"pause"));assertTrue(d.field(id).paused);
        assertTrue(d.edit(owner,id,"resume"));assertFalse(d.field(id).paused);assertTrue(d.edit(owner,id,"fertilize"));assertTrue(d.field(id).fertilize);
    }
    @Test void saveReloadKeepsIdsCountersAndConfiguration(){
        var d=new ProductionData();long f=d.addField(owner,BlockPos.ZERO,BlockPos.ZERO,FarmCrop.BEETROOTS);
        long a=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,12),b=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAINTAIN,16);
        d.bill(a).made=12;d.bill(b).made=23;d.edit(owner,f,"fertilize");d.edit(owner,b,"pause");
        var loaded=ProductionData.load(d.save(new CompoundTag()));assertEquals(2,loaded.bills().size());assertTrue(loaded.bill(a).finished());
        assertEquals(23,loaded.bill(b).made);assertTrue(loaded.bill(b).paused);assertTrue(loaded.field(f).fertilize);
        assertTrue(loaded.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,1)>b);
    }
    @Test void removingOneBillDoesNotRemoveOtherStationOrders(){
        var d=new ProductionData();long a=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,1),b=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAINTAIN,5);
        assertTrue(d.edit(owner,a,"remove"));assertNull(d.bill(a));assertNotNull(d.bill(b));assertFalse(d.edit(owner,b,"fertilize"));
    }
    @Test void makeCountsItemsAndMaintainNeverPermanentlyFinishes(){
        var d=new ProductionData();var b=d.bill(d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,3));
        b.made=2;assertFalse(b.finished());b.made=4;assertTrue(b.finished());
        var m=d.bill(d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAINTAIN,3));m.made=100;assertFalse(m.finished());
    }
    @Test void malformedHugeSavedIdsCannotPoisonValidRecords(){
        var d=new ProductionData();long id=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,1);var tag=d.save(new CompoundTag());
        var bad=tag.getList("Bills",10).getCompound(0).copy();bad.putLong("Id",Long.MAX_VALUE);tag.getList("Bills",10).add(bad);tag.putLong("Next",Long.MAX_VALUE);
        var loaded=ProductionData.load(tag);assertNotNull(loaded.bill(id));assertEquals(1,loaded.bills().size());assertTrue(loaded.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,2)>id);
    }
}
