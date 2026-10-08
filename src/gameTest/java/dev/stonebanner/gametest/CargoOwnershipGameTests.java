package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.production.*;
import dev.stonebanner.settlement.SettlementData;
import dev.stonebanner.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CargoOwnershipGameTests {
    private static final BlockPos CHEST = new BlockPos(4,1,2);
    private record Fixture(HumanNpcEntity npc, UUID owner) {}
    private static Fixture prepare(GameTestHelper h) {
        var npc = CitizenQueueGameTests.prepare(h);
        h.getLevel().getDataStorage().set("stonebanner_settlements",new SettlementData());
        h.getLevel().getDataStorage().set("stonebanner_storage",new StorageData());
        h.getLevel().getDataStorage().set("stonebanner_production",new ProductionData());
        for (var type : WorkType.values()) npc.setWorkPriority(type,WorkPriority.DISABLED);
        for(int x=1;x<15;x++)for(int z=1;z<15;z++)for(int y=1;y<5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        h.setBlock(CHEST,Blocks.CHEST);
        h.assertTrue(StorageData.forLevel(h.getLevel()).register(h.getLevel(),h.absolutePos(CHEST))==StorageData.RegisterResult.ADDED,"Storage missing");
        var owner = UUID.randomUUID(); var settlements = SettlementData.forLevel(h.getLevel());
        h.assertTrue(settlements.create(owner,"Cargo camp",npc.blockPosition())==SettlementData.Result.CREATED,"Camp missing");
        h.assertTrue(settlements.addResident(owner,npc.getUUID())==SettlementData.Result.JOINED,"Member missing");
        return new Fixture(npc,owner);
    }
    private static Container chest(GameTestHelper h) { return (Container)h.getLevel().getBlockEntity(h.absolutePos(CHEST)); }
    private static int count(Container c, net.minecraft.world.item.Item item) {
        int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;
    }
    private static void deliver(Fixture f) {
        f.npc().setWorkPriority(WorkType.HAULING,WorkPriority.NORMAL);
        for(int i=0;i<3;i++)f.npc().workController().tick();
    }
    @GameTest(template="empty",batch="cargo_origin_pickup",timeoutTicks=100)
    public static void dropOwnerSurvivesPickupAndNpcReload(GameTestHelper h) {
        var f=prepare(h);var pos=f.npc().blockPosition();
        var item=new ItemEntity(h.getLevel(),pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,new ItemStack(Items.STICK,7));
        CargoOwnership.markDrop(item,f.owner());h.getLevel().addFreshEntity(item);
        h.assertTrue(DroppedItemHauling.collectInto(h.getLevel(),pos,f.npc())==7&&!item.isAlive(),"Pickup failed");
        var saved=new CompoundTag();f.npc().addAdditionalSaveData(saved);f.npc().readAdditionalSaveData(saved);
        var cargo=f.npc().citizenData().inventory().haulCargoSnapshot();
        h.assertTrue(cargo.size()==1&&f.owner().equals(cargo.get(0).owner())&&cargo.get(0).stack().getCount()==7,"Origin lost on NPC reload"); h.succeed();
    }
    @GameTest(template="empty",batch="cargo_origin_partial",timeoutTicks=100)
    public static void partialDepositKeepsOwnerOnRemainder(GameTestHelper h) {
        var f=prepare(h);var c=chest(h);
        for(int i=0;i<c.getContainerSize();i++)c.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        c.setItem(0,new ItemStack(Items.STICK,63));
        f.npc().citizenData().inventory().addHaulCargo(new ItemStack(Items.STICK,4),f.owner());deliver(f);
        var cargo=f.npc().citizenData().inventory().haulCargoSnapshot();
        h.assertTrue(count(c,Items.STICK)==64&&cargo.size()==1&&cargo.get(0).stack().getCount()==3&&f.owner().equals(cargo.get(0).owner()),"Partial deposit changed count or origin");h.succeed();
    }
    @GameTest(template="empty",batch="cargo_origin_switch",timeoutTicks=100)
    public static void membershipChangeBeforeDepositCannotRelabelOrDepositOldCargo(GameTestHelper h) {
        var f=prepare(h);f.npc().citizenData().inventory().addHaulCargo(new ItemStack(Items.STICK,4),f.owner());
        f.npc().setWorkPriority(WorkType.HAULING,WorkPriority.NORMAL);
        f.npc().workController().tick();f.npc().workController().tick();
        h.assertTrue(f.npc().workController().deliveryStatus()==DeliveryStatus.DEPOSITING,"Did not arrive before switch");
        SettlementData.forLevel(h.getLevel()).removeResident(f.npc().getUUID());
        f.npc().workController().tick();
        var cargo=f.npc().citizenData().inventory().haulCargoSnapshot();
        h.assertTrue(count(chest(h),Items.STICK)==0&&cargo.size()==1&&cargo.get(0).stack().getCount()==4&&f.owner().equals(cargo.get(0).owner()),"Membership loss laundered/lost cargo");
        h.assertTrue(f.npc().workController().deliveryStatus()==DeliveryStatus.WAITING_STORAGE,"No wait for authorized destination");h.succeed();
    }
    @GameTest(template="empty",batch="cargo_origin_mixed",timeoutTicks=100)
    public static void mixedCargoDeliversOnlyAuthorizedSlots(GameTestHelper h) {
        var f=prepare(h);var other=UUID.randomUUID();var settlements=SettlementData.forLevel(h.getLevel());
        h.assertTrue(settlements.create(other,"Other cargo camp",f.npc().blockPosition().offset(96,0,0))==SettlementData.Result.CREATED,"Other camp missing");
        var inv=f.npc().citizenData().inventory();inv.addHaulCargo(new ItemStack(Items.STICK,4),f.owner());inv.addHaulCargo(new ItemStack(Items.STICK,9),other);
        inv.add(new ItemStack(Items.APPLE,3));deliver(f);
        var cargo=inv.haulCargoSnapshot();
        h.assertTrue(count(chest(h),Items.STICK)==4&&cargo.size()==1&&cargo.get(0).stack().getCount()==9&&other.equals(cargo.get(0).owner()),"Foreign cargo deposited or origin changed");
        h.assertTrue(inv.countPersonalItem(Items.APPLE)==3,"Personal supplies deposited");h.succeed();
    }
    @GameTest(template="empty",batch="cargo_origin_stock",timeoutTicks=100)
    public static void maintainCountsOriginNotCurrentMembership(GameTestHelper h) {
        var f=prepare(h);var station=new BlockPos(2,1,3);h.setBlock(station,Blocks.CRAFTING_TABLE);
        var data=ProductionData.forLevel(h.getLevel());long id=data.addBill(f.owner(),h.absolutePos(station),new ResourceLocation("minecraft","stick"),ProductionData.Mode.MAINTAIN,4);
        f.npc().citizenData().inventory().addHaulCargo(new ItemStack(Items.STICK,4),f.owner());
        f.npc().citizenData().inventory().addHaulCargo(new ItemStack(Items.STICK,9),UUID.randomUUID());
        SettlementData.forLevel(h.getLevel()).removeResident(f.npc().getUUID());
        h.assertTrue(ProductionService.stock(h.getLevel(),data.bill(id),new ItemStack(Items.STICK))==4&&ProductionService.activeBill(h.getLevel(),h.absolutePos(station))==null,"Maintain reassigned cargo after membership loss");
        f.npc().citizenData().inventory().setHaulCargoStack(0,ItemStack.EMPTY);
        h.assertTrue(ProductionService.stock(h.getLevel(),data.bill(id),new ItemStack(Items.STICK))==0&&ProductionService.activeBill(h.getLevel(),h.absolutePos(station))!=null,"Foreign cargo stopped production");h.succeed();
    }
    @GameTest(template="empty",batch="cargo_origin_death",timeoutTicks=100)
    public static void deathDropsRealCargoWithEachOwnerExactlyOnce(GameTestHelper h) {
        var f=prepare(h);var other=UUID.randomUUID();var inv=f.npc().citizenData().inventory();inv.clear();
        inv.addHaulCargo(new ItemStack(Items.STICK,4),f.owner());inv.addHaulCargo(new ItemStack(Items.IRON_INGOT,9),other);inv.add(new ItemStack(Items.APPLE,3));
        f.npc().hurt(f.npc().damageSources().genericKill(),1000);
        h.startSequence().thenExecuteAfter(5,()->{
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(BlockPos.ZERO),h.absolutePos(new BlockPos(16,6,16))));
            h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.STICK)&&f.owner().equals(CargoOwnership.dropOwner(e))).mapToInt(e->e.getItem().getCount()).sum()==4,"Own death cargo lost ownership/count");
            h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.IRON_INGOT)&&other.equals(CargoOwnership.dropOwner(e))).mapToInt(e->e.getItem().getCount()).sum()==9,"Other death cargo lost ownership/count");
            h.assertTrue(drops.stream().filter(e->e.getItem().is(Items.APPLE)&&CargoOwnership.dropOwner(e)==null).mapToInt(e->e.getItem().getCount()).sum()==3&&!inv.hasHaulCargo(),"Personal item claimed or death retained cargo");
        }).thenSucceed();
    }
    @GameTest(template="empty",batch="cargo_origin_harvest",timeoutTicks=100)
    public static void harvestCargoBelongsToFieldOwner(GameTestHelper h) {
        var f=prepare(h);var soil=new BlockPos(2,0,3);h.setBlock(soil,Blocks.FARMLAND);h.setBlock(soil.above(),FarmCrop.WHEAT.block().getStateForAge(7));
        var data=ProductionData.forLevel(h.getLevel());long id=data.addField(f.owner(),h.absolutePos(soil),h.absolutePos(soil),FarmCrop.WHEAT);
        h.assertTrue(id>0,"Field missing");f.npc().setWorkPriority(WorkType.HAULING,WorkPriority.NORMAL);
        var controller=new CitizenProductionController(f.npc());var job=CitizenJob.simple(1,WorkType.FARMING,h.absolutePos(soil),h.getLevel().getGameTime());
        CitizenProductionController.Result result=null;
        for(int i=0;i<100&&result!=CitizenProductionController.Result.COMPLETE&&result!=CitizenProductionController.Result.DEFER;i++)result=controller.tick(job);
        var cargo=f.npc().citizenData().inventory().haulCargoSnapshot();
        h.assertTrue(result==CitizenProductionController.Result.COMPLETE&&cargo.stream().anyMatch(c->c.stack().is(Items.WHEAT))&&cargo.stream().allMatch(c->f.owner().equals(c.owner())),"Harvest origin lost: "+result+" / "+controller.reason());h.succeed();
    }
}
