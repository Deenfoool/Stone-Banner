package dev.stonebanner.client.screen;

import dev.stonebanner.client.control.CitizenSelectionController;
import dev.stonebanner.geology.GeologyService;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.GeologySnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GeologyResearchScreen extends Screen {
    private GeologySnapshotPacket state;
    private final Screen parent;
    private int ticks;
    private GeologyResearchScreen(GeologySnapshotPacket state,Screen parent){super(Component.translatable("geology.stonebanner.research"));this.state=state;this.parent=parent;}
    public static void open(GeologySnapshotPacket state){var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(state.dimension()))return;if(mc.screen instanceof GeologyResearchScreen screen&&screen.state.table().equals(state.table())){screen.state=state;screen.rebuildWidgets();}else mc.setScreen(new GeologyResearchScreen(state,mc.screen));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override protected void init(){int x=width/2-150,y=Math.max(32,height/2-90);
        var start=addRenderableWidget(Button.builder(Component.translatable(state.remaining()>0?"geology.stonebanner.resume":"geology.stonebanner.start"),b->send(GeologyService.Action.START)).bounds(x,y+116,300,20).build());start.active=state.tier()<4;
        var assign=addRenderableWidget(Button.builder(Component.translatable("geology.stonebanner.assign"),b->send(GeologyService.Action.ASSIGN)).bounds(x,y+140,300,20).build());assign.active=CitizenSelectionController.selected().isPresent();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x,y+164,300,20).build());
    }
    private void send(GeologyService.Action action){StoneBannerNetwork.sendGeologyAction(state.table(),action,CitizenSelectionController.selected().map(n->n.getId()).orElse(-1));}
    @Override public void tick(){if(++ticks%40==0)send(GeologyService.Action.OPEN);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g);int x=width/2-150,y=Math.max(32,height/2-90);g.drawCenteredString(font,title,width/2,y-18,0xFFE7C46A);
        g.drawString(font,Component.translatable("geology.stonebanner.stage",state.tier(),4),x,y,0xFFFFFFFF);
        g.drawString(font,Component.translatable("geology.stonebanner.tier."+state.tier()),x,y+16,0xFFE7C46A);
        int next=Math.min(4,state.tier()+1);
        if(state.tier()<4){g.drawString(font,Component.translatable("geology.stonebanner.next",Component.translatable("geology.stonebanner.tier."+next)),x,y+34,0xFFD8D2C8);g.drawString(font,Component.translatable("geology.stonebanner.cost",8*next,Component.translatable("geology.stonebanner.extra."+next)),x,y+50,0xFFD8D2C8);}
        g.drawString(font,Component.translatable("geology.stonebanner.progress",state.remaining()),x,y+68,0xFFD8D2C8);
        g.drawString(font,Component.translatable("geology.stonebanner.stay"),x,y+84,0xFFD8D2C8);
        g.drawString(font,Component.translatable("geology.stonebanner.survey_hint"),x,y+100,0xFFD8D2C8);super.render(g,mx,my,partial);
    }
}
