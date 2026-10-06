package dev.stonebanner.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static dev.stonebanner.settlement.SettlementData.Result.*;

class SettlementDataTest {
    private final UUID owner = UUID.randomUUID();
    private final BlockPos flag = new BlockPos(8, 64, 8);
    private SettlementData camp() {
        var data = new SettlementData();
        assertEquals(CREATED, data.create(owner, "Stone camp", flag)); return data;
    }
    @Test void onlyExplicitCreationRegistersCamp() {
        var data = new SettlementData(); data.bannerRemoved(flag);
        assertTrue(data.communities().isEmpty());
        assertEquals(CREATED, data.create(owner, "  Лагерь  ", flag));
        assertEquals("Лагерь", data.ownedBy(owner).orElseThrow().name());
        assertEquals(ALREADY_OWNED, data.create(owner, "Other", new BlockPos(256,64,0)));
    }
    @Test void nameRejectsControlsFormattingAndInvalidLength() {
        for (String name : new String[]{"", "a", "a".repeat(33), "a\nb", "§aCamp"})
            assertEquals("", SettlementData.validName(name));
        assertEquals("", SettlementData.validName(null));
        assertEquals("Камень", SettlementData.validName(" Камень "));
    }
    @Test void territoryCannotOverlapEvenWhenBannerIsMissing() {
        var d=camp(); d.bannerRemoved(flag);
        assertEquals(OVERLAP,d.create(UUID.randomUUID(),"Near",new BlockPos(32,64,32)));
        assertEquals(CREATED,d.create(UUID.randomUUID(),"Far",new BlockPos(48,64,0)));
    }
    @Test void destructionPreservesIdentityResidentsStageAndClaim() {
        var d=camp(); var npc=UUID.randomUUID(); d.addResident(owner,npc);
        assertEquals(PROMOTED,d.promote(owner,new SettlementData.Readiness(1,2,8,1)));
        var c=d.ownedBy(owner).orElseThrow(); var id=c.id(); d.bannerRemoved(flag);
        var loaded=SettlementData.load(d.save(new CompoundTag())).ownedBy(owner).orElseThrow();
        assertEquals(id,loaded.id()); assertEquals(c.name(),loaded.name());
        assertTrue(loaded.settlement());assertFalse(loaded.bannerActive());assertTrue(loaded.residents().contains(npc));
        assertTrue(loaded.contains(new BlockPos(-16,64,-16)));
    }
    @Test void restorationAndRelocationNeverShiftClaim() {
        var d=camp(); var c=d.ownedBy(owner).orElseThrow(); d.bannerRemoved(flag);
        assertFalse(c.bannerActive()); assertEquals(MOVED,d.relocate(owner,flag)); assertTrue(c.bannerActive());
        var next=new BlockPos(31,80,-16);assertEquals(MOVED,d.relocate(owner,next));
        assertEquals(0,c.centerX());assertEquals(0,c.centerZ());assertTrue(d.atBanner(flag).isEmpty());
        assertEquals(OUTSIDE_TERRITORY,d.relocate(owner,new BlockPos(32,64,0)));assertEquals(next,c.banner());
        assertEquals(NOT_FOUND,d.relocate(UUID.randomUUID(),flag));
    }
    @Test void residentsCannotBelongToTwoCommunities() {
        var d=camp();var other=UUID.randomUUID();d.create(other,"Other",new BlockPos(48,64,0));var npc=UUID.randomUUID();
        assertEquals(JOINED,d.addResident(owner,npc));assertEquals(ALREADY_RESIDENT,d.addResident(owner,npc));
        assertEquals(FOREIGN_RESIDENT,d.addResident(other,npc));d.removeResident(npc);
        assertTrue(d.residentHome(npc).isEmpty());assertEquals(JOINED,d.addResident(other,npc));
    }
    @Test void promotionNeedsActiveBannerAndCompleteProvisioning() {
        var d=camp();
        for(var r:new SettlementData.Readiness[]{new SettlementData.Readiness(0,2,8,1),new SettlementData.Readiness(1,1,8,1),new SettlementData.Readiness(1,2,7,1),new SettlementData.Readiness(1,2,8,0),new SettlementData.Readiness(1,2,8,1,2)})
            assertEquals(NOT_READY,d.promote(owner,r));
        d.bannerRemoved(flag);assertEquals(NOT_READY,d.promote(owner,new SettlementData.Readiness(1,2,8,1)));
        d.relocate(owner,flag);assertEquals(PROMOTED,d.promote(owner,new SettlementData.Readiness(1,2,8,1)));
        assertEquals(ALREADY_SETTLEMENT,d.promote(owner,new SettlementData.Readiness(1,2,8,1)));
    }
    @Test void residentLimitSurvivesSavingAndProvisioningIncludesEntireRoster() {
        var d=camp();
        for(int i=0;i<SettlementData.MAX_RESIDENTS;i++) assertEquals(JOINED,d.addResident(owner,UUID.randomUUID()));
        assertEquals(LIMIT,d.addResident(owner,UUID.randomUUID()));
        var loaded=SettlementData.load(d.save(new CompoundTag()));
        assertEquals(SettlementData.MAX_RESIDENTS,loaded.ownedBy(owner).orElseThrow().residents().size());
        var ready=new SettlementData.Readiness(1,257,1028,1,256);
        assertTrue(ready.ready());assertEquals(257,ready.people());
        assertThrows(IllegalArgumentException.class,()->new SettlementData.Readiness(2,3,12,1,1));
    }
    @Test void invalidSavedEntriesDoNotClaimTerritory() {
        var root=new CompoundTag();var list=new net.minecraft.nbt.ListTag();list.add(new CompoundTag());root.put("Communities",list);
        assertTrue(SettlementData.load(root).communities().isEmpty());
        var d=camp();root=d.save(new CompoundTag());root.getList("Communities",10).getCompound(0).putInt("CenterX",100);
        assertTrue(SettlementData.load(root).communities().isEmpty());
    }
}
