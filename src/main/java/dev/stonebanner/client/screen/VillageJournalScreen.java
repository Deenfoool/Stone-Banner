package dev.stonebanner.client.screen;

import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.*;
import dev.stonebanner.network.packet.VillageJournalPacket.*;
import dev.stonebanner.village.VillageData.QuestType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.*;

/** Minecraft-font journal, with paged content instead of text extending outside the playfield. */
public final class VillageJournalScreen extends Screen {
    private VillageJournalPacket state;
    private final Screen parent;
    private UUID selected;
    private int questPage,rows,panelX,panelWidth,pendingTicks;
    private boolean pending;
    public VillageJournalScreen(VillageJournalPacket state,Screen parent){
        super(Component.translatable("village.stonebanner.journal.title"));this.state=state;this.parent=parent;
        if(!state.villages().isEmpty())selected=state.villages().get(0).id();
    }
    public static void open(VillageJournalPacket state){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(state.dimension()))return;
        if(mc.screen instanceof VillageJournalScreen menu){menu.state=state;menu.pending=false;menu.rebuildWidgets();return;}
        if(!state.opening())return; // A late action/refresh response must not reopen an escaped screen.
        PlayerCommandController.stop();mc.setScreen(new VillageJournalScreen(state,mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen?null:mc.screen));
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void tick(){
        if(minecraft.level==null||!minecraft.level.dimension().location().equals(state.dimension())){onClose();return;}
        if(pending&&++pendingTicks>=40){pending=false;rebuildWidgets();}
    }
    private Entry entry(){return state.villages().stream().filter(v->v.id().equals(selected)).findFirst().orElse(state.villages().isEmpty()?null:state.villages().get(0));}
    private void send(VillageJournalActionPacket.Action action,QuestType type){
        var v=entry();pending=true;pendingTicks=0;
        StoneBannerNetwork.sendVillageJournalAction(new VillageJournalActionPacket(action,v==null?new UUID(0,0):v.id(),type));rebuildWidgets();
    }
    private Button button(Component label,int x,int y,int w,Runnable action,boolean enabled){
        var b=addRenderableWidget(Button.builder(label,ignored->action.run()).bounds(x,y,w,20).build());b.active=enabled;return b;
    }
    private void village(int direction){
        if(state.villages().isEmpty())return;int current=state.villages().indexOf(entry());
        selected=state.villages().get(Math.floorMod(current+direction,state.villages().size())).id();questPage=0;rebuildWidgets();
    }
    @Override protected void init(){
        panelWidth=Math.min(520,width-16);panelX=(width-panelWidth)/2;rows=Math.max(1,Math.min(4,(height-132)/52));
        var v=entry();if(v!=null){selected=v.id();questPage=Math.max(0,Math.min(questPage,(v.quests().size()-1)/rows));}
        button(Component.literal("<"),panelX+8,12,24,()->village(-1),state.villages().size()>1);
        button(Component.literal(">"),panelX+panelWidth-32,12,24,()->village(1),state.villages().size()>1);
        int footer=height-30;
        button(Component.translatable("village.stonebanner.recruit.short"),panelX+68,footer,70,()->{if(minecraft.getConnection()!=null)minecraft.getConnection().sendCommand("sbvillage recruitment "+(v==null?new UUID(0,0):v.id()));},!pending);
        button(Component.literal("<"),panelX+8,footer,24,()->{questPage--;rebuildWidgets();},v!=null&&questPage>0);
        button(Component.literal(">"),panelX+36,footer,24,()->{questPage++;rebuildWidgets();},v!=null&&(questPage+1)*rows<v.quests().size());
        button(Component.translatable("village.stonebanner.journal.refresh"),panelX+panelWidth-160,footer,84,()->send(VillageJournalActionPacket.Action.OPEN,QuestType.FOOD),!pending);
        button(Component.translatable("gui.done"),panelX+panelWidth-70,footer,62,this::onClose,true);
        if(v!=null)for(int i=0;i<rows&&questPage*rows+i<v.quests().size();i++){
            var q=v.quests().get(questPage*rows+i);int y=82+i*52;
            boolean accept=q.state()==State.AVAILABLE;
            button(Component.translatable(accept?"village.stonebanner.journal.accept":"village.stonebanner.journal.submit"),panelX+panelWidth-88,y+4,76,
                ()->send(accept?VillageJournalActionPacket.Action.ACCEPT:VillageJournalActionPacket.Action.SUBMIT,q.type()),!pending&&(accept?q.accept():q.submit()));
        }
    }
    private void text(GuiGraphics g,Component text,int x,int y,int maxWidth,int color){g.drawString(font,font.plainSubstrByWidth(text.getString(),Math.max(0,maxWidth)),x,y,color,false);}
    private static Component coordinates(net.minecraft.core.BlockPos pos){return Component.literal(pos.getX()+", "+pos.getY()+", "+pos.getZ());}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta){
        renderBackground(g);g.fill(panelX,8,panelX+panelWidth,height-6,0xE51C2429);
        text(g,title,panelX+40,18,panelWidth-80,0xF4E5BE);
        var v=entry();
        if(v==null)text(g,Component.translatable("village.stonebanner.journal.empty"),panelX+12,48,panelWidth-24,0xFFFFFF);
        else{
            text(g,Component.literal(v.name()).append("  ").append(coordinates(v.center())),panelX+12,38,panelWidth-24,0xFFFFFF);
            text(g,Component.translatable("village.stonebanner.journal.summary",v.population(),v.trust()),panelX+12,51,panelWidth-24,0xBFCAD0);
            text(g,Component.translatable("village.stonebanner.elder",v.elder().isEmpty()?Component.translatable("village.stonebanner.unavailable"):Component.literal(v.elder())),panelX+12,64,panelWidth-24,0xBFCAD0);
            for(int i=0;i<rows&&questPage*rows+i<v.quests().size();i++){
                var q=v.quests().get(questPage*rows+i);int y=82+i*52;g.fill(panelX+6,y,panelX+panelWidth-6,y+48,0xB52E3940);
                var icon=switch(q.type()){case FOOD->Items.BREAD;case TIMBER->Items.OAK_LOG;case IRON->Items.IRON_INGOT;case DEFENCE->Items.IRON_SWORD;};
                g.renderItem(new ItemStack(icon),panelX+10,y+5);
                int x=panelX+30,w=panelWidth-126;
                text(g,Component.translatable("village.stonebanner.quest."+q.type().name().toLowerCase(Locale.ROOT)).append("  ")
                    .append(Component.translatable("village.stonebanner.journal.state."+q.state().name().toLowerCase(Locale.ROOT))),x,y+5,w,q.state()==State.COMPLETED?0x8CDC98:0xF4E5BE);
                text(g,Component.translatable("village.stonebanner.journal.progress",q.progress(),q.type().amount,q.type().emeralds,q.type().trust),x,y+17,w,0xD8E0E4);
                Component recipient=q.recipient()==null?Component.translatable("village.stonebanner.unavailable"):Component.literal(q.name()).append(" [").append(coordinates(q.position())).append("]");
                text(g,Component.translatable("village.stonebanner.author",recipient),x,y+29,panelWidth-42,0xBFCAD0);
            }
        }
        if(!state.result().isEmpty())text(g,Component.translatable("village.stonebanner.result."+state.result()),panelX+12,height-44,panelWidth-24,state.result().equals("ok")?0x8CDC98:0xFFBA89);
        else text(g,Component.translatable("village.stonebanner.journal.hint"),panelX+12,height-44,panelWidth-24,0xBFCAD0);
        super.render(g,mouseX,mouseY,delta);
    }
}
