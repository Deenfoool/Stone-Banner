package dev.stonebanner.client.screen;

import dev.stonebanner.client.control.*;
import dev.stonebanner.control.HeroActionRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;

/** A snapshot menu: choosing one entry consumes the click and closes the menu. */
public final class ContextActionScreen extends Screen {
    private final Screen parent;
    private final HitResult hit;
    private final net.minecraft.client.multiplayer.ClientLevel world;
    private final java.util.UUID uuid;
    private final net.minecraft.world.level.block.state.BlockState blockState;
    private Component detail=Component.empty();
    public ContextActionScreen(Screen parent,HitResult hit){
        super(Component.translatable("screen.stonebanner.actions"));this.parent=parent;this.hit=hit;
        world=net.minecraft.client.Minecraft.getInstance().level;
        uuid=hit instanceof EntityHitResult e?e.getEntity().getUUID():null;
        blockState=hit instanceof BlockHitResult b?world.getBlockState(b.getBlockPos()):null;
    }
    private boolean valid(){
        if(minecraft.level!=world || minecraft.player==null || !minecraft.player.isAlive())return false;
        if(hit instanceof EntityHitResult e)return e.getEntity().isAlive() && uuid.equals(e.getEntity().getUUID())
                && world.getEntity(e.getEntity().getId())==e.getEntity();
        return hit instanceof BlockHitResult b && world.hasChunkAt(b.getBlockPos()) && blockState.equals(world.getBlockState(b.getBlockPos()));
    }
    @Override protected void init(){
        int x=width/2-90,y=height/2-68;
        addRenderableWidget(Button.builder(Component.translatable("screen.stonebanner.actions.inspect"),b->{
            if(!valid()){onClose();return;}
            detail=hit instanceof EntityHitResult e?e.getEntity().getName():blockState.getBlock().getName();
        }).bounds(x,y,180,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.stonebanner.actions.interact"),b->choose(()->{
            if(hit instanceof EntityHitResult e)PlayerCommandController.interactEntity(e.getEntity());
            else if(hit instanceof BlockHitResult h)PlayerCommandController.interactBlock(h);
        })).bounds(x,y+24,180,20).build());
        var attack=addRenderableWidget(Button.builder(Component.translatable("screen.stonebanner.actions.attack"),b->choose(()->{
            if(hit instanceof EntityHitResult e)PlayerCommandController.attack(e.getEntity());
        })).bounds(x,y+48,180,20).build());
        attack.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.stonebanner.actions.attack_hint")));
        attack.active=!HeroInputController.commandMode() && hit instanceof EntityHitResult e && HeroActionRules.canAttack(minecraft.player,e.getEntity());
        var work=addRenderableWidget(Button.builder(Component.translatable("screen.stonebanner.actions.work"),b->choose(()->{
            if(hit instanceof BlockHitResult h)CitizenSelectionController.workSelected(h);
        })).bounds(x,y+72,180,20).build());
        work.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.stonebanner.actions.work_hint")));
        work.active=hit instanceof BlockHitResult && CitizenSelectionController.hasSelection()
                && (blockState.is(net.minecraft.tags.BlockTags.LOGS)||blockState.is(net.minecraftforge.common.Tags.Blocks.ORES));
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x,y+96,180,20).build());
    }
    private void choose(Runnable action){boolean allowed=valid();minecraft.setScreen(parent);if(allowed)action.run();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        g.fill(width/2-100,height/2-96,width/2+100,height/2+84,0xEE201E1B);
        g.drawCenteredString(font,title,width/2,height/2-87,0xFFE2C18A);
        super.render(g,x,y,partial);g.drawCenteredString(font,detail,width/2,height/2+63,0xFFD8D2C8);
    }
}
