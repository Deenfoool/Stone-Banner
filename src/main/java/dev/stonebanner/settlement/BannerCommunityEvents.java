package dev.stonebanner.settlement;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StoneAndBanner.MOD_ID)
public final class BannerCommunityEvents {
    private BannerCommunityEvents() {}
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isShiftKeyDown()
                || !event.getEntity().getMainHandItem().isEmpty()) return;
        if (event.getEntity() instanceof ServerPlayer player
                && BannerCommunityService.isStandingBanner(player.serverLevel(), event.getPos())) {
            event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
            BannerCommunityService.execute(player, event.getPos(), BannerCommunityService.Action.OPEN, "", -1);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void broken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) SettlementData.forLevel(level).bannerRemoved(event.getPos());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) SettlementData.forLevel(level).bannerRemoved(event.getPos());
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        var data = SettlementData.forLevel(level);
        for (var c : data.communities()) {
            if (c.bannerActive() && level.hasChunkAt(c.banner()) && !BannerCommunityService.isStandingBanner(level, c.banner()))
                data.bannerRemoved(c.banner());
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof HumanNpcEntity npc && npc.level() instanceof ServerLevel level)
            SettlementData.forLevel(level).removeResident(npc.getUUID());
    }
}
