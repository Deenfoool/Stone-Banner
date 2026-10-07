package dev.stonebanner.client.screen;

import dev.stonebanner.citizen.*;
import dev.stonebanner.client.control.CitizenInventoryClientCache;
import dev.stonebanner.entity.HumanNpcEntity;
import dev.stonebanner.network.StoneBannerNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.*;

/** All five tabs share UUID/world identity, paging, authoritative snapshots and access checks. */
public final class CitizenDetailsScreen extends Screen {
    private final Screen parent;
    private final int citizenEntityId;
    private UUID identity;
    private ResourceLocation dimension;
    private Tab activeTab;
    private int page,workPage,refreshTicks;
    private CitizenInspectorLayout layout;
    public CitizenDetailsScreen(Screen parent,int entityId,Tab tab){super(Component.translatable("screen.stonebanner.citizen"));this.parent=parent;citizenEntityId=entityId;activeTab=tab==null?Tab.OVERVIEW:tab;}
    private HumanNpcEntity citizen(){
        if(minecraft==null||minecraft.level==null||dimension==null||!minecraft.level.dimension().location().equals(dimension))return null;
        return minecraft.level.getEntity(citizenEntityId) instanceof HumanNpcEntity npc&&npc.isAlive()&&npc.getUUID().equals(identity)?npc:null;
    }
    private CitizenData data(){return identity==null?null:CitizenInventoryClientCache.snapshot(dimension,identity);}
    private List<HumanNpcEntity> nearby(){
        var npc=citizen();if(npc==null)return List.of();var list=minecraft.level.getEntitiesOfClass(HumanNpcEntity.class,npc.getBoundingBox().inflate(48),HumanNpcEntity::isAlive);
        list.sort(Comparator.comparing((HumanNpcEntity n)->n.getUUID().equals(identity)?0:1).thenComparing(n->n.getDisplayName().getString()).thenComparing(n->n.getUUID().toString()));return list;
    }
    private Button button(Component text,int x,int y,int w,Runnable action,boolean enabled){var b=addRenderableWidget(Button.builder(text,ignored->action.run()).bounds(x,y,w,20).build());b.active=enabled;return b;}
    @Override protected void init(){
        layout=CitizenInspectorLayout.of(width,height);
        if(identity==null&&minecraft.level!=null&&minecraft.level.getEntity(citizenEntityId) instanceof HumanNpcEntity npc){identity=npc.getUUID();dimension=minecraft.level.dimension().location();}
        if(identity!=null){CitizenInventoryClientCache.begin(dimension,identity);request();}
        widgets();
    }
    private int total(){var d=data();return switch(activeTab){case OVERVIEW->overview(citizen(),d).size();case HEALTH->11;case SKILLS->CitizenSkill.values().length+1;case WORK->nearby().size();case INVENTORY->1;};}
    private int pageRows(){return activeTab==Tab.HEALTH?Math.max(1,layout.rows()-1):layout.dataRows(activeTab==Tab.WORK);}
    private void widgets(){
        clearWidgets();int x=layout.x(),y=layout.y(),w=layout.width();int tabWidth=(w-20)/5;
        for(var tab:Tab.values()){
            Component label=Component.translatable("screen.stonebanner.citizen.tab."+tab.name().toLowerCase(Locale.ROOT));
            var b=button(Component.literal(font.plainSubstrByWidth(label.getString(),tabWidth-6)),x+10+tab.ordinal()*tabWidth,y+37,tabWidth,()->{activeTab=tab;page=0;widgets();},activeTab!=tab);b.setTooltip(Tooltip.create(label));
        }
        page=layout.clampPage(page,total(),pageRows());workPage=layout.clampPage(workPage,WorkType.values().length,layout.workColumns());
        int fy=y+layout.height()-26;
        button(Component.literal("<"),x+10,fy,24,()->{page--;widgets();},page>0);
        button(Component.literal(">"),x+38,fy,24,()->{page++;widgets();},(page+1)*pageRows()<total());
        if(activeTab==Tab.WORK){
            button(Component.literal("<<"),x+76,fy,28,()->{workPage--;widgets();},workPage>0);
            button(Component.literal(">>"),x+108,fy,28,()->{workPage++;widgets();},(workPage+1)*layout.workColumns()<WorkType.values().length);
        }
        if (activeTab == Tab.HEALTH) {
            var npc = citizen();
            boolean allowed = npc != null && minecraft.player != null && npc.hudCanView(minecraft.player.getUUID()) && minecraft.player.distanceToSqr(npc) <= 36;
            button(tr("medical.stonebanner.bandage"), x+10, fy-24, 54, () -> StoneBannerNetwork.treatCitizen(citizenEntityId, identity, dimension, false), allowed)
                    .setTooltip(Tooltip.create(tr("medical.stonebanner.hint")));
            button(tr("medical.stonebanner.splint"), x+68, fy-24, 54, () -> StoneBannerNetwork.treatCitizen(citizenEntityId, identity, dimension, true), allowed)
                    .setTooltip(Tooltip.create(tr("medical.stonebanner.hint")));
        }
        button(Component.translatable("village.stonebanner.journal.refresh"),x+w-136,fy,66,this::request,citizen()!=null&&minecraft.player!=null&&citizen().hudCanView(minecraft.player.getUUID()));
        button(Component.translatable("gui.done"),x+w-66,fy,56,this::onClose,true);
    }
    private void request(){var npc=citizen();if(npc!=null&&minecraft.player!=null&&npc.hudCanView(minecraft.player.getUUID()))StoneBannerNetwork.requestCitizenInventory(citizenEntityId,identity,dimension);refreshTicks=0;}
    @Override public void tick(){
        var npc=citizen();if(npc==null){CitizenInventoryClientCache.clear();return;}
        if(minecraft.player!=null&&!npc.hudCanView(minecraft.player.getUUID()))CitizenInventoryClientCache.clear();
        if(++refreshTicks>=20){request();widgets();}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){CitizenInventoryClientCache.clear();minecraft.setScreen(parent);}
    private record Line(Component label,Component value,int color){Line(Component label,Component value){this(label,value,0xF0ECE3);}}
    private static Component tr(String key){return Component.translatable(key);}
    private static Component number(double value){return Component.literal(String.format(Locale.ROOT,"%.0f%%",value));}
    private List<Line> overview(HumanNpcEntity npc,CitizenData d){
        var lines=new ArrayList<Line>();if(npc==null)return lines;
        lines.add(new Line(tr("screen.stonebanner.citizen.state"),tr("brain_state.stonebanner."+npc.brainState().serializedName())));
        if (npc.brainState()==CitizenBrainState.SLEEP) {
            lines.add(new Line(tr("sleep.stonebanner.rest"),tr(npc.hudSeekingBed()?"sleep.stonebanner.seeking":npc.isSleeping()?"sleep.stonebanner.bed":"sleep.stonebanner.ground")));
            npc.getSleepingPos().ifPresent(pos -> lines.add(new Line(tr("sleep.stonebanner.bed"),Component.literal(pos.toShortString()))));
        }
        lines.add(new Line(tr("screen.stonebanner.citizen.current_work"),npc.hudWorkType()==null?tr("screen.stonebanner.none"):tr("work_type.stonebanner."+npc.hudWorkType().serializedName())));
        lines.add(new Line(tr("screen.stonebanner.citizen.work_block"),tr(npc.hudWorkBlockReason().key())));
        lines.add(new Line(tr("hud.stonebanner.npc.health"),number(npc.getHealth()/npc.getMaxHealth()*100)));
        lines.add(new Line(tr("hud.stonebanner.npc.hunger"),number(npc.hudHunger())));lines.add(new Line(tr("hud.stonebanner.npc.fatigue"),number(npc.hudFatigue())));lines.add(new Line(tr("hud.stonebanner.npc.danger"),number(npc.hudDanger())));
        lines.add(new Line(tr("screen.stonebanner.citizen.cargo"),Component.literal(""+npc.hudCargoCount())));
        lines.add(new Line(tr("screen.stonebanner.citizen.delivery"),tr(npc.hudDeliveryStatus().key())));
        lines.add(new Line(tr("screen.stonebanner.inspector.queue"),Component.literal(""+npc.hudQueuedMoves())));
        if(d!=null){
            lines.add(new Line(tr("screen.stonebanner.inspector.role"),tr("village.stonebanner.recruit.status."+d.participation().serializedName())));
            if(d.home().hasHome())lines.add(new Line(tr("screen.stonebanner.inspector.home"),Component.literal(d.home().homePos().toShortString())));
            if(d.returningToVillage())lines.add(new Line(tr("screen.stonebanner.inspector.contract"),tr("village.stonebanner.recruit.status.returning")));
        }
        return lines;
    }
    private void text(GuiGraphics g,Component text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),Math.max(0,width)),x,y,color,false);}
    private void lines(GuiGraphics g,List<Line> lines,int mouseX,int mouseY){
        int x=layout.x()+14,y=layout.y()+68,w=layout.width()-28,keyWidth=Math.min(160,w/2);
        for(int i=0;i<pageRows()&&page*pageRows()+i<lines.size();i++){
            var line=lines.get(page*pageRows()+i);int ly=y+i*22;
            text(g,line.label,x,ly,keyWidth-6,0xAAA49A);text(g,line.value,x+keyWidth,ly,w-keyWidth,line.color);
            if(mouseX>=x&&mouseX<x+w&&mouseY>=ly&&mouseY<ly+20)g.renderTooltip(font,line.label.copy().append(": ").append(line.value),mouseX,mouseY);
        }
    }
    private List<Line> health(CitizenData d){
        var rows=new ArrayList<Line>();for(var part:BodyPart.values()){var injury=d.health().injury(part);Component state=tr("injury.stonebanner."+injury.serializedName());int seconds=d.health().recoverySeconds(part);if(seconds>0)state=state.copy().append(" · ").append(Component.translatable("medical.stonebanner.recovery",seconds));rows.add(new Line(tr("body_part.stonebanner."+part.serializedName()),state,injury==InjuryState.NORMAL?0x79C979:injury==InjuryState.WOUNDED?0xE2B85C:0xE76F6F));}
        rows.add(new Line(tr("screen.stonebanner.inspector.mobility"),number(d.health().canMoveIndependently()?d.health().movementMultiplier()*100:0)));
        rows.add(new Line(tr("screen.stonebanner.inspector.work_rate"),number(d.health().workEfficiencyMultiplier()*100)));
        rows.add(new Line(tr("screen.stonebanner.inspector.combat_rate"),number(CitizenSkillRules.combatRate(d)*100)));
        rows.add(new Line(tr("medical.stonebanner.bleeding"),tr(d.health().isBleeding()?"medical.stonebanner.yes":"medical.stonebanner.no"),d.health().isBleeding()?0xE76F6F:0x79C979));
        rows.add(new Line(tr("medical.stonebanner.care"),tr("medical.stonebanner.hint")));return rows;
    }
    private List<Line> skills(CitizenData d){
        var rows=new ArrayList<Line>();for(var skill:CitizenSkill.values())rows.add(new Line(tr("skill.stonebanner."+skill.serializedName()),Component.literal(d.skill(skill)+"/10 · "+(d.skill(skill)>=10?"MAX":d.experience(skill)+"/"+d.experienceNeeded(skill)+" XP"))));
        rows.add(new Line(tr("screen.stonebanner.inspector.practice"),tr("screen.stonebanner.inspector.practice_hint")));return rows;
    }
    private void work(GuiGraphics g,int mx,int my){
        int x=layout.x()+10,y=layout.y()+68;var citizens=nearby();int start=workPage*layout.workColumns();
        for(int col=0;col<layout.workColumns()&&start+col<WorkType.values().length;col++){
            var type=WorkType.values()[start+col];text(g,tr("work_short.stonebanner."+type.serializedName()),x+104+col*38,y,36,0xE0B66A);
            boolean implemented=switch(type){case MINING,FORESTRY,CLEARING,BUILDING,HAULING,FARMING,CRAFTING->true;default->false;};
            if(mx>=x+104+col*38&&mx<x+142+col*38&&my>=y&&my<y+18)g.renderTooltip(font,Component.translatable("screen.stonebanner.inspector.work_hint",tr("work_type.stonebanner."+type.serializedName()),tr(implemented?"screen.stonebanner.inspector.implemented":"screen.stonebanner.inspector.planned")),mx,my);
        }
        int rows=Math.max(1,layout.rows()-1);
        // Work uses one fewer data row because its heading is inside the content rectangle.
        int offset=page*pageRows();
        for(int i=0;i<rows&&offset+i<citizens.size();i++){
            var npc=citizens.get(offset+i);int ry=y+20+i*22;
            text(g,npc.getDisplayName(),x+2,ry+5,98,npc.getUUID().equals(identity)?0xE0B66A:0xF0ECE3);
            for(int col=0;col<layout.workColumns()&&start+col<WorkType.values().length;col++){
                var type=WorkType.values()[start+col];int cx=x+104+col*38;var priority=npc.hudWorkPriority(type);boolean editable=minecraft.player!=null&&npc.hudCanDirect(minecraft.player.getUUID());
                g.fill(cx,ry,cx+36,ry+20,editable?0xFF29332B:0xFF282828);text(g,Component.literal(priority==WorkPriority.DISABLED?"X":""+priority.code()),cx+12,ry+5,20,editable?0xE0B66A:0x888888);
                if(mx>=cx&&mx<cx+36&&my>=ry&&my<ry+20)g.renderTooltip(font,tr(editable?"screen.stonebanner.work.hint":"screen.stonebanner.inspector.readonly"),mx,my);
            }
        }
    }
    private void inventory(GuiGraphics g,CitizenData d,int mx,int my){
        int x=layout.x()+14,y=layout.y()+68;var cargo=d.inventory().haulCargoSnapshot();ItemStack hovered=ItemStack.EMPTY;
        for(int i=0;i<CitizenInventory.SLOT_COUNT;i++){
            int sx=x+(i%3)*26,sy=y+(i/3)*26;final int slot=i;boolean hauled=cargo.stream().anyMatch(c->c.slot()==slot);
            g.fill(sx,sy,sx+24,sy+24,hauled?0xFF514127:0xFF262E34);var stack=d.inventory().stack(i);
            if(!stack.isEmpty()){g.renderItem(stack,sx+4,sy+4);g.renderItemDecorations(font,stack,sx+4,sy+4);if(mx>=sx&&mx<sx+24&&my>=sy&&my<sy+24)hovered=stack;}
        }
        text(g,tr("screen.stonebanner.inventory.real_items"),x+90,y,layout.width()-120,0xAAA49A);
        text(g,tr("screen.stonebanner.inspector.cargo_hint"),x+90,y+24,layout.width()-120,0xE0B66A);
        text(g,tr("screen.stonebanner.inventory.food_hint"),x+90,y+48,layout.width()-120,0xAAA49A);
        if(!hovered.isEmpty())g.renderTooltip(font,hovered,mx,my);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        int x=layout.x(),y=layout.y(),w=layout.width();g.fill(0,0,width,height,0x99000000);g.fill(x,y,x+w,y+layout.height(),0xEE111418);
        var npc=citizen();var d=data();
        if(npc==null)text(g,tr("screen.stonebanner.citizen.missing"),x+12,y+12,w-24,0xE76F6F);
        else{
            text(g,npc.getDisplayName(),x+12,y+10,w-24,0xF0ECE3);text(g,tr("profession.stonebanner."+npc.hudProfession().serializedName()),x+12,y+23,w-24,0xAAA49A);
            text(g,Component.literal((page+1)+"/"+Math.max(1,(total()+pageRows()-1)/pageRows())),x+w-46,y+23,34,0xE0B66A);
            if(activeTab==Tab.OVERVIEW)lines(g,overview(npc,d),mx,my);
            else if(activeTab==Tab.WORK)work(g,mx,my);
            else if(d==null)text(g,tr(minecraft.player!=null&&!npc.hudCanView(minecraft.player.getUUID())?"screen.stonebanner.inspector.readonly":"screen.stonebanner.inventory.loading"),x+14,y+68,w-28,0xAAA49A);
            else switch(activeTab){case HEALTH->lines(g,health(d),mx,my);case SKILLS->lines(g,skills(d),mx,my);case INVENTORY->inventory(g,d,mx,my);default->{}}
        }
        super.render(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(activeTab==Tab.WORK&&button==0&&minecraft.player!=null){int x=layout.x()+10,y=layout.y()+88;var list=nearby();int offset=page*pageRows();
            for(int row=0;row<Math.max(1,layout.rows()-1)&&offset+row<list.size();row++)for(int col=0;col<layout.workColumns()&&workPage*layout.workColumns()+col<WorkType.values().length;col++){
                if(mx<x+104+col*38||mx>=x+140+col*38||my<y+row*22||my>=y+row*22+20)continue;
                var npc=list.get(offset+row);if(!npc.hudCanDirect(minecraft.player.getUUID()))return true;
                var type=WorkType.values()[workPage*layout.workColumns()+col];StoneBannerNetwork.sendWorkPriority(npc.getId(),npc.getUUID(),dimension,type.ordinal(),CitizenHudCodec.nextPriority(npc.hudWorkPriority(type)).code());return true;
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    public enum Tab {OVERVIEW,HEALTH,SKILLS,WORK,INVENTORY}
}
