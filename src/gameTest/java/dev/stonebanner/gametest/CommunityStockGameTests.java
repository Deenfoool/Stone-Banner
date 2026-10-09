package dev.stonebanner.gametest;

import dev.stonebanner.citizen.*;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.entity.ModEntities;
import dev.stonebanner.production.*;
import dev.stonebanner.settlement.SettlementData;
import dev.stonebanner.storage.StorageData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class CommunityStockGameTests {
    private static final BlockPos STATION = new BlockPos(4, 1, 2), CHEST = new BlockPos(2, 1, 4);
    private record Fixture(HumanNpcEntity npc, ProductionData.Bill bill, UUID owner) {}
    private static Fixture prepare(GameTestHelper h, boolean camp) {
        var npc=CitizenQueueGameTests.prepare(h);
        h.getLevel().getDataStorage().set("stonebanner_settlements",new SettlementData());
        h.getLevel().getDataStorage().set("stonebanner_production",new ProductionData());
        // The 64-block stock query includes neighbouring arenas, unlike local worker fixtures.
        // Each scenario owns an exclusive batch; isolate its registry without changing their blocks/items.
        h.getLevel().getDataStorage().set("stonebanner_storage",new StorageData());
        for(var type:WorkType.values())npc.setWorkPriority(type,WorkPriority.DISABLED);
        for(int x=1;x<15;x++)for(int z=1;z<15;z++)for(int y=1;y<5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        h.setBlock(STATION,Blocks.CRAFTING_TABLE);h.setBlock(CHEST,Blocks.CHEST);
        h.assertTrue(StorageData.forLevel(h.getLevel()).register(h.getLevel(),h.absolutePos(CHEST))==StorageData.RegisterResult.ADDED,"Storage missing");
        var owner=UUID.randomUUID();var settlements=SettlementData.forLevel(h.getLevel());
        if(camp){
            h.assertTrue(settlements.create(owner,"Stock camp",npc.blockPosition())==SettlementData.Result.CREATED,"Camp missing");
            h.assertTrue(settlements.addResident(owner,npc.getUUID())==SettlementData.Result.JOINED,"Member missing");
        }
        var data=ProductionData.forLevel(h.getLevel());
        long id=data.addBill(owner,h.absolutePos(STATION),new ResourceLocation("minecraft","stick"),ProductionData.Mode.MAINTAIN,4);
        h.assertTrue(id>0,"Bill missing");
        var fixture=new Fixture(npc,data.bill(id),owner);
        if(camp)h.assertTrue(stock(h,fixture)==0,"Stock fixture starts with unrelated supplies: "+stock(h,fixture));
        return fixture;
    }
    private static int stock(GameTestHelper h,Fixture f){return ProductionService.stock(h.getLevel(),f.bill(),new ItemStack(Items.STICK));}
    private static ItemEntity dropped(GameTestHelper h,BlockPos pos,UUID owner,int count){
        var item=new ItemEntity(h.getLevel(),pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,new ItemStack(Items.STICK,count));
        if(owner!=null)item.getPersistentData().putUUID("SBProductionOwner",owner);
        h.getLevel().addFreshEntity(item);return item;
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_stock_own",timeoutTicks=100)
    public static void ownStockSatisfiesBillAndRemovalResumesIt(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {
        var f=prepare(h,true);var chest=(Container)h.getLevel().getBlockEntity(h.absolutePos(CHEST));
        chest.setItem(0,new ItemStack(Items.STICK,4));
        h.assertTrue(stock(h,f)==4&&ProductionService.activeBill(h.getLevel(),f.bill().station)==null,"Own stock incorrect: count="+stock(h,f)+" status="+f.bill().status);
        chest.removeItem(0,1);
        h.assertTrue(stock(h,f)==3&&ProductionService.activeBill(h.getLevel(),f.bill().station)==f.bill(),"Maintain did not resume");
        h.assertTrue(f.bill().made==0,"Stock observation changed produced counter");h.succeed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_stock_foreign_store",timeoutTicks=100)
    public static void storeOutsideCampDoesNotSatisfyBill(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {
        var f=prepare(h,false);
        // Move the scope, not the container: all physical changes stay inside this arena.
        var settlements=SettlementData.forLevel(h.getLevel());
        h.assertTrue(settlements.create(f.owner(),"Remote stock camp",f.npc().blockPosition().offset(96,0,0))==SettlementData.Result.CREATED,"Camp missing");
        var foreign=(Container)h.getLevel().getBlockEntity(h.absolutePos(CHEST));foreign.setItem(0,new ItemStack(Items.STICK,8));
        h.assertTrue(stock(h,f)==0&&ProductionService.activeBill(h.getLevel(),f.bill().station)==f.bill(),"Foreign stock stopped own production");
        h.assertTrue(foreign.getItem(0).getCount()==8,"Accounting mutated foreign stock");
        h.succeed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_stock_cargo",timeoutTicks=100)
    public static void cargoFollowsAuthoritativeMembershipNotPosition(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {
        var f=prepare(h,true);f.npc().citizenData().inventory().addHaulCargo(new ItemStack(Items.STICK,4));
        h.assertTrue(stock(h,f)==4,"Own member cargo ignored");
        var foreign=h.spawn(ModEntities.HUMAN_NPC.get(),new BlockPos(6,1,6));
        for(var type:WorkType.values())foreign.setWorkPriority(type,WorkPriority.DISABLED);
        foreign.citizenData().inventory().addHaulCargo(new ItemStack(Items.STICK,9));
        h.assertTrue(stock(h,f)==4,"Unaffiliated cargo counted");
        var settlements=SettlementData.forLevel(h.getLevel());var other=UUID.randomUUID();
        h.assertTrue(settlements.create(other,"Other stock camp",f.npc().blockPosition().offset(96,0,0))==SettlementData.Result.CREATED,"Other camp missing");
        h.assertTrue(settlements.addResident(other,foreign.getUUID())==SettlementData.Result.JOINED,"Other member missing");
        h.assertTrue(stock(h,f)==4,"Foreign member cargo counted");
        settlements.removeResident(f.npc().getUUID());settlements.addResident(other,f.npc().getUUID());
        h.assertTrue(stock(h,f)==0&&ProductionService.activeBill(h.getLevel(),f.bill().station)!=null,"Membership change did not invalidate stock");h.succeed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_stock_drops",timeoutTicks=100)
    public static void onlyTaggedOwnProductionDropsInsideCampAreCounted(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {
        var f=prepare(h,true);var pos=h.absolutePos(new BlockPos(7,1,7));
        var own=dropped(h,pos,f.owner(),4);var foreign=dropped(h,pos,UUID.randomUUID(),9);var unmarked=dropped(h,pos,null,8);
        h.assertTrue(stock(h,f)==4,"Production drops incorrect: count="+stock(h,f)+" ownAlive="+own.isAlive()+" memberScope="+SettlementData.forLevel(h.getLevel()).ownedBy(f.owner()).orElseThrow().contains(pos));
        var camp=SettlementData.forLevel(h.getLevel()).ownedBy(f.owner()).orElseThrow();
        own.setPos((camp.centerX()+2)*16+.5,pos.getY()+.5,pos.getZ()+.5);
        h.assertTrue(stock(h,f)==0,"Outside-camp production drop counted");
        own.discard();foreign.discard();unmarked.discard();h.succeed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_stock_worker",timeoutTicks=100)
    public static void communityBillRequiresCommunityWorker(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {
        var f=prepare(h,true);var job=CitizenJob.simple(1,WorkType.CRAFTING,f.bill().station,h.getLevel().getGameTime());
        h.assertTrue(ProductionService.allowed(f.npc(),job),"Own member refused");
        SettlementData.forLevel(h.getLevel()).removeResident(f.npc().getUUID());
        h.assertTrue(!ProductionService.allowed(f.npc(),job),"Unaffiliated worker can create uncounted community output");
        var before=f.bill().made;f.npc().citizenData().inventory().add(new ItemStack(Items.OAK_PLANKS,2));
        h.assertTrue(new CitizenProductionController(f.npc()).tick(job)==CitizenProductionController.Result.DEFER,"Foreign worker crafted");
        h.assertTrue(f.bill().made==before&&f.npc().citizenData().inventory().countPersonalItem(Items.OAK_PLANKS)==2,"Denied worker spent ingredients");h.succeed();

        });
    }
    @GameTest(setupTicks = 5, template="empty",batch="community_stock_legacy",timeoutTicks=100)
    public static void legacyBillKeepsLoadedSharedStock(GameTestHelper h){
        GameTestFixtures.runWhenReady(h, () -> {
        var f=prepare(h,false);
        // Legacy stock intentionally includes loaded NPCs in neighbouring arenas. Verify the
        // exact contribution, not an assumed empty shared world; never clear their inventories.
        int baseline=stock(h,f);f.bill().amount=baseline+4;
        f.npc().citizenData().inventory().addHaulCargo(new ItemStack(Items.STICK,4));
        h.assertTrue(stock(h,f)==baseline+4&&ProductionService.activeBill(h.getLevel(),f.bill().station)==null,"Legacy cargo contribution incorrect: baseline="+baseline+" actual="+stock(h,f));
        var job=CitizenJob.simple(1,WorkType.CRAFTING,f.bill().station,h.getLevel().getGameTime());
        f.npc().citizenData().inventory().clear();
        h.assertTrue(stock(h,f)==baseline,"Removing own cargo changed unrelated stock");
        h.assertTrue(ProductionService.allowed(f.npc(),job),"Legacy worker blocked");h.succeed();

        });
    }
}
