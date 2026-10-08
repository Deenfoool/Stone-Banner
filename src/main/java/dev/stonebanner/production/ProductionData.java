package dev.stonebanner.production;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent owned fields and bills. Physical supplies and outputs are never stored as counters here. */
public final class ProductionData extends SavedData {
    public enum Mode { MAKE, MAINTAIN }
    public static final class Field {
        public final long id; public final UUID owner; public final BlockPos min,max; public final FarmCrop crop;
        public boolean paused, fertilize;
        Field(long id,UUID owner,BlockPos min,BlockPos max,FarmCrop crop){this.id=id;this.owner=owner;this.min=min.immutable();this.max=max.immutable();this.crop=crop;}
        public boolean contains(BlockPos p){return p.getY()==min.getY()&&p.getX()>=min.getX()&&p.getX()<=max.getX()&&p.getZ()>=min.getZ()&&p.getZ()<=max.getZ();}
    }
    public static final class Bill {
        public final long id; public final UUID owner; public final BlockPos station; public final ResourceLocation recipe;
        public Mode mode; public int amount; public int made; public boolean paused; public String status="ready";
        Bill(long id,UUID owner,BlockPos station,ResourceLocation recipe,Mode mode,int amount){this.id=id;this.owner=owner;this.station=station.immutable();this.recipe=recipe;this.mode=mode;this.amount=amount;}
        public boolean finished(){return mode==Mode.MAKE&&made>=amount;}
    }
    private final Map<Long,Field> fields=new LinkedHashMap<>();
    private final Map<Long,Bill> bills=new LinkedHashMap<>();
    private long nextId=1;
    int fieldCursor;
    public static ProductionData forLevel(ServerLevel level){return level.getDataStorage().computeIfAbsent(ProductionData::load,ProductionData::new,"stonebanner_production");}
    public Collection<Field> fields(){return List.copyOf(fields.values());}
    public Collection<Bill> bills(){return List.copyOf(bills.values());}
    public Field fieldAt(BlockPos p){return fields.values().stream().filter(f->f.contains(p)).findFirst().orElse(null);}
    public Field field(long id){return fields.get(id);}
    public Bill bill(long id){return bills.get(id);}
    public long addField(UUID owner,BlockPos a,BlockPos b,FarmCrop crop){
        if(owner==null||crop==null||a==null||b==null||a.getY()!=b.getY()||fields.size()>=256||nextId>=Long.MAX_VALUE-1024)return -1;
        var min=new BlockPos(Math.min(a.getX(),b.getX()),a.getY(),Math.min(a.getZ(),b.getZ()));
        var max=new BlockPos(Math.max(a.getX(),b.getX()),a.getY(),Math.max(a.getZ(),b.getZ()));
        long width=(long)max.getX()-min.getX()+1,depth=(long)max.getZ()-min.getZ()+1;
        if(width>256||depth>256||width*depth>256)return -1;
        if(fields.values().stream().anyMatch(f->f.min.getY()==min.getY()&&f.min.getX()<=max.getX()&&f.max.getX()>=min.getX()&&f.min.getZ()<=max.getZ()&&f.max.getZ()>=min.getZ()))return -1;
        long id=nextId++;fields.put(id,new Field(id,owner,min,max,crop));setDirty();return id;
    }
    public long addBill(UUID owner,BlockPos station,ResourceLocation recipe,Mode mode,int amount){
        if(owner==null||station==null||recipe==null||mode==null||amount<1||amount>4096||bills.size()>=512||nextId>=Long.MAX_VALUE-1024||bills.values().stream().anyMatch(b->b.station.equals(station)&&!b.owner.equals(owner)))return -1;
        long id=nextId++;bills.put(id,new Bill(id,owner,station,recipe,mode,amount));setDirty();return id;
    }
    public boolean edit(UUID owner,long id,String action){
        var f=fields.get(id);var b=bills.get(id);if(f==null&&b==null||!owner.equals(f!=null?f.owner:b.owner))return false;
        switch(action){case "remove"->{fields.remove(id);bills.remove(id);}case "pause"->{if(f!=null)f.paused=true;else b.paused=true;}case "resume"->{if(f!=null)f.paused=false;else b.paused=false;}case "fertilize"->{if(f==null)return false;f.fertilize=!f.fertilize;}default->{return false;}}
        setDirty();return true;
    }
    /** Keep identity, recipe, completed output and pause state when changing the target. */
    public boolean updateBill(UUID owner,long id,Mode mode,int amount){
        var bill=bills.get(id);
        if(bill==null||!bill.owner.equals(owner)||mode==null||amount<1||amount>4096)return false;
        bill.mode=mode;bill.amount=amount;bill.status=bill.finished()?"complete":"ready";
        setDirty();return true;
    }
    /** Swap adjacent orders at this station only; map/NBT order is the scheduling order. */
    public boolean moveBill(UUID owner,long id,int direction){
        var bill=bills.get(id);
        if(bill==null||!bill.owner.equals(owner)||(direction!=-1&&direction!=1))return false;
        var queue=bills.values().stream().filter(b->b.owner.equals(owner)&&b.station.equals(bill.station)).toList();
        int index=queue.indexOf(bill),target=index+direction;
        if(target<0||target>=queue.size())return false;
        long other=queue.get(target).id;
        var reordered=new LinkedHashMap<Long,Bill>();
        for(var entry:bills.entrySet()){
            long key=entry.getKey();
            if(key==id)reordered.put(other,bills.get(other));
            else if(key==other)reordered.put(id,bill);
            else reordered.put(key,entry.getValue());
        }
        bills.clear();bills.putAll(reordered);setDirty();return true;
    }
    @Override public CompoundTag save(CompoundTag root){
        root.putLong("Next",nextId);var fs=new ListTag();var bs=new ListTag();
        for(var f:fields.values()){var t=new CompoundTag();t.putLong("Id",f.id);t.putUUID("Owner",f.owner);t.putLong("Min",f.min.asLong());t.putLong("Max",f.max.asLong());t.putString("Crop",f.crop.name());t.putBoolean("Paused",f.paused);t.putBoolean("Fertilize",f.fertilize);fs.add(t);}
        for(var b:bills.values()){var t=new CompoundTag();t.putLong("Id",b.id);t.putUUID("Owner",b.owner);t.putLong("Station",b.station.asLong());t.putString("Recipe",b.recipe.toString());t.putString("Mode",b.mode.name());t.putInt("Amount",b.amount);t.putInt("Made",b.made);t.putBoolean("Paused",b.paused);bs.add(t);}
        root.put("Fields",fs);root.put("Bills",bs);return root;
    }
    public static ProductionData load(CompoundTag root){
        var data=new ProductionData();
        // Allocate validation scratch IDs above saved IDs, never over an already restored record.
        long savedMaximum=0;
        for(String key:new String[]{"Fields","Bills"})for(var entry:root.getList(key,Tag.TAG_COMPOUND)){
            long id=((CompoundTag)entry).getLong("Id");if(id>0&&id<Long.MAX_VALUE-2048)savedMaximum=Math.max(savedMaximum,id);
        }
        data.nextId=savedMaximum+1;
        for(var entry:root.getList("Fields",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)entry;long saved=t.getLong("Id");
            if(saved<=0||saved>=Long.MAX_VALUE-2048||!t.hasUUID("Owner")||data.fields.containsKey(saved))continue;
            try{
                long id=data.addField(t.getUUID("Owner"),BlockPos.of(t.getLong("Min")),BlockPos.of(t.getLong("Max")),FarmCrop.valueOf(t.getString("Crop")));
                if(id<0)continue;var f=data.fields.remove(id);var restored=new Field(saved,f.owner,f.min,f.max,f.crop);
                restored.paused=t.getBoolean("Paused");restored.fertilize=t.getBoolean("Fertilize");data.fields.put(saved,restored);
            }catch(IllegalArgumentException ignored){}
        }
        for(var entry:root.getList("Bills",Tag.TAG_COMPOUND)){
            var t=(CompoundTag)entry;long saved=t.getLong("Id");
            if(saved<=0||saved>=Long.MAX_VALUE-2048||!t.hasUUID("Owner")||data.bills.containsKey(saved)||data.fields.containsKey(saved))continue;
            try{
                var recipe=ResourceLocation.tryParse(t.getString("Recipe"));if(recipe==null)continue;
                long id=data.addBill(t.getUUID("Owner"),BlockPos.of(t.getLong("Station")),recipe,Mode.valueOf(t.getString("Mode")),t.getInt("Amount"));
                if(id<0)continue;var b=data.bills.remove(id);var restored=new Bill(saved,b.owner,b.station,b.recipe,b.mode,b.amount);
                restored.made=Math.max(0,Math.min(1000000,t.getInt("Made")));restored.paused=t.getBoolean("Paused");data.bills.put(saved,restored);
            }catch(IllegalArgumentException ignored){}
        }
        long maximum=java.util.stream.Stream.concat(data.fields.keySet().stream(),data.bills.keySet().stream()).mapToLong(Long::longValue).max().orElse(0);
        long next=root.getLong("Next");data.nextId=Math.max(maximum+1,next>0&&next<Long.MAX_VALUE-1024?next:1);data.setDirty(false);return data;
    }
}
