package dev.stonebanner.network;

import dev.stonebanner.construction.ConstructionService;
import dev.stonebanner.network.packet.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionCodecTest {
    private final ResourceLocation dimension=new ResourceLocation("minecraft:overworld");
    @Test void snapshotAndEveryActionRoundTrip(){
        var packet=new ConstructionSnapshotPacket(dimension,true,"created",List.of(new ConstructionSnapshotPacket.Plan(7,new BlockPos(-2,66,9),Rotation.CLOCKWISE_90,false,false,12,"materials")));
        var b=new FriendlyByteBuf(Unpooled.buffer());try{
            ConstructionSnapshotPacket.encode(packet,b);assertEquals(packet,ConstructionSnapshotPacket.decode(b));assertEquals(0,b.readableBytes());
            for(var action:ConstructionService.Action.values()){
                b.clear();var command=new ConstructionActionPacket(dimension,action,BlockPos.ZERO,3,7);ConstructionActionPacket.encode(command,b);assertEquals(command,ConstructionActionPacket.decode(b));assertEquals(0,b.readableBytes());
            }
        }finally{b.release();}
    }
    @Test void oversizedAndNegativeCountsRejectedBeforeAllocation(){
        for(int count:new int[]{-1,65}){var b=new FriendlyByteBuf(Unpooled.buffer());try{b.writeResourceLocation(dimension);b.writeBoolean(false);b.writeUtf("ready",32);b.writeVarInt(count);assertThrows(IllegalArgumentException.class,()->ConstructionSnapshotPacket.decode(b));}finally{b.release();}}
    }
    @Test void selectedGeometryAndRejectedCatalogEntryRoundTrip(){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        // Plain JUnit does not run Forge's block-state registry population lifecycle.
        // Populate vanilla states here; runtime uses Forge's synchronized numeric IDs.
        if(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.size()==0)
            for(var block:net.minecraft.core.registries.BuiltInRegistries.BLOCK)
                for(var state:block.getStateDefinition().getPossibleStates())
                    net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.add(state);
        var blueprint=dev.stonebanner.construction.BuildingBlueprint.cottage();
        var packet=new ConstructionSnapshotPacket(dimension,true,"ready",List.of(),List.of(
            new ConstructionSnapshotPacket.Entry(blueprint.id(),blueprint.title(),""),
            new ConstructionSnapshotPacket.Entry("local:bad.blueprint","Bad house","Unsupported block")),blueprint);
        var b=new FriendlyByteBuf(Unpooled.buffer());try{
            ConstructionSnapshotPacket.encode(packet,b);assertEquals(packet,ConstructionSnapshotPacket.decode(b));assertEquals(0,b.readableBytes());
            b.clear();var command=new ConstructionActionPacket(dimension,ConstructionService.Action.CREATE,new BlockPos(1,64,2),2,-1,"local:house.blueprint","immutable-hash");
            ConstructionActionPacket.encode(command,b);assertEquals(command,ConstructionActionPacket.decode(b));
        }finally{b.release();}
    }
}
