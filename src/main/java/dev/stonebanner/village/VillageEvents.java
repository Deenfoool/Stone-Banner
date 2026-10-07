package dev.stonebanner.village;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID)
public final class VillageEvents {
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.level instanceof ServerLevel level)||level.getGameTime()%200!=0)return;
        for(var p:level.players().stream().limit(16).toList())
            level.getPoiManager().findAll(type->type.is(PoiTypes.MEETING),pos->true,p.blockPosition(),48,PoiManager.Occupancy.ANY)
                    .limit(2).forEach(pos->VillageService.discover(level,pos));
    }
    @SubscribeEvent public static void bell(PlayerInteractEvent.RightClickBlock e){
        if(e.getHand()!=InteractionHand.MAIN_HAND||!(e.getEntity() instanceof ServerPlayer p)
                ||!p.serverLevel().getBlockState(e.getPos()).is(Blocks.BELL)||p.isSpectator())return;
        var v=VillageService.discover(p.serverLevel(),e.getPos());
        if(v!=null&&p.isShiftKeyDown()&&v.board()!=null){VillageService.openBoard(p,v.board());e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);}
    }
    @SubscribeEvent public static void talk(PlayerInteractEvent.EntityInteract e){
        if (dev.stonebanner.citizen.MedicalItems.isMedicine(e.getItemStack())) return;
        if(e.getHand()==InteractionHand.MAIN_HAND&&e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof dev.stonebanner.entity.HumanNpcEntity npc
                &&VillageReturnService.talk(p,npc)){e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);return;}
        if(e.getHand()==InteractionHand.MAIN_HAND&&e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof Villager villager
                &&VillageService.talk(p,villager)){e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e){
        if(!(e.getEntity().level() instanceof ServerLevel level))return;
        var data=VillageData.forLevel(level);
        if(e.getEntity() instanceof dev.stonebanner.entity.HumanNpcEntity)data.closeContract(e.getEntity().getUUID());
        if(e.getEntity() instanceof Villager){
            var v=data.resident(e.getEntity().getUUID()).orElse(null);
            if(v!=null&&e.getSource().getEntity() instanceof ServerPlayer p)data.reputation(v,p.getUUID(),-20);
            data.removeResident(e.getEntity().getUUID());
        } else if(e.getEntity() instanceof Monster&&e.getSource().getEntity() instanceof ServerPlayer p)
            data.at(e.getEntity().blockPosition()).ifPresent(v->{data.kill(v,p.getUUID());data.reputation(v,p.getUUID(),1);});
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingHurtEvent e){
        if(e.getAmount()>0&&e.getEntity() instanceof Villager&&e.getEntity().level() instanceof ServerLevel level
                &&e.getSource().getEntity() instanceof ServerPlayer p)
            VillageData.forLevel(level).resident(e.getEntity().getUUID()).ifPresent(v->VillageData.forLevel(level).reputation(v,p.getUUID(),-5));
    }
}
