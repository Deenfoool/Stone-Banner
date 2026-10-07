package dev.stonebanner.village;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class VillageBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,StoneAndBanner.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,StoneAndBanner.MOD_ID);
    public static final RegistryObject<Block> BOARD=BLOCKS.register("notice_board",()->new Block(Block.Properties.copy(Blocks.OAK_PLANKS)) {
        @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
            if(player instanceof ServerPlayer server&&hand==InteractionHand.MAIN_HAND)VillageService.openBoard(server,pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    });
    public static final RegistryObject<Item> BOARD_ITEM=ITEMS.register("notice_board",()->new BlockItem(BOARD.get(),new Item.Properties()));
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS)e.accept(BOARD_ITEM);}
}
