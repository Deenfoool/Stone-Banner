package dev.stonebanner.gametest;

import com.mojang.authlib.GameProfile;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.packet.*;
import dev.stonebanner.network.packet.VillageRecruitmentActionPacket.Action;
import dev.stonebanner.village.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class VillageRecruitmentGameTests {
    private static VillageRecruitmentPacket snapshot(VillageGameTests.Fixture f,boolean contracts,int page){return VillageRecruitmentService.snapshot(f.player(),f.village().id(),contracts,page,null,false);}
    private static VillageRecruitmentPacket.Row row(VillageGameTests.Fixture f,UUID id){
        for(int page=0;page<2;page++)for(var r:snapshot(f,false,page).rows())if(r.id().equals(id))return r;
        throw new IllegalStateException("Missing resident");
    }
    @GameTest(template="empty",batch="village_recruitment_preview",timeoutTicks=100)
    public static void previewUsesSharedRequirementsWithoutMutation(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var old=VillageGameTests.recruit(f);VillageGameTests.beside(f.player(),old);
        h.assertTrue(row(f,old.getUUID()).companion()==VillageService.Result.TRUST,"Preview skipped trust");
        VillageData.forLevel(h.getLevel()).reputation(f.village(),f.player().getUUID(),100);f.player().getInventory().add(new ItemStack(Items.EMERALD,64));
        old.setVillagerData(old.getVillagerData().setProfession(VillagerProfession.NITWIT));
        var preview=row(f,old.getUUID());h.assertTrue(preview.companion()==VillageService.Result.OK&&preview.companionPrice()==4,"Nitwit quote does not match authoritative price");
        h.assertTrue(preview.settler()==VillageService.Result.PROVISIONS,"Unprepared camp preview passed");
        var elder=h.getLevel().getEntity(f.village().elder());f.player().setPos(elder.getX(),elder.getY(),elder.getZ()-1);
        h.assertTrue(row(f,f.village().elder()).companion()==VillageService.Result.ELDER,"Elder preview recruitable");
        h.assertTrue(!old.isRemoved()&&f.village().residents().size()==5&&f.village().contracts().isEmpty()&&VillageService.count(f.player(),s->s.is(Items.EMERALD))==64,"Read-only preview changed world/items");h.succeed();
    }
    @GameTest(template="empty",batch="village_recruitment_actions",timeoutTicks=150)
    public static void confirmedPriceIsBoundAndReplayCannotDuplicateNpc(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var old=VillageGameTests.recruit(f);UUID original=old.getUUID();VillageGameTests.beside(f.player(),old);
        var data=VillageData.forLevel(h.getLevel());data.reputation(f.village(),f.player().getUUID(),20);f.player().getInventory().add(new ItemStack(Items.EMERALD,64));int quote=row(f,original).companionPrice();
        data.reputation(f.village(),f.player().getUUID(),80);
        var stale=new VillageRecruitmentActionPacket(Action.COMPANION,f.village().id(),original,false,0,quote);
        h.assertTrue(VillageRecruitmentService.act(f.player(),stale)==VillageService.Result.PRICE_CHANGED,"Stale price silently charged");
        h.assertTrue(!old.isRemoved()&&f.village().contracts().isEmpty()&&VillageService.count(f.player(),s->s.is(Items.EMERALD))==64,"Stale quote mutated recruitment");
        h.assertTrue(VillageRecruitmentService.act(f.player(),new VillageRecruitmentActionPacket(Action.COMPANION,f.village().id(),original,false,0,-1))==VillageService.Result.INVALID,"Negative quote bypassed price binding");
        int price=row(f,original).companionPrice();var request=new VillageRecruitmentActionPacket(Action.COMPANION,f.village().id(),original,false,0,price);
        h.assertTrue(VillageRecruitmentService.act(f.player(),request)==VillageService.Result.OK,"Confirmed hire rejected");
        h.assertTrue(VillageRecruitmentService.act(f.player(),request)==VillageService.Result.INVALID,"Replay duplicated hired villager");
        var contracts=snapshot(f,true,0);h.assertTrue(contracts.total()==1&&contracts.rows().get(0).status().equals("companion"),"Owner contract missing");
        UUID id=contracts.rows().get(0).id();var npc=(HumanNpcEntity)h.getLevel().getEntity(id);f.player().setPos(npc.getX(),npc.getY(),npc.getZ()-1);
        var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"ContractGuest"));stranger.setPos(npc.getX(),npc.getY(),npc.getZ()-1);
        var dismissal=new VillageRecruitmentActionPacket(Action.DISMISS,f.village().id(),id,true,0,0);
        h.assertTrue(VillageRecruitmentService.act(stranger,dismissal)==VillageService.Result.INVALID,"Guest dismissed owner's NPC");
        h.startSequence().thenExecuteAfter(2,()->{
            h.assertTrue(VillageRecruitmentService.act(f.player(),dismissal)==VillageService.Result.OK,"GUI dismissal failed");
            var returning=snapshot(f,true,0).rows().get(0);h.assertTrue(returning.status().equals("returning")&&returning.dismiss()==VillageService.Result.ALREADY,"Pending return can be dismissed again");
            h.assertTrue(VillageService.count(f.player(),s->s.is(Items.EMERALD))==64-price,"Hire charged more than once or dismissal refunded");
        }).thenSucceed();
    }
    @GameTest(template="empty",batch="village_recruitment_paging",timeoutTicks=100)
    public static void serverPagesAreBoundedAndContractsArePrivate(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var data=VillageData.forLevel(h.getLevel());for(int i=0;i<7;i++)data.addResident(f.village(),UUID.randomUUID());
        var first=snapshot(f,false,-5);var last=snapshot(f,false,Integer.MAX_VALUE);
        h.assertTrue(first.page()==0&&first.rows().size()==4&&last.page()==2&&last.rows().size()==4&&last.total()==12,"Page not clamped/bounded");
        for(int i=0;i<5;i++)data.contract(f.village(),UUID.randomUUID(),f.player().getUUID());
        UUID outsider=UUID.randomUUID();data.contract(f.village(),UUID.randomUUID(),outsider);
        h.assertTrue(snapshot(f,true,0).total()==5&&snapshot(f,true,1).rows().size()==1,"Foreign contract leaked to owner");
        var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"ContractReader"));
        h.assertTrue(VillageRecruitmentService.snapshot(stranger,f.village().id(),true,0,null,false).rows().isEmpty(),"Private contract data leaked");h.succeed();
    }
    @GameTest(template="empty",batch="village_recruitment_stale",timeoutTicks=100)
    public static void staleEligibilityDoesNotAuthorizeRemoteOrUntrustedHire(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var old=VillageGameTests.recruit(f);VillageGameTests.beside(f.player(),old);var data=VillageData.forLevel(h.getLevel());data.reputation(f.village(),f.player().getUUID(),100);f.player().getInventory().add(new ItemStack(Items.EMERALD,64));
        var preview=row(f,old.getUUID());h.assertTrue(preview.companion()==VillageService.Result.OK,"Setup not eligible");
        var request=new VillageRecruitmentActionPacket(Action.COMPANION,f.village().id(),old.getUUID(),false,0,preview.companionPrice());
        f.player().setPos(old.getX()+50,old.getY(),old.getZ());h.assertTrue(VillageRecruitmentService.act(f.player(),request)==VillageService.Result.INVALID,"Remote hire used stale enabled button");
        VillageGameTests.beside(f.player(),old);data.reputation(f.village(),f.player().getUUID(),-100);
        h.assertTrue(VillageRecruitmentService.act(f.player(),request)==VillageService.Result.TRUST,"Stale trust authorized hire");
        h.assertTrue(!old.isRemoved()&&VillageService.count(f.player(),s->s.is(Items.EMERALD))==64&&f.village().contracts().isEmpty(),"Rejected hire mutated state");h.succeed();
    }
}
