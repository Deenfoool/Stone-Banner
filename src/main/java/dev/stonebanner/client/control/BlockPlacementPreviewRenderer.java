package dev.stonebanner.client.control;

import dev.stonebanner.StoneAndBanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID,value=Dist.CLIENT)
public final class BlockPlacementPreviewRenderer {
    private BlockPlacementPreviewRenderer(){}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||ConstructionPreviewController.active()
                ||!(mc.screen instanceof dev.stonebanner.client.screen.TacticalControlScreen screen))return;
        var preview=BlockPlacementPreview.at(screen.hoveredHit());if(preview==null)return;
        var poses=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
        float red=preview.allowed()?.25f:1,green=preview.allowed()?.9f:.2f;
        var boxes=preview.state().getShape(mc.level,preview.pos()).toAabbs();
        if(boxes.isEmpty())boxes=java.util.List.of(new net.minecraft.world.phys.AABB(0,0,0,1,1,1));
        for(var box:boxes){var at=box.move(preview.pos());
            LevelRenderer.addChainedFilledBoxVertices(poses,buffers.getBuffer(RenderType.debugFilledBox()),
                    at.minX,at.minY,at.minZ,at.maxX,at.maxY,at.maxZ,red,green,.9f,.18f);
            LevelRenderer.renderLineBox(poses,buffers.getBuffer(RenderType.lines()),at.inflate(.003),red,green,.9f,.8f);
        }
        poses.popPose();buffers.endBatch(RenderType.debugFilledBox());buffers.endBatch(RenderType.lines());
    }
}
