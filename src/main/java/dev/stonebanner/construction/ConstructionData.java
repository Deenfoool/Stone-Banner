package dev.stonebanner.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Plans contain intentions only. Placed blocks and supplies remain in Minecraft world/inventories. */
public final class ConstructionData extends SavedData {
    public static final int LIMIT=64;
    public static final class Plan {
        public final long id;public final UUID owner;public final BlockPos origin;public final Rotation rotation;
        public boolean paused,completed;public String status="ready";
        private Plan(long id,UUID owner,BlockPos origin,Rotation rotation){this.id=id;this.owner=owner;this.origin=origin.immutable();this.rotation=rotation;}
    }
    private final Map<Long,Plan> plans=new LinkedHashMap<>();
    private long nextId=1;
    public static ConstructionData forLevel(ServerLevel level){return level.getDataStorage().computeIfAbsent(ConstructionData::load,ConstructionData::new,"stonebanner_construction");}
    public List<Plan> plans(){return List.copyOf(plans.values());}
    public Plan plan(long id){return plans.get(id);}
    public Plan at(BlockPos anchor){return plans.values().stream().filter(p->p.origin.equals(anchor)).findFirst().orElse(null);}
    public long add(UUID owner,BlockPos origin,Rotation rotation){
        if(owner==null||origin==null||rotation==null||plans.size()>=LIMIT||nextId>=Long.MAX_VALUE-1024
                ||plans.values().stream().anyMatch(p->CottageBlueprint.bounds(p.origin,p.rotation).intersects(CottageBlueprint.bounds(origin,rotation))))return -1;
        long id=nextId++;plans.put(id,new Plan(id,owner,origin,rotation));setDirty();return id;
    }
    public boolean edit(UUID owner,long id,String action){
        var plan=plans.get(id);if(plan==null||!plan.owner.equals(owner))return false;
        switch(action){case "pause"->plan.paused=true;case "resume"->plan.paused=false;case "cancel"->plans.remove(id);default->{return false;}}
        setDirty();return true;
    }
    public void complete(Plan plan){plan.completed=true;plan.status="complete";setDirty();}
    @Override public CompoundTag save(CompoundTag root){
        root.putLong("Next",nextId);var list=new ListTag();
        for(var p:plans.values()){var t=new CompoundTag();t.putLong("Id",p.id);t.putUUID("Owner",p.owner);t.putLong("Origin",p.origin.asLong());t.putInt("Rotation",p.rotation.ordinal());t.putBoolean("Paused",p.paused);t.putBoolean("Completed",p.completed);list.add(t);}
        root.put("Plans",list);return root;
    }
    public static ConstructionData load(CompoundTag root){
        var data=new ConstructionData();long maximum=0;
        for(var entry:root.getList("Plans",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)entry;long id=t.getLong("Id");int rotation=t.getInt("Rotation");
            if(data.plans.size()>=LIMIT||id<1||id>=Long.MAX_VALUE-1024||data.plans.containsKey(id)||!t.hasUUID("Owner")
                    ||!t.contains("Origin",Tag.TAG_LONG)||rotation<0||rotation>=Rotation.values().length)continue;
            var origin=BlockPos.of(t.getLong("Origin"));var orientation=Rotation.values()[rotation];
            if(data.plans.values().stream().anyMatch(p->CottageBlueprint.bounds(p.origin,p.rotation).intersects(CottageBlueprint.bounds(origin,orientation))))continue;
            var p=new Plan(id,t.getUUID("Owner"),origin,orientation);p.paused=t.getBoolean("Paused");p.completed=t.getBoolean("Completed");p.status=p.completed?"complete":"ready";
            data.plans.put(id,p);maximum=Math.max(maximum,id);
        }
        long next=root.getLong("Next");data.nextId=Math.max(maximum+1,next>0&&next<Long.MAX_VALUE-1024?next:1);return data;
    }
}
