package dev.stonebanner.gametest;

import com.mojang.authlib.GameProfile;
import dev.stonebanner.network.packet.*;
import dev.stonebanner.village.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stonebanner")
@PrefixGameTestTemplate(false)
public final class VillageJournalGameTests {
    private static VillageJournalPacket.Quest row(VillageGameTests.Fixture f,VillageData.QuestType type){
        return VillageJournalService.snapshot(f.player(),"").villages().stream().filter(v->v.id().equals(f.village().id())).findFirst().orElseThrow().quests().stream().filter(q->q.type()==type).findFirst().orElseThrow();
    }
    private static VillageJournalActionPacket request(VillageGameTests.Fixture f,VillageJournalActionPacket.Action action){return new VillageJournalActionPacket(action,f.village().id(),VillageData.QuestType.FOOD);}
    @GameTest(template="empty",batch="village_journal_cycle",timeoutTicks=100)
    public static void journalActionsUseActualInventoryAndRewardOnce(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var type=VillageData.QuestType.FOOD;
        h.assertTrue(row(f,type).state()==VillageJournalPacket.State.AVAILABLE&&row(f,type).accept(),"Board did not enable accept");
        h.assertTrue(VillageJournalService.act(f.player(),request(f,VillageJournalActionPacket.Action.ACCEPT))==VillageService.Result.OK,"GUI accept failed");
        f.player().getInventory().add(new ItemStack(Items.BREAD,12));
        var recipient=(Villager)VillageService.questRecipient(h.getLevel(),f.village(),type);VillageGameTests.beside(f.player(),recipient);
        var before=row(f,type);h.assertTrue(before.state()==VillageJournalPacket.State.ACTIVE&&before.progress()==12&&before.submit(),"Inventory readiness not reflected");
        h.assertTrue(VillageService.count(f.player(),s->s.is(Items.BREAD))==12,"Snapshot consumed supplies");
        h.assertTrue(VillageJournalService.act(f.player(),request(f,VillageJournalActionPacket.Action.SUBMIT))==VillageService.Result.OK,"GUI submit failed");
        h.assertTrue(VillageService.count(f.player(),s->s.is(Items.BREAD))==0&&VillageService.count(f.player(),s->s.is(Items.EMERALD))==4,"GUI did not transfer/pay actual items");
        var complete=row(f,type);h.assertTrue(complete.state()==VillageJournalPacket.State.COMPLETED&&!complete.submit(),"Completed quest remains actionable");
        h.assertTrue(VillageJournalService.act(f.player(),request(f,VillageJournalActionPacket.Action.SUBMIT))==VillageService.Result.ALREADY,"Duplicate GUI submit paid");h.succeed();
    }
    @GameTest(template="empty",batch="village_journal_security",timeoutTicks=100)
    public static void forgedButtonsCannotBypassSuppliesOrDistance(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var type=VillageData.QuestType.FOOD;
        VillageJournalService.act(f.player(),request(f,VillageJournalActionPacket.Action.ACCEPT));
        f.player().getInventory().add(new ItemStack(Items.BREAD,11));
        VillageGameTests.beside(f.player(),(Villager)VillageService.questRecipient(h.getLevel(),f.village(),type));
        h.assertTrue(row(f,type).progress()==11&&!row(f,type).submit(),"Missing supply button enabled");
        h.assertTrue(VillageJournalService.act(f.player(),request(f,VillageJournalActionPacket.Action.SUBMIT))==VillageService.Result.ITEMS,"Forged submit ignored supplies");
        f.player().getInventory().add(new ItemStack(Items.BREAD,1));f.player().setPos(f.player().getX()+100,f.player().getY(),f.player().getZ());
        h.assertTrue(!row(f,type).submit()&&VillageJournalService.act(f.player(),request(f,VillageJournalActionPacket.Action.SUBMIT))==VillageService.Result.AUTHOR,"Remote GUI submit succeeded");
        h.assertTrue(VillageJournalService.act(f.player(),new VillageJournalActionPacket(VillageJournalActionPacket.Action.ACCEPT,UUID.randomUUID(),type))==VillageService.Result.INVALID,"Unknown village accepted");
        h.assertTrue(VillageService.count(f.player(),s->s.is(Items.BREAD))==12&&VillageService.count(f.player(),s->s.is(Items.EMERALD))==0,"Rejected action changed items");h.succeed();
    }
    @GameTest(template="empty",batch="village_journal_fallback",timeoutTicks=100)
    public static void journalShowsFallbackAndPersonalProgress(GameTestHelper h){
        var f=VillageGameTests.prepare(h);var type=VillageData.QuestType.FOOD;var data=VillageData.forLevel(h.getLevel());
        data.accept(f.village(),f.player().getUUID(),type);
        var author=VillageGameTests.recruit(f);data.author(f.village(),type,author.getUUID());author.discard();data.removeResident(author.getUUID());
        h.assertTrue(row(f,type).recipient().equals(f.village().elder()),"Journal did not show fallback recipient");
        var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"JournalGuest"));
        h.assertTrue(VillageJournalService.snapshot(stranger,"").villages().get(0).quests().get(0).state()==VillageJournalPacket.State.AVAILABLE,"Other player received owner's progress");
        h.assertTrue(row(f,type).state()==VillageJournalPacket.State.ACTIVE,"Read-only journal reset owner progress");h.succeed();
    }
}
