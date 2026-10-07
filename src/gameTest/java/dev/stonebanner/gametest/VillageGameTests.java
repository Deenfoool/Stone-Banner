package dev.stonebanner.gametest;

import com.mojang.authlib.GameProfile;
import dev.stonebanner.village.*;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class VillageGameTests {
    @GameTest(template="empty",batch="village_trust",timeoutTicks=100)
    public static void lowTrustAndLastSpecialistPreventContracts(GameTestHelper h){
        var f=prepare(h);var old=recruit(f);beside(f.player,old);f.player.getInventory().add(new ItemStack(Items.EMERALD,64));
        h.assertTrue(VillageService.hire(f.player,f.village.id(),old.getUUID(),false)==VillageService.Result.TRUST,"Untrusted player recruited");
        VillageData.forLevel(h.getLevel()).reputation(f.village,f.player.getUUID(),100);
        old.setVillagerData(old.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        h.assertTrue(VillageService.hire(f.player,f.village.id(),old.getUUID(),false)==VillageService.Result.SPECIALIST,"Last farmer recruited");
        h.assertTrue(VillageService.count(f.player,s->s.is(Items.EMERALD))==64,"Rejected hire charged emeralds");h.succeed();
    }

    @GameTest(template="empty",batch="village_combat",timeoutTicks=100)
    public static void realKillsAdvanceDefenceAndViolenceReducesTrust(GameTestHelper h){
        var f=prepare(h);var data=VillageData.forLevel(h.getLevel());
        h.assertTrue(VillageService.accept(f.player,f.village.id(),VillageData.QuestType.DEFENCE)==VillageService.Result.OK,"Defence accept failed");
        for(int i=0;i<3;i++){
            var zombie=h.spawn(EntityType.ZOMBIE,new BlockPos(10,1,10));zombie.setNoAi(true);
            zombie.hurt(zombie.damageSources().playerAttack(f.player),1000);
        }
        h.assertTrue(data.progress(f.village,f.player.getUUID(),VillageData.QuestType.DEFENCE).orElseThrow().count()==3,"Real kills not counted");
        var author=(Villager)VillageService.questRecipient(h.getLevel(),f.village,VillageData.QuestType.DEFENCE);beside(f.player,author);
        h.assertTrue(VillageService.submit(f.player,f.village.id(),VillageData.QuestType.DEFENCE,author.getUUID())==VillageService.Result.OK,"Defence not rewarded");
        int previous=data.reputation(f.village,f.player.getUUID());var victim=recruit(f);victim.hurt(victim.damageSources().playerAttack(f.player),1);
        h.assertTrue(data.reputation(f.village,f.player.getUUID())==previous-5,"Violence not penalized");h.succeed();
    }

    @GameTest(template="empty",batch="village_migration",timeoutTicks=350)
    public static void preparedSettlerWalksToCamp(GameTestHelper h){
        var f=prepare(h);var old=recruit(f);beside(f.player,old);
        for(int x=15;x<=44;x++)for(int z=1;z<15;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        for(int y=1;y<5;y++)h.setBlock(new BlockPos(15,y,4),Blocks.AIR);
        var banner=new BlockPos(40,1,4);h.setBlock(banner,Blocks.WHITE_BANNER);
        var settlements=dev.stonebanner.settlement.SettlementData.forLevel(h.getLevel());
        h.assertTrue(settlements.create(f.player.getUUID(),"Test camp",h.absolutePos(banner))==dev.stonebanner.settlement.SettlementData.Result.CREATED,"Camp creation failed");
        for(int x:new int[]{39,41}){
            h.setBlock(new BlockPos(x,1,6),Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING,net.minecraft.core.Direction.SOUTH));
            h.setBlock(new BlockPos(x,1,7),Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING,net.minecraft.core.Direction.SOUTH)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        }
        var chest=new BlockPos(40,1,9);h.setBlock(chest,Blocks.CHEST);
        ((net.minecraft.world.Container)h.getLevel().getBlockEntity(h.absolutePos(chest))).setItem(0,new ItemStack(Items.BREAD,8));
        dev.stonebanner.storage.StorageData.forLevel(h.getLevel()).register(h.getLevel(),h.absolutePos(chest));
        VillageData.forLevel(h.getLevel()).reputation(f.village,f.player.getUUID(),100);f.player.getInventory().add(new ItemStack(Items.EMERALD,64));
        var migrationResult = VillageService.hire(f.player,f.village.id(),old.getUUID(),true);
        h.assertTrue(migrationResult==VillageService.Result.OK,"Prepared migration rejected: " + migrationResult);
        var camp=settlements.ownedBy(f.player.getUUID()).orElseThrow();UUID id=f.village.contracts().keySet().iterator().next();
        var npc=(HumanNpcEntity)h.getLevel().getEntity(id);
        h.assertTrue(camp.residents().contains(id)&&npc.citizenData().home().communityId().equals(camp.id().toString()),"New home not assigned");
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(!npc.commandController().hasActiveCommand(),"Migration route unfinished");
            h.assertTrue(npc.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(banner)))<9,"Settler did not walk to camp");
        }).thenSucceed();
    }
    record Fixture(VillageData.Village village,List<Villager> residents,ServerPlayer player) {}
    static Fixture prepare(GameTestHelper h) {
        CitizenQueueGameTests.prepare(h);
        // Separate batches deliberately reset only their isolated GameTest world's village SavedData.
        h.getLevel().getDataStorage().set("stonebanner_villages",new VillageData());
        h.getLevel().getDataStorage().set("stonebanner_settlements",new dev.stonebanner.settlement.SettlementData());
        for(var old:h.getLevel().getEntitiesOfClass(Villager.class,new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(8,1,8))).inflate(48)))old.discard();
        var villagers=new ArrayList<Villager>();
        for(int i=0;i<5;i++){var npc=h.spawn(EntityType.VILLAGER,new BlockPos(3+i,1,4));npc.setNoAi(true);villagers.add(npc);}
        var bell=new BlockPos(8,1,8);h.setBlock(bell,Blocks.BELL);
        var v=VillageService.discover(h.getLevel(),h.absolutePos(bell));h.assertTrue(v!=null,"Village not detected");
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"VillageTester"));
        p.getInventory().clearContent();p.setPos(v.board().getX()+.5,v.board().getY(),v.board().getZ()-1.5);
        return new Fixture(v,villagers,p);
    }
    static Villager recruit(Fixture f){return f.residents.stream().filter(n->!n.getUUID().equals(f.village.elder())).findFirst().orElseThrow();}
    static void beside(ServerPlayer p,Villager v){p.setPos(v.getX(),v.getY(),v.getZ()-1);}

    @GameTest(template="empty",batch="village_discovery",timeoutTicks=100)
    public static void detectionAndBoardAreIdempotent(GameTestHelper h){
        var f=prepare(h);var again=VillageService.discover(h.getLevel(),f.village.center());
        h.assertTrue(again.id().equals(f.village.id())&&again.residents().size()==5,"Detection duplicated village/population");
        h.assertTrue(h.getLevel().getBlockState(f.village.board()).is(VillageBlocks.BOARD.get()),"Board not placed");h.succeed();
    }
    @GameTest(template="empty",batch="village_delivery",timeoutTicks=100)
    public static void suppliesPayExactlyOnceToAuthor(GameTestHelper h){
        var f=prepare(h);var type=VillageData.QuestType.FOOD;
        h.assertTrue(VillageService.accept(f.player,f.village.id(),type)==VillageService.Result.OK,"Quest not accepted at board");
        f.player.getInventory().add(new ItemStack(Items.BREAD,type.amount));
        var author=(Villager)VillageService.questRecipient(h.getLevel(),f.village,type);beside(f.player,author);
        h.assertTrue(VillageService.submit(f.player,f.village.id(),type,author.getUUID())==VillageService.Result.OK,"Author refused supplies");
        h.assertTrue(VillageService.count(f.player,s->s.is(Items.BREAD))==0,"Supplies not removed");
        h.assertTrue(VillageService.count(f.player,s->s.is(Items.EMERALD))==type.emeralds,"Incorrect reward");
        h.assertTrue(VillageService.submit(f.player,f.village.id(),type,author.getUUID())==VillageService.Result.ALREADY,"Quest paid twice");
        h.assertTrue(VillageService.count(f.player,s->s.is(Items.EMERALD))==type.emeralds,"Repeat submission duplicated reward");h.succeed();
    }
    @GameTest(template="empty",batch="village_author",timeoutTicks=100)
    public static void wrongAuthorAndRemoteSubmissionDoNotConsumeItems(GameTestHelper h){
        var f=prepare(h);var type=VillageData.QuestType.IRON;
        h.assertTrue(VillageService.accept(f.player,f.village.id(),type)==VillageService.Result.OK,"Accept failed");
        f.player.getInventory().add(new ItemStack(Items.IRON_INGOT,type.amount));
        var author=(Villager)VillageService.questRecipient(h.getLevel(),f.village,type);
        var wrong=f.residents.stream().filter(n->n!=author).findFirst().orElseThrow();beside(f.player,wrong);
        h.assertTrue(VillageService.submit(f.player,f.village.id(),type,wrong.getUUID())==VillageService.Result.AUTHOR,"Wrong author accepted");
        f.player.setPos(author.getX()+30,author.getY(),author.getZ());
        h.assertTrue(VillageService.submit(f.player,f.village.id(),type,author.getUUID())==VillageService.Result.AUTHOR,"Remote submission accepted");
        h.assertTrue(VillageService.count(f.player,s->s.is(Items.IRON_INGOT))==type.amount,"Rejected submission took items");h.succeed();
    }
    @GameTest(template="empty",batch="village_fallback",timeoutTicks=100)
    public static void unavailableAuthorFallsBackToElder(GameTestHelper h){
        var f=prepare(h);var data=VillageData.forLevel(h.getLevel());var author=recruit(f);
        data.author(f.village,VillageData.QuestType.FOOD,author.getUUID());
        h.assertTrue(VillageService.accept(f.player,f.village.id(),VillageData.QuestType.FOOD)==VillageService.Result.OK,"Accept failed");
        author.discard();var elder=(Villager)h.getLevel().getEntity(f.village.elder());beside(f.player,elder);
        f.player.getInventory().add(new ItemStack(Items.BREAD,12));
        h.assertTrue(VillageService.submit(f.player,f.village.id(),VillageData.QuestType.FOOD,elder.getUUID())==VillageService.Result.OK,"Elder fallback failed");h.succeed();
    }
    @GameTest(template="empty",batch="village_recruit",timeoutTicks=100)
    public static void paidRecruitmentTransfersOnlyOneResident(GameTestHelper h){
        var f=prepare(h);var old=recruit(f);beside(f.player,old);var data=VillageData.forLevel(h.getLevel());
        data.reputation(f.village,f.player.getUUID(),30);f.player.getInventory().add(new ItemStack(Items.EMERALD,64));
        old.getInventory().addItem(new ItemStack(Items.BREAD,3));
        int price=RecruitmentRules.price(true,1,30,false);
        h.assertTrue(VillageService.hire(f.player,f.village.id(),old.getUUID(),false)==VillageService.Result.OK,"Recruitment failed");
        h.assertTrue(old.isRemoved()&&!f.village.residents().contains(old.getUUID()),"Old villager remained");
        h.assertTrue(f.village.residents().size()==4&&f.village.contracts().size()==1,"Population/contract duplicated");
        UUID hired=f.village.contracts().keySet().iterator().next();var citizen=(HumanNpcEntity)h.getLevel().getEntity(hired);
        h.assertTrue(citizen!=null&&citizen.citizenData().canBeDirectedBy(f.player.getUUID())&&!citizen.citizenData().canBeDirectedBy(UUID.randomUUID()),"Recruit authority missing");
        h.assertTrue(citizen.citizenData().inventory().snapshot().stream().filter(s->s.is(Items.BREAD)).mapToInt(ItemStack::getCount).sum()==3,"Villager inventory lost");
        h.assertTrue(VillageService.count(f.player,s->s.is(Items.EMERALD))==64-price,"Wrong contract payment");
        h.assertTrue(VillageService.hire(f.player,f.village.id(),old.getUUID(),false)==VillageService.Result.INVALID,"Same resident hired twice");h.succeed();
    }
    @GameTest(template="empty",batch="village_protection",timeoutTicks=100)
    public static void elderAndUnpreparedMigrationAreRejectedWithoutPayment(GameTestHelper h){
        var f=prepare(h);var data=VillageData.forLevel(h.getLevel());data.reputation(f.village,f.player.getUUID(),100);
        f.player.getInventory().add(new ItemStack(Items.EMERALD,64));var elder=(Villager)h.getLevel().getEntity(f.village.elder());beside(f.player,elder);
        h.assertTrue(VillageService.hire(f.player,f.village.id(),elder.getUUID(),false)==VillageService.Result.ELDER,"Elder recruited");
        var resident=recruit(f);beside(f.player,resident);
        h.assertTrue(VillageService.hire(f.player,f.village.id(),resident.getUUID(),true)==VillageService.Result.PROVISIONS,"Unprepared migration accepted");
        h.assertTrue(VillageService.count(f.player,s->s.is(Items.EMERALD))==64&&!resident.isRemoved(),"Rejected contract changed world/payment");h.succeed();
    }
    @GameTest(template="empty",batch="village_succession",timeoutTicks=100)
    public static void elderDeathElectsSuccessor(GameTestHelper h){
        var f=prepare(h);UUID old=f.village.elder();var elder=(Villager)h.getLevel().getEntity(old);
        elder.hurt(elder.damageSources().genericKill(),1000);
        h.assertTrue(!old.equals(f.village.elder())&&f.village.residents().contains(f.village.elder()),"No elder successor after death");
        h.assertTrue(f.village.residents().size()==4,"Dead elder remained resident");h.succeed();
    }
}
