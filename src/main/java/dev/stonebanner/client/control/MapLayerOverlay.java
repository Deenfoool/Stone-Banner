package dev.stonebanner.client.control;

import com.mojang.blaze3d.vertex.*;
import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.geology.FertilityRules;
import dev.stonebanner.network.packet.GeologySnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

/** Surface overlays respect depth: no underground blocks or full-chunk cuboids are highlighted. */
@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID,value=Dist.CLIENT)
public final class MapLayerOverlay {
    private record Cell(int x,int y,int z,int fertility){}
    private static final Map<Long,List<Cell>> SURFACE=new HashMap<>();
    private static long refreshed=Long.MIN_VALUE;
    private MapLayerOverlay(){}
    public static void clear(){SURFACE.clear();refreshed=Long.MIN_VALUE;}
    private static List<Cell> sample(Minecraft mc,int cx,int cz) {
        var cells=new ArrayList<Cell>();
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++) {
            int x=(cx<<4)+dx,z=(cz<<4)+dz;
            if(!mc.level.hasChunkAt(new BlockPos(x,0,z)))continue;
            int y=mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
            var pos=new BlockPos(x,y,z);var block=mc.level.getBlockState(pos);
            boolean farmland=block.is(Blocks.FARMLAND),soil=farmland||block.is(Blocks.DIRT)||block.is(Blocks.GRASS_BLOCK)||block.is(Blocks.ROOTED_DIRT);
            boolean water=false;
            if(soil)for(int wx=-4;wx<=4&&!water;wx+=2)for(int wz=-4;wz<=4;wz+=2) {
                var p=pos.offset(wx,0,wz);if(mc.level.hasChunkAt(p)&&(mc.level.getFluidState(p).is(FluidTags.WATER)||mc.level.getFluidState(p.above()).is(FluidTags.WATER))){water=true;break;}
            }
            var biome=mc.level.getBiome(pos).value();
            int score=FertilityRules.score(soil,farmland,farmland?block.getValue(FarmBlock.MOISTURE):0,biome.getModifiedClimateSettings().downfall(),biome.getBaseTemperature(),water);
            cells.add(new Cell(x,y,z,score));
        }return List.copyOf(cells);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();var state=MapLayerState.snapshot();if(mc.level==null||state==null||!mc.level.dimension().location().equals(state.dimension()))return;
        boolean resources=MapLayerState.enabled(MapLayerState.Layer.RESOURCES),fertility=MapLayerState.enabled(MapLayerState.Layer.FERTILITY),boundaries=MapLayerState.enabled(MapLayerState.Layer.BOUNDARIES);
        if(!resources&&!fertility&&!boundaries)return;
        long now=mc.level.getGameTime();
        if(refreshed==Long.MIN_VALUE||now<refreshed||now-refreshed>=40){SURFACE.clear();refreshed=now;}
        var camera=event.getCamera().getPosition();var poses=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
        poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
        if(resources||fertility) {
            var quads=buffers.getBuffer(RenderType.debugQuads());
            for(var tile:state.tiles()) {
                long key=net.minecraft.world.level.ChunkPos.asLong(tile.x(),tile.z());var cells=SURFACE.computeIfAbsent(key,k->sample(mc,tile.x(),tile.z()));
                for(var cell:cells) {
                    if(fertility&&cell.fertility==0)continue;
                    float r=fertility?1-cell.fertility/100f:tile.ready()&&tile.deposits()?1f:.5f;
                    float g=fertility?cell.fertility/100f:tile.richness()>=2?.75f:.5f;
                    float b=fertility?.15f:tile.ready()&&tile.deposits()?.2f:.5f;
                    float y=cell.y+1.015f;
                    quad(quads,poses.last().pose(),cell.x,y,cell.z,r,g,b);
                }
            }buffers.endBatch(RenderType.debugQuads());
            var lines=buffers.getBuffer(RenderType.lines());
            for(var tile:state.tiles())outline(mc,poses,lines,(tile.x()<<4),(tile.z()<<4),16,.8f,.7f,.3f);
            buffers.endBatch(RenderType.lines());
            // Limit labels to four nearest chunks so the strategic view stays readable.
            state.tiles().stream().sorted(Comparator.comparingDouble(t->camera.distanceToSqr((t.x()<<4)+8,camera.y,(t.z()<<4)+8))).limit(4).forEach(tile->{
                int x=(tile.x()<<4)+8,z=(tile.z()<<4)+8;
                if(!mc.level.hasChunkAt(new BlockPos(x,0,z)))return;
                int y=mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+3;
                var labels=new ArrayList<Component>();
                labels.add(Component.translatable("geology.stonebanner.chunk",tile.x(),tile.z()));
                if(fertility){var cells=SURFACE.get(net.minecraft.world.level.ChunkPos.asLong(tile.x(),tile.z()));int value=cells==null?0:(int)cells.stream().mapToInt(Cell::fertility).average().orElse(0);labels.add(Component.translatable("geology.stonebanner.fertility",value));}
                else labels.addAll(resourceLabels(tile));
                label(mc,poses,buffers,x,y,z,labels);
            });
        }
        if(boundaries) {
            var lines=buffers.getBuffer(RenderType.lines());
            for(var claim:state.claims())outline(mc,poses,lines,((claim.x()-1)<<4),((claim.z()-1)<<4),48,claim.own()?.3f:1f,claim.own()?1f:.35f,.55f);
            buffers.endBatch(RenderType.lines());
            for(var c:state.claims()) {
                var p=c.banner();if(!mc.level.hasChunkAt(p))continue;
                label(mc,poses,buffers,p.getX()+.5,p.getY()+3,p.getZ()+.5,List.of(Component.literal(c.name()),Component.translatable(c.active()?"geology.stonebanner.banner":"community.stonebanner.missing_banner")));
            }
        }
        poses.popPose();buffers.endBatch();
    }
    public static List<Component> resourceLabels(GeologySnapshotPacket.Tile tile) {
        if(!tile.ready())return List.of(Component.translatable("geology.stonebanner.pending"));
        if(!tile.deposits())return List.of(Component.translatable("geology.stonebanner.none"));
        if(tile.surveyed()==0)return List.of(Component.translatable("geology.stonebanner.signs"));
        if(tile.surveyed()==1)return List.of(Component.translatable("geology.stonebanner.total",Component.translatable("geology.stonebanner.richness."+tile.richness())));
        var labels=new ArrayList<Component>();
        for(var entry:tile.ores())labels.add(Component.translatable("geology.stonebanner.entry",Component.translatable(entry.ore().key()),Component.translatable(tile.surveyed()==3&&!entry.ore().ordinary()?"geology.stonebanner.detected":"geology.stonebanner.richness."+entry.richness())));
        if(labels.isEmpty())labels.add(Component.translatable("geology.stonebanner.no_known"));
        if(tile.surveyed()<4)labels.add(Component.translatable("geology.stonebanner.partial"));
        return List.copyOf(labels);
    }
    private static void quad(VertexConsumer v,Matrix4f m,float x,float y,float z,float r,float g,float b){v.vertex(m,x,y,z).color(r,g,b,.22f).endVertex();v.vertex(m,x,y,z+1).color(r,g,b,.22f).endVertex();v.vertex(m,x+1,y,z+1).color(r,g,b,.22f).endVertex();v.vertex(m,x+1,y,z).color(r,g,b,.22f).endVertex();}
    private static void outline(Minecraft mc,PoseStack poses,VertexConsumer lines,int x,int z,int size,float r,float g,float b){for(int i=0;i<size;i+=2){edge(mc,poses,lines,x+i,z,2,0,r,g,b);edge(mc,poses,lines,x+i,z+size,2,0,r,g,b);edge(mc,poses,lines,x,z+i,0,2,r,g,b);edge(mc,poses,lines,x+size,z+i,0,2,r,g,b);}}
    private static void edge(Minecraft mc,PoseStack poses,VertexConsumer lines,int x,int z,int dx,int dz,float r,float g,float b){var p=new BlockPos(x,0,z);if(!mc.level.hasChunkAt(p)||!mc.level.hasChunkAt(p.offset(dx,0,dz)))return;double y=mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+.05;LevelRenderer.renderLineBox(poses,lines,new AABB(x,y,z,x+dx,y+.08,z+dz),r,g,b,1f);}
    private static void label(Minecraft mc,PoseStack poses,MultiBufferSource buffers,double x,double y,double z,List<Component> labels){poses.pushPose();poses.translate(x,y,z);poses.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());poses.scale(-.035f,-.035f,.035f);int row=0;for(var text:labels){mc.font.drawInBatch(text,-mc.font.width(text)/2f,row,0xFFEFE8D6,false,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0x99000000,15728880);row+=11;}poses.popPose();}
}
