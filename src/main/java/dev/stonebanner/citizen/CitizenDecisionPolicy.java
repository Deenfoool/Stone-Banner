package dev.stonebanner.citizen;

/**
 * Deterministic high-level decision ordering for needs versus commanded activity.
 * Jobs will later plug into the idle branch instead of changing this priority contract.
 */
public final class CitizenDecisionPolicy {
    private CitizenDecisionPolicy() {
    }

    public static CitizenBrainState chooseState(CitizenData data, CitizenBrainState commandedState) {
        CitizenNeeds needs = data.needs();

        if (needs.isInCriticalDanger()) {
            return data.profession() == CitizenProfession.GUARD
                    ? CitizenBrainState.DEFEND
                    : CitizenBrainState.FLEE;
        }
        if (needs.isCriticallyHungry()) {
            return CitizenBrainState.EAT;
        }
        if (needs.isCriticallyTired()) {
            return CitizenBrainState.SLEEP;
        }

        CitizenBrainState current = commandedState == null ? CitizenBrainState.IDLE : commandedState;
        if (current != CitizenBrainState.IDLE) {
            return current;
        }
        if (needs.isHungry()) {
            return CitizenBrainState.EAT;
        }
        if (needs.isTired()) {
            return CitizenBrainState.SLEEP;
        }
        return CitizenBrainState.IDLE;
    }

    public static boolean isCriticalPreemption(CitizenData data) {
        CitizenNeeds needs = data.needs();
        return needs.isInCriticalDanger()
                || needs.isCriticallyHungry()
                || needs.isCriticallyTired();
    }
}
