package dev.stonebanner.citizen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CitizenDecisionPolicyTest {
    @Test
    void criticalDangerMakesCivilianFlee() {
        CitizenData data = new CitizenData();
        data.needs().setDanger(100.0D);

        assertEquals(
                CitizenBrainState.FLEE,
                CitizenDecisionPolicy.chooseState(data, CitizenBrainState.MOVE)
        );
    }

    @Test
    void criticalDangerMakesGuardDefend() {
        CitizenData data = new CitizenData();
        data.setProfession(CitizenProfession.GUARD, true);
        data.needs().setDanger(100.0D);

        assertEquals(
                CitizenBrainState.DEFEND,
                CitizenDecisionPolicy.chooseState(data, CitizenBrainState.FOLLOW)
        );
    }

    @Test
    void criticalHungerPreemptsMovement() {
        CitizenData data = new CitizenData();
        data.needs().setHunger(CitizenNeeds.CRITICAL_HUNGER_THRESHOLD);

        assertEquals(
                CitizenBrainState.EAT,
                CitizenDecisionPolicy.chooseState(data, CitizenBrainState.MOVE)
        );
    }

    @Test
    void nonCriticalNeedDoesNotInterruptExplicitCommand() {
        CitizenData data = new CitizenData();
        data.needs().setHunger(CitizenNeeds.HUNGRY_THRESHOLD);

        assertEquals(
                CitizenBrainState.FOLLOW,
                CitizenDecisionPolicy.chooseState(data, CitizenBrainState.FOLLOW)
        );
    }
}
