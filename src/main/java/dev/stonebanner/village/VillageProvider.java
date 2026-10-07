package dev.stonebanner.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import java.util.List;

/** Generator-independent discovery contract; only the vanilla bell provider ships for now. */
public interface VillageProvider {
    List<Villager> residents(ServerLevel level, BlockPos center);
}
