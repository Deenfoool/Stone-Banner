package dev.stonebanner.gametest;

import com.mojang.authlib.GameProfile;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.village.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class VillageReturnGameTests {
    private static HumanNpcEntity hire(GameTestHelper h,VillageGameTests.Fixture f,Villager source){
        VillageGameTests.beside(f.player(),source);
        VillageData.forLevel(h.getLevel()).reputation(f.village(),f.player().getUUID(),100);
        f.player().getInventory().add(new ItemStack(Items.EMERALD,64));
        h.assertTrue(VillageService.hire(f.player(),f.village().id(),source.getUUID(),false)==VillageService.Result.OK,"Hire failed");
        return (HumanNpcEntity)h.getLevel().getEntity(f.village().contracts().keySet().iterator().next());
    }
    @GameTest(template="empty",batch="village_return",timeoutTicks=250)
    public static void roundTripPreservesTradesIdentityAndCurrentCargo(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var source=VillageGameTests.recruit(f);UUID original=source.getUUID();
        source.setCustomName(Component.literal("Return Trader"));
        source.setVillagerData(source.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        f.residents().stream().filter(v->v!=source).findFirst().orElseThrow().setVillagerData(source.getVillagerData());
        source.getOffers().clear();source.getOffers().add(new MerchantOffer(new ItemStack(Items.EMERALD,2),new ItemStack(Items.APPLE,3),5,2,.05f));
        var offers=source.getOffers().createTag();source.getInventory().addItem(new ItemStack(Items.BREAD,3));
        var npc=hire(h,f,source);npc.citizenData().inventory().addHaulCargo(new ItemStack(Items.DIAMOND,7),f.player().getUUID());
        npc.setPos(h.absolutePos(new BlockPos(2,1,2)).getX()+.5,h.absolutePos(new BlockPos(2,1,2)).getY(),h.absolutePos(new BlockPos(2,1,2)).getZ()+.5);
        f.player().setPos(npc.getX(),npc.getY(),npc.getZ()-1);
        int funds=VillageService.count(f.player(),s->s.is(Items.EMERALD));
        h.startSequence().thenExecuteAfter(2,()->{
            h.assertTrue(VillageReturnService.dismiss(f.player(),f.village().id(),npc.getUUID())==VillageService.Result.OK,"Dismissal rejected");
            h.assertTrue(!npc.citizenData().canBeDirectedBy(f.player().getUUID()),"Returning NPC accepts owner commands");
        }).thenWaitUntil(()->{
            var restored=h.getLevel().getEntity(original);
            h.assertTrue(restored instanceof Villager,"Resident not restored on arrival");
            var villager=(Villager)restored;
            h.assertTrue(npc.isRemoved()&&f.village().residents().size()==5&&f.village().contracts().isEmpty(),"Return duplicated roster/contract");
            h.assertTrue(villager.getOffers().createTag().equals(offers)&&villager.getVillagerData().getProfession()==VillagerProfession.FARMER,"Trades/profession lost");
            h.assertTrue(villager.getCustomName().getString().equals("Return Trader"),"Name lost");
            int bread=0,diamonds=0;for(int i=0;i<villager.getInventory().getContainerSize();i++){
                var stack=villager.getInventory().getItem(i);if(stack.is(Items.BREAD))bread+=stack.getCount();if(stack.is(Items.DIAMOND))diamonds+=stack.getCount();
            }
            h.assertTrue(bread==3&&diamonds==0,"Old food resurrected or employer cargo became personal supplies");
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(villager.blockPosition()).inflate(3));
            int dropped= drops.stream().filter(e->e.getItem().is(Items.DIAMOND)
                    &&f.player().getUUID().equals(dev.stonebanner.citizen.CargoOwnership.dropOwner(e))).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(dropped==7&&!npc.citizenData().inventory().hasHaulCargo(),"Return lost, duplicated or unmarked employer cargo");
            h.assertTrue(VillageService.count(f.player(),s->s.is(Items.EMERALD))==funds,"Dismissal refunded contract payment");
        }).thenSucceed();
    }
    @GameTest(template="empty",batch="village_return_reload",timeoutTicks=250)
    public static void pendingReturnResumesAfterNbtReload(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var source=VillageGameTests.recruit(f);UUID original=source.getUUID();
        var npc=hire(h,f,source);npc.setPos(h.absolutePos(new BlockPos(2,1,2)).getX()+.5,h.absolutePos(new BlockPos(2,1,2)).getY(),h.absolutePos(new BlockPos(2,1,2)).getZ()+.5);
        f.player().setPos(npc.getX(),npc.getY(),npc.getZ()-1);
        h.startSequence().thenExecuteAfter(2,()->{
            h.assertTrue(VillageReturnService.dismiss(f.player(),f.village().id(),npc.getUUID())==VillageService.Result.OK,"Dismissal failed");
            var saved=new CompoundTag();npc.addAdditionalSaveData(saved);npc.readAdditionalSaveData(saved);
            var data=VillageData.forLevel(h.getLevel());h.getLevel().getDataStorage().set("stonebanner_villages",VillageData.load(data.save(new CompoundTag())));
            h.assertTrue(npc.citizenData().returningToVillage()&&!npc.commandController().hasActiveCommand(),"Transient route not reset on reload");
        }).thenWaitUntil(()->h.assertTrue(h.getLevel().getEntity(original) instanceof Villager&&npc.isRemoved(),"Saved return did not resume")).thenSucceed();
    }
    @GameTest(template="empty",batch="village_return_reject",timeoutTicks=100)
    public static void unauthorizedAndBlockedReturnsKeepContract(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var npc=hire(h,f,VillageGameTests.recruit(f));
        var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Stranger"));stranger.setPos(npc.getX(),npc.getY(),npc.getZ()-1);
        h.assertTrue(VillageReturnService.dismiss(stranger,f.village().id(),npc.getUUID())==VillageService.Result.INVALID,"Stranger dismissed contract");
        npc.setPos(h.absolutePos(new BlockPos(2,1,2)).getX()+.5,h.absolutePos(new BlockPos(2,1,2)).getY(),h.absolutePos(new BlockPos(2,1,2)).getZ()+.5);
        f.player().setPos(npc.getX(),npc.getY(),npc.getZ()-1);
        for(int x=5;x<=11;x++)for(int z=5;z<=11;z++)for(int y=1;y<=5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        h.assertTrue(VillageReturnService.dismiss(f.player(),f.village().id(),npc.getUUID())==VillageService.Result.ROUTE,"Blocked return accepted");
        h.assertTrue(!npc.citizenData().returningToVillage()&&!npc.isRemoved()&&f.village().contracts().containsKey(npc.getUUID()),"Rejected return mutated contract");h.succeed();
    }
    @GameTest(template="empty",batch="village_return_legacy",timeoutTicks=100)
    public static void legacyContractDoesNotInventReplacementVillager(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var npc=hire(h,f,VillageGameTests.recruit(f));var data=VillageData.forLevel(h.getLevel());
        data.closeContract(npc.getUUID());data.contract(f.village(),npc.getUUID(),f.player().getUUID());
        h.assertTrue(VillageReturnService.dismiss(f.player(),f.village().id(),npc.getUUID())==VillageService.Result.LEGACY,"Legacy villager invented");
        h.assertTrue(!npc.isRemoved()&&!npc.citizenData().returningToVillage()&&f.village().residents().size()==4,"Legacy refusal lost resident");h.succeed();
    }
}
