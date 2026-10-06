package dev.stonebanner.geology;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FertilityRulesTest {
    @Test void stoneAndWaterNeverBecomeFertileFromClimateAlone(){assertEquals(0,FertilityRules.score(false,false,0,1,.7f,true));}
    @Test void WaterAndHydratedFarmlandImproveActualSoil(){int dry=FertilityRules.score(true,false,0,.5f,.7f,false);assertTrue(FertilityRules.score(true,false,0,.5f,.7f,true)>dry);assertTrue(FertilityRules.score(true,true,7,.5f,.7f,false)>FertilityRules.score(true,true,0,.5f,.7f,false));}
    @Test void scoreStaysBoundedAcrossExtremeClimateValues(){assertTrue(FertilityRules.score(true,true,100,10,100,true)<=100);assertTrue(FertilityRules.score(true,true,-1,-1,-100,false)>=1);}
}
