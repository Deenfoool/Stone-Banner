package dev.stonebanner.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.List;

public final class BellVillageProvider implements VillageProvider {
    @Override public List<Villager> residents(ServerLevel level, BlockPos center) {
        if(!level.hasChunkAt(center)||!level.getBlockState(center).is(Blocks.BELL))return List.of();
        return level.getEntitiesOfClass(Villager.class,new AABB(center).inflate(48),v->v.isAlive()&&!v.isBaby());
    }
}
