package dev.stonebanner.village;

import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.*;
import dev.stonebanner.network.packet.VillageJournalPacket.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** No progress is fabricated: material counts are the player's current inventory. */
public final class VillageJournalService {
    private static final Map<ServerPlayer,Long> LAST_REQUEST=new WeakHashMap<>();
    private VillageJournalService() {}
    private static String label(net.minecraft.world.entity.Entity e){String name=e.getDisplayName().getString();return name.substring(0,Math.min(256,name.length()));}
    public static VillageJournalPacket snapshot(ServerPlayer p,String result) {
        var level=p.serverLevel();var data=VillageData.forLevel(level);var entries=new ArrayList<Entry>();
        var villages=data.villages().stream().sorted(Comparator.comparingDouble(v->v.center().distSqr(p.blockPosition()))).toList();
        for(var v:villages){
            var elder=v.elder()==null?null:level.getEntity(v.elder());var quests=new ArrayList<Quest>();
            for(var type:VillageData.QuestType.values()){
                var progress=data.progress(v,p.getUUID(),type).orElse(null);
                var state=progress==null?State.AVAILABLE:progress.complete()?State.COMPLETED:State.ACTIVE;
                int count=type==VillageData.QuestType.DEFENCE?(progress==null?0:progress.count()):Math.min(type.amount,VillageService.count(p,VillageService.ingredient(type)));
                if(state==State.COMPLETED)count=type.amount;
                var recipient=VillageService.questRecipient(level,v,type);
                if(recipient!=null&&!recipient.isAlive())recipient=null;
                quests.add(new Quest(type,state,count,recipient==null?null:recipient.getUUID(),recipient==null?"":label(recipient),
                    recipient==null?null:recipient.blockPosition(),state==State.AVAILABLE&&VillageService.canAccept(p,v,type),
                    state==State.ACTIVE&&count>=type.amount&&VillageService.near(p,recipient)));
            }
            entries.add(new Entry(v.id(),v.name(),v.center(),v.residents().size(),data.reputation(v,p.getUUID()),elder==null?"":label(elder),quests));
        }
        return new VillageJournalPacket(level.dimension().location(),entries,result);
    }
    public static void open(ServerPlayer p){if(p.isAlive()&&!p.isSpectator()){
        var state=snapshot(p,"");StoneBannerNetwork.sendVillageJournal(p,new VillageJournalPacket(state.dimension(),state.villages(),state.result(),true));
    }}
    public static VillageService.Result act(ServerPlayer p,VillageJournalActionPacket request){
        if(!p.isAlive()||p.isSpectator())return VillageService.Result.INVALID;
        if(request.action()==VillageJournalActionPacket.Action.OPEN)return VillageService.Result.OK;
        if(request.action()==VillageJournalActionPacket.Action.ACCEPT)return VillageService.accept(p,request.village(),request.quest());
        var village=VillageData.forLevel(p.serverLevel()).get(request.village()).orElse(null);
        if(village==null)return VillageService.Result.INVALID;
        var recipient=VillageService.questRecipient(p.serverLevel(),village,request.quest());
        return recipient==null?VillageService.Result.AUTHOR:VillageService.submit(p,village.id(),request.quest(),recipient.getUUID());
    }
    public static void handle(ServerPlayer p,VillageJournalActionPacket request){
        if(!p.isAlive()||p.isSpectator())return;
        long now=p.serverLevel().getGameTime();Long previous=LAST_REQUEST.get(p);
        if(previous!=null&&now>=previous&&now-previous<5)return;
        LAST_REQUEST.put(p,now);
        var result=act(p,request);
        StoneBannerNetwork.sendVillageJournal(p,snapshot(p,request.action()==VillageJournalActionPacket.Action.OPEN?"":result.name().toLowerCase(Locale.ROOT)));
    }
}
