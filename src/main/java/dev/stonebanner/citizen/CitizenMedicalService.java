package dev.stonebanner.citizen;

import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative, physical first aid. No virtual medicine or remote healing. */
public final class CitizenMedicalService {
    private CitizenMedicalService() {}

    public static boolean allowed(ServerPlayer player, HumanNpcEntity npc) {
        return player.isAlive() && !player.isSpectator() && npc.isAlive()
                && player.level() == npc.level() && player.distanceToSqr(npc) <= 36
                && player.hasLineOfSight(npc) && npc.citizenData().canBeViewedBy(player.getUUID());
    }

    public static BodyPart target(CitizenHealth health, boolean splint) {
        BodyPart best = null;
        for (BodyPart part : BodyPart.values()) {
            if (health.canTreat(part, splint) && (best == null || health.injury(part).ordinal() > health.injury(best).ordinal())) best = part;
        }
        return best;
    }

    public static boolean apply(ServerPlayer player, HumanNpcEntity npc, boolean splint, ItemStack supply) {
        if (!allowed(player, npc) || supply.isEmpty() || !supply.is(splint ? MedicalItems.SPLINT.get() : MedicalItems.BANDAGE.get())) return false;
        BodyPart part = target(npc.citizenData().health(), splint);
        if (part == null || !npc.citizenData().health().treat(part, splint)) return false;
        // Even creative mode uses one real supply: the medical action is not a free debug heal.
        supply.shrink(1);
        npc.sleepController().cancel(true);
        npc.workController().interrupt(true);
        npc.commandController().stop();
        dev.stonebanner.geology.GeologyService.cancelSurvey(player.serverLevel(), npc.getUUID());
        npc.setBrainState(CitizenDecisionPolicy.chooseState(npc.citizenData(), CitizenBrainState.IDLE));
        player.getInventory().setChanged();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("medical.stonebanner.applied",
                net.minecraft.network.chat.Component.translatable("body_part.stonebanner." + part.serializedName())), true);
        return true;
    }

    public static boolean fromInventory(ServerPlayer player, HumanNpcEntity npc, boolean splint) {
        if (!allowed(player, npc)) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(splint ? MedicalItems.SPLINT.get() : MedicalItems.BANDAGE.get())) return apply(player, npc, splint, stack);
        }
        return false;
    }
}
