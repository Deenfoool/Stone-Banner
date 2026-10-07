package dev.stonebanner.client;

import dev.stonebanner.client.control.CitizenInventoryClientCache;
import dev.stonebanner.client.screen.CitizenInspectorLayout;
import dev.stonebanner.citizen.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CitizenInspectorTest {
    @Test void workPagesCoverEveryColumnAtAllCommonScales(){for(int width:new int[]{320,427,640,960}){var l=CitizenInspectorLayout.of(width,240);var seen=new HashSet<Integer>();for(int page=0;page*l.workColumns()<WorkType.values().length;page++)for(int c=0;c<l.workColumns()&&page*l.workColumns()+c<WorkType.values().length;c++)assertTrue(seen.add(page*l.workColumns()+c));assertEquals(WorkType.values().length,seen.size());assertTrue(104+l.workColumns()*38<=l.width()-16);}}
    @Test void workPagesDoNotSkipCitizensAtRowBoundaries(){var l=CitizenInspectorLayout.of(320,240);int rows=l.dataRows(true);var seen=new HashSet<Integer>();for(int page=0;page*rows<19;page++)for(int row=0;row<rows&&page*rows+row<19;row++)assertTrue(seen.add(page*rows+row));assertEquals(19,seen.size());}
    @Test void pageClampingHandlesEmptyAndShrinkingLists(){var l=CitizenInspectorLayout.of(427,240);assertEquals(0,l.clampPage(8,0,5));assertEquals(0,l.clampPage(-2,30,5));assertEquals(1,l.clampPage(8,6,5));}
    @Test void cacheRejectsLateDifferentWorldAndIdentityResponses(){var world=ResourceLocation.parse("minecraft:overworld");UUID id=UUID.randomUUID();var d=new CitizenData();d.setSkill(CitizenSkill.MINING,7);CitizenInventoryClientCache.begin(world,id);CitizenInventoryClientCache.update(ResourceLocation.parse("minecraft:the_nether"),id,d.save());assertNull(CitizenInventoryClientCache.snapshot(world,id));CitizenInventoryClientCache.update(world,UUID.randomUUID(),d.save());assertNull(CitizenInventoryClientCache.snapshot(world,id));CitizenInventoryClientCache.clear();CitizenInventoryClientCache.update(world,id,d.save());assertNull(CitizenInventoryClientCache.snapshot(world,id));}
    @Test void cacheCopiesBothInputsAndOutputs(){var world=ResourceLocation.parse("minecraft:overworld");UUID id=UUID.randomUUID();var d=new CitizenData();d.setSkill(CitizenSkill.MINING,7);var tag=d.save();CitizenInventoryClientCache.begin(world,id);CitizenInventoryClientCache.update(world,id,tag);tag.getCompound("Skills").putInt("mining",0);var first=CitizenInventoryClientCache.snapshot(world,id);assertEquals(7,first.skill(CitizenSkill.MINING));first.setSkill(CitizenSkill.MINING,0);assertEquals(7,CitizenInventoryClientCache.snapshot(world,id).skill(CitizenSkill.MINING));CitizenInventoryClientCache.clear();}
}
