package dev.stonebanner.client.control;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/** Client-side selection state for directly commanding Human NPCs in tactical control. */
public final class CitizenSelectionController {
    private static Integer selectedNpcId;

    private CitizenSelectionController() {
    }

    public static boolean select(Entity entity) {
        if (!(entity instanceof HumanNpcEntity npc) || !npc.isAlive()) {
            return false;
        }
        selectedNpcId = npc.getId();
        return true;
    }

    public static Optional<HumanNpcEntity> selected() {
        Minecraft minecraft = Minecraft.getInstance();
        if (selectedNpcId == null || minecraft.level == null) {
            return Optional.empty();
        }

        Entity entity = minecraft.level.getEntity(selectedNpcId);
        if (!(entity instanceof HumanNpcEntity npc) || !npc.isAlive()) {
            selectedNpcId = null;
            return Optional.empty();
        }
        return Optional.of(npc);
    }

    public static boolean hasSelection() {
        return selected().isPresent();
    }

    public static boolean moveSelected(BlockHitResult hit) {
        HumanNpcEntity npc = selected().orElse(null);
        if (npc == null) {
            return false;
        }

        Direction face = hit.getDirection();
        BlockPos target = face == Direction.UP
                ? hit.getBlockPos().above()
                : hit.getBlockPos().relative(face);
        StoneBannerNetwork.sendMoveCitizen(npc.getId(), target);
        return true;
    }

    public static boolean stopAndClear() {
        HumanNpcEntity npc = selected().orElse(null);
        if (npc == null) {
            selectedNpcId = null;
            return false;
        }

        StoneBannerNetwork.sendStopCitizen(npc.getId());
        selectedNpcId = null;
        return true;
    }

    public static void clear() {
        selectedNpcId = null;
    }
}
