package dev.stonebanner.village;

import dev.stonebanner.citizen.CitizenData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VillageDataTest {
    @Test void returnSnapshotAndStatePersistAndDoNotExposeMutableNbt() {
        var data=new VillageData();var v=data.discover(BlockPos.ZERO,roster());UUID npc=UUID.randomUUID(),p=UUID.randomUUID(),original=UUID.randomUUID();
        var tag=new CompoundTag();tag.putUUID("UUID",original);tag.putString("Profession","farmer");data.contract(v,npc,p,tag);tag.putString("Profession","changed");
        assertEquals("farmer",v.origin(npc).orElseThrow().getString("Profession"));v.origin(npc).orElseThrow().putString("Profession","changed");
        data.beginReturn(v,npc);var copy=VillageData.load(data.save(new CompoundTag()));var c=copy.get(v.id()).orElseThrow();
        assertTrue(c.returning(npc));assertEquals("farmer",c.origin(npc).orElseThrow().getString("Profession"));
        assertTrue(copy.finishReturn(c,npc,original));assertFalse(copy.finishReturn(c,npc,original));assertTrue(c.contracts().isEmpty());assertTrue(c.origin(npc).isEmpty());
    }
    @Test void returningAuthorityAndDeathCleanupArePersistent() {
        var citizen=new CitizenData();UUID p=UUID.randomUUID();citizen.setRecruitedBy(p);citizen.setReturningToVillage(true);
        var loaded=new CitizenData();loaded.load(citizen.save());assertTrue(loaded.returningToVillage());assertFalse(loaded.canBeDirectedBy(p));
        var data=new VillageData();var v=data.discover(BlockPos.ZERO,roster());UUID npc=UUID.randomUUID();data.contract(v,npc,p,new CompoundTag());data.beginReturn(v,npc);data.closeContract(npc);
        assertTrue(data.contractHome(npc).isEmpty());assertTrue(v.origin(npc).isEmpty());assertFalse(v.returning(npc));
    }
    private static List<UUID> roster() { return List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()); }
    @Test void discoveryIsIdempotentAndDoesNotDuplicateResidents() {
        var data=new VillageData();var ids=roster();var v=data.discover(BlockPos.ZERO,ids);
        assertSame(v,data.discover(new BlockPos(3,0,0),ids));assertEquals(1,data.villages().size());assertEquals(4,v.residents().size());
        assertNull(data.discover(new BlockPos(200,0,0),List.of(UUID.randomUUID())));
    }
    @Test void elderSuccessionAndOriginalQuestAuthorAreIndependent() {
        var data=new VillageData();var v=data.discover(BlockPos.ZERO,roster());UUID old=v.elder();
        data.removeResident(old);assertNotEquals(old,v.elder());assertTrue(v.residents().contains(v.elder()));
        assertEquals(old,v.author(VillageData.QuestType.FOOD));
    }
    @Test void eachQuestPaysTrustOncePerPlayer() {
        var data=new VillageData();var v=data.discover(BlockPos.ZERO,roster());UUID p=UUID.randomUUID();
        assertTrue(data.accept(v,p,VillageData.QuestType.FOOD));assertFalse(data.accept(v,p,VillageData.QuestType.FOOD));
        assertTrue(data.complete(v,p,VillageData.QuestType.FOOD));assertFalse(data.complete(v,p,VillageData.QuestType.FOOD));
        assertEquals(12,data.reputation(v,p));assertEquals(0,data.reputation(v,UUID.randomUUID()));
    }
    @Test void defenceRequiresRealProgressAfterAcceptance() {
        var data=new VillageData();var v=data.discover(BlockPos.ZERO,roster());UUID p=UUID.randomUUID();
        data.kill(v,p);data.accept(v,p,VillageData.QuestType.DEFENCE);assertFalse(data.complete(v,p,VillageData.QuestType.DEFENCE));
        data.kill(v,p);data.kill(v,p);assertFalse(data.complete(v,p,VillageData.QuestType.DEFENCE));
        data.kill(v,p);assertTrue(data.complete(v,p,VillageData.QuestType.DEFENCE));
    }
    @Test void persistenceRetainsBoardAuthorsContractsTrustAndCompletion() {
        var data=new VillageData();var v=data.discover(BlockPos.ZERO,roster());UUID p=UUID.randomUUID(),citizen=UUID.randomUUID();
        data.board(v,new BlockPos(3,0,0));data.contract(v,citizen,p);data.accept(v,p,VillageData.QuestType.IRON);data.complete(v,p,VillageData.QuestType.IRON);
        var copy=VillageData.load(data.save(new CompoundTag()));var loaded=copy.get(v.id()).orElseThrow();
        assertEquals(v.board(),loaded.board());assertEquals(v.elder(),loaded.elder());assertEquals(v.residents(),loaded.residents());
        assertEquals(v.author(VillageData.QuestType.IRON),loaded.author(VillageData.QuestType.IRON));assertEquals(p,loaded.contracts().get(citizen));
        assertEquals(16,copy.reputation(loaded,p));assertTrue(copy.progress(loaded,p,VillageData.QuestType.IRON).orElseThrow().complete());
        assertFalse(copy.complete(loaded,p,VillageData.QuestType.IRON));
    }
    @Test void trustIsLocalAndClamped() {
        var d=new VillageData();var a=d.discover(BlockPos.ZERO,roster());var b=d.discover(new BlockPos(200,0,0),roster());UUID p=UUID.randomUUID();
        d.reputation(a,p,1000);assertEquals(100,d.reputation(a,p));assertEquals(0,d.reputation(b,p));d.reputation(a,p,-1000);assertEquals(-100,d.reputation(a,p));
    }
    @Test void recruitmentPriceAndThresholdsProtectVillage() {
        assertFalse(RecruitmentRules.enoughPopulation(3));assertTrue(RecruitmentRules.enoughPopulation(4));
        assertFalse(RecruitmentRules.trusted(19,false));assertTrue(RecruitmentRules.trusted(20,false));assertFalse(RecruitmentRules.trusted(39,true));
        assertTrue(RecruitmentRules.price(false,3,20,false)>RecruitmentRules.price(true,1,20,false));
        assertTrue(RecruitmentRules.price(true,1,100,true)>=4);
    }
    @Test void contractAuthorityPersistsAcrossReloadAndLegacyCitizensStayCompatible() {
        UUID owner=UUID.randomUUID();var citizen=new CitizenData();assertTrue(citizen.canBeDirectedBy(UUID.randomUUID()));
        citizen.setRecruitedBy(owner);var copy=new CitizenData();copy.load(citizen.save());assertTrue(copy.canBeDirectedBy(owner));assertFalse(copy.canBeDirectedBy(UUID.randomUUID()));
        copy.load(new CompoundTag());assertTrue(copy.canBeDirectedBy(UUID.randomUUID()));
    }
}
