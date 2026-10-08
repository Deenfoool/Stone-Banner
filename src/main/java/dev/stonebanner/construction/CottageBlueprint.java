package dev.stonebanner.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** One bounded, deterministic blueprint. Multi-block furniture costs one real item per placement. */
public final class CottageBlueprint {
    public record Cell(BlockPos offset,BlockState state) {
        public BlockPos at(BlockPos origin,Rotation rotation){return origin.offset(offset.rotate(rotation));}
        public BlockState oriented(Rotation rotation){return state.rotate(rotation);}
    }
    public record Placement(Item item,List<Cell> cells) {
        public Placement {cells=List.copyOf(cells);}
        public boolean matches(Level level,BlockPos origin,Rotation rotation){
            return cells.stream().allMatch(cell->level.hasChunkAt(cell.at(origin,rotation))
                    &&same(level.getBlockState(cell.at(origin,rotation)),cell.oriented(rotation)));
        }
    }
    private static final List<Placement> PLACEMENTS=create();
    private CottageBlueprint(){}
    public static List<Placement> placements(){return PLACEMENTS;}
    public static Map<Item,Integer> materials(){
        var result=new LinkedHashMap<Item,Integer>();for(var placement:PLACEMENTS)result.merge(placement.item(),1,Integer::sum);return Collections.unmodifiableMap(result);
    }
    public static AABB bounds(BlockPos origin,Rotation rotation){
        var a=origin.offset(new BlockPos(0,0,0).rotate(rotation));var b=origin.offset(new BlockPos(4,4,6).rotate(rotation));
        return new AABB(Math.min(a.getX(),b.getX()),origin.getY(),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,origin.getY()+5,Math.max(a.getZ(),b.getZ())+1);
    }
    public static boolean same(BlockState actual,BlockState expected){
        if(!actual.is(expected.getBlock()))return false;
        // Neighbor connections, open/powered doors and occupied beds are allowed to change after placement.
        for(Property<?> property:new Property<?>[]{BlockStateProperties.HORIZONTAL_FACING,BlockStateProperties.DOUBLE_BLOCK_HALF,
                BlockStateProperties.BED_PART,BlockStateProperties.SLAB_TYPE,BlockStateProperties.AXIS})
            if(expected.hasProperty(property)&&!Objects.equals(actual.getValue(property),expected.getValue(property)))return false;
        return true;
    }
    private static void add(List<Placement> out,Item item,BlockState state,int x,int y,int z){out.add(new Placement(item,List.of(new Cell(new BlockPos(x,y,z),state))));}
    private static boolean edge(int x,int z){return x==0||x==4||z==0||z==6;}
    private static boolean corner(int x,int z){return (x==0||x==4)&&(z==0||z==6);}
    private static boolean window(int x,int z){return (x==0||x==4)&&(z==2||z==4)||(z==0||z==6)&&(x==1||x==3);}
    private static void walls(List<Placement> out,int y){
        for(int z=0;z<7;z++)for(int x=0;x<5;x++)if(edge(x,z)&&!(x==2&&z==0&&y<=2)){
            if(corner(x,z))add(out,Items.OAK_LOG,Blocks.OAK_LOG.defaultBlockState(),x,y,z);
            else if(y==2&&window(x,z))add(out,Items.GLASS_PANE,Blocks.GLASS_PANE.defaultBlockState(),x,y,z);
            else add(out,Items.OAK_PLANKS,Blocks.OAK_PLANKS.defaultBlockState(),x,y,z);
        }
    }
    private static List<Placement> create(){
        var out=new ArrayList<Placement>();
        for(int z=0;z<7;z++)for(int x=0;x<5;x++)add(out,Items.OAK_PLANKS,Blocks.OAK_PLANKS.defaultBlockState(),x,0,z);
        walls(out,1);walls(out,2);
        // Leave the upper wall course open while the worker reaches the roof from the floor.
        for(int z=0;z<7;z++)for(int x=0;x<5;x++)add(out,Items.OAK_SLAB,Blocks.OAK_SLAB.defaultBlockState(),x,4,z);
        walls(out,3);
        for(int x:new int[]{1,3}){
            var foot=Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,BedPart.FOOT);
            out.add(new Placement(Items.WHITE_BED,List.of(new Cell(new BlockPos(x,1,4),foot),new Cell(new BlockPos(x,1,5),foot.setValue(BedBlock.PART,BedPart.HEAD)))));
        }
        var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH);
        out.add(new Placement(Items.OAK_DOOR,List.of(new Cell(new BlockPos(2,1,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER)),
                new Cell(new BlockPos(2,2,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER)))));
        return List.copyOf(out);
    }
}
