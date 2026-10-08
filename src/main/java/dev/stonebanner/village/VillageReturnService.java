package dev.stonebanner.village;

import dev.stonebanner.citizen.*;
import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.control.TacticalInteractionRules;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.navigation.BlockPathfinder;
import dev.stonebanner.settlement.SettlementData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A dismissal commits only on arrival: no teleport, item copies or synthetic legacy villager. */
public final class VillageReturnService {
    private VillageReturnService() {}
    public static CompoundTag origin(net.minecraft.world.entity.npc.Villager villager) {
        var tag=new CompoundTag();villager.saveWithoutId(tag);
        // Transfer current cargo separately; never restore old food or stale routes/passengers.
        for(String key:List.of("Inventory","Pos","Motion","Rotation","Brain","Passengers","Leash"))tag.remove(key);
        return tag;
    }
    public static VillageService.Result checkDismiss(ServerPlayer player,UUID villageId,UUID citizenId) {
        var level=player.serverLevel();var data=VillageData.forLevel(level);var v=data.get(villageId).orElse(null);
        if(v==null||!player.isAlive()||player.isSpectator()
                ||!player.getUUID().equals(v.contracts().get(citizenId))
                ||!(level.getEntity(citizenId) instanceof HumanNpcEntity npc)||!npc.isAlive()
                ||player.distanceToSqr(npc)>64||!TacticalInteractionRules.visible(level,player,npc.getBoundingBox().getCenter(),null))return VillageService.Result.INVALID;
        if(v.returning(citizenId)||npc.citizenData().returningToVillage())return VillageService.Result.ALREADY;
        if(!npc.citizenData().canBeDirectedBy(player.getUUID()))return VillageService.Result.INVALID;
        var origin=v.origin(citizenId).orElse(null);
        if(origin==null||!origin.hasUUID("UUID"))return VillageService.Result.LEGACY;
        if(v.residents().size()>=256)return VillageService.Result.POPULATION;
        if(!npc.citizenData().health().canMoveIndependently()
                ||!level.hasChunkAt(v.center())||BlockPathfinder.findPath(level,npc.blockPosition(),v.center()).isEmpty())return VillageService.Result.ROUTE;
        if(identityExists(level,origin.getUUID("UUID")))return VillageService.Result.INVALID;
        return VillageService.Result.OK;
    }
    public static VillageService.Result dismiss(ServerPlayer player,UUID villageId,UUID citizenId) {
        var check=checkDismiss(player,villageId,citizenId);if(check!=VillageService.Result.OK)return check;
        var level=player.serverLevel();var data=VillageData.forLevel(level);var v=data.get(villageId).orElseThrow();
        var npc=(HumanNpcEntity)level.getEntity(citizenId);
        npc.issueCommand(new ActorCommand.Stop());
        npc.citizenData().home().assign(v.id().toString(),v.center(),2);
        npc.citizenData().setParticipation(CitizenParticipation.LOCAL_HELPER);
        npc.citizenData().setReturningToVillage(true);
        data.beginReturn(v,citizenId);
        npc.commandController().issueSystemMove(v.center(),CitizenBrainState.RETURN_HOME);
        return VillageService.Result.OK;
    }
    private static boolean identityExists(ServerLevel level,UUID id) {
        for(var world:level.getServer().getAllLevels())if(world.getEntity(id)!=null)return true;
        return false;
    }
    public static void tick(HumanNpcEntity npc) {
        if(!(npc.level() instanceof ServerLevel level)||!npc.isAlive()||!npc.citizenData().returningToVillage())return;
        var data=VillageData.forLevel(level);var v=data.contractHome(npc.getUUID()).orElse(null);
        if(v==null||!v.returning(npc.getUUID()))return;
        if(CitizenDecisionPolicy.isCriticalPreemption(npc.citizenData())||npc.foodController().isSeeking()||npc.brainState()==CitizenBrainState.SLEEP)return;
        if(npc.distanceToSqr(Vec3.atBottomCenterOf(v.center()))<=16) {
            if(npc.tickCount%20!=0)return;
            var saved=v.origin(npc.getUUID()).orElse(null);
            if(saved==null||!saved.hasUUID("UUID")||v.residents().size()>=256||identityExists(level,saved.getUUID("UUID")))return;
            var restored=EntityType.VILLAGER.create(level);if(restored==null)return;
            restored.load(saved);restored.moveTo(npc.position());restored.setYRot(npc.getYRot());
            restored.setHealth(Math.min(npc.getHealth(),restored.getMaxHealth()));restored.setPersistenceRequired();
            if(npc.hasCustomName())restored.setCustomName(npc.getCustomName());
            var overflow=new ArrayList<ItemStack>();
            var cargo=npc.citizenData().inventory().haulCargoSnapshot();
            for(var stack:npc.citizenData().inventory().personalSnapshot()){
                var remainder=restored.getInventory().addItem(stack);if(!remainder.isEmpty())overflow.add(remainder);
            }
            if(!level.addFreshEntity(restored))return;
            if(!data.finishReturn(v,npc.getUUID(),restored.getUUID())){restored.discard();return;}
            SettlementData.forLevel(level).removeResident(npc.getUUID());
            npc.citizenData().inventory().clear();npc.workController().interrupt(true);npc.foodController().cancel(true);
            for(var stack:overflow)restored.spawnAtLocation(stack);
            // A village conversion must not turn a former employer's cargo into personal supplies.
            for(var carried:cargo){
                var item=restored.spawnAtLocation(carried.stack());
                dev.stonebanner.citizen.CargoOwnership.markDrop(item,carried.owner());
                if(item!=null)DroppedItemHauling.publishIfNeeded(level,item.blockPosition());
            }
            npc.discard();return;
        }
        if(npc.tickCount%40==0&&!npc.commandController().hasActiveCommand()&&npc.citizenData().health().canMoveIndependently())
            npc.commandController().issueSystemMove(v.center(),CitizenBrainState.RETURN_HOME);
    }
    public static void list(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("village.stonebanner.contracts"));
        for(var v:VillageData.forLevel(player.serverLevel()).villages())for(var entry:v.contracts().entrySet()){
            if(!entry.getValue().equals(player.getUUID()))continue;
            var entity=player.serverLevel().getEntity(entry.getKey());
            var label=entity==null?Component.literal(entry.getKey().toString()):entity.getDisplayName();
            var line=Component.translatable("village.stonebanner.contract_line",label,v.name());
            if(v.returning(entry.getKey()))line.append(Component.translatable("village.stonebanner.returning"));
            else line.append(Component.translatable("village.stonebanner.dismiss").withStyle(s->s.withColor(net.minecraft.ChatFormatting.GOLD)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/sbvillage dismiss "+v.id()+" "+entry.getKey()))));
            player.sendSystemMessage(line);
        }
    }
    public static boolean talk(ServerPlayer player,HumanNpcEntity npc) {
        var v=VillageData.forLevel(player.serverLevel()).contractHome(npc.getUUID()).orElse(null);
        if(v==null||!player.isShiftKeyDown()||!player.isAlive()||player.isSpectator()
                ||!player.getUUID().equals(v.contracts().get(npc.getUUID()))||player.distanceToSqr(npc)>64)return false;
        VillageRecruitmentService.open(player,v.id(),true);return true;
    }
}
