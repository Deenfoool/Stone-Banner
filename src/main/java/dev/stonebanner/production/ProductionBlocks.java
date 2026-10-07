package dev.stonebanner.production;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ProductionBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,StoneAndBanner.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,StoneAndBanner.MOD_ID);
    public static final RegistryObject<Block> CARPENTER=BLOCKS.register("carpenters_workbench",()->new ProductionWorkbenchBlock(Block.Properties.copy(Blocks.CRAFTING_TABLE)));
    public static final RegistryObject<Block> FORGE=BLOCKS.register("forge_workbench",()->new ProductionWorkbenchBlock(Block.Properties.copy(Blocks.CRAFTING_TABLE)));
    public static final RegistryObject<Item> CARPENTER_ITEM=ITEMS.register("carpenters_workbench",()->new BlockItem(CARPENTER.get(),new Item.Properties()));
    public static final RegistryObject<Item> FORGE_ITEM=ITEMS.register("forge_workbench",()->new BlockItem(FORGE.get(),new Item.Properties()));
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS){e.accept(CARPENTER_ITEM);e.accept(FORGE_ITEM);}}
}
