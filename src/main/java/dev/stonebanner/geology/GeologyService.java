package dev.stonebanner.geology;

import dev.stonebanner.citizen.*;
import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.GeologySnapshotPacket;
import dev.stonebanner.settlement.SettlementData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import dev.stonebanner.StoneAndBanner;
import java.util.*;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID)
public final class GeologyService {
    public enum Action { OPEN, START, ASSIGN, SURVEY }
    private record Survey(UUID owner,UUID community,UUID npc,BlockPos target,int tier,long deadline,int progress) {
        Survey progressed(){return new Survey(owner,community,npc,target,tier,deadline,progress+1);}
    }
    private static final Map<ServerLevel,Map<UUID,Survey>> SURVEYS=new WeakHashMap<>();
    private static final Map<UUID,Long> REQUESTS=new HashMap<>();
    private GeologyService(){}
    private static final Map<UUID,EnumMap<Action,Long>> COMMANDS = new HashMap<>();
    public static void research(ServerPlayer player,BlockPos pos,Action action,int npcId) {
        var level=player.serverLevel();
        long now = level.getGameTime();
        Long previous = COMMANDS.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(Action.class)).put(action, now);
        if (previous != null && now >= previous && now - previous < 5) return;
        if(player.isSpectator()||!level.hasChunkAt(pos)||player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>(action==Action.SURVEY?96*96:16*16))return;
        var c=SettlementData.forLevel(level).ownedBy(player.getUUID()).orElse(null);
        if(c==null){feedback(player,"camp_required");return;}
        if(action==Action.SURVEY){startSurvey(player,c,pos,npcId);return;}
        if(!level.getBlockState(pos).is(ResearchBlocks.TABLE.get())||!c.contains(pos)){feedback(player,"table_required");return;}
        var data=GeologyData.forLevel(level);var k=data.knowledge(c.id());
        if(action==Action.START) {
            if(k.tier()>=4){feedback(player,"complete");return;}
            if(!c.bannerActive()){feedback(player,"banner_required");return;}
            int next=k.tier()+1;
            if(k.remaining()==0) {
                if(!has(player,Items.PAPER,8*next)||!has(player,Items.BOOK,1)
                        ||(next==3&&!has(player,Items.GOLD_INGOT,2))||(next==4&&!has(player,Items.DIAMOND,1))){feedback(player,"materials");return;}
                consume(player,Items.PAPER,8*next);consume(player,Items.BOOK,1);
                if(next==3)consume(player,Items.GOLD_INGOT,2);if(next==4)consume(player,Items.DIAMOND,1);
            }
            data.start(c.id(),pos);feedback(player,"research_started");
        } else if(action==Action.ASSIGN) {
            var entity=level.getEntity(npcId);
            if(!(entity instanceof HumanNpcEntity npc)||!npc.isAlive()||!c.residents().contains(npc.getUUID())||npc.distanceToSqr(player)>16*16){feedback(player,"resident_required");return;}
            cancelSurvey(level,npc.getUUID());npc.issueCommand(new ActorCommand.Stop());
            npc.citizenData().setProfession(CitizenProfession.GEOLOGIST,true);feedback(player,"assigned");
        }
        StoneBannerNetwork.sendGeology(player,GeologySnapshotPacket.research(level.dimension().location(),pos,k.tier(),k.remaining()));
    }
    private static boolean has(ServerPlayer p,Item item,int required){int n=0;for(var stack:p.getInventory().items)if(stack.is(item))n+=stack.getCount();return n>=required;}
    private static void consume(ServerPlayer p,Item item,int count){for(var stack:p.getInventory().items){if(!stack.is(item))continue;int used=Math.min(count,stack.getCount());stack.shrink(used);count-=used;if(count==0)break;}p.getInventory().setChanged();}
    private static void startSurvey(ServerPlayer player,SettlementData.Community c,BlockPos pos,int id) {
        var level=player.serverLevel();var k=GeologyData.forLevel(level).knowledge(c.id());var entity=level.getEntity(id);
        if(k.tier()<1){feedback(player,"research_required");return;}
        if(!(entity instanceof HumanNpcEntity npc)||!npc.isAlive()||!c.residents().contains(npc.getUUID())
                ||npc.citizenData().profession()!=CitizenProfession.GEOLOGIST||npc.distanceToSqr(player)>64*64
                ||!npc.citizenData().canTravelTo(pos)){feedback(player,"geologist_required");return;}
        if(CitizenDecisionPolicy.isCriticalPreemption(npc.citizenData())||npc.citizenData().inventory().hasHaulCargo()){feedback(player,"busy");return;}
        if(!npc.issueCommand(new ActorCommand.MoveTo(pos))){feedback(player,"unreachable");return;}
        SURVEYS.computeIfAbsent(level,l->new HashMap<>()).put(npc.getUUID(),new Survey(player.getUUID(),c.id(),npc.getUUID(),pos.immutable(),k.tier(),level.getGameTime()+2400,0));
        feedback(player,"survey_started");
    }
    public static boolean surveying(HumanNpcEntity npc){var tasks=SURVEYS.get(npc.level());return tasks!=null&&tasks.containsKey(npc.getUUID());}
    public static void cancelSurvey(ServerLevel level,UUID npc){var tasks=SURVEYS.get(level);if(tasks!=null)tasks.remove(npc);}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        ChunkResourceCache.forLevel(level).tick(level);
        if(level.getGameTime()%20!=0)return;
        var data=GeologyData.forLevel(level);
        for(var c:SettlementData.forLevel(level).communities()) {
            var k=data.knowledge(c.id());if(k.remaining()==0)continue;
            var owner=level.getServer().getPlayerList().getPlayer(c.owner());
            if(owner!=null&&owner.serverLevel()==level&&c.bannerActive()&&level.hasChunkAt(k.table())
                    &&level.getBlockState(k.table()).is(ResearchBlocks.TABLE.get())&&c.contains(k.table())
                    &&owner.distanceToSqr(k.table().getX()+.5,k.table().getY()+.5,k.table().getZ()+.5)<=8*8) {
                if(data.advance(c.id())) feedback(owner,"research_completed");
            }
        }
        var tasks=SURVEYS.get(level);if(tasks==null)return;
        for(var task:new ArrayList<>(tasks.values())) {
            var entity=level.getEntity(task.npc);var owner=level.getServer().getPlayerList().getPlayer(task.owner);
            var camp = SettlementData.forLevel(level).ownedBy(task.owner).orElse(null);
            if(!(entity instanceof HumanNpcEntity npc)||!npc.isAlive()||camp==null||!camp.id().equals(task.community)
                    ||!camp.residents().contains(task.npc)||!npc.citizenData().canTravelTo(task.target)||owner==null||owner.serverLevel()!=level||level.getGameTime()>task.deadline
                    ||npc.citizenData().profession()!=CitizenProfession.GEOLOGIST||CitizenDecisionPolicy.isCriticalPreemption(npc.citizenData())) {
                tasks.remove(task.npc);
                if (entity instanceof HumanNpcEntity stopped && stopped.isAlive()
                        && !CitizenDecisionPolicy.isCriticalPreemption(stopped.citizenData())) stopped.commandController().stop();
                if(owner!=null)feedback(owner,"survey_interrupted");continue;
            }
            // Commands issued after the survey (move/stop) cancel it through HumanNpcEntity.issueCommand.
            if(npc.distanceToSqr(task.target.getX()+.5,task.target.getY(),task.target.getZ()+.5)>3*3)continue;
            npc.setBrainState(CitizenBrainState.WORK);
            var progress=task.progressed();tasks.put(task.npc,progress);
            var profile=ChunkResourceCache.forLevel(level).request(level,task.target.getX()>>4,task.target.getZ()>>4);
            int seconds=Math.max(8,24-npc.citizenData().skill(CitizenSkill.GEOLOGY));
            if(progress.progress>=seconds&&profile!=null) {
                data.surveyed(task.community,task.target.getX()>>4,task.target.getZ()>>4,task.tier);
                npc.citizenData().practice(CitizenSkill.GEOLOGY,25);
                tasks.remove(task.npc);npc.setBrainState(CitizenBrainState.IDLE);feedback(owner,"survey_completed");
            }
        }
    }
    public static void layers(ServerPlayer player,int x,int z) {
        long now=player.serverLevel().getGameTime();Long last=REQUESTS.get(player.getUUID());if(last!=null&&now>=last&&now-last<10)return;REQUESTS.put(player.getUUID(),now);
        if(Math.abs((long)x-(player.blockPosition().getX()>>4))>4||Math.abs((long)z-(player.blockPosition().getZ()>>4))>4)return;
        var level=player.serverLevel();var camp=SettlementData.forLevel(level).ownedBy(player.getUUID()).orElse(null);
        var knowledge=camp==null?null:GeologyData.forLevel(level).knowledge(camp.id());int research=knowledge==null?0:knowledge.tier();
        var tiles=new ArrayList<GeologySnapshotPacket.Tile>();
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            if(level.getChunkSource().getChunkNow(x+dx,z+dz)==null)continue;
            var profile=ChunkResourceCache.forLevel(level).request(level,x+dx,z+dz);int surveyed=knowledge==null?0:knowledge.surveyed(x+dx,z+dz);
            int tier=Math.min(research,surveyed);
            tiles.add(new GeologySnapshotPacket.Tile(x+dx,z+dz,profile!=null,profile!=null&&profile.hasOre(),tier,
                    profile!=null&&tier>=1?GeologyRules.totalRichness(profile.counts()):0,
                    profile==null?List.of():GeologyRules.visible(research,surveyed,profile.counts())));
        }
        var claims=SettlementData.forLevel(level).communities().stream().filter(c->Math.abs((long)c.centerX()-x)<=5&&Math.abs((long)c.centerZ()-z)<=5)
                .map(c->new GeologySnapshotPacket.Claim(c.name(),c.centerX(),c.centerZ(),c.banner(),c.owner().equals(player.getUUID()),c.bannerActive())).toList();
        StoneBannerNetwork.sendGeology(player,new GeologySnapshotPacket(level.dimension().location(),false,BlockPos.ZERO,research,0,tiles,claims));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void broken(BlockEvent.BreakEvent event){if(event.getLevel() instanceof ServerLevel level)ChunkResourceCache.invalidate(level,event.getPos());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent event){if(event.getLevel() instanceof ServerLevel level)ChunkResourceCache.invalidate(level,event.getPos());}
    @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event){if(event.getLevel() instanceof ServerLevel level)ChunkResourceCache.invalidate(level,event.getChunk().getPos().getWorldPosition());}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){SURVEYS.remove(level);ChunkResourceCache.clear(level);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){REQUESTS.remove(event.getEntity().getUUID());COMMANDS.remove(event.getEntity().getUUID());}
    private static void feedback(ServerPlayer player,String key){player.displayClientMessage(Component.translatable("geology.stonebanner.result."+key),true);}
}
