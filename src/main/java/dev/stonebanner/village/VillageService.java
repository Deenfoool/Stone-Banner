package dev.stonebanner.village;

import dev.stonebanner.citizen.*;
import dev.stonebanner.command.ActorCommand;
import dev.stonebanner.control.TacticalInteractionRules;
import dev.stonebanner.entity.ModEntities;
import dev.stonebanner.settlement.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Predicate;

/** Server authority for all village actions. Chat links never bypass world/identity checks. */
public final class VillageService {
    private static final VillageProvider PROVIDER = new BellVillageProvider();
    private VillageService() {}
    public enum Result { OK, INVALID, ALREADY, ITEMS, TRUST, POPULATION, ELDER, SPECIALIST, PROVISIONS, AUTHOR, PROGRESS, ROUTE, LEGACY, PRICE_CHANGED }
    public static VillageData.Village discover(ServerLevel level, BlockPos bell) {
        var villagers=PROVIDER.residents(level,bell);
        if(villagers.size()<3)return VillageData.forLevel(level).at(bell).orElse(null);
        var data=VillageData.forLevel(level);boolean fresh=data.at(bell).isEmpty();
        var v=data.discover(bell,villagers.stream().map(Entity::getUUID).toList());if(v==null)return null;
        if(fresh){int i=0;for(var type:VillageData.QuestType.values())data.author(v,type,villagers.get(i++%villagers.size()).getUUID());}
        if(v.board()==null)for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL){
            BlockPos pos=bell.relative(direction,3);
            if(level.getBlockState(pos).isAir()&&level.getBlockState(pos.below()).isSolidRender(level,pos.below())
                    &&level.getWorldBorder().isWithinBounds(pos)){
                if(level.setBlock(pos,VillageBlocks.BOARD.get().defaultBlockState(),3))data.board(v,pos);break;
            }
        }
        return v;
    }
    private static boolean usable(ServerPlayer player) { return player.isAlive()&&!player.isSpectator(); }
    private static Entity entity(ServerLevel level,UUID id) { return id==null?null:level.getEntity(id); }
    static boolean near(ServerPlayer p,Entity entity) {
        return usable(p)&&entity!=null&&entity.isAlive()&&entity.level()==p.level()&&p.distanceToSqr(entity)<=8*8
                &&TacticalInteractionRules.visible(p.level(),p,entity.getBoundingBox().getCenter(),null);
    }
    private static boolean atBoard(ServerPlayer p,VillageData.Village v) {
        return usable(p)&&v.board()!=null&&p.serverLevel().hasChunkAt(v.board())
                &&p.serverLevel().getBlockState(v.board()).is(VillageBlocks.BOARD.get())
                &&p.distanceToSqr(Vec3.atCenterOf(v.board()))<=8*8
                &&TacticalInteractionRules.visible(p.level(),p,Vec3.atCenterOf(v.board()),v.board());
    }
    public static void openBoard(ServerPlayer p,BlockPos pos) {
        if(!usable(p)||p.distanceToSqr(Vec3.atCenterOf(pos))>64)return;
        var data=VillageData.forLevel(p.serverLevel());var v=data.at(pos).orElse(null);
        if(v==null){feedback(p,Result.INVALID);return;}
        if((v.board()==null||p.serverLevel().hasChunkAt(v.board())&&!p.serverLevel().getBlockState(v.board()).is(VillageBlocks.BOARD.get()))
                &&v.center().distSqr(pos)<=12*12&&p.serverLevel().getBlockState(pos).is(VillageBlocks.BOARD.get()))data.board(v,pos);
        if(!pos.equals(v.board())||!atBoard(p,v)){feedback(p,Result.INVALID);return;}
        show(p,v,null);
    }
    public static boolean talk(ServerPlayer p,Villager npc) {
        var v=VillageData.forLevel(p.serverLevel()).resident(npc.getUUID()).orElse(null);
        if(v==null||!near(p,npc)||!p.isShiftKeyDown()&&!npc.getUUID().equals(v.elder()))return false;
        show(p,v,npc);return true;
    }
    private static MutableComponent link(String key,String command) {
        return Component.translatable(key).withStyle(style->style.withColor(net.minecraft.ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command)));
    }
    private static void show(ServerPlayer p,VillageData.Village v,Villager interlocutor) {
        var data=VillageData.forLevel(p.serverLevel());
        p.sendSystemMessage(Component.translatable("village.stonebanner.overview",v.name(),v.residents().size(),data.reputation(v,p.getUUID())));
        var elder=entity(p.serverLevel(),v.elder());
        p.sendSystemMessage(Component.translatable("village.stonebanner.elder",elder==null?Component.translatable("village.stonebanner.unavailable"):elder.getDisplayName()));
        for(var type:VillageData.QuestType.values()){
            var progress=data.progress(v,p.getUUID(),type).orElse(null);
            var line=Component.translatable("village.stonebanner.quest",Component.translatable("village.stonebanner.quest."+type.name().toLowerCase(Locale.ROOT)),
                    type.amount,type.emeralds,type.trust);
            var author=entity(p.serverLevel(),v.author(type));
            if(author!=null)line.append(Component.literal(" ["+author.getBlockX()+", "+author.getBlockY()+", "+author.getBlockZ()+"] "));
            line.append(Component.translatable("village.stonebanner.author",author==null?Component.translatable("village.stonebanner.unavailable"):author.getDisplayName()));
            if(progress==null)line.append(link("village.stonebanner.accept","/sbvillage accept "+v.id()+" "+type.name()));
            else if(progress.complete())line.append(Component.translatable("village.stonebanner.completed"));
            else if(interlocutor!=null&&questRecipient(p.serverLevel(),v,type)==interlocutor)line.append(link("village.stonebanner.submit","/sbvillage submit "+v.id()+" "+type.name()+" "+interlocutor.getUUID()));
            else line.append(Component.translatable("village.stonebanner.return",progress.count(),type.amount));
            p.sendSystemMessage(line);
        }
        if(interlocutor!=null&&!interlocutor.getUUID().equals(v.elder())){
            int trust=data.reputation(v,p.getUUID());
            p.sendSystemMessage(Component.translatable("village.stonebanner.hire_cost",hirePrice(interlocutor,trust,false),hirePrice(interlocutor,trust,true)));
            p.sendSystemMessage(link("village.stonebanner.companion","/sbvillage hire "+v.id()+" "+interlocutor.getUUID()+" companion")
                    .append(" ").append(link("village.stonebanner.settler","/sbvillage hire "+v.id()+" "+interlocutor.getUUID()+" settler")));
        }
        p.sendSystemMessage(link("village.stonebanner.journal.open","/sbvillage journal"));
        p.sendSystemMessage(link("village.stonebanner.recruit.open","/sbvillage recruitment "+v.id()));
        p.sendSystemMessage(Component.translatable("village.stonebanner.instructions"));
    }
    public static Result accept(ServerPlayer p,UUID village,VillageData.QuestType type) {
        var data=VillageData.forLevel(p.serverLevel());var v=data.get(village).orElse(null);
        if(v==null||!canAccept(p,v,type))return Result.INVALID;
        return data.accept(v,p.getUUID(),type)?Result.OK:Result.ALREADY;
    }
    static boolean canAccept(ServerPlayer p,VillageData.Village v,VillageData.QuestType type) {
        return atBoard(p,v)||near(p,entity(p.serverLevel(),v.elder()))||near(p,questRecipient(p.serverLevel(),v,type));
    }
    public static Entity questRecipient(ServerLevel level,VillageData.Village v,VillageData.QuestType type) {
        var author=entity(level,v.author(type));
        return author!=null&&author.isAlive()&&v.residents().contains(author.getUUID())?author:entity(level,v.elder());
    }
    public static Result submit(ServerPlayer p,UUID village,VillageData.QuestType type,UUID npcId) {
        var data=VillageData.forLevel(p.serverLevel());var v=data.get(village).orElse(null);
        if(v==null)return Result.INVALID;
        var recipient=questRecipient(p.serverLevel(),v,type);
        if(recipient==null||!npcId.equals(recipient.getUUID())||!near(p,recipient))return Result.AUTHOR;
        var progress=data.progress(v,p.getUUID(),type).orElse(null);
        if(progress==null||progress.complete())return Result.ALREADY;
        if(type==VillageData.QuestType.DEFENCE){if(progress.count()<type.amount)return Result.PROGRESS;}
        else {
            Predicate<ItemStack> matching=ingredient(type);
            if(count(p,matching)<type.amount)return Result.ITEMS;
            for(var stack:remove(p,matching,type.amount)) {
                var remainder=recipient instanceof Villager villager? villager.getInventory().addItem(stack):stack;
                if(!remainder.isEmpty())dropAt(recipient,remainder);
            }
        }
        if(!data.complete(v,p.getUUID(),type))return Result.ALREADY;
        var reward=new ItemStack(Items.EMERALD,type.emeralds);
        if(!p.getInventory().add(reward)&&!reward.isEmpty())p.drop(reward,false);
        return Result.OK;
    }
    static Predicate<ItemStack> ingredient(VillageData.QuestType type) {
        return switch(type){case FOOD->s->s.is(Items.BREAD);case TIMBER->s->s.is(ItemTags.LOGS);case IRON->s->s.is(Items.IRON_INGOT);case DEFENCE->s->false;};
    }
    public static int count(ServerPlayer p,Predicate<ItemStack> predicate){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++){var s=p.getInventory().getItem(i);if(predicate.test(s))n+=s.getCount();}return n;}
    private static List<ItemStack> remove(ServerPlayer p,Predicate<ItemStack> predicate,int amount){
        var removed=new ArrayList<ItemStack>();
        for(int i=0;i<p.getInventory().getContainerSize()&&amount>0;i++){var s=p.getInventory().getItem(i);if(predicate.test(s)){int n=Math.min(amount,s.getCount());removed.add(s.split(n));amount-=n;}}p.getInventory().setChanged();return removed;
    }
    private static void dropAt(Entity npc,ItemStack stack){npc.spawnAtLocation(stack);}
    public static Result checkHire(ServerPlayer p,UUID village,UUID resident,boolean settler) {
        var level=p.serverLevel();var data=VillageData.forLevel(level);var v=data.get(village).orElse(null);
        if(v==null||!v.residents().contains(resident)||!(level.getEntity(resident) instanceof Villager old)||old.isBaby()||!near(p,old))return Result.INVALID;
        if(resident.equals(v.elder()))return Result.ELDER;
        int trust=data.reputation(v,p.getUUID());if(!RecruitmentRules.trusted(trust,settler))return Result.TRUST;
        if(!RecruitmentRules.enoughPopulation(v.residents().size()))return Result.POPULATION;
        var profession=old.getVillagerData().getProfession();
        if(profession!=VillagerProfession.NONE&&profession!=VillagerProfession.NITWIT
                &&v.residents().stream().map(level::getEntity).filter(e->e instanceof Villager villager&&villager.getVillagerData().getProfession()==profession).count()<=1)return Result.SPECIALIST;
        var camp=SettlementData.forLevel(level).ownedBy(p.getUUID()).orElse(null);
        if(settler){
            if(camp==null||!camp.bannerActive()||!BannerCommunityService.isStandingBanner(level,camp.banner()))return Result.PROVISIONS;
            var r=BannerCommunityService.readiness(level,camp);int people=camp.residents().size()+2;
            if(camp.residents().size()>=SettlementData.MAX_RESIDENTS||r.beds()<people||r.food()<people*4||r.stores()<1)return Result.PROVISIONS;
        }
        int cost=RecruitmentRules.price(profession==VillagerProfession.NONE||profession==VillagerProfession.NITWIT,old.getVillagerData().getLevel(),trust,settler);
        if(count(p,s->s.is(Items.EMERALD))<cost)return Result.ITEMS;
        return Result.OK;
    }
    public static int hirePrice(Villager old,int trust,boolean settler){
        var profession=old.getVillagerData().getProfession();
        return RecruitmentRules.price(profession==VillagerProfession.NONE||profession==VillagerProfession.NITWIT,old.getVillagerData().getLevel(),trust,settler);
    }
    public static Result hire(ServerPlayer p,UUID village,UUID resident,boolean settler) {return hire(p,village,resident,settler,-1);}
    public static Result hire(ServerPlayer p,UUID village,UUID resident,boolean settler,int expectedPrice) {
        var check=checkHire(p,village,resident,settler);if(check!=Result.OK)return check;
        var level=p.serverLevel();var data=VillageData.forLevel(level);var v=data.get(village).orElseThrow();
        var old=(Villager)level.getEntity(resident);var profession=old.getVillagerData().getProfession();
        var camp=SettlementData.forLevel(level).ownedBy(p.getUUID()).orElse(null);
        int cost=hirePrice(old,data.reputation(v,p.getUUID()),settler);
        if(expectedPrice>=0&&expectedPrice!=cost)return Result.PRICE_CHANGED;
        var origin=VillageReturnService.origin(old);
        var npc=ModEntities.HUMAN_NPC.get().create(level);if(npc==null)return Result.INVALID;
        npc.moveTo(old.position());npc.setYRot(old.getYRot());npc.ensureIdentity();npc.citizenData().initializeStarterSkills(old.getUUID().hashCode());
        npc.citizenData().setProfession(profession(profession),true);
        npc.citizenData().setParticipation(settler?CitizenParticipation.SETTLER:CitizenParticipation.COMPANION);
        npc.citizenData().setRecruitedBy(p.getUUID());
        if(old.hasCustomName())npc.setCustomName(old.getCustomName());
        if(!level.addFreshEntity(npc))return Result.INVALID;
        if(settler&&!npc.commandController().issueSystemMove(camp.banner(),CitizenBrainState.RETURN_HOME)){
            npc.discard();return Result.ROUTE;
        }
        if(settler&&SettlementData.forLevel(level).addResident(p.getUUID(),npc.getUUID())!=SettlementData.Result.JOINED){npc.discard();return Result.PROVISIONS;}
        remove(p,s->s.is(Items.EMERALD),cost);
        for(int i=0;i<old.getInventory().getContainerSize();i++){
            var stack=old.getInventory().removeItemNoUpdate(i);var remainder=npc.citizenData().inventory().add(stack);if(!remainder.isEmpty())npc.spawnAtLocation(remainder);
        }
        if(settler)npc.citizenData().home().assign(camp.id().toString(),camp.banner(),1);
        else {npc.citizenData().home().assign(v.id().toString(),v.center(),2);npc.issueCommand(new ActorCommand.FollowEntity(p.getId(),2.5));}
        data.removeResident(resident);data.contract(v,npc.getUUID(),p.getUUID(),origin);old.discard();return Result.OK;
    }
    private static CitizenProfession profession(VillagerProfession p){
        if(p==VillagerProfession.FARMER)return CitizenProfession.FARMER;
        if(p==VillagerProfession.MASON)return CitizenProfession.BUILDER;
        if(p==VillagerProfession.TOOLSMITH||p==VillagerProfession.WEAPONSMITH||p==VillagerProfession.ARMORER)return CitizenProfession.CRAFTSMAN;
        if(p==VillagerProfession.LIBRARIAN||p==VillagerProfession.CARTOGRAPHER)return CitizenProfession.GEOLOGIST;
        if(p==VillagerProfession.NONE||p==VillagerProfession.NITWIT)return CitizenProfession.UNEMPLOYED;
        return CitizenProfession.TRADER;
    }
    public static void feedback(ServerPlayer p,Result result){p.displayClientMessage(Component.translatable("village.stonebanner.result."+result.name().toLowerCase(Locale.ROOT)),false);}
}
