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
        public final String blueprintId,fingerprint;public final int sizeX,sizeY,sizeZ;
        public net.minecraft.world.phys.AABB bounds(){return BuildingBlueprint.bounds(origin,rotation,sizeX,sizeY,sizeZ);}
        public final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> temporary=new LinkedHashMap<>();
        public ScaffoldRoute route;public boolean cleanup,cancelled;
        public boolean paused,completed;public String status="ready";
        private Plan(long id,UUID owner,BlockPos origin,Rotation rotation,String blueprintId,String fingerprint,int sizeX,int sizeY,int sizeZ){this.id=id;this.owner=owner;this.origin=origin.immutable();this.rotation=rotation;this.blueprintId=blueprintId;this.fingerprint=fingerprint;this.sizeX=sizeX;this.sizeY=sizeY;this.sizeZ=sizeZ;}
    }
    private final Map<Long,Plan> plans=new LinkedHashMap<>();
    private long nextId=1;
    public static ConstructionData forLevel(ServerLevel level){return level.getDataStorage().computeIfAbsent(ConstructionData::load,ConstructionData::new,"stonebanner_construction");}
    public List<Plan> plans(){return List.copyOf(plans.values());}
    public Plan plan(long id){return plans.get(id);}
    public Plan at(BlockPos anchor){return plans.values().stream().filter(p->p.origin.equals(anchor)).findFirst().orElse(null);}
    public long add(UUID owner,BlockPos origin,Rotation rotation){return add(owner,origin,rotation,BuildingBlueprint.cottage());}
    public long add(UUID owner,BlockPos origin,Rotation rotation,BuildingBlueprint blueprint){
        if(owner==null||origin==null||rotation==null||plans.size()>=LIMIT||nextId>=Long.MAX_VALUE-1024
                ||plans.values().stream().anyMatch(p->p.bounds().intersects(blueprint.bounds(origin,rotation))
                    ||p.temporary.keySet().stream().anyMatch(pos->blueprint.bounds(origin,rotation).contains(net.minecraft.world.phys.Vec3.atCenterOf(pos)))
                    ||p.route!=null&&p.route.blocks().keySet().stream().anyMatch(pos->blueprint.bounds(origin,rotation).contains(net.minecraft.world.phys.Vec3.atCenterOf(pos)))))return -1;
        long id=nextId++;plans.put(id,new Plan(id,owner,origin,rotation,blueprint.id(),blueprint.fingerprint(),blueprint.sizeX(),blueprint.sizeY(),blueprint.sizeZ()));setDirty();return id;
    }
    public boolean edit(UUID owner,long id,String action){
        var plan=plans.get(id);if(plan==null||!plan.owner.equals(owner))return false;
        switch(action){case "pause"->plan.paused=true;case "resume"->plan.paused=false;case "cancel"->{if(plan.temporary.isEmpty())plans.remove(id);else{plan.cancelled=true;plan.cleanup=true;plan.paused=false;plan.status="scaffold_cleanup";}}default->{return false;}}
        setDirty();return true;
    }
    public void finishCancel(Plan plan){if(plan.cancelled&&plan.temporary.isEmpty()){plans.remove(plan.id);setDirty();}}
    public boolean reserved(BlockPos pos,Plan except){return plans.values().stream().filter(p->p!=except).anyMatch(p->p.bounds().contains(net.minecraft.world.phys.Vec3.atCenterOf(pos))||p.temporary.containsKey(pos)||p.route!=null&&p.route.contains(pos));}
    public void forget(BlockPos pos){for(var plan:plans.values())if(plan.temporary.remove(pos)!=null){plan.cleanup=true;setDirty();}}
    public void complete(Plan plan){plan.completed=true;plan.status="complete";setDirty();}
    @Override public CompoundTag save(CompoundTag root){
        root.putLong("Next",nextId);var list=new ListTag();
        for(var p:plans.values()){var t=new CompoundTag();t.putLong("Id",p.id);t.putUUID("Owner",p.owner);t.putLong("Origin",p.origin.asLong());t.putInt("Rotation",p.rotation.ordinal());t.putString("Blueprint",p.blueprintId);t.putString("Fingerprint",p.fingerprint);t.putInt("SizeX",p.sizeX);t.putInt("SizeY",p.sizeY);t.putInt("SizeZ",p.sizeZ);t.putBoolean("Paused",p.paused);t.putBoolean("Completed",p.completed);t.putBoolean("Cleanup",p.cleanup);t.putBoolean("Cancelled",p.cancelled);
            var temporary=new ListTag();for(var cell:p.temporary.entrySet()){
                var tag=new CompoundTag();tag.putLong("Pos",cell.getKey().asLong());tag.put("State",NbtUtils.writeBlockState(cell.getValue()));temporary.add(tag);
            }t.put("Temporary",temporary);
            if(p.route!=null){var route=new CompoundTag();route.putLong("Base",p.route.base().asLong());route.putInt("Top",p.route.topY());route.putString("Side",p.route.side().getName());
                route.putLongArray("Bridge",p.route.bridge().stream().mapToLong(BlockPos::asLong).toArray());t.put("ScaffoldRoute",route);}
            list.add(t);}
        root.put("Plans",list);return root;
    }
    public static ConstructionData load(CompoundTag root){
        var data=new ConstructionData();long maximum=0;
        for(var entry:root.getList("Plans",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)entry;long id=t.getLong("Id");int rotation=t.getInt("Rotation");
            if(data.plans.size()>=LIMIT||id<1||id>=Long.MAX_VALUE-1024||data.plans.containsKey(id)||!t.hasUUID("Owner")
                    ||!t.contains("Origin",Tag.TAG_LONG)||rotation<0||rotation>=Rotation.values().length)continue;
            var origin=BlockPos.of(t.getLong("Origin"));var orientation=Rotation.values()[rotation];
            boolean legacy=!t.contains("Blueprint",Tag.TAG_STRING);
            String blueprint=legacy?"stonebanner:cottage":t.getString("Blueprint"),hash=legacy?"cottage-v1":t.getString("Fingerprint");
            int x=legacy?5:t.getInt("SizeX"),y=legacy?5:t.getInt("SizeY"),z=legacy?7:t.getInt("SizeZ");
            if(blueprint.isEmpty()||blueprint.length()>256||hash.isEmpty()||hash.length()>96||x<1||y<1||z<1||x>64||y>64||z>64||(long)x*y*z>BuildingBlueprint.MAX_VOLUME)continue;
            if(data.plans.values().stream().anyMatch(p->p.bounds().intersects(BuildingBlueprint.bounds(origin,orientation,x,y,z))))continue;
            var p=new Plan(id,t.getUUID("Owner"),origin,orientation,blueprint,hash,x,y,z);p.paused=t.getBoolean("Paused");p.completed=t.getBoolean("Completed");p.status=p.completed?"complete":"ready";
            p.cleanup=t.getBoolean("Cleanup");p.cancelled=t.getBoolean("Cancelled");
            var region=p.bounds().inflate(8);
            for(var cell:t.getList("Temporary",Tag.TAG_COMPOUND)){
                if(p.temporary.size()>=ScaffoldRoute.MAX_BLOCKS)break;var tag=(CompoundTag)cell;
                if(!tag.contains("Pos",Tag.TAG_LONG))continue;var pos=BlockPos.of(tag.getLong("Pos"));
                var state=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),tag.getCompound("State"));
                if(region.contains(net.minecraft.world.phys.Vec3.atCenterOf(pos))&&(state.is(net.minecraft.world.level.block.Blocks.COBBLESTONE)||state.is(net.minecraft.world.level.block.Blocks.LADDER)))p.temporary.put(pos,state);
            }
            if(t.contains("ScaffoldRoute",Tag.TAG_COMPOUND))try{
                var route=t.getCompound("ScaffoldRoute");var side=net.minecraft.core.Direction.byName(route.getString("Side"));var road=route.getLongArray("Bridge");
                if(side!=null&&road.length<=ScaffoldRoute.MAX_BRIDGE){
                    var candidate=new ScaffoldRoute(BlockPos.of(route.getLong("Base")),route.getInt("Top"),side,Arrays.stream(road).mapToObj(BlockPos::of).toList());
                    if(candidate.blocks().keySet().stream().allMatch(pos->region.contains(net.minecraft.world.phys.Vec3.atCenterOf(pos))))p.route=candidate;
                }
            }catch(IllegalArgumentException ignored){}
            if(!p.temporary.isEmpty()){p.completed=false;if(p.route==null)p.cleanup=true;}
            if(p.cancelled){p.completed=false;p.cleanup=true;}
            data.plans.put(id,p);maximum=Math.max(maximum,id);
        }
        long next=root.getLong("Next");data.nextId=Math.max(maximum+1,next>0&&next<Long.MAX_VALUE-1024?next:1);return data;
    }
}
