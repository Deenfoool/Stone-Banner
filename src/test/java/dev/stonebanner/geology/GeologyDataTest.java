package dev.stonebanner.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GeologyDataTest {
    private final UUID camp=UUID.randomUUID();private final BlockPos table=new BlockPos(1,65,2);
    private GeologyData researched(int tier){var d=new GeologyData();for(int i=1;i<=tier;i++){assertTrue(d.start(camp,table));for(int j=0;j<30*i;j++)d.advance(camp);}return d;}
    @Test void tableResearchTakesRealWorkAndUnlocksOnlyNextStage(){var d=new GeologyData();d.start(camp,table);for(int i=0;i<29;i++)assertFalse(d.advance(camp));assertEquals(0,d.knowledge(camp).tier());assertTrue(d.advance(camp));assertEquals(1,d.knowledge(camp).tier());assertFalse(d.advance(camp));assertEquals(0,d.knowledge(camp).surveyed(0,0));}
    @Test void rebindingPausedResearchDoesNotRestartOrUpgradeIt(){var d=new GeologyData();d.start(camp,table);d.advance(camp);d.start(camp,table.offset(2,0,0));assertEquals(29,d.knowledge(camp).remaining());assertEquals(table.offset(2,0,0),d.knowledge(camp).table());}
    @Test void progressAndSurveyTierSurviveSaving(){var d=researched(2);d.surveyed(camp,-2,3,2);d.start(camp,table);d.advance(camp);var loaded=GeologyData.load(d.save(new CompoundTag()));var k=loaded.knowledge(camp);assertEquals(2,k.tier());assertEquals(89,k.remaining());assertEquals(2,k.surveyed(-2,3));assertEquals(table,k.table());}
    @Test void knowledgeBelongsToCommunityAndChunk(){var d=researched(2);d.surveyed(camp,-1,0,2);assertEquals(0,d.knowledge(camp).surveyed(0,0));assertEquals(0,d.knowledge(UUID.randomUUID()).surveyed(-1,0));}
    @Test void newResearchDoesNotSilentlyUpgradeExistingSurveys(){var d=researched(1);d.surveyed(camp,0,0,1);d.start(camp,table);for(int i=0;i<60;i++)d.advance(camp);assertEquals(1,d.knowledge(camp).surveyed(0,0));d.surveyed(camp,0,0,2);assertEquals(2,d.knowledge(camp).surveyed(0,0));d.surveyed(camp,0,0,1);assertEquals(2,d.knowledge(camp).surveyed(0,0));}
    @Test void finalStageStopsFurtherResearch(){var d=researched(4);assertFalse(d.start(camp,table));assertEquals(4,d.knowledge(camp).tier());assertEquals(0,d.knowledge(camp).remaining());}
    @Test void malformedNbtCannotUnlockSurveyBeyondResearch(){var d=researched(1);d.surveyed(camp,0,0,1);var root=d.save(new CompoundTag());var tag=root.getList("Communities",10).getCompound(0);tag.putInt("Remaining",9999);tag.getList("Surveys",10).getCompound(0).putInt("Tier",4);var k=GeologyData.load(root).knowledge(camp);assertEquals(1,k.surveyed(0,0));assertEquals(60,k.remaining());}
    @Test void surveyCapIsBoundedWithoutClearingResearch(){var d=researched(4);for(int i=0;i<GeologyData.MAX_SURVEYS+1;i++)d.surveyed(camp,i,0,4);assertEquals(0,d.knowledge(camp).surveyed(0,0));assertEquals(4,d.knowledge(camp).surveyed(GeologyData.MAX_SURVEYS,0));assertEquals(4,d.knowledge(camp).tier());assertThrows(IllegalArgumentException.class,()->d.surveyed(camp,0,0,5));}
}
