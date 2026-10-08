package dev.stonebanner.network.packet;

import dev.stonebanner.construction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

public record ConstructionSnapshotPacket(ResourceLocation dimension,boolean opening,String result,List<Plan> plans,List<Entry> catalog,BuildingBlueprint selected){
    public record Plan(long id,BlockPos origin,Rotation rotation,boolean paused,boolean completed,int done,String status,String title,int total){
        public Plan(long id,BlockPos origin,Rotation rotation,boolean paused,boolean completed,int done,String status){this(id,origin,rotation,paused,completed,done,status,"Cottage",131);}
    }
    public record Entry(String id,String title,String problem){}
    public ConstructionSnapshotPacket(ResourceLocation dimension,boolean opening,String result,List<Plan> plans){this(dimension,opening,result,plans,List.of(),null);}
    public ConstructionSnapshotPacket{plans=List.copyOf(plans);catalog=List.copyOf(catalog);if(plans.size()>ConstructionData.LIMIT||catalog.size()>BlueprintCatalog.LIMIT)throw new IllegalArgumentException("Construction snapshot too large");}
    public static ConstructionSnapshotPacket forPlayer(ServerPlayer player,boolean opening,String result){return forPlayer(player,opening,result,"stonebanner:cottage");}
    public static ConstructionSnapshotPacket forPlayer(ServerPlayer player,boolean opening,String result,String selectedId){
        var level=player.serverLevel();var catalog=BlueprintCatalog.forLevel(level);
        return new ConstructionSnapshotPacket(level.dimension().location(),opening,result,ConstructionData.forLevel(level).plans().stream().filter(p->p.owner.equals(player.getUUID()))
            .map(p->{var blueprint=BlueprintCatalog.forPlan(level,p);return new Plan(p.id,p.origin,p.rotation,p.paused,p.completed,blueprint==null?0:(int)blueprint.placements().stream().filter(v->v.matches(level,p.origin,p.rotation)).count(),p.status,blueprint==null?p.blueprintId:blueprint.title(),blueprint==null?0:blueprint.placements().size());}).toList(),
            catalog.entries().stream().map(e->new Entry(e.id(),e.title(),e.problem())).toList(),catalog.get(selectedId));
    }
    public static void encode(ConstructionSnapshotPacket p,FriendlyByteBuf b){
        b.writeResourceLocation(p.dimension);b.writeBoolean(p.opening);b.writeUtf(p.result,32);b.writeVarInt(p.plans.size());
        for(var v:p.plans){b.writeLong(v.id);b.writeBlockPos(v.origin);b.writeEnum(v.rotation);b.writeBoolean(v.paused);b.writeBoolean(v.completed);b.writeVarInt(v.done);b.writeUtf(v.status,32);b.writeUtf(v.title,256);b.writeVarInt(v.total);}
        b.writeVarInt(p.catalog.size());for(var e:p.catalog){b.writeUtf(e.id,256);b.writeUtf(e.title,160);b.writeUtf(e.problem,256);}
        b.writeBoolean(p.selected!=null);if(p.selected!=null)writeBlueprint(b,p.selected);
    }
    public static ConstructionSnapshotPacket decode(FriendlyByteBuf b){
        var dimension=b.readResourceLocation();boolean opening=b.readBoolean();String result=b.readUtf(32);int count=count(b,ConstructionData.LIMIT);
        var plans=new ArrayList<Plan>();for(int i=0;i<count;i++)plans.add(new Plan(b.readLong(),b.readBlockPos(),b.readEnum(Rotation.class),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readUtf(32),b.readUtf(256),b.readVarInt()));
        int size=count(b,BlueprintCatalog.LIMIT);var entries=new ArrayList<Entry>();for(int i=0;i<size;i++)entries.add(new Entry(b.readUtf(256),b.readUtf(160),b.readUtf(256)));
        return new ConstructionSnapshotPacket(dimension,opening,result,plans,entries,b.readBoolean()?readBlueprint(b):null);
    }
    private static int count(FriendlyByteBuf b,int maximum){int n=b.readVarInt();if(n<0||n>maximum)throw new IllegalArgumentException("Invalid construction count");return n;}
    private static void writeBlueprint(FriendlyByteBuf b,BuildingBlueprint v){
        b.writeUtf(v.id(),256);b.writeUtf(v.title(),160);b.writeUtf(v.fingerprint(),96);b.writeVarInt(v.sizeX());b.writeVarInt(v.sizeY());b.writeVarInt(v.sizeZ());b.writeUtf(v.note(),2048);
        b.writeVarInt(v.preserved().size());for(var pos:v.preserved())b.writeBlockPos(pos);
        b.writeVarInt(v.placements().size());for(var p:v.placements()){
            b.writeResourceLocation(BuiltInRegistries.ITEM.getKey(p.item()));b.writeVarInt(p.count());b.writeVarInt(p.cells().size());
            for(var c:p.cells()){b.writeBlockPos(c.offset());b.writeVarInt(Block.getId(c.state()));}
        }
    }
    private static BuildingBlueprint readBlueprint(FriendlyByteBuf b){
        String id=b.readUtf(256),title=b.readUtf(160),hash=b.readUtf(96);int x=count(b,64),y=count(b,64),z=count(b,64);String note=b.readUtf(2048);
        if(x<1||y<1||z<1||(long)x*y*z>BuildingBlueprint.MAX_VOLUME)throw new IllegalArgumentException("Invalid blueprint bounds");
        int n=count(b,BuildingBlueprint.MAX_VOLUME);var occupied=new HashSet<BlockPos>();var preserved=new HashSet<BlockPos>();
        for(int i=0;i<n;i++){var pos=position(b,x,y,z);if(!occupied.add(pos))throw new IllegalArgumentException("Duplicate blueprint cell");preserved.add(pos);}
        n=count(b,BuildingBlueprint.MAX_PLACEMENTS);var placements=new ArrayList<CottageBlueprint.Placement>();
        for(int i=0;i<n;i++){
            var key=b.readResourceLocation();if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Unknown blueprint item");var item=BuiltInRegistries.ITEM.get(key);int cost=count(b,64),cells=count(b,2);var group=new ArrayList<CottageBlueprint.Cell>();
            for(int c=0;c<cells;c++){
                var pos=position(b,x,y,z);int stateId=b.readVarInt();var state=Block.stateById(stateId);
                if(stateId<0||Block.getId(state)!=stateId||state.isAir())throw new IllegalArgumentException("Invalid blueprint state ID: " + stateId);
                if(!occupied.add(pos))throw new IllegalArgumentException("Duplicate blueprint cell: " + pos);
                group.add(new CottageBlueprint.Cell(pos,state));
            }
            placements.add(new CottageBlueprint.Placement(item,cost,group));
        }
        return new BuildingBlueprint(id,title,hash,x,y,z,placements,preserved,note);
    }
    private static BlockPos position(FriendlyByteBuf b,int x,int y,int z){var p=b.readBlockPos();if(p.getX()<0||p.getX()>=x||p.getY()<0||p.getY()>=y||p.getZ()<0||p.getZ()>=z)throw new IllegalArgumentException("Blueprint cell outside bounds");return p;}
    public static void handle(ConstructionSnapshotPacket p,Supplier<NetworkEvent.Context> supplier){
        var ctx=supplier.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->dev.stonebanner.client.network.ClientScreenPacketHandlers.construction(p)));ctx.setPacketHandled(true);
    }
}
