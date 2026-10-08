package dev.stonebanner.client.control;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.construction.CottageBlueprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID,value=Dist.CLIENT)
public final class ConstructionPreviewRenderer {
    private ConstructionPreviewRenderer(){}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!ConstructionPreviewController.active()||ConstructionPreviewController.origin()==null)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var origin=ConstructionPreviewController.origin();var rotation=ConstructionPreviewController.rotation();
        var poses=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
        boolean allowed=ConstructionPreviewController.allowed();float red=allowed?.35f:1,green=allowed?.9f:.25f;
        poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
        LevelRenderer.renderLineBox(poses,lines,ConstructionPreviewController.blueprint().bounds(origin,rotation).inflate(.008),red,green,.35f,.5f);
        for(var placement:ConstructionPreviewController.blueprint().placements())for(var cell:placement.cells()){
            var at=cell.at(origin,rotation);var shape=cell.oriented(rotation).getShape(mc.level,at);
            for(var box:shape.toAabbs())LevelRenderer.renderLineBox(poses,lines,box.move(at).inflate(.002),red,green,.35f,.75f);
        }
        poses.popPose();buffers.endBatch(RenderType.lines());
    }
}
