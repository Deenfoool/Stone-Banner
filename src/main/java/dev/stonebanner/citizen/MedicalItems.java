package dev.stonebanner.citizen;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class MedicalItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StoneAndBanner.MOD_ID);
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage", () -> medicine(false));
    public static final RegistryObject<Item> SPLINT = ITEMS.register("splint", () -> medicine(true));
    private static Item medicine(boolean splint) {
        return new Item(new Item.Properties()) {
            @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
                if (!(entity instanceof HumanNpcEntity npc)) return InteractionResult.PASS;
                if (player instanceof ServerPlayer server && !CitizenMedicalService.apply(server, npc, splint, stack))
                    server.displayClientMessage(net.minecraft.network.chat.Component.translatable("medical.stonebanner.rejected"), true);
                return InteractionResult.sidedSuccess(player.level().isClientSide);
            }
        };
    }
    public static boolean isMedicine(ItemStack stack) { return stack.is(BANDAGE.get()) || stack.is(SPLINT.get()); }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) { e.accept(BANDAGE); e.accept(SPLINT); }
    }
}
