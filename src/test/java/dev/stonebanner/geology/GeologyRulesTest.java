package dev.stonebanner.geology;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.stonebanner.geology.GeologyRules.Ore.*;

class GeologyRulesTest {
    private final Map<GeologyRules.Ore,Integer> counts=Map.of(COAL,500,IRON,10,GOLD,90,DIAMOND,2,ANCIENT_DEBRIS,10);
    @Test void researchingEveryMethodDoesNotRevealUnsurveyedChunks(){assertTrue(GeologyRules.visible(4,0,counts).isEmpty());}
    @Test void fundamentalsNeverTransmitOreNames(){assertTrue(GeologyRules.visible(1,4,counts).isEmpty());assertTrue(GeologyRules.visible(4,1,counts).isEmpty());}
    @Test void ordinaryMethodsOmitAllPreciousCategories(){var visible=GeologyRules.visible(2,4,counts);assertEquals(Set.of(COAL,IRON),new HashSet<>(visible.stream().map(GeologyRules.Entry::ore).toList()));}
    @Test void deepGeologyReportsPreciousPresenceWithoutActualAbundance(){var visible=GeologyRules.visible(3,3,counts);assertEquals(1,visible.stream().filter(e->e.ore()==GOLD).findFirst().orElseThrow().richness());assertEquals(3,visible.stream().filter(e->e.ore()==COAL).findFirst().orElseThrow().richness());}
    @Test void fullSurveyUsesPerResourceThresholds(){var visible=GeologyRules.visible(4,4,counts);assertEquals(3,visible.stream().filter(e->e.ore()==GOLD).findFirst().orElseThrow().richness());assertEquals(1,visible.stream().filter(e->e.ore()==DIAMOND).findFirst().orElseThrow().richness());assertEquals(3,DIAMOND.richness(24));assertEquals(1,COAL.richness(24));}
    @Test void depletionRemovesResourceFromSnapshot(){var depleted=new EnumMap<GeologyRules.Ore,Integer>(GeologyRules.Ore.class);depleted.putAll(counts);depleted.put(DIAMOND,0);assertFalse(GeologyRules.visible(4,4,depleted).stream().anyMatch(e->e.ore()==DIAMOND));assertEquals(0,GeologyRules.totalRichness(Map.of()));}
    @Test void researchUpgradeRequiresNewSurveyToGainNewDetails(){assertEquals(GeologyRules.visible(2,2,counts),GeologyRules.visible(4,2,counts));}
}
