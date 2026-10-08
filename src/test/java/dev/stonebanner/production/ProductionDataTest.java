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
    @Test void changingTargetKeepsProgressAndPauseAndCanReopenCompletedOrder(){
        var d=new ProductionData();long id=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,16);
        var bill=d.bill(id);bill.made=16;bill.paused=true;
        assertTrue(d.updateBill(owner,id,ProductionData.Mode.MAKE,24));assertFalse(bill.finished());
        assertEquals(16,bill.made);assertTrue(bill.paused);assertEquals(recipe,bill.recipe);
        assertTrue(d.updateBill(owner,id,ProductionData.Mode.MAKE,8));assertTrue(bill.finished());
        assertTrue(d.updateBill(owner,id,ProductionData.Mode.MAINTAIN,8));assertFalse(bill.finished());
        var loaded=ProductionData.load(d.save(new CompoundTag())).bill(id);
        assertEquals(ProductionData.Mode.MAINTAIN,loaded.mode);assertEquals(8,loaded.amount);
        assertEquals(16,loaded.made);assertTrue(loaded.paused);
    }
    @Test void rejectedEditsAndMovesLeaveOrdersUnchanged(){
        var d=new ProductionData();long id=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,16);
        assertFalse(d.updateBill(UUID.randomUUID(),id,ProductionData.Mode.MAINTAIN,64));
        assertFalse(d.updateBill(owner,id,ProductionData.Mode.MAKE,0));
        assertFalse(d.updateBill(owner,id,ProductionData.Mode.MAKE,4097));
        assertFalse(d.updateBill(owner,id,null,32));assertFalse(d.updateBill(owner,id+1,ProductionData.Mode.MAKE,32));
        assertFalse(d.moveBill(owner,id,-1));assertFalse(d.moveBill(owner,id,1));assertFalse(d.moveBill(owner,id,0));
        assertEquals(16,d.bill(id).amount);assertEquals(ProductionData.Mode.MAKE,d.bill(id).mode);
    }
    @Test void reorderingIsStationLocalAndSurvivesReload(){
        var d=new ProductionData();
        long first=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,1);
        long separate=d.addBill(UUID.randomUUID(),new BlockPos(10,0,0),recipe,ProductionData.Mode.MAKE,1);
        long second=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAINTAIN,16);
        long third=d.addBill(owner,BlockPos.ZERO,recipe,ProductionData.Mode.MAKE,32);
        d.bill(second).paused=true;
        assertFalse(d.moveBill(UUID.randomUUID(),third,-1));
        assertTrue(d.moveBill(owner,third,-1));assertTrue(d.moveBill(owner,third,-1));assertFalse(d.moveBill(owner,third,-1));
        var loaded=ProductionData.load(d.save(new CompoundTag()));
        assertEquals(java.util.List.of(third,separate,first,second),loaded.bills().stream().map(b->b.id).toList());
        assertTrue(loaded.bill(second).paused);assertTrue(loaded.moveBill(owner,third,1));
        assertEquals(java.util.List.of(first,separate,third,second),loaded.bills().stream().map(b->b.id).toList());
    }
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
