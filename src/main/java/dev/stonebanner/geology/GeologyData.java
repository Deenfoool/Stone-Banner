package dev.stonebanner.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Per-dimension community research and knowledge. Runtime tasks cannot reveal anything until completed. */
public final class GeologyData extends SavedData {
    public static final int MAX_SURVEYS=2048;
    private final Map<UUID,Knowledge> communities=new LinkedHashMap<>();
    public static GeologyData forLevel(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(GeologyData::load,GeologyData::new,"stonebanner_geology");
    }
    public Knowledge knowledge(UUID community) {
        if(!communities.containsKey(community)&&communities.size()>=128)throw new IllegalStateException("Community limit");
        return communities.computeIfAbsent(community,id->new Knowledge());
    }
    public boolean start(UUID id,BlockPos table) {
        var k=knowledge(id);
        if(k.tier>=GeologyRules.MAX_TIER) return false;
        if(k.remaining<=0) k.remaining=30*(k.tier+1);
        k.table=table.immutable();setDirty();return true;
    }
    public boolean advance(UUID id) {
        var k=knowledge(id);if(k.remaining<=0)return false;
        k.remaining--;if(k.remaining==0){k.tier++;setDirty();return true;}
        setDirty();return false;
    }
    public void surveyed(UUID id,int x,int z,int tier) {
        if(tier<1||tier>GeologyRules.MAX_TIER)throw new IllegalArgumentException("Survey tier");
        var k=knowledge(id);long pos=ChunkPos.asLong(x,z);
        if(!k.surveys.containsKey(pos)&&k.surveys.size()>=MAX_SURVEYS)k.surveys.remove(k.surveys.keySet().iterator().next());
        k.surveys.merge(pos,Math.min(tier,k.tier),Math::max);setDirty();
    }
    public static final class Knowledge {
        private int tier,remaining;
        private BlockPos table=BlockPos.ZERO;
        private final LinkedHashMap<Long,Integer> surveys=new LinkedHashMap<>();
        public int tier(){return tier;} public int remaining(){return remaining;} public BlockPos table(){return table;}
        public int surveyed(int x,int z){return surveys.getOrDefault(ChunkPos.asLong(x,z),0);}
    }
    @Override public CompoundTag save(CompoundTag root) {
        var list=new ListTag();
        communities.forEach((id,k)->{
            var t=new CompoundTag();t.putUUID("Community",id);t.putInt("Tier",k.tier);t.putInt("Remaining",k.remaining);t.putLong("Table",k.table.asLong());
            var surveys=new ListTag();k.surveys.forEach((pos,tier)->{var s=new CompoundTag();s.putLong("Chunk",pos);s.putInt("Tier",tier);surveys.add(s);});t.put("Surveys",surveys);list.add(t);
        });root.put("Communities",list);return root;
    }
    static GeologyData load(CompoundTag root) {
        var data=new GeologyData();var list=root.getList("Communities",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size()&&data.communities.size()<128;i++) {
            var t=list.getCompound(i);if(!t.hasUUID("Community"))continue;var id=t.getUUID("Community");if(data.communities.containsKey(id))continue;
            var k=data.knowledge(id);k.tier=Math.max(0,Math.min(4,t.getInt("Tier")));k.remaining=k.tier==4?0:Math.max(0,Math.min(30*(k.tier+1),t.getInt("Remaining")));k.table=BlockPos.of(t.getLong("Table"));
            var surveys=t.getList("Surveys",Tag.TAG_COMPOUND);
            for(int j=0;j<surveys.size()&&k.surveys.size()<MAX_SURVEYS;j++) {
                var s=surveys.getCompound(j);int tier=Math.min(k.tier,s.getInt("Tier"));if(tier>0)k.surveys.merge(s.getLong("Chunk"),tier,Math::max);
            }
        }return data;
    }
}
