package dev.stonebanner.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Immutable, server-selected geometry. Preserved cells are never modified or charged. */
public record BuildingBlueprint(String id,String title,String fingerprint,int sizeX,int sizeY,int sizeZ,
        List<CottageBlueprint.Placement> placements,Set<BlockPos> preserved,String note) {
    public static final int MAX_VOLUME=16384,MAX_PLACEMENTS=4096;
    public BuildingBlueprint {
        placements=List.copyOf(placements);preserved=Set.copyOf(preserved);
        if(sizeX<1||sizeY<1||sizeZ<1||sizeX>64||sizeY>64||sizeZ>64||(long)sizeX*sizeY*sizeZ>MAX_VOLUME||placements.size()>MAX_PLACEMENTS)
            throw new IllegalArgumentException("Blueprint dimensions exceed limits");
    }
    public static BuildingBlueprint cottage(){return new BuildingBlueprint("stonebanner:cottage","Cottage","cottage-v1",5,5,7,CottageBlueprint.placements(),Set.of(),"");}
    public AABB bounds(BlockPos origin,Rotation rotation){return bounds(origin,rotation,sizeX,sizeY,sizeZ);}
    public static AABB bounds(BlockPos origin,Rotation rotation,int x,int y,int z){
        var a=origin;var b=origin.offset(new BlockPos(x-1,y-1,z-1).rotate(rotation));
        return new AABB(Math.min(a.getX(),b.getX()),origin.getY(),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,origin.getY()+y,Math.max(a.getZ(),b.getZ())+1);
    }
    public Map<Item,Integer> materials(){var out=new LinkedHashMap<Item,Integer>();for(var p:placements)out.merge(p.item(),p.count(),Integer::sum);return Collections.unmodifiableMap(out);}
}
