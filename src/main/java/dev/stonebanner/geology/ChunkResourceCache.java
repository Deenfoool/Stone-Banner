package dev.stonebanner.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.Tags;
import java.util.*;

/** Server-only counts. At most 8192 block reads per dimension tick; never loads or generates chunks. */
public final class ChunkResourceCache {
    private static final Map<ServerLevel,ChunkResourceCache> LEVELS=new WeakHashMap<>();
    private final LinkedHashMap<Long,Profile> profiles=new LinkedHashMap<>(64,.75f,true);
    private final LinkedHashSet<Long> queue=new LinkedHashSet<>();
    private Scan scan;
    public record Profile(Map<GeologyRules.Ore,Integer> counts,long time) { public Profile { counts=Map.copyOf(counts); } public boolean hasOre(){return counts.values().stream().anyMatch(n->n>0);} }
    private static final class Scan {
        final long key;final LevelChunk chunk;final EnumMap<GeologyRules.Ore,Integer> counts=new EnumMap<>(GeologyRules.Ore.class);int cursor;
        Scan(long key,LevelChunk chunk){this.key=key;this.chunk=chunk;}
    }
    public static ChunkResourceCache forLevel(ServerLevel level){return LEVELS.computeIfAbsent(level,l->new ChunkResourceCache());}
    public Profile request(ServerLevel level,int x,int z) {
        long key=ChunkPos.asLong(x,z);var p=profiles.get(key);
        if((p==null||level.getGameTime()-p.time>1200)&&queue.size()<128)queue.add(key);
        return p;
    }
    public static void invalidate(ServerLevel level,BlockPos pos) {
        var c=LEVELS.get(level);if(c==null)return;long key=ChunkPos.asLong(pos.getX()>>4,pos.getZ()>>4);
        if(c.profiles.remove(key)!=null)c.queue.add(key);
        if(c.scan!=null&&c.scan.key==key){c.scan=null;c.queue.add(key);}
    }
    public void tick(ServerLevel level) {
        if(scan==null&&!queue.isEmpty()) {
            long key=queue.iterator().next();queue.remove(key);
            var chunk=level.getChunkSource().getChunkNow(ChunkPos.getX(key),ChunkPos.getZ(key));
            if(chunk!=null)scan=new Scan(key,chunk);
        }
        if(scan==null)return;
        if(level.getChunkSource().getChunkNow(scan.chunk.getPos().x,scan.chunk.getPos().z)!=scan.chunk){scan=null;return;}
        int total=level.getHeight()*256,limit=Math.min(total,scan.cursor+8192);
        var pos=new BlockPos.MutableBlockPos();
        for(;scan.cursor<limit;scan.cursor++) {
            int i=scan.cursor;pos.set(scan.chunk.getPos().getMinBlockX()+(i&15),level.getMinBuildHeight()+(i>>8),scan.chunk.getPos().getMinBlockZ()+((i>>4)&15));
            var state=scan.chunk.getBlockState(pos);if(state.is(Tags.Blocks.ORES))scan.counts.merge(GeologyRules.Ore.of(state),1,Integer::sum);
        }
        if(scan.cursor==total) {
            profiles.put(scan.key,new Profile(scan.counts,level.getGameTime()));
            while(profiles.size()>512)profiles.remove(profiles.keySet().iterator().next());scan=null;
        }
    }
    public static void clear(ServerLevel level){LEVELS.remove(level);}
}
