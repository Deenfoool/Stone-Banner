package dev.stonebanner.network.packet;

import dev.stonebanner.geology.GeologyRules;
import dev.stonebanner.client.control.MapLayerState;
import dev.stonebanner.client.screen.GeologyResearchScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Contains only permitted categorical information. No raw ore counts or underground positions. */
public record GeologySnapshotPacket(ResourceLocation dimension,boolean research,BlockPos table,int tier,int remaining,List<Tile> tiles,List<Claim> claims) {
    public GeologySnapshotPacket {if(tier<0||tier>4||remaining<0||remaining>120||tiles.size()>9||claims.size()>128)throw new IllegalArgumentException("Geology snapshot");tiles=List.copyOf(tiles);claims=List.copyOf(claims);table=table.immutable();}
    public record Tile(int x,int z,boolean ready,boolean deposits,int surveyed,int richness,List<GeologyRules.Entry> ores) {
        public Tile {if(surveyed<0||surveyed>4||richness<0||richness>3||ores.size()>GeologyRules.Ore.values().length)throw new IllegalArgumentException("Geology tile");ores=List.copyOf(ores);}
    }
    public record Claim(String name,int x,int z,BlockPos banner,boolean own,boolean active){}
    public static GeologySnapshotPacket research(ResourceLocation dimension,BlockPos table,int tier,int remaining){return new GeologySnapshotPacket(dimension,true,table,tier,remaining,List.of(),List.of());}
    public static void encode(GeologySnapshotPacket p,FriendlyByteBuf b) {
        b.writeResourceLocation(p.dimension);b.writeBoolean(p.research);b.writeBlockPos(p.table);b.writeVarInt(p.tier);b.writeVarInt(p.remaining);b.writeVarInt(p.tiles.size());
        for(var t:p.tiles){b.writeInt(t.x);b.writeInt(t.z);b.writeBoolean(t.ready);b.writeBoolean(t.deposits);b.writeVarInt(t.surveyed);b.writeVarInt(t.richness);b.writeVarInt(t.ores.size());for(var ore:t.ores){b.writeEnum(ore.ore());b.writeVarInt(ore.richness());}}
        b.writeVarInt(p.claims.size());for(var c:p.claims){b.writeUtf(c.name,32);b.writeInt(c.x);b.writeInt(c.z);b.writeBlockPos(c.banner);b.writeBoolean(c.own);b.writeBoolean(c.active);}
    }
    private static int bounded(FriendlyByteBuf b,int max){int n=b.readVarInt();if(n<0||n>max)throw new IllegalArgumentException("Geology list limit");return n;}
    public static GeologySnapshotPacket decode(FriendlyByteBuf b) {
        var dim=b.readResourceLocation();boolean research=b.readBoolean();var table=b.readBlockPos();int tier=bounded(b,4),remaining=bounded(b,120);int n=bounded(b,9);var tiles=new ArrayList<Tile>();
        for(int i=0;i<n;i++){int x=b.readInt(),z=b.readInt();boolean ready=b.readBoolean(),deposits=b.readBoolean();int surveyed=bounded(b,4),richness=bounded(b,3),m=bounded(b,GeologyRules.Ore.values().length);var ores=new ArrayList<GeologyRules.Entry>();for(int j=0;j<m;j++)ores.add(new GeologyRules.Entry(b.readEnum(GeologyRules.Ore.class),b.readVarInt()));tiles.add(new Tile(x,z,ready,deposits,surveyed,richness,ores));}
        n=bounded(b,128);var claims=new ArrayList<Claim>();for(int i=0;i<n;i++)claims.add(new Claim(b.readUtf(32),b.readInt(),b.readInt(),b.readBlockPos(),b.readBoolean(),b.readBoolean()));
        return new GeologySnapshotPacket(dim,research,table,tier,remaining,tiles,claims);
    }
    public static void handle(GeologySnapshotPacket p,Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->{if(p.research)GeologyResearchScreen.open(p);else MapLayerState.accept(p);}));ctx.setPacketHandled(true);}
}
