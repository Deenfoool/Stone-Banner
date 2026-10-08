package dev.stonebanner.construction;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MineColoniesBlueprintReaderTest {
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    private CompoundTag root(int x,int y,int z,String... names){
        var root=new CompoundTag();root.putInt("version",1);root.putInt("size_x",x);root.putInt("size_y",y);root.putInt("size_z",z);
        var palette=new ListTag();for(var name:names){var entry=new CompoundTag();entry.putString("Name",name);palette.add(entry);}root.put("palette",palette);return root;
    }
    private BuildingBlueprint read(CompoundTag root){return MineColoniesBlueprintReader.decode("test:house","Test",root,"hash");}
    @Test void indicesAreUnsignedHighThenLowXThenZThenYOddPaddingIgnored(){
        var root=root(3,1,1,"minecraft:air","minecraft:oak_planks","minecraft:cobblestone");root.putIntArray("blocks",new int[]{(1<<16)|2,(1<<16)|65535});
        var b=read(root);assertEquals(List.of(new BlockPos(0,0,0),new BlockPos(1,0,0),new BlockPos(2,0,0)),b.placements().stream().map(p->p.cells().get(0).offset()).toList());
        assertEquals(2,b.materials().get(Items.OAK_PLANKS));assertEquals(1,b.materials().get(Items.COBBLESTONE));
        root=root(2,2,2,"minecraft:oak_planks");root.putIntArray("blocks",new int[4]);
        assertEquals(List.of(new BlockPos(0,0,0),new BlockPos(1,0,0),new BlockPos(0,0,1),new BlockPos(1,0,1),new BlockPos(0,1,0),new BlockPos(1,1,0),new BlockPos(0,1,1),new BlockPos(1,1,1)),read(root).placements().stream().map(p->p.cells().get(0).offset()).toList());
    }
    @Test void specialMarkersHaveExplicitPreserveAirAndPaidSolidSemantics(){
        var r=root(3,1,1,"structurize:blocksubstitution","minecolonies:blockhutcitizen","structurize:blocksolidsubstitution");r.putIntArray("blocks",new int[]{1,2<<16});var b=read(r);
        assertEquals(Set.of(BlockPos.ZERO),b.preserved());assertEquals(1,b.placements().size());assertEquals(Items.COBBLESTONE,b.placements().get(0).item());assertTrue(b.note().contains("hut"));
    }
    @Test void invalidPaletteUnknownBlocksCommandsAndBoundsAreRejected(){
        var r=root(1,1,1,"minecraft:oak_planks");r.putIntArray("blocks",new int[]{1<<16});assertThrows(IllegalArgumentException.class,()->read(r));
        for(var name:List.of("other:unknown","minecraft:not_a_block","minecraft:command_block","minecraft:water","structurize:blockfluidsubstitution")){
            var bad=root(1,1,1,name);bad.putIntArray("blocks",new int[1]);assertThrows(IllegalArgumentException.class,()->read(bad));
        }
        r.putInt("size_y",65);assertThrows(IllegalArgumentException.class,()->read(r));
        assertThrows(IOException.class,()->MineColoniesBlueprintReader.read("test:bad","Bad",new byte[]{1,2,3}));
        assertThrows(IOException.class,()->MineColoniesBlueprintReader.read("test:bad","Bad",new byte[MineColoniesBlueprintReader.MAX_BYTES+1]));
    }
    @Test void doubleSlabsRequireTwoRealItemsAndUnpairedDoorIsRejected(){
        var r=root(1,1,1,"minecraft:oak_slab");var props=new CompoundTag();props.putString("type","double");r.getList("palette",10).getCompound(0).put("Properties",props);r.putIntArray("blocks",new int[1]);assertEquals(2,read(r).materials().get(Items.OAK_SLAB));
        r=root(1,1,1,"minecraft:oak_door");r.putIntArray("blocks",new int[1]);var bad=r;assertThrows(IllegalArgumentException.class,()->read(bad));
    }
    @Test void originalMedievalOakHomesDecodeIntoBoundedVanillaGeometry()throws Exception {
        for(String name:List.of("residence1","residence2")){
            var path=Path.of("blueprint-packs/minecolonies-medievaloak/config/stonebanner/blueprints/minecolonies/medievaloak/fundamentals/"+name+".blueprint");
            var b=MineColoniesBlueprintReader.read("test:"+name,name,Files.readAllBytes(path));
            assertFalse(b.placements().isEmpty());assertFalse(b.preserved().isEmpty());assertTrue(b.materials().getOrDefault(Items.WHITE_BED,0)>0);assertTrue(b.note().contains("Vanilla adaptation"));
            var cells=new HashSet<BlockPos>();for(var p:b.placements())for(var c:p.cells()){assertTrue(cells.add(c.offset()));assertTrue(b.bounds(BlockPos.ZERO,net.minecraft.world.level.block.Rotation.NONE).contains(net.minecraft.world.phys.Vec3.atCenterOf(c.offset())));}
        }
    }
}
