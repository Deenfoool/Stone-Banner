package dev.stonebanner.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class ResearchTableBlock extends Block {
    public ResearchTableBlock(Properties properties){super(properties);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(player.isShiftKeyDown())return InteractionResult.PASS;
        if(player instanceof ServerPlayer serverPlayer&&hand==InteractionHand.MAIN_HAND)
            GeologyService.research(serverPlayer,pos,GeologyService.Action.OPEN,-1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
