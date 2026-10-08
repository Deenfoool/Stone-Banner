package dev.stonebanner.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class CottageBlueprintTest {
    @Test void everyRotationHasUniqueCellsInsideBoundsAndOneItemPerFurniture(){
        for(var rotation:Rotation.values()){
            var origin=new BlockPos(-14,65,9);var cells=new HashSet<BlockPos>();var bounds=CottageBlueprint.bounds(origin,rotation);
            for(var placement:CottageBlueprint.placements())for(var cell:placement.cells()){
                var at=cell.at(origin,rotation);assertTrue(cells.add(at),"Duplicate cell "+at);
                assertTrue(bounds.contains(net.minecraft.world.phys.Vec3.atCenterOf(at)));assertEquals(cell.state().getBlock(),cell.oriented(rotation).getBlock());
            }
            assertEquals(134,cells.size());
        }
        assertEquals(2,CottageBlueprint.materials().get(Items.WHITE_BED));assertEquals(1,CottageBlueprint.materials().get(Items.OAK_DOOR));
        assertEquals(73,CottageBlueprint.materials().get(Items.OAK_PLANKS));assertEquals(12,CottageBlueprint.materials().get(Items.OAK_LOG));
        assertEquals(8,CottageBlueprint.materials().get(Items.GLASS_PANE));assertEquals(35,CottageBlueprint.materials().get(Items.OAK_SLAB));
    }
    @Test void completedFurnitureCanOpenOrBeOccupiedButCannotFaceWrongWay(){
        var door=Blocks.OAK_DOOR.defaultBlockState();assertTrue(CottageBlueprint.same(door.setValue(DoorBlock.OPEN,true),door));
        assertFalse(CottageBlueprint.same(door.rotate(Rotation.CLOCKWISE_90),door));
        var bed=Blocks.WHITE_BED.defaultBlockState();assertTrue(CottageBlueprint.same(bed.setValue(BedBlock.OCCUPIED,true),bed));
        assertFalse(CottageBlueprint.same(Blocks.RED_BED.defaultBlockState(),bed));
    }
}
