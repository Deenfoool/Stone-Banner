package dev.stonebanner.citizen;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CitizenMedicineTest {
    @Test void smallDamageRoundTripDoesNotAmplifyTrauma() {
        var health=new CitizenHealth();health.damage(BodyPart.HEAD,0.1);var copy=new CitizenHealth();copy.load(health.save());
        assertEquals(0.1,copy.save().getCompound("Trauma").getDouble("head"),0.00001);
    }
    @Test void damageAccumulatesAndDoesNotGenerateMissingLimbs() {
        var health = new CitizenHealth();
        health.damage(BodyPart.LEFT_LEG, 3); assertEquals(InjuryState.WOUNDED, health.injury(BodyPart.LEFT_LEG));
        health.damage(BodyPart.LEFT_LEG, 3); assertEquals(InjuryState.HEAVY_WOUND, health.injury(BodyPart.LEFT_LEG));
        health.damage(BodyPart.LEFT_LEG, 5); assertEquals(InjuryState.FRACTURE, health.injury(BodyPart.LEFT_LEG));
        health.damage(BodyPart.HEAD, 100); assertEquals(InjuryState.HEAVY_WOUND, health.injury(BodyPart.HEAD));
        health.damage(BodyPart.RIGHT_ARM, Double.NaN); health.damage(BodyPart.RIGHT_ARM, -1);
        assertEquals(InjuryState.NORMAL, health.injury(BodyPart.RIGHT_ARM));
    }
    @Test void correctSupplyAndNoDuplicateTreatment() {
        var health = new CitizenHealth(); health.setInjury(BodyPart.LEFT_LEG, InjuryState.FRACTURE);
        assertFalse(health.treat(BodyPart.LEFT_LEG, false)); assertTrue(health.treat(BodyPart.LEFT_LEG, true));
        assertFalse(health.treat(BodyPart.LEFT_LEG, true)); assertEquals(180, health.recoverySeconds(BodyPart.LEFT_LEG));
        health.setInjury(BodyPart.RIGHT_ARM, InjuryState.MISSING); assertFalse(health.treat(BodyPart.RIGHT_ARM, true));
    }
    @Test void fractureRecoversAllStagesOnlyWithRestAndFood() {
        var health = new CitizenHealth(); health.setInjury(BodyPart.LEFT_LEG, InjuryState.FRACTURE); health.treat(BodyPart.LEFT_LEG, true);
        for(int i=0;i<500;i++) { health.recoverSecond(false,true); health.recoverSecond(true,false); }
        assertEquals(180,health.recoverySeconds(BodyPart.LEFT_LEG));
        for(int i=0;i<180;i++)health.recoverSecond(true,true);
        assertEquals(InjuryState.HEAVY_WOUND,health.injury(BodyPart.LEFT_LEG));
        for(int i=0;i<120;i++)health.recoverSecond(true,true);
        assertEquals(InjuryState.WOUNDED,health.injury(BodyPart.LEFT_LEG));
        for(int i=0;i<60;i++)health.recoverSecond(true,true);
        assertEquals(InjuryState.NORMAL,health.injury(BodyPart.LEFT_LEG));assertFalse(health.needsRecovery());
    }
    @Test void coreBleedingStopsOnBandageAndReturnsOnNewDamage() {
        var health=new CitizenHealth();health.damage(BodyPart.TORSO,6);assertTrue(health.isBleeding());
        health.treat(BodyPart.TORSO,false);assertFalse(health.isBleeding());health.damage(BodyPart.TORSO,1);assertTrue(health.isBleeding());
    }
    @Test void careAndAccumulationPersistAndLegacyStillWorks() {
        var health=new CitizenHealth();health.damage(BodyPart.LEFT_ARM,3);health.damage(BodyPart.HEAD,5);health.treat(BodyPart.HEAD,false);health.recoverSecond(true,true);
        var copy=new CitizenHealth();copy.load(health.save());assertEquals(119,copy.recoverySeconds(BodyPart.HEAD));assertFalse(copy.isBleeding());
        copy.damage(BodyPart.LEFT_ARM,3);assertEquals(InjuryState.HEAVY_WOUND,copy.injury(BodyPart.LEFT_ARM));
        var legacy=health.save();legacy.remove("Recovery");legacy.remove("Trauma");copy.load(legacy);assertTrue(copy.isBleeding());assertEquals(0,copy.recoverySeconds(BodyPart.HEAD));
        copy.load(new CompoundTag());assertEquals(InjuryState.NORMAL,copy.injury(BodyPart.HEAD));
    }
    @Test void malformedRecoveryDoesNotRestoreMissingParts() {
        var health=new CitizenHealth();health.setInjury(BodyPart.LEFT_ARM,InjuryState.MISSING);health.setInjury(BodyPart.RIGHT_ARM,InjuryState.WOUNDED);
        var tag=health.save();tag.getCompound("Recovery").putInt("left_arm",100);tag.getCompound("Recovery").putInt("right_arm",Integer.MAX_VALUE);
        var copy=new CitizenHealth();copy.load(tag);assertEquals(0,copy.recoverySeconds(BodyPart.LEFT_ARM));assertEquals(60,copy.recoverySeconds(BodyPart.RIGHT_ARM));
    }
    @Test void healingAiEatsAndFleesBeforeResting() {
        var data=new CitizenData();data.health().setInjury(BodyPart.HEAD,InjuryState.HEAVY_WOUND);
        assertEquals(CitizenBrainState.SLEEP,CitizenDecisionPolicy.chooseState(data,CitizenBrainState.WORK));assertTrue(CitizenDecisionPolicy.isCriticalPreemption(data));
        data.needs().setHunger(65);assertEquals(CitizenBrainState.EAT,CitizenDecisionPolicy.chooseState(data,CitizenBrainState.WORK));
        data.needs().setDanger(100);assertEquals(CitizenBrainState.FLEE,CitizenDecisionPolicy.chooseState(data,CitizenBrainState.WORK));
    }
}
