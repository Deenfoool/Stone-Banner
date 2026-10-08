package dev.stonebanner.client.screen;

import dev.stonebanner.client.ClientKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Arrays;
import java.util.List;

/** Live key labels and conflicts; assignments are edited in the vanilla key-binding menu. */
public final class ControlBindingsScreen extends Screen {
    private final Screen parent;
    private List<KeyMapping> bindings=List.of();
    private int offset;
    public ControlBindingsScreen(Screen parent) { super(Component.translatable("controls.stonebanner.title"));this.parent=parent; }
    @Override protected void init() {
        bindings=Arrays.stream(minecraft.options.keyMappings).filter(k->k.getCategory().equals(ClientKeyMappings.CATEGORY)
                || k==minecraft.options.keyAttack || k==minecraft.options.keyUse || k==minecraft.options.keyJump
                || k==minecraft.options.keyShift || k==minecraft.options.keySprint || k==minecraft.options.keySwapOffhand
                || k==minecraft.options.keyInventory).toList();
        addRenderableWidget(Button.builder(Component.translatable("controls.stonebanner.edit"),b->minecraft.setScreen(
                new net.minecraft.client.gui.screens.controls.KeyBindsScreen(this,minecraft.options)))
                .bounds(width/2-154,height-28,150,20).build());
        addRenderableWidget(Button.builder(Component.translatable("settings.stonebanner.title"),
                b -> minecraft.setScreen(new StoneBannerSettingsScreen(this)))
                .bounds(width/2+4,height-28,150,20).build());
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double delta){
        offset=Math.max(0,Math.min(Math.max(0,bindings.size()-visibleRows()),offset+(delta>0?-1:1)));return true;
    }
    private int visibleRows(){return Math.max(1,(height-94)/26);}
    private String conflict(KeyMapping key) {
        if(key.getKey().equals(InputConstants.UNKNOWN))return "";
        return Arrays.stream(minecraft.options.keyMappings).filter(other->other!=key
                && other.getKey().equals(key.getKey()) && other.getKeyModifier()==key.getKeyModifier()
                // Jump and stop are intentionally in disjoint hero/orders contexts.
                && !(key==ClientKeyMappings.STOP && other==minecraft.options.keyJump)
                && !(other==ClientKeyMappings.STOP && key==minecraft.options.keyJump))
                .map(other->Component.translatable(other.getName()).getString()).reduce((a,b)->a+", "+b).orElse("");
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial) {
        renderBackground(g);
        g.drawCenteredString(font,title,width/2,10,0xFFFFFFFF);
        g.drawCenteredString(font,Component.translatable("controls.stonebanner.hint"),width/2,28,0xFFD8D2C8);
        for(int i=offset;i<Math.min(bindings.size(),offset+visibleRows());i++) {
            var key=bindings.get(i);int row=48+(i-offset)*26;
            g.drawString(font,Component.translatable(key.getName()),12,row,0xFFFFFFFF);
            g.drawString(font,key.getTranslatedKeyMessage(),width/2,row,0xFFE7C46A);
            String conflict=conflict(key);
            if(!conflict.isEmpty())g.drawString(font,font.plainSubstrByWidth(
                    Component.translatable("controls.stonebanner.conflict",conflict).getString(),width-24),12,row+11,0xFFFF8866);
        }
        super.render(g,x,y,partial);
    }
}
