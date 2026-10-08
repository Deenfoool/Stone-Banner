package dev.stonebanner.construction;

import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** A vertical jump-built column with a recoverable ladder, followed by a horizontal footbridge. */
public record ScaffoldRoute(BlockPos base,int topY,Direction side,List<BlockPos> bridge) {
    public static final int MAX_HEIGHT=72,MAX_BRIDGE=96,MAX_BLOCKS=256;
    public ScaffoldRoute {
        base=base.immutable();bridge=List.copyOf(bridge);
        if(side.getAxis()==Direction.Axis.Y||topY<base.getY()||topY-base.getY()>=MAX_HEIGHT||bridge.size()>MAX_BRIDGE)
            throw new IllegalArgumentException("Invalid scaffold route");
        var previous=new BlockPos(base.getX(),topY,base.getZ());var seen=new HashSet<BlockPos>();seen.add(previous);
        for(var p:bridge){if(p.getY()!=topY||p.distManhattan(previous)!=1||!seen.add(p)||p.equals(new BlockPos(base.getX(),topY,base.getZ()).relative(side)))throw new IllegalArgumentException("Disconnected scaffold bridge");previous=p;}
        if((topY-base.getY()+1)*2+bridge.size()>MAX_BLOCKS)throw new IllegalArgumentException("Scaffold route too large");
    }
    public boolean contains(BlockPos p){return p.getY()>=base.getY()&&p.getY()<=topY&&(p.getX()==base.getX()&&p.getZ()==base.getZ()||p.getX()==base.getX()+side.getStepX()&&p.getZ()==base.getZ()+side.getStepZ())||bridge.contains(p);}
    public BlockPos columnTop(){return new BlockPos(base.getX(),topY,base.getZ());}
    public BlockPos end(){return bridge.isEmpty()?columnTop():bridge.get(bridge.size()-1);}
    public LinkedHashMap<BlockPos,BlockState> blocks(){
        var out=new LinkedHashMap<BlockPos,BlockState>();
        for(int y=base.getY();y<=topY;y++){
            var p=new BlockPos(base.getX(),y,base.getZ());out.put(p,Blocks.COBBLESTONE.defaultBlockState());
            out.put(p.relative(side),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,side));
        }
        for(var p:bridge)out.put(p,Blocks.COBBLESTONE.defaultBlockState());return out;
    }
}
