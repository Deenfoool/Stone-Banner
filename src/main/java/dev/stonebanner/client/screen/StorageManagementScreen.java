package dev.stonebanner.client.screen;

import dev.stonebanner.client.control.HeroInputController;
import dev.stonebanner.client.control.PlayerCommandController;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.StorageManagementSnapshotPacket;
import dev.stonebanner.storage.StorageManagementService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

public final class StorageManagementScreen extends Screen {
    private StorageManagementSnapshotPacket state;
    private final Screen parent;
    private boolean pending;
    private int ticks,x,y,w;
    private StorageManagementScreen(StorageManagementSnapshotPacket state,Screen parent) {
        super(label("title"));this.state=state;this.parent=parent;
    }
    private static Component label(String key,Object... args) {
        return Component.translatable("storage.stonebanner.ui."+key,args);
    }
    public static void open(StorageManagementSnapshotPacket p) {
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(p.dimension()))return;
        if(mc.screen instanceof StorageManagementScreen screen) {
            if(!screen.state.target().equals(p.target()))return;
            screen.state=p;screen.pending=false;screen.rebuildWidgets();return;
        }
        if(!p.opening())return;
        HeroInputController.cancel();PlayerCommandController.stop();
        mc.setScreen(new StorageManagementScreen(p,mc.screen));
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void tick(){
        if(minecraft.level==null||minecraft.player==null||!minecraft.player.isAlive()
                ||!minecraft.level.dimension().location().equals(state.dimension())
                ||!minecraft.level.hasChunkAt(state.target())||!StorageManagementService.supported(minecraft.level,state.target())
                ||!minecraft.player.canReach(state.target(),0)){onClose();return;}
        if(pending&&++ticks>=40){pending=false;rebuildWidgets();}
    }
    private void send(StorageManagementService.Action action){
        pending=true;ticks=0;StoneBannerNetwork.sendStorageManagementAction(state.dimension(),state.target(),action);rebuildWidgets();
    }
    private void button(Component text,int by,Runnable action,boolean active){
        var b=addRenderableWidget(Button.builder(text,ignored->action.run()).bounds(x+8,by,w-16,20).build());b.active=active;
    }
    @Override protected void init(){
        w=Math.min(360,width-16);x=(width-w)/2;y=Math.max(6,(height-218)/2);
        button(label("register"),y+122,()->send(StorageManagementService.Action.REGISTER),!pending&&state.manageable()&&state.registered()<state.members());
        button(label("unregister"),y+146,()->send(StorageManagementService.Action.UNREGISTER),!pending&&state.manageable()&&state.registered()>0);
        button(label("refresh"),y+170,()->send(StorageManagementService.Action.REFRESH),!pending);
        button(Component.translatable("gui.done"),y+194,this::onClose,true);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        renderBackground(g);g.fill(x,y,x+w,y+218,0xEC1C2429);
        g.drawCenteredString(font,title,width/2,y+8,0xF4E5BE);
        g.drawString(font,label("position",state.target().getX(),state.target().getY(),state.target().getZ()),x+8,y+26,0xE6DCC9,false);
        String status=state.registered()==0?"inactive":state.registered()==state.members()?"active":"partial";
        g.drawString(font,label(status),x+8,y+40,0xE7C46A,false);
        g.drawString(font,label("contents",state.items(),state.occupied(),state.slots()),x+8,y+54,0xE6DCC9,false);
        g.drawWordWrap(font,label(state.manageable()?"shared_hint":"readonly_hint"),x+8,y+70,w-16,0xB8B8B8);
        if(state.result()!=StorageManagementService.Result.READY)
            g.drawString(font,Component.translatable("storage.stonebanner.result."+state.result().name().toLowerCase(Locale.ROOT)),x+8,y+108,0xE7C46A,false);
        super.render(g,mx,my,delta);
    }
}
