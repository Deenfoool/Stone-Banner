package dev.stonebanner.geology;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ResearchBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,StoneAndBanner.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,StoneAndBanner.MOD_ID);
    public static final RegistryObject<Block> TABLE=BLOCKS.register("research_table",()->new ResearchTableBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)));
    public static final RegistryObject<Item> TABLE_ITEM=ITEMS.register("research_table",()->new BlockItem(TABLE.get(),new Item.Properties()));
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS)event.accept(TABLE_ITEM);}
}
