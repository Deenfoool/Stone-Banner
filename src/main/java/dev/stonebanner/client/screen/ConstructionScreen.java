package dev.stonebanner.client.screen;

import dev.stonebanner.client.control.*;
import dev.stonebanner.construction.*;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public final class ConstructionScreen extends Screen {
    private ConstructionSnapshotPacket state;
    private final Screen parent;
    private int page,ticks,x,y,w,rows;
    private boolean pending;
    private ConstructionScreen(ConstructionSnapshotPacket state,Screen parent){super(label("title"));this.state=state;this.parent=parent;}
    private static Component label(String key,Object... args){return Component.translatable("construction.stonebanner.ui."+key,args);}
    public static void requestOpen(){
        var mc=Minecraft.getInstance();if(mc.level!=null)StoneBannerNetwork.sendConstructionAction(new ConstructionActionPacket(mc.level.dimension().location(),ConstructionService.Action.OPEN,BlockPos.ZERO,0,-1));
    }
    public static void open(ConstructionSnapshotPacket packet){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(packet.dimension()))return;
        if(mc.screen instanceof ConstructionScreen screen){screen.state=packet;screen.pending=false;screen.rebuildWidgets();return;}
        if(!packet.opening()){
            if(!packet.result().equals("ready")&&mc.player!=null)mc.player.displayClientMessage(Component.translatable("construction.stonebanner.result."+packet.result()),true);
            return;
        }
        ConstructionPreviewController.cancel();HeroInputController.cancel();PlayerCommandController.stop();mc.setScreen(new ConstructionScreen(packet,mc.screen));
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void tick(){
        if(minecraft.level==null||minecraft.player==null||!minecraft.player.isAlive()||!minecraft.level.dimension().location().equals(state.dimension())){onClose();return;}
        if(pending&&++ticks>=40){pending=false;rebuildWidgets();}
    }
    private void send(ConstructionService.Action action,long id){pending=true;ticks=0;StoneBannerNetwork.sendConstructionAction(new ConstructionActionPacket(state.dimension(),action,BlockPos.ZERO,0,id));rebuildWidgets();}
    private void button(Component text,int bx,int by,int bw,Runnable action,boolean enabled){var b=addRenderableWidget(Button.builder(text,ignored->action.run()).bounds(bx,by,bw,20).build());b.active=enabled;}
    @Override protected void init(){
        w=Math.min(470,width-16);x=(width-w)/2;y=8;rows=Math.max(1,(height-190)/48);page=Math.min(page,Math.max(0,(state.plans().size()-1)/rows));
        button(label("place"),x+8,y+96,w-16,()->{ConstructionPreviewController.start();minecraft.setScreen(parent instanceof TacticalControlScreen?parent:new TacticalControlScreen());},!pending&&state.plans().size()<ConstructionData.LIMIT);
        for(int i=0;i<rows&&page*rows+i<state.plans().size();i++){
            var plan=state.plans().get(page*rows+i);int by=y+138+i*48;
            button(label(plan.paused()?"resume":"pause"),x+8,by,Math.max(60,(w-24)/2),()->send(plan.paused()?ConstructionService.Action.RESUME:ConstructionService.Action.PAUSE,plan.id()),!pending&&!plan.completed());
            button(label("cancel"),x+w/2+4,by,w/2-12,()->send(ConstructionService.Action.CANCEL,plan.id()),!pending);
        }
        int bottom=height-27;
        button(Component.literal("<"),x+8,bottom,24,()->{page--;rebuildWidgets();},page>0);
        button(Component.literal(">"),x+36,bottom,24,()->{page++;rebuildWidgets();},(page+1)*rows<state.plans().size());
        button(label("refresh"),x+68,bottom,Math.max(70,w/2-80),()->send(ConstructionService.Action.REFRESH,-1),!pending);
        button(Component.translatable("gui.done"),x+w/2+4,bottom,w/2-12,this::onClose,true);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        renderBackground(g);g.fill(x,y,x+w,height-4,0xEC1C2429);g.drawCenteredString(font,title,width/2,y+7,0xF4E5BE);
        g.drawString(font,label("cottage"),x+8,y+23,0xE7C46A,false);
        int row=0;for(var material:CottageBlueprint.materials().entrySet()){
            g.drawString(font,Component.translatable("construction.stonebanner.ui.material",material.getKey().getDescription(),material.getValue()),x+8+(row%2)*(w/2),y+39+(row/2)*12,0xD8D2C8,false);row++;
        }
        for(int i=0;i<rows&&page*rows+i<state.plans().size();i++){
            var plan=state.plans().get(page*rows+i);String status=plan.completed()?"complete":plan.paused()?"paused":plan.status();
            var text=label("plan",plan.id(),plan.origin().getX(),plan.origin().getY(),plan.origin().getZ(),plan.done(),CottageBlueprint.placements().size(),Component.translatable("construction.stonebanner.status."+status));
            g.drawString(font,font.plainSubstrByWidth(text.getString(),w-16),x+8,y+124+i*48,0xD8D2C8,false);
        }
        if(state.plans().isEmpty())g.drawWordWrap(font,label("hint"),x+8,y+124,w-16,0xD8D2C8);
        super.render(g,mx,my,delta);
    }
}
