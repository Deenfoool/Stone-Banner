package dev.stonebanner.client.control;

import dev.stonebanner.StoneAndBanner;
import dev.stonebanner.network.StoneBannerNetwork;
import dev.stonebanner.network.packet.GeologySnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=StoneAndBanner.MOD_ID,value=Dist.CLIENT)
public final class MapLayerState {
    public enum Layer { BOUNDARIES, RESOURCES, FERTILITY;
        public String key(){return "geology.stonebanner.layer."+name().toLowerCase(Locale.ROOT);}
    }
    private static final EnumSet<Layer> ENABLED=EnumSet.noneOf(Layer.class);
    private static ResourceLocation dimension;
    private static GeologySnapshotPacket snapshot;
    private static int ticks;
    private MapLayerState(){}
    public static boolean enabled(Layer layer){return ENABLED.contains(layer);}
    public static void toggle(Layer layer) {
        if(!ENABLED.remove(layer)) {
            if(layer==Layer.RESOURCES)ENABLED.remove(Layer.FERTILITY);
            if(layer==Layer.FERTILITY)ENABLED.remove(Layer.RESOURCES);
            ENABLED.add(layer);
        }
        ticks=40;
    }
    public static GeologySnapshotPacket snapshot(){return snapshot;}
    public static void accept(GeologySnapshotPacket p){var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.dimension().location().equals(p.dimension())){dimension=p.dimension();snapshot=p;}}
    public static void clear(){ENABLED.clear();snapshot=null;dimension=null;ticks=0;MapLayerOverlay.clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        var current=mc.level.dimension().location();if(dimension!=null&&!dimension.equals(current)){snapshot=null;MapLayerOverlay.clear();}dimension=current;
        if(ENABLED.isEmpty()||++ticks<40)return;ticks=0;
        var camera=mc.gameRenderer.getMainCamera().getPosition();int px=mc.player.blockPosition().getX()>>4,pz=mc.player.blockPosition().getZ()>>4;
        int cx=Math.max(px-4,Math.min(px+4,((int)Math.floor(camera.x))>>4)),cz=Math.max(pz-4,Math.min(pz+4,((int)Math.floor(camera.z))>>4));
        StoneBannerNetwork.requestMapLayers(new BlockPos(cx<<4,0,cz<<4));
    }
}
