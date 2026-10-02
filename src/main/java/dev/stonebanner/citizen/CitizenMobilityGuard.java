package dev.stonebanner.citizen;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Enforces body-part mobility limits without adding another vanilla AI goal. */
@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class CitizenMobilityGuard {
    private CitizenMobilityGuard() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof HumanNpcEntity npc)
                || npc.level().isClientSide
                || npc.citizenData().health().canMoveIndependently()) {
            return;
        }

        if (npc.workController().hasActiveJob()) {
            npc.workController().interrupt(true);
        } else if (npc.commandController().hasActiveCommand()) {
            npc.commandController().stop();
        }

        npc.getNavigation().stop();
        if (npc.brainState() == CitizenBrainState.MOVE
                || npc.brainState() == CitizenBrainState.FOLLOW
                || npc.brainState() == CitizenBrainState.FLEE
                || npc.brainState() == CitizenBrainState.RETURN_HOME
                || npc.brainState() == CitizenBrainState.WORK) {
            npc.setBrainState(CitizenBrainState.IDLE);
        }
    }
}
