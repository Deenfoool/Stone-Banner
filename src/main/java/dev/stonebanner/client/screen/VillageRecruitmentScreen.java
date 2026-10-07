package dev.stonebanner.client.screen;

import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.*;
import dev.stonebanner.network.packet.VillageRecruitmentPacket.Row;
import dev.stonebanner.network.packet.VillageRecruitmentActionPacket.Action;
import dev.stonebanner.village.VillageService.Result;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.*;

/** Snapshot-based contract UI. A confirmation sends identities and the quoted price, not authority. */
public final class VillageRecruitmentScreen extends Screen {
    private VillageRecruitmentPacket state;
    private final Screen parent;
    private UUID selected;
    private int x,w,left,detail,pendingTicks;
    private boolean pending;
    public VillageRecruitmentScreen(VillageRecruitmentPacket state,Screen parent){super(Component.translatable("village.stonebanner.recruit.title"));this.state=state;this.parent=parent;}
    public static void open(VillageRecruitmentPacket state){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(state.dimension()))return;
        if(mc.screen instanceof VillageRecruitmentScreen menu){menu.state=state;menu.pending=false;menu.rebuildWidgets();return;}
        if(!state.opening())return;
        PlayerCommandController.stop();mc.setScreen(new VillageRecruitmentScreen(state,mc.screen instanceof ChatScreen?null:mc.screen));
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void tick(){
        if(minecraft.level==null||!minecraft.level.dimension().location().equals(state.dimension())){minecraft.setScreen(null);return;}
        if(pending&&++pendingTicks>=40){pending=false;rebuildWidgets();}
    }
    private Row row(){return state.rows().stream().filter(r->r.id().equals(selected)).findFirst().orElse(state.rows().isEmpty()?null:state.rows().get(0));}
    private Button button(Component label,int bx,int by,int bw,Runnable action,boolean active){var b=addRenderableWidget(Button.builder(label,ignored->action.run()).bounds(bx,by,bw,20).build());b.active=active;return b;}
    private void send(Action action,UUID village,UUID npc,boolean contracts,int page,int price){
        pending=true;pendingTicks=0;StoneBannerNetwork.sendVillageRecruitmentAction(new VillageRecruitmentActionPacket(action,village,npc,contracts,page,price));rebuildWidgets();
    }
    private void query(UUID village,boolean contracts,int page){send(Action.OPEN,village,new UUID(0,0),contracts,page,0);}
    private void village(int direction){
        if(state.villages().isEmpty())return;int index=0;for(int i=0;i<state.villages().size();i++)if(state.villages().get(i).id().equals(state.selected()))index=i;
        query(state.villages().get(Math.floorMod(index+direction,state.villages().size())).id(),state.contracts(),0);
    }
    private void confirm(Row r,Action action,int price){
        Component message=action==Action.DISMISS?Component.translatable("village.stonebanner.recruit.confirm_dismiss",r.name()):
            Component.translatable("village.stonebanner.recruit.confirm_hire",r.name(),price);
        minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)send(action,state.selected(),r.id(),state.contracts(),state.page(),price);},title,message));
    }
    private static Component reason(Result result){return Component.translatable("village.stonebanner.result."+result.name().toLowerCase(Locale.ROOT));}
    @Override protected void init(){
        w=Math.min(540,width-16);x=(width-w)/2;left=Math.min(172,Math.max(100,w/3));detail=x+left+12;
        var r=row();selected=r==null?null:r.id();
        button(Component.literal("<"),x+8,12,24,()->village(-1),!pending&&state.villages().size()>1);
        button(Component.literal(">"),x+w-32,12,24,()->village(1),!pending&&state.villages().size()>1);
        button(Component.translatable("village.stonebanner.recruit.residents"),x+8,38,82,()->query(state.selected(),false,0),!pending&&state.contracts());
        button(Component.translatable("village.stonebanner.recruit.contracts"),x+94,38,104,()->query(state.selected(),true,0),!pending&&!state.contracts());
        for(int i=0;i<state.rows().size();i++){
            var entry=state.rows().get(i);button(Component.literal(font.plainSubstrByWidth(entry.name(),left-22)),x+8,68+i*22,left-8,()->{selected=entry.id();rebuildWidgets();},!pending);
        }
        int footer=height-30;
        button(Component.literal("<"),x+8,footer,24,()->query(state.selected(),state.contracts(),state.page()-1),!pending&&state.page()>0);
        button(Component.literal(">"),x+36,footer,24,()->query(state.selected(),state.contracts(),state.page()+1),!pending&&(state.page()+1)*VillageRecruitmentPacket.PAGE_SIZE<state.total());
        button(Component.translatable("village.stonebanner.journal.refresh"),x+w-160,footer,84,()->query(state.selected(),state.contracts(),state.page()),!pending);
        button(Component.translatable("gui.done"),x+w-70,footer,62,this::onClose,true);
        if(r!=null){
            if(state.contracts()){
                var dismiss=button(Component.translatable("village.stonebanner.recruit.dismiss"),detail,height-76,w-left-24,()->confirm(r,Action.DISMISS,0),!pending&&r.dismiss()==Result.OK);
                dismiss.setTooltip(Tooltip.create(r.status().equals("returning")?Component.translatable("village.stonebanner.recruit.status.returning"):reason(r.dismiss())));
            }else{
                int bw=(w-left-28)/2;
                var companion=button(Component.translatable("village.stonebanner.recruit.companion"),detail,height-76,bw,()->confirm(r,Action.COMPANION,r.companionPrice()),!pending&&r.companion()==Result.OK);
                var settler=button(Component.translatable("village.stonebanner.recruit.settler"),detail+bw+4,height-76,bw,()->confirm(r,Action.SETTLER,r.settlerPrice()),!pending&&r.settler()==Result.OK);
                companion.setTooltip(Tooltip.create(reason(r.companion())));settler.setTooltip(Tooltip.create(reason(r.settler())));
            }
        }
    }
    private void text(GuiGraphics g,Component label,int bx,int by,int maxWidth,int color){g.drawString(font,font.plainSubstrByWidth(label.getString(),Math.max(0,maxWidth)),bx,by,color,false);}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        renderBackground(g);g.fill(x,8,x+w,height-6,0xE51C2429);text(g,title,x+40,18,w-80,0xF4E5BE);
        text(g,Component.translatable("village.stonebanner.recruit.balance",state.trust(),state.emeralds()),x+206,44,w-214,0xBFCAD0);
        var r=row();int available=w-left-24;
        if(r==null)text(g,Component.translatable(state.contracts()?"village.stonebanner.recruit.empty_contracts":"village.stonebanner.recruit.empty_residents"),x+8,72,w-16,0xBFCAD0);
        else{
            g.fill(detail-4,64,x+w-8,height-80,0xB52E3940);
            g.renderItem(new ItemStack(state.contracts()?Items.WRITABLE_BOOK:Items.EMERALD),detail,68);
            text(g,Component.literal(r.name()),detail+20,70,available-20,0xF4E5BE);
            var profession=Component.translatable(r.status().equals("unavailable")?"village.stonebanner.unavailable":(state.contracts()?"profession.stonebanner.":"entity.minecraft.villager.")+r.profession());
            text(g,profession.append(r.level()>0?"  "+r.level():""),detail,88,available,0xFFFFFF);
            text(g,Component.translatable("village.stonebanner.recruit.status."+r.status()),detail,101,available,0xBFCAD0);
            if(r.position()!=null)text(g,Component.literal(r.position().getX()+", "+r.position().getY()+", "+r.position().getZ()),detail,114,available,0xBFCAD0);
            if(!state.contracts()){
                text(g,Component.translatable("village.stonebanner.recruit.prices",r.companionPrice(),r.settlerPrice()),detail,127,available,0xF4E5BE);
                text(g,Component.translatable("village.stonebanner.recruit.requirements"),detail,140,available,0xBFCAD0);
            }else text(g,reason(r.dismiss()),detail,140,available,r.dismiss()==Result.OK?0x8CDC98:0xFFBA89);
        }
        String village=state.villages().stream().filter(v->v.id().equals(state.selected())).map(VillageRecruitmentPacket.Village::name).findFirst().orElse("");
        text(g,Component.literal(village+"  "+(state.page()+1)+"/"+Math.max(1,(state.total()+3)/4)),x+66,height-24,w-230,0xBFCAD0);
        text(g,state.result()==null?Component.translatable("village.stonebanner.recruit.hint"):reason(state.result()),x+8,height-44,w-16,state.result()==null||state.result()==Result.OK?0xBFCAD0:0xFFBA89);
        super.render(g,mouseX,mouseY,delta);
    }
}
