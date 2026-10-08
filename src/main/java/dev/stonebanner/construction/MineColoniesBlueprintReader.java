package dev.stonebanner.construction;

import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Independent decoder for Structurize v1: x-fastest, then z, then y; two unsigned indices per int. */
public final class MineColoniesBlueprintReader {
    public static final int MAX_BYTES=2*1024*1024;
    private MineColoniesBlueprintReader(){}
    public static BuildingBlueprint read(String id,String title,byte[] compressed)throws IOException {
        if(compressed.length>MAX_BYTES)throw new IOException("Compressed blueprint exceeds 2 MiB");
        byte[] raw;
        try(var in=new GZIPInputStream(new ByteArrayInputStream(compressed))){raw=in.readNBytes(MAX_BYTES+1);}
        if(raw.length>MAX_BYTES)throw new IOException("Expanded blueprint exceeds 2 MiB");
        CompoundTag root=NbtIo.read(new DataInputStream(new ByteArrayInputStream(raw)),new NbtAccounter(MAX_BYTES));
        if(root==null)throw new IOException("Missing blueprint root");
        try{return decode(id,title,root,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}
    }
    static BuildingBlueprint decode(String id,String title,CompoundTag root,String fingerprint) {
        if(root.getInt("version")!=1)throw new IllegalArgumentException("Only Structurize blueprint version 1 is supported");
        int x=root.getInt("size_x"),y=root.getInt("size_y"),z=root.getInt("size_z");
        if(x<1||y<1||z<1||x>64||y>64||z>64||(long)x*y*z>BuildingBlueprint.MAX_VOLUME)throw new IllegalArgumentException("Blueprint dimensions exceed limits");
        int current=SharedConstants.getCurrentVersion().getDataVersion().getVersion(),source=root.getInt("mcversion");
        if(source<0||source>current)throw new IllegalArgumentException("Unsupported Minecraft data version: "+source);
        var palette=root.getList("palette",Tag.TAG_COMPOUND);var packed=root.getIntArray("blocks");int volume=x*y*z;
        if(palette.isEmpty()||palette.size()>65536||packed.length!=(volume+1)/2)throw new IllegalArgumentException("Invalid palette or packed block count");
        var states=new BlockState[palette.size()];var preserve=new boolean[palette.size()];var adaptations=new TreeSet<String>();
        for(int i=0;i<palette.size();i++){
            CompoundTag tag=palette.getCompound(i).copy();String name=tag.getString("Name");
            if(name.equals("structurize:blocksubstitution")){preserve[i]=true;continue;}
            if(name.equals("structurize:blocksolidsubstitution")){states[i]=Blocks.COBBLESTONE.defaultBlockState();adaptations.add("solid substitution → cobblestone");continue;}
            if(name.equals("structurize:blockfluidsubstitution"))throw new IllegalArgumentException("Fluid substitution is unsupported");
            if(name.equals("structurize:blockairsubstitution")||name.startsWith("minecolonies:blockhut")){
                states[i]=Blocks.AIR.defaultBlockState();adaptations.add("hut/air markers → empty space");continue;
            }
            // Explicit vanilla appearance adapters. DO material/container NBT is deliberately never executed.
            String mapped=switch(name){
                case "domum_ornamentum:plain","domum_ornamentum:one_crossed_rl","domum_ornamentum:one_crossed_lr","domum_ornamentum:double_crossed" -> "minecraft:oak_planks";
                case "domum_ornamentum:panel" -> "minecraft:oak_trapdoor";
                case "minecolonies:blockrack" -> "minecraft:barrel";
                default -> null;
            };
            if(mapped!=null){adaptations.add(name+" → "+mapped);tag.putString("Name",mapped);var props=tag.getCompound("Properties");props.remove("type");if(mapped.endsWith("oak_planks"))tag.remove("Properties");}
            else if(!name.startsWith("minecraft:"))throw new IllegalArgumentException("Unsupported block: "+name);
            if(source>0&&source<current&&mapped==null){
                var fixed=DataFixers.getDataFixer().update(References.BLOCK_STATE,new Dynamic<>(NbtOps.INSTANCE,tag),source,current).getValue();
                if(!(fixed instanceof CompoundTag compound))throw new IllegalArgumentException("Invalid migrated block state");tag=compound;
            }
            states[i]=state(tag);
        }
        var cells=new LinkedHashMap<BlockPos,BlockState>();var preserved=new HashSet<BlockPos>();
        for(int n=0;n<volume;n++){
            int index=(n%2==0?packed[n/2]>>>16:packed[n/2]&65535);
            if(index>=palette.size())throw new IllegalArgumentException("Palette index out of range");
            var pos=new BlockPos(n%x,n/(x*z),(n/x)%z);
            if(preserve[index])preserved.add(pos);else if(!states[index].isAir())cells.put(pos,states[index]);
        }
        var placements=new ArrayList<CottageBlueprint.Placement>();var consumed=new HashSet<BlockPos>();
        for(var entry:cells.entrySet()){
            var pos=entry.getKey();var state=entry.getValue();if(consumed.contains(pos))continue;
            var group=new ArrayList<CottageBlueprint.Cell>();group.add(new CottageBlueprint.Cell(pos,state));
            BlockPos other=null;BlockState counterpart=null;
            if(state.getBlock() instanceof DoorBlock||state.getBlock() instanceof DoublePlantBlock){
                if(state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)==DoubleBlockHalf.UPPER){other=pos.below();counterpart=state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER);}
                else{other=pos.above();counterpart=state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER);}
            }else if(state.getBlock() instanceof BedBlock){
                var facing=state.getValue(BedBlock.FACING);boolean head=state.getValue(BedBlock.PART)==BedPart.HEAD;
                other=pos.relative(head?facing.getOpposite():facing);counterpart=state.setValue(BedBlock.PART,head?BedPart.FOOT:BedPart.HEAD);
            }
            if(other!=null){
                if(!counterpart.equals(cells.get(other)))throw new IllegalArgumentException("Unpaired furniture at "+pos.toShortString());
                group.add(new CottageBlueprint.Cell(other,counterpart));consumed.add(other);
                group.sort(Comparator.comparingInt(c->c.state().getBlock() instanceof BedBlock?(c.state().getValue(BedBlock.PART)==BedPart.FOOT?0:1):c.offset().getY()));
            }
            consumed.add(pos);var item=state.getBlock().asItem();
            if(state.getBlock() instanceof WallTorchBlock)item=state.is(Blocks.SOUL_WALL_TORCH)?Items.SOUL_TORCH:state.is(Blocks.REDSTONE_WALL_TORCH)?Items.REDSTONE_TORCH:Items.TORCH;
            if(state.getBlock() instanceof WallSignBlock){var name=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().replace("_wall_sign","_sign");item=BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft",name));}
            if(item==Items.AIR)throw new IllegalArgumentException("Block has no placeable item: "+BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            int count=state.getBlock() instanceof SlabBlock&&state.getValue(SlabBlock.TYPE)==SlabType.DOUBLE?2:1;
            for(var property:List.of(BlockStateProperties.CANDLES,BlockStateProperties.PICKLES,BlockStateProperties.EGGS,BlockStateProperties.LAYERS))
                if(state.hasProperty(property))count=state.getValue(property);
            placements.add(new CottageBlueprint.Placement(item,count,group));
        }
        if(placements.isEmpty()||placements.size()>BuildingBlueprint.MAX_PLACEMENTS)throw new IllegalArgumentException("Empty or oversized build");
        // Structural courses first, then furniture and dependent decorations. Stairs remain accessible early.
        placements.sort(Comparator.comparingInt((CottageBlueprint.Placement p)->late(p.cells().get(0).state())?1:0).thenComparingInt(p->p.cells().get(0).offset().getY()));
        String note=adaptations.isEmpty()?"":"Vanilla adaptation: "+String.join("; ",adaptations);
        if(!root.getList("tile_entities",Tag.TAG_COMPOUND).isEmpty()||!root.getList("entities",Tag.TAG_COMPOUND).isEmpty())note+=(note.isEmpty()?"":"; ")+"entity/inventory NBT omitted";
        return new BuildingBlueprint(id,title,fingerprint+"-adapter1",x,y,z,placements,preserved,note);
    }
    private static boolean late(BlockState s){return s.getBlock() instanceof DoorBlock||s.getBlock() instanceof BedBlock||s.getBlock() instanceof TorchBlock||s.getBlock() instanceof LadderBlock||s.getBlock() instanceof SignBlock||s.getBlock() instanceof DoublePlantBlock;}
    private static BlockState state(CompoundTag tag){
        String name=tag.getString("Name");var key=ResourceLocation.tryParse(name);
        if(key==null||!BuiltInRegistries.BLOCK.containsKey(key)||!key.getNamespace().equals("minecraft"))throw new IllegalArgumentException("Unknown block: "+name);
        var block=BuiltInRegistries.BLOCK.get(key);
        if(block instanceof CommandBlock||block instanceof StructureBlock||block instanceof JigsawBlock||block==Blocks.BARRIER||block==Blocks.STRUCTURE_VOID||block==Blocks.TNT||block==Blocks.SPAWNER||block==Blocks.END_PORTAL||block==Blocks.END_PORTAL_FRAME||block==Blocks.NETHER_PORTAL||!block.defaultBlockState().getFluidState().isEmpty())throw new IllegalArgumentException("Unsupported special block: "+name);
        var state=block.defaultBlockState();var props=tag.getCompound("Properties");
        for(String property:props.getAllKeys()){
            var definition=block.getStateDefinition().getProperty(property);
            if(definition==null)throw new IllegalArgumentException("Unknown property: "+name+"."+property);
            state=property(state,definition,props.getString(property));
        }
        if(state.hasProperty(BlockStateProperties.WATERLOGGED)&&state.getValue(BlockStateProperties.WATERLOGGED))throw new IllegalArgumentException("Waterlogged blocks are unsupported");
        return state;
    }
    private static <T extends Comparable<T>> BlockState property(BlockState state,Property<T> property,String value){return state.setValue(property,property.getValue(value).orElseThrow(()->new IllegalArgumentException("Invalid property value: "+property.getName()+"="+value)));}
}
