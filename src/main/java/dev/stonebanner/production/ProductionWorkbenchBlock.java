package dev.stonebanner.production;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A real manual crafting table as well as an NPC workstation. Vanilla checks the exact table block. */
public final class ProductionWorkbenchBlock extends CraftingTableBlock {
    public ProductionWorkbenchBlock(Properties properties){super(properties);}
    @Override public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos){
        return new SimpleMenuProvider((id,inventory,player)->new CraftingMenu(id,inventory,ContainerLevelAccess.create(level,pos)){
            @Override public boolean stillValid(Player player){return level.getBlockState(pos).is(ProductionWorkbenchBlock.this)&&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
        },Component.translatable(getDescriptionId()));
    }
}
