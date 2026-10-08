package dev.stonebanner.storage;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID)
public final class StorageManagementEvents {
    private StorageManagementEvents() {}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)
                || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || !StorageManagementService.supported(player.serverLevel(),event.getPos()))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        StorageManagementService.execute(player,event.getPos(),StorageManagementService.Action.OPEN);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void broken(BlockEvent.BreakEvent event) {
        if(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            StorageData.forLevel(level).unregister(event.getPos());
    }
}
