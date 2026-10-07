package dev.stonebanner.village;

import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.*;
import dev.stonebanner.network.packet.VillageRecruitmentPacket.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import java.util.*;

public final class VillageRecruitmentService {
    public static final UUID NONE=new UUID(0,0);
    private static final Map<ServerPlayer,Long> LAST_OPEN=new WeakHashMap<>();
    private VillageRecruitmentService() {}
    private static String name(Entity e,UUID id){String s=e==null?id.toString():e.getDisplayName().getString();return s.substring(0,Math.min(s.length(),256));}
    public static VillageRecruitmentPacket snapshot(ServerPlayer p,UUID selected,boolean contracts,int page,VillageService.Result result,boolean opening){
        var level=p.serverLevel();var data=VillageData.forLevel(level);
        var all=data.villages().stream().sorted(Comparator.comparingDouble(v->v.center().distSqr(p.blockPosition()))).toList();
        var choices=all.stream().map(v->new Village(v.id(),v.name())).toList();
        var v=data.get(selected).orElse(all.isEmpty()?null:all.get(0));var rows=new ArrayList<Row>();int total=0,trust=0;
        if(v!=null){
            trust=data.reputation(v,p.getUUID());
            var ids=(contracts?v.contracts().entrySet().stream().filter(e->e.getValue().equals(p.getUUID())).map(Map.Entry::getKey):v.residents().stream()).sorted().toList();
            total=ids.size();page=Math.max(0,Math.min(page,Math.max(0,(total-1)/VillageRecruitmentPacket.PAGE_SIZE)));
            for(var id:ids.stream().skip((long)page*VillageRecruitmentPacket.PAGE_SIZE).limit(VillageRecruitmentPacket.PAGE_SIZE).toList()){
                var entity=level.getEntity(id);String profession="none",status="unavailable";int rank=0,companion=0,settler=0;
                var c=VillageService.Result.INVALID;var s=c;var dismiss=c;
                if(!contracts&&entity instanceof Villager old&&old.isAlive()){
                    profession=net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(old.getVillagerData().getProfession()).getPath();rank=old.getVillagerData().getLevel();status="resident";
                    companion=VillageService.hirePrice(old,trust,false);settler=VillageService.hirePrice(old,trust,true);
                    c=VillageService.checkHire(p,v.id(),id,false);s=VillageService.checkHire(p,v.id(),id,true);
                }else if(contracts){
                    if(v.returning(id))status="returning";
                    else if(entity instanceof HumanNpcEntity npc&&npc.isAlive())status=npc.citizenData().participation().name().toLowerCase(Locale.ROOT);
                    if(entity instanceof HumanNpcEntity npc){profession=npc.citizenData().profession().name().toLowerCase(Locale.ROOT);dismiss=VillageReturnService.checkDismiss(p,v.id(),id);}
                }
                rows.add(new Row(id,name(entity,id),profession,rank,entity==null?null:entity.blockPosition(),status,companion,settler,c,s,dismiss));
            }
        }else page=0;
        return new VillageRecruitmentPacket(level.dimension().location(),choices,v==null?NONE:v.id(),contracts,page,total,trust,VillageService.count(p,stack->stack.is(Items.EMERALD)),rows,result,opening);
    }
    public static VillageService.Result act(ServerPlayer p,VillageRecruitmentActionPacket request){
        if(!p.isAlive()||p.isSpectator())return VillageService.Result.INVALID;
        return switch(request.action()){
            case OPEN->VillageService.Result.OK;
            case DISMISS->VillageReturnService.dismiss(p,request.village(),request.npc());
            case COMPANION,SETTLER->request.expectedPrice()<0?VillageService.Result.INVALID:VillageService.hire(p,request.village(),request.npc(),request.action()==VillageRecruitmentActionPacket.Action.SETTLER,request.expectedPrice());
        };
    }
    public static void open(ServerPlayer p,UUID village,boolean contracts){if(p.isAlive()&&!p.isSpectator())StoneBannerNetwork.sendVillageRecruitment(p,snapshot(p,village,contracts,0,null,true));}
    public static void handle(ServerPlayer p,VillageRecruitmentActionPacket request){
        if(!p.isAlive()||p.isSpectator())return;
        if(request.action()==VillageRecruitmentActionPacket.Action.OPEN){long now=p.serverLevel().getGameTime();Long previous=LAST_OPEN.get(p);if(previous!=null&&now>=previous&&now-previous<5)return;LAST_OPEN.put(p,now);}
        var result=act(p,request);boolean hired=result==VillageService.Result.OK&&(request.action()==VillageRecruitmentActionPacket.Action.COMPANION||request.action()==VillageRecruitmentActionPacket.Action.SETTLER);
        StoneBannerNetwork.sendVillageRecruitment(p,snapshot(p,request.village(),hired||request.contracts(),hired?0:request.page(),request.action()==VillageRecruitmentActionPacket.Action.OPEN?null:result,false));
    }
}
