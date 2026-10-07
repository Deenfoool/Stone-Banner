package dev.stonebanner.client.control;

import dev.stonebanner.citizen.CitizenData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** One active inspector; late replies cannot repopulate a closed/different-world screen. */
public final class CitizenInventoryClientCache {
    private static ResourceLocation dimension;
    private static UUID citizen;
    private static CompoundTag snapshot;
    private CitizenInventoryClientCache(){}
    public static void begin(ResourceLocation world,UUID id){dimension=world;citizen=id;snapshot=null;}
    public static void update(ResourceLocation world,UUID id,CompoundTag data){if(id.equals(citizen)&&world.equals(dimension))snapshot=data.copy();}
    public static CitizenData snapshot(ResourceLocation world,UUID id){
        if(snapshot==null||!id.equals(citizen)||!world.equals(dimension))return null;
        var data=new CitizenData();data.load(snapshot.copy());return data;
    }
    public static void clear(){dimension=null;citizen=null;snapshot=null;}
}
